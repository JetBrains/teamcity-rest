/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest;

import com.intellij.openapi.diagnostic.Logger;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.2009
 */
public class APIController extends BaseController implements ServletContextAware {
  Logger LOG = Logger.getInstance(APIController.class.getName());
  private JerseyWebComponent myWebComponent;
  private ConfigurableApplicationContext myConfigurableApplicationContext;
  private SecurityContextEx mySecurityContext;

  private final ClassLoader myClassloader;
  private String myAuthToken;

  public APIController(final SBuildServer server,
                       WebControllerManager webControllerManager,
                       final ConfigurableApplicationContext configurableApplicationContext,
                       final SecurityContextEx securityContext) throws ServletException {
    super(server);
    myConfigurableApplicationContext = configurableApplicationContext;
    mySecurityContext = securityContext;
    webControllerManager.registerController("/api/**", this);

    myClassloader = getClass().getClassLoader();

    try {
      myAuthToken = URLEncoder.encode(UUID.randomUUID().toString() + (new Date()).toString().hashCode(), "UTF-8");
      LOG.info("Authentication token for superuser generated: '" + myAuthToken + "'.");
    } catch (UnsupportedEncodingException e) {
      LOG.warn(e);
    }
  }

  private void init() throws ServletException {
    myWebComponent = new JerseyWebComponent();
    myWebComponent.setWebApplicationContext(myConfigurableApplicationContext);
    myWebComponent.init(new FilterConfig() {
      public String getFilterName() {
        return "jerseyFilter";
      }

      public ServletContext getServletContext() {
        //return APIController.this.getServletContext();
        // workaround for http://jetbrains.net/tracker/issue2/TW-7656
        for (ApplicationContext ctx = getApplicationContext(); ctx != null; ctx = ctx.getParent()) {
          if (ctx instanceof WebApplicationContext) {
            return ((WebApplicationContext)ctx).getServletContext();
          }
        }
        throw new RuntimeException("WebApplication context was not found.");
      }

      public String getInitParameter(final String s) {
        return null;
      }

      public Enumeration getInitParameterNames() {
        return new Vector<String>(Collections.<String>emptySet()).elements();
      }
    });
  }

  protected ModelAndView doHandle(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    ensureInitialized();

    boolean runAsSystem = false;
    String authToken = request.getParameter("authToken");
    if (authToken != null) {
      if (authToken.equals(getAuthToken())) {
        runAsSystem = true;
      } else {
        synchronized (this) {
          Thread.sleep(10000); //to prevent bruteforcing
        }
        response.sendError(403, "Wrong authToken specified");
        return null;
      }
    }

    // workaround for http://jetbrains.net/tracker/issue2/TW-7656
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(myClassloader);

    try {
      if (runAsSystem) {
        try {
          mySecurityContext.runAsSystem(new SecurityContextEx.RunAsAction() {
            public void run() throws Throwable {
              myWebComponent.doFilter(request, response, null);
            }
          });
        } catch (Throwable throwable) {
          LOG.debug(throwable);
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable.getMessage());
        }
      } else {
        myWebComponent.doFilter(request, response, null);
      }
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
    return null;
  }

  private void ensureInitialized() throws ServletException {
    //todo: check synchronization
    synchronized (this) {
      // workaround for http://jetbrains.net/tracker/issue2/TW-7656
      if (myWebComponent == null) {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(myClassloader);
        try {
          init();
        } finally {
          Thread.currentThread().setContextClassLoader(cl);
        }
      }
    }
  }

  private String getAuthToken() {
    return myAuthToken;
  }

}
