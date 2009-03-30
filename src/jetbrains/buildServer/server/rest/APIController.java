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

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuildServer;
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
  private JerseyWebComponent myWebComponent;
  private ConfigurableApplicationContext myConfigurableApplicationContext;

  private final ClassLoader myClassloader;

  public APIController(final SBuildServer server,
                       WebControllerManager webControllerManager,
                       final ConfigurableApplicationContext configurableApplicationContext) throws ServletException {
    super(server);
    myConfigurableApplicationContext = configurableApplicationContext;
    webControllerManager.registerController("/api/**", this);

    myClassloader = getClass().getClassLoader();
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
    //if (myWebComponent == null) {
    //  init();
    //}
    //myWebComponent.doFilter(request, response, null);
    // workaround for http://jetbrains.net/tracker/issue2/TW-7656

    //todo: check synchronization
    synchronized (this) {
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

    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(myClassloader);
    try {
      myWebComponent.doFilter(request, response, null);
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
    return null;
  }
}
