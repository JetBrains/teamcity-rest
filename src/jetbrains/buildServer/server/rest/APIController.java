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
import com.intellij.openapi.util.text.StringUtil;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.server.rest.jersey.JerseyWebComponent;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.web.util.SessionUser;
import jetbrains.buildServer.web.util.WebUtil;
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
  final Logger LOG = Logger.getInstance(APIController.class.getName());
  private JerseyWebComponent myWebComponent;
  private final ConfigurableApplicationContext myConfigurableApplicationContext;
  private final SecurityContextEx mySecurityContext;

  private final ClassLoader myClassloader;
  private String myAuthToken;
  private RequestPathTransformInfo myRequestPathTransformInfo;

  public APIController(final SBuildServer server,
                       WebControllerManager webControllerManager,
                       final ConfigurableApplicationContext configurableApplicationContext,
                       final SecurityContextEx securityContext,
                       final RequestPathTransformInfo requestPathTransformInfo,
                       final ServerPluginInfo pluginDescriptor) throws ServletException {
    super(server);
    setSupportedMethods(new String[]{METHOD_GET, METHOD_HEAD, METHOD_POST, "PUT", "OPTIONS", "DELETE"});

    myConfigurableApplicationContext = configurableApplicationContext;
    mySecurityContext = securityContext;
    myRequestPathTransformInfo = requestPathTransformInfo;

    myRequestPathTransformInfo.setOriginalPathPrefixes(addPrefix(getBindPaths(pluginDescriptor), Constants.URL_PREFIX));
    myRequestPathTransformInfo.setNewPathPrefix(Constants.API_URL);
    LOG.debug("Will use request mapping: " + myRequestPathTransformInfo);

    registerController(webControllerManager, getBindPaths(pluginDescriptor));

    myClassloader = getClass().getClassLoader();

    try {
      myAuthToken = URLEncoder.encode(UUID.randomUUID().toString() + (new Date()).toString().hashCode(), "UTF-8");
      LOG.info("Authentication token for superuser generated: '" + myAuthToken + "'.");
    } catch (UnsupportedEncodingException e) {
      LOG.warn(e);
    }
  }

  private List<String> addPrefix(final List<String> paths, final String prefix) {
    List<String> result = new ArrayList<String>(paths.size());
    for (String path : paths) {
      result.add(prefix + path);
    }
    return result;
  }

  private void registerController(final WebControllerManager webControllerManager, final List<String> bindPaths) {
    try {
      for (String controllerBindPath : bindPaths) {
        LOG.info("Binding REST API to path '" + controllerBindPath + "'");
        webControllerManager.registerController(controllerBindPath + "/**", this);
      }
    } catch (Exception e) {
      LOG.error("Error registering controller", e);
    }
  }

  private List<String> getBindPaths(final ServerPluginInfo pluginDescriptor) {
    String bindPath = pluginDescriptor.getParameterValue(Constants.BIND_PATH_PROPERTY_NAME);
    if (bindPath == null) {
      return Collections.singletonList(Constants.API_URL_SUFFIX);
    }

    final String[] bindPaths = bindPath.split(",");

    if (bindPath.length() == 0) {
      LOG.error("Invalid REST API bind path in plugin descriptor: '" + bindPath + "', using defaults");
      return Collections.singletonList(Constants.API_URL_SUFFIX);
    }

    return Arrays.asList(bindPaths);
  }

  private void init() throws ServletException {
    myWebComponent = new JerseyWebComponent();
    myWebComponent.setWebApplicationContext(myConfigurableApplicationContext);
    myWebComponent.init(new FilterConfig() {
      Map<String, String> initParameters = new HashMap<String, String>();

      {
//        initParameters.put("com.sun.jersey.config.property.WadlGeneratorConfig", "jetbrains.buildServer.server.rest.WadlGenerator");
        initParameters.put("com.sun.jersey.config.property.packages", "jetbrains.buildServer.server.rest.request");
      }

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
        return initParameters.get(s);
      }

      public Enumeration getInitParameterNames() {
        return new Vector<String>(initParameters.keySet()).elements();
      }
    });
  }

  protected ModelAndView doHandle(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    final long requestStartProcessing = System.nanoTime();
    if (LOG.isDebugEnabled()) {
      LOG.debug("REST API " + request.getMethod() + " request received: " +
                WebUtil.createPathWithParameters(request) + " , remote address: " + request.getRemoteAddr() +
                ", by user: " + LogUtil.describe(SessionUser.getUser(request)));
    }
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

    // patching request
    final HttpServletRequest actualRequest =
      new RequestWrapper(patchRequest(request, "Accept", "overrideAccept"), myRequestPathTransformInfo);

    try {
      if (runAsSystem) {
        try {
          mySecurityContext.runAsSystem(new SecurityContextEx.RunAsAction() {
            public void run() throws Throwable {
              myWebComponent.doFilter(actualRequest, response, null);
            }
          });
        } catch (Throwable throwable) {
          LOG.debug(throwable);
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable.getMessage());
        }
      } else {
        myWebComponent.doFilter(actualRequest, response, null);
      }
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
    if (LOG.isDebugEnabled()) {
      final long requestFinishProcessing = System.nanoTime();
      LOG.debug("REST API request processing finished in " + (requestFinishProcessing - requestStartProcessing) / 1000000 + " ms");
    }
    return null;
  }

  //todo: move to RequestWrapper
  private HttpServletRequest patchRequest(final HttpServletRequest request, final String headerName, final String parameterName) {
    final String newValue = request.getParameter(parameterName);
    if (!StringUtil.isEmpty(newValue)) {
      return modifyRequestHeader(request, headerName, newValue);
    }
    return request;
  }

  private HttpServletRequest modifyRequestHeader(final HttpServletRequest request, final String headerName, final String newValue) {
    return new HttpServletRequestWrapper(request) {
      @Override
      public String getHeader(final String name) {
        if (headerName.equalsIgnoreCase(name)) {
          return newValue;
        }
        return super.getHeader(name);
      }

      @Override
      public Enumeration getHeaders(final String name) {
        if (headerName.equalsIgnoreCase(name)) {
          return Collections.enumeration(Collections.singletonList(newValue));
        }
        return super.getHeaders(name);
      }
    };
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
        } catch (RuntimeException e) {
          //otherwise exception here is swallowed and logged nowhere
          LOG.error("Error initializing REST API: ", e);
          throw e;
        }
        finally {
          Thread.currentThread().setContextClassLoader(cl);
        }
      }
    }
  }

  private String getAuthToken() {
    return myAuthToken;
  }

}
