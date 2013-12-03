/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.core.util.FeaturesAndProperties;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperUtil;
import jetbrains.buildServer.server.rest.jersey.JerseyWebComponent;
import jetbrains.buildServer.server.rest.jersey.WadlGenerator;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.request.ServerRequest;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FuncThrow;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  public static final String REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL = "rest.compatibility.allowExternalIdAsInternal";
  public static final String INCLUDE_INTERNAL_ID_PROPERTY_NAME = "rest.beans.includeInternalId";
  final static Logger LOG = Logger.getInstance(APIController.class.getName());
  public static final String REST_CORS_ORIGINS_INTERNAL_PROPERTY_NAME = "rest.cors.origins";
  public static final String REST_RESPONSE_PRETTYFORMAT = "rest.response.prettyformat";
  private JerseyWebComponent myWebComponent;
  private final ConfigurableApplicationContext myConfigurableApplicationContext;
  private final SecurityContextEx mySecurityContext;
  private final ServerPluginInfo myPluginDescriptor;
  private final ExtensionHolder myExtensionHolder;
  private final AuthorizationInterceptor myAuthorizationInterceptor;

  private final ClassLoader myClassloader;
  private String myAuthToken;
  private final RequestPathTransformInfo myRequestPathTransformInfo;

  public APIController(final SBuildServer server,
                       WebControllerManager webControllerManager,
                       final ConfigurableApplicationContext configurableApplicationContext,
                       final SecurityContextEx securityContext,
                       final RequestPathTransformInfo requestPathTransformInfo,
                       final ServerPluginInfo pluginDescriptor,
                       final ExtensionHolder extensionHolder,
                       final AuthorizationInterceptor authorizationInterceptor) throws ServletException {
    super(server);
    myPluginDescriptor = pluginDescriptor;
    myExtensionHolder = extensionHolder;
    myAuthorizationInterceptor = authorizationInterceptor;
    setSupportedMethods(new String[]{METHOD_GET, METHOD_HEAD, METHOD_POST, "PUT", "OPTIONS", "DELETE"});

    myConfigurableApplicationContext = configurableApplicationContext;
    mySecurityContext = securityContext;
    myRequestPathTransformInfo = requestPathTransformInfo;

    final List<String> originalBindPaths = getBindPaths(pluginDescriptor);
    List<String> bindPaths = new ArrayList<String>(originalBindPaths);
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.HTTP_AUTH_PREFIX)));
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.GUEST_AUTH_PREFIX)));

    Map<String, String> transformBindPaths = new HashMap<String, String>();
    addEntries(transformBindPaths, bindPaths, Constants.API_URL);
    addEntries(transformBindPaths, addSuffix(bindPaths, Constants.EXTERNAL_APPLICATION_WADL_NAME), Constants.JERSEY_APPLICATION_WADL_NAME);

    myRequestPathTransformInfo.setPathMapping(transformBindPaths);
    LOG.debug("Will use request mapping: " + myRequestPathTransformInfo);

    registerController(webControllerManager, originalBindPaths);

    myClassloader = getClass().getClassLoader();

    if (TeamCityProperties.getBoolean("rest.use.authToken")) {
      try {
        myAuthToken = URLEncoder.encode(UUID.randomUUID().toString() + (new Date()).toString().hashCode(), "UTF-8");
        LOG.info("Authentication token for Super user generated: '" + myAuthToken + "' (plugin '" + myPluginDescriptor.getPluginName() +
                 "', listening for paths " + originalBindPaths + ").");
      } catch (UnsupportedEncodingException e) {
        LOG.warn(e);
      }
    }
  }

  private static void addEntries(final Map<String, String> map, final List<String> keys, final String value) {
    for (String key : keys) {
      map.put(key, value);
    }
  }

  private List<String> addPrefix(final List<String> paths, final String prefix) {
    List<String> result = new ArrayList<String>(paths.size());
    for (String path : paths) {
      result.add(prefix + path);
    }
    return result;
  }

  private List<String> addSuffix(final List<String> paths, final String suffix) {
    List<String> result = new ArrayList<String>(paths.size());
    for (String path : paths) {
      result.add(path + suffix);
    }
    return result;
  }

  private void registerController(final WebControllerManager webControllerManager, final List<String> bindPaths) {
    try {
      for (String controllerBindPath : bindPaths) {
        LOG.debug("Binding REST API to path '" + controllerBindPath + "'");
        webControllerManager.registerController(controllerBindPath + "/**", this);
        myAuthorizationInterceptor.addPathNotRequiringAuth(controllerBindPath + BuildRequest.BUILDS_ROOT_REQUEST_PATH + "/*/" + BuildRequest.STATUS_ICON_REQUEST_NAME);
        myAuthorizationInterceptor.addPathNotRequiringAuth(controllerBindPath + ServerRequest.API_SERVER_URL + "/" + ServerRequest.SERVER_VERSION_RQUEST_PATH);
        myAuthorizationInterceptor.addPathNotRequiringAuth(controllerBindPath + Constants.EXTERNAL_APPLICATION_WADL_NAME);
      }
    } catch (Exception e) {
      LOG.error("Error registering controller", e);
    }
  }

  private List<String> getBindPaths(final ServerPluginInfo pluginDescriptor) {
    String bindPath = pluginDescriptor.getParameterValue(Constants.BIND_PATH_PROPERTY_NAME);
    if (bindPath == null) {
      return Collections.singletonList(Constants.API_URL);
    }

    final String[] bindPaths = bindPath.split(",");

    if (bindPath.length() == 0) {
      LOG.error("Invalid REST API bind path in plugin descriptor: '" + bindPath + "', using defaults");
      return Collections.singletonList(Constants.API_URL);
    }

    return Arrays.asList(bindPaths);
  }

  private void init() throws ServletException {
    myWebComponent = new JerseyWebComponent();
    myWebComponent.setExtensionHolder(myExtensionHolder);
    myWebComponent.setWebApplicationContext(myConfigurableApplicationContext);
    myWebComponent.init(createJerseyConfig());
  }

  private FilterConfig createJerseyConfig() {
    return new FilterConfig() {
      Map<String, String> initParameters = new HashMap<String, String>();

      {
        initParameters.put(ResourceConfig.PROPERTY_WADL_GENERATOR_CONFIG, WadlGenerator.class.getCanonicalName());
        initParameters.put(JSONConfiguration.FEATURE_POJO_MAPPING, "true");
        initParameters.put(PackagesResourceConfig.PROPERTY_PACKAGES, "org.codehaus.jackson.jaxrs;jetbrains.buildServer.server.rest.request;" + getPackagesFromExtensions());
        if (TeamCityProperties.getBoolean(APIController.REST_RESPONSE_PRETTYFORMAT)) {
          initParameters.put(FeaturesAndProperties.FEATURE_FORMATTED, "true");
        }
      }

      private String getPackagesFromExtensions() {
        return StringUtil.join(myServer.getExtensions(RESTControllerExtension.class), new Function<RESTControllerExtension, String>() {
          public String fun(final RESTControllerExtension restControllerExtension) {
            return restControllerExtension.getPackage();
          }
        }, ";");
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
    };
  }

  protected ModelAndView doHandle(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) throws Exception {
    if (TeamCityProperties.getBoolean("rest.disable")) {
      reportRestErrorResponse(response, HttpServletResponse.SC_NOT_IMPLEMENTED, null,
                              "REST API is disabled on TeamCity server with 'rest.disable' internal property.", request.getRequestURI(),
                              Level.INFO);
      return null;
    }

    final long requestStartProcessing = System.nanoTime();
    if (LOG.isDebugEnabled()) {
      LOG.debug("REST API request received: " + WebUtil.getRequestDump(request));
    }
    try {
      ensureInitialized();
    } catch (Throwable throwable) {
      reportRestErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable, "Error initializing REST API", request.getRequestURI(), Level.ERROR);
      return null;
    }

    boolean runAsSystem = false;
    if (TeamCityProperties.getBoolean("rest.use.authToken")) {
      String authToken = request.getParameter("authToken");
      if (StringUtil.isNotEmpty(authToken) && StringUtil.isNotEmpty(getAuthToken())) {
        if (authToken.equals(getAuthToken())) {
          runAsSystem = true;
        } else {
          synchronized (this) {
            Thread.sleep(10000); //to prevent brute-forcing
          }
          reportRestErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, null, "Wrong authToken specified", request.getRequestURI(),
                                  Level.INFO);
          return null;
        }
      }
    }

    final boolean runAsSystemActual = runAsSystem;
    try {

      processCorsRequest(request, response);

      // workaround for http://jetbrains.net/tracker/issue2/TW-7656
      jetbrains.buildServer.util.Util.doUnderContextClassLoader(getClass().getClassLoader(), new FuncThrow<Void, Throwable>() {
        public Void apply() throws Throwable {
          // patching request
          final HttpServletRequest actualRequest =
            new RequestWrapper(patchRequest(request, "Accept", "overrideAccept"), myRequestPathTransformInfo);

          if (runAsSystemActual) {
            LOG.debug("Executing request with system security level");
            mySecurityContext.runAsSystem(new SecurityContextEx.RunAsAction() {
              public void run() throws Throwable {
                myWebComponent.doFilter(actualRequest, response, null);
              }
            });
          } else {
            myWebComponent.doFilter(actualRequest, response, null);
          }
          return null;
        }
      });

    } catch (Throwable throwable) {
      // Sometimes Jersey throws IllegalArgumentException and probably other without utilizing ExceptionMappers
      // forcing plain text error reporting
      reportRestErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable, null, request.getRequestURI(), Level.WARN);
    } finally{
      if (LOG.isDebugEnabled()) {
        final long requestFinishProcessing = System.nanoTime();
        LOG.debug("REST API request processing finished in " +
                  TimeUnit.MILLISECONDS.convert(requestFinishProcessing - requestStartProcessing, TimeUnit.NANOSECONDS) + " ms, status code: " +
                  getStatus(response));
      }
    }
    return null;
  }

  private String getStatus(final HttpServletResponse response) {
    String result = "<unknown>";
    try {
      result = String.valueOf(response.getStatus());
    } catch (NoSuchMethodError e) {
      //ignore: this occurs for Servlet API < 3.0
    }
    return result;
  }

  private void processCorsRequest(final HttpServletRequest request, final HttpServletResponse response) {
    final String origin = request.getHeader("Origin");
    if (StringUtil.isNotEmpty(origin)) {
      final String[] originsArray = getAllowedOrigins();
      if (ArrayUtil.contains(origin, originsArray)) {
        addOriginHeaderToResponse(response, origin);
        addOtherHeadersToResponse(request, response);
      } else if (ArrayUtil.contains("*", originsArray)){
        LOG.debug("Got CORS request from origin '" + origin + "', but this origin is not allowed. However, '*' is. Replying with '*'." +
                  " Add the origin to '" + REST_CORS_ORIGINS_INTERNAL_PROPERTY_NAME +
                  "' internal property (comma-separated) to trust the applications hosted on the domain. Current allowed origins are: " +
                  Arrays.toString(originsArray));
        addOriginHeaderToResponse(response, "*");
      }else {
        LOG.debug("Got CORS request from origin '" + origin + "', but this origin is not allowed. Add the origin to '" +
                  REST_CORS_ORIGINS_INTERNAL_PROPERTY_NAME +
                  "' internal property (comma-separated) to trust the applications hosted on the domain. Current allowed origins are: " +
                  Arrays.toString(originsArray));
      }
    }
  }

  private void addOriginHeaderToResponse(final HttpServletResponse response, final String origin) {
    response.addHeader("Access-Control-Allow-Origin", origin);
  }

  private void addOtherHeadersToResponse(final HttpServletRequest request, final HttpServletResponse response) {
    response.addHeader("Access-Control-Allow-Methods", request.getHeader("Access-Control-Request-Method"));
    response.addHeader("Access-Control-Allow-Credentials", "true");

    //this will actually not function for OPTION request until http://youtrack.jetbrains.com/issue/TW-22019 is fixed
    response.addHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
  }

  private String myAllowedOrigins;
  private String[] myOriginsArray;

  @NotNull
  private synchronized String[] getAllowedOrigins() {
    final String allowedOrigins = TeamCityProperties.getProperty(REST_CORS_ORIGINS_INTERNAL_PROPERTY_NAME);
    if (myAllowedOrigins == null || !myAllowedOrigins.equals(allowedOrigins)) {
      myAllowedOrigins = allowedOrigins;
      myOriginsArray = allowedOrigins.split(",");
      for (int i = 0; i < myOriginsArray.length; i++) {
        myOriginsArray[i] = myOriginsArray[i].trim();
      }
    }
    return myOriginsArray;
  }

  public static void reportRestErrorResponse(@NotNull final HttpServletResponse response,
                                             final int statusCode,
                                             @Nullable final Throwable e,
                                             @Nullable final String message,
                                             @NotNull String requestUri, final Level level) {
    final String responseText =
      ExceptionMapperUtil.getResponseTextAndLogRestErrorErrorMessage(statusCode, e, message, requestUri, statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR, level);
    response.setStatus(statusCode);
    response.setContentType("text/plain");

    try {
      response.getWriter().print(responseText);
    } catch (Throwable nestedException) {
      final String message1 = "Error while adding error description into response: " + nestedException.getMessage();
      LOG.warn(message1);
      LOG.debug(message1, nestedException);
    }
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
          myWebComponent = null;
          throw e;
        } catch (Error e) {
          LOG.error("Error initializing REST API: ", e);
          myWebComponent = null;
          throw e;
        } catch (ServletException e) {
          LOG.error("Error initializing REST API: ", e);
          myWebComponent = null;
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
