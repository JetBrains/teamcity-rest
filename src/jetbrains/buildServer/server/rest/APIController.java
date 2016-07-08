/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.core.util.FeaturesAndProperties;
import com.sun.jersey.spi.container.servlet.WebComponent;
import java.io.IOException;
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
import jetbrains.buildServer.controllers.interceptors.PathSet;
import jetbrains.buildServer.controllers.interceptors.auth.HttpAuthenticationManager;
import jetbrains.buildServer.controllers.interceptors.auth.HttpAuthenticationResult;
import jetbrains.buildServer.plugins.PluginManager;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperUtil;
import jetbrains.buildServer.server.rest.jersey.ExtensionsAwareResourceConfig;
import jetbrains.buildServer.server.rest.jersey.JerseyWebComponent;
import jetbrains.buildServer.server.rest.jersey.WadlGenerator;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.request.RootApiRequest;
import jetbrains.buildServer.server.rest.request.ServerRequest;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FuncThrow;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.SessionUser;
import jetbrains.buildServer.web.util.UserAgentUtil;
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
  private Logger LOG = Logger.getInstance(APIController.class.getName());
  public static final String REST_CORS_ORIGINS_INTERNAL_PROPERTY_NAME = "rest.cors.origins";
  public static final String REST_RESPONSE_PRETTYFORMAT = "rest.response.prettyformat";
  public static final String REST_PREFER_OWN_BIND_PATHS = "rest.allow.bind.paths.override.for.plugins";

  private final boolean myInternalAuthProcessing = TeamCityProperties.getBoolean("rest.cors.optionsRequest.allowUnauthorized");
  private final String[] myPathsWithoutAuth = new String[]{
    BuildRequest.BUILDS_ROOT_REQUEST_PATH + "/*/" + BuildRequest.STATUS_ICON_REQUEST_NAME,
    ServerRequest.SERVER_REQUEST_PATH + "/" + ServerRequest.SERVER_VERSION_RQUEST_PATH,
    RootApiRequest.VERSION,
    RootApiRequest.API_VERSION,
    Constants.EXTERNAL_APPLICATION_WADL_NAME,
    Constants.EXTERNAL_APPLICATION_WADL_NAME + "/xsd*.xsd"};

  private JerseyWebComponent myWebComponent;
  private final ConfigurableApplicationContext myConfigurableApplicationContext;
  private final SecurityContextEx mySecurityContext;
  private final WebControllerManager myWebControllerManager;
  private final ServerPluginInfo myPluginDescriptor;
  private final ExtensionHolder myExtensionHolder;
  private final AuthorizationInterceptor myAuthorizationInterceptor;
  @NotNull private final HttpAuthenticationManager myAuthManager;
  @NotNull private final PluginManager myPluginManager;

  private ClassLoader myClassloader;
  private String myAuthToken;
  private final RequestPathTransformInfo myRequestPathTransformInfo;
  private final PathSet myUnauthenticatedPathSet = new PathSet();

  private final CachingValuesFromInternalProperty myAllowedOrigins = new CachingValuesFromInternalProperty();
  private final CachingValuesFromInternalProperty myDisabledRequests = new CachingValuesFromInternalProperty();
  public static String ourFirstBindPath;

  public APIController(final SBuildServer server,
                       WebControllerManager webControllerManager,
                       final ConfigurableApplicationContext configurableApplicationContext,
                       final SecurityContextEx securityContext,
                       final RequestPathTransformInfo requestPathTransformInfo,
                       final ServerPluginInfo pluginDescriptor,
                       final ExtensionHolder extensionHolder,
                       final AuthorizationInterceptor authorizationInterceptor,
                       @NotNull final HttpAuthenticationManager authManager,
                       @NotNull final PluginManager pluginManager) throws ServletException {
    super(server);
    LOG = Logger.getInstance(APIController.class.getName() + "/" + pluginDescriptor.getPluginName());
    myWebControllerManager = webControllerManager;
    myPluginDescriptor = pluginDescriptor;
    myExtensionHolder = extensionHolder;
    myAuthorizationInterceptor = authorizationInterceptor;
    myPluginManager = pluginManager;
    myAuthManager = authManager;
    setSupportedMethods(new String[]{METHOD_GET, METHOD_HEAD, METHOD_POST, "PUT", "OPTIONS", "DELETE"});

    myConfigurableApplicationContext = configurableApplicationContext;
    mySecurityContext = securityContext;
    myRequestPathTransformInfo = requestPathTransformInfo;

    server.addListener(new BuildServerAdapter() {
      @Override
      public void pluginsLoaded() {
        initializeController();
      }
    });

    if (TeamCityProperties.getBoolean("rest.use.authToken")) {
      try {
        myAuthToken = URLEncoder.encode(UUID.randomUUID().toString() + (new Date()).toString().hashCode(), "UTF-8");
        LOG.info("Authentication token for Super user generated: '" + myAuthToken + "' (" + getPluginIdentifyingText() + ")");
      } catch (UnsupportedEncodingException e) {
        LOG.warn(e);
      }
    }
  }

  public void initializeController() {
    final List<String> unfilteredOriginalBindPaths = getBindPaths(myPluginDescriptor);
    if (unfilteredOriginalBindPaths.isEmpty()) {
      final String message = "Error while initializing REST API " + getPluginIdentifyingText() + ": No bind paths found. Corrupted plugin?";
      LOG.error(message + " Reporting plugin load error.");
      throw new RuntimeException(message);
    }
    ourFirstBindPath = unfilteredOriginalBindPaths.get(0);

    final List<String> originalBindPaths = filterOtherPlugins(unfilteredOriginalBindPaths);
    if (originalBindPaths.isEmpty()) {
      final String message = "Error while initializing REST API " + getPluginIdentifyingText() + ": No unique bind paths found. Conflicting plugins set is installed.";
      LOG.error(message + " Reporting plugin load error.");
      throw new RuntimeException(message);
    }

    LOG.info("Listening for paths " + originalBindPaths + " in " + getPluginIdentifyingText());

    List<String> bindPaths = new ArrayList<String>(originalBindPaths);
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.HTTP_AUTH_PREFIX)));
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.GUEST_AUTH_PREFIX)));

    Map<String, String> transformBindPaths = new HashMap<String, String>();
    addEntries(transformBindPaths, bindPaths, Constants.API_URL);
    addEntries(transformBindPaths, addSuffix(bindPaths, Constants.EXTERNAL_APPLICATION_WADL_NAME), Constants.JERSEY_APPLICATION_WADL_NAME);

    myRequestPathTransformInfo.setPathMapping(transformBindPaths);
    LOG.debug("Will use request mapping: " + myRequestPathTransformInfo);

    registerController(myWebControllerManager, originalBindPaths);

    myClassloader = getClass().getClassLoader();
  }

  private List<String> filterOtherPlugins(final List<String> bindPaths) {
    final String pluginNames = TeamCityProperties.getProperty(REST_PREFER_OWN_BIND_PATHS, "rest-api"); //by default allow only the latest/main plugin paths to be overriden
    final String[] pluginNamesList = pluginNames.split(",");

    final String ownPluginName = myPluginDescriptor.getPluginName();
    boolean overridesAllowed = false;
    for (String pluginName : pluginNamesList) {
      if (ownPluginName.equals(pluginName.trim())){
        overridesAllowed = true;
        break;
      }
    }
    if (!overridesAllowed) {
      return bindPaths;
    }
    final ArrayList<String> result = new ArrayList<String>(bindPaths);

    final Collection<PluginInfo> allPlugins = myPluginManager.getDetectedPlugins();
    //is the plugin actually loaded? Might need to check only the successfully loaded plugins
    for (PluginInfo plugin : allPlugins) {
      if (ownPluginName.equals(plugin.getPluginName())) {
        continue;
      }
      if (plugin instanceof PluginDescriptor) {
        final PluginDescriptor pluginDescriptor = (ServerPluginInfo)plugin; //TeamCity API issue: cast
        String bindPath = pluginDescriptor.getParameterValue(Constants.BIND_PATH_PROPERTY_NAME);
        if (!StringUtil.isEmpty(bindPath)) {
          final List<String> pathToExclude = getBindPaths(pluginDescriptor);
          if (result.removeAll(pathToExclude)){
            LOG.info("Excluding paths from handling by plugin '" + ownPluginName + "' as they are handled by plugin '" + plugin.getPluginName() + "': " +
                     pathToExclude + ". Set " + REST_PREFER_OWN_BIND_PATHS + " internal property to empty value to prohibit overriding." +
                     " (The property sets comma-separated list of plugin names which bind paths can be overriden.)");
          }
        }
      } else {
        LOG.warn("Cannot get plugin info for plugin '" + plugin.getPluginName() + "', ignoring it while filtering out REST bind paths. " +
                 "This is a bug in the REST plugin, please report it to JetBrains.");
      }
    }
    return result;
  }

  private String getPluginIdentifyingText() {
    return "plugin '" + myPluginDescriptor.getPluginName() + "'";
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
        LOG.debug("Binding REST API " + getPluginIdentifyingText() + " to path '" + controllerBindPath + "'");
        webControllerManager.registerController(controllerBindPath + "/**", this);
        if (myInternalAuthProcessing &&
            !controllerBindPath.equals(Constants.API_URL)) {// this is a special case as it contains paths of other plugins under it. Thus, it cannot be registered as not requiring auth
          myAuthorizationInterceptor.addPathNotRequiringAuth(controllerBindPath + "/**");
          for (String path : myPathsWithoutAuth) {
            myUnauthenticatedPathSet.addPath(controllerBindPath + path);
          }
        } else {
          for (String path : myPathsWithoutAuth) {
            myAuthorizationInterceptor.addPathNotRequiringAuth(controllerBindPath + path);
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Error registering controller in " + getPluginIdentifyingText(), e);
    }
  }

  private boolean requestForMyPathNotRequiringAuth(final HttpServletRequest request) {
    return myUnauthenticatedPathSet.matches(WebUtil.getOriginalPathWithoutAuthenticationType(request));
  }

  private List<String> getBindPaths(@NotNull final PluginDescriptor pluginDescriptor) {
    String bindPath = pluginDescriptor.getParameterValue(Constants.BIND_PATH_PROPERTY_NAME);
    if (bindPath == null) {
      LOG.error("No property '" + Constants.BIND_PATH_PROPERTY_NAME + "' found in pugin descriptor file in " + getPluginIdentifyingText() + ". Corrupted plugin?");
      return Collections.emptyList();
    }

    final String[] bindPaths = bindPath.split(",");

    if (bindPath.length() == 0) {
      LOG.error("Invalid REST API bind path in plugin descriptor: '" + bindPath + "'in " + getPluginIdentifyingText() + ". Corrupted plugin?");
      return Collections.emptyList();
    }

    return Arrays.asList(bindPaths);
  }

  private void init() throws ServletException {
    myWebComponent = new JerseyWebComponent(myPluginDescriptor.getPluginName());
    myWebComponent.setExtensionHolder(myExtensionHolder);
    final Set<ConfigurableApplicationContext> contexts = new HashSet<ConfigurableApplicationContext>();
    contexts.add(myConfigurableApplicationContext);
    for (RESTControllerExtension extension : getExtensions()) {
      contexts.add(extension.getContext());
    }
    myWebComponent.setContexts(contexts);
    // ExtensionsAwareResourceConfig not initialized yet. We should wait for all extensions to load first.
    // Now it's time to initilize and scan for extensions.
    final ExtensionsAwareResourceConfig config = getApplicationContext().getBean(ExtensionsAwareResourceConfig.class);
    config.onReload();
    myWebComponent.init(createJerseyConfig());
  }

  private FilterConfig createJerseyConfig() {
    return new FilterConfig() {
      private final Map<String, String> initParameters = new HashMap<String, String>();

      {
        initParameters.put(ResourceConfig.PROPERTY_WADL_GENERATOR_CONFIG, WadlGenerator.class.getCanonicalName());
        initParameters.put(JSONConfiguration.FEATURE_POJO_MAPPING, "true");
        initParameters.put(WebComponent.RESOURCE_CONFIG_CLASS, ExtensionsAwareResourceConfig.class.getCanonicalName());
        if (TeamCityProperties.getBoolean(APIController.REST_RESPONSE_PRETTYFORMAT)) {
          initParameters.put(FeaturesAndProperties.FEATURE_FORMATTED, "true");
        }
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
      final String message = TeamCityProperties.getProperty("rest.disable.message", "REST API is disabled on this TeamCity server with 'rest.disable' internal property.");
      reportRestErrorResponse(response, HttpServletResponse.SC_NOT_IMPLEMENTED, null, message, Level.INFO, request);
      return null;
    }

    if (matches(WebUtil.getRequestUrl(request),
                myDisabledRequests.getParsedValues(TeamCityProperties.getProperty("rest.disable.requests"), TeamCityProperties.getProperty("rest.disable.requests.delimiter")))) {
      final String defaultMessage = "Requests for URL \"" + WebUtil.getRequestUrl(request) +
                                    "\" are disabled in REST API on this server with 'rest.disable.requests' internal property.";
      final String message = TeamCityProperties.getProperty("rest.disable.message", defaultMessage);
      reportRestErrorResponse(response, HttpServletResponse.SC_NOT_IMPLEMENTED, null, message, Level.INFO, request);
      return null;
    }

    final long requestStartProcessing = System.nanoTime();
    if (LOG.isDebugEnabled()) {
      LOG.debug("REST API request received: " + WebUtil.getRequestDump(request) + ", " + getPluginIdentifyingText());
    }
    try {
      ensureInitialized();
    } catch (Throwable throwable) {
      reportRestErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable, "Error initializing REST API", Level.ERROR, request);
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
          reportRestErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, null, "Wrong authToken specified",
                                  Level.INFO, request);
          return null;
        }
      }
    }

    final boolean runAsSystemActual = runAsSystem;
    try {

      final boolean corsRequest = processCorsRequest(request, response);
      if (corsRequest && request.getMethod().equalsIgnoreCase("OPTIONS")){
        //handling browser pre-flight requests
        LOG.debug("Pre-flight OPTIONS request detected, replying with status 204");
        response.setStatus(204);
        return null;
      }
      if (myInternalAuthProcessing && SessionUser.getUser(request) == null && !requestForMyPathNotRequiringAuth(request)){
        if (processRequestAuthentication(request, response, myAuthManager)){
          return null;
        }
        if (SessionUser.getUser(request) == null){
          response.setStatus(401);
          response.getWriter().write("TeamCity core was unable to handle authentication.");
          return null;
        }
      }

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
      reportRestErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable, null, Level.WARN, request);
      //todo: process exception mappers here to use correct error presentation in the log
    } finally{
      if (LOG.isDebugEnabled()) {
        final long requestFinishProcessing = System.nanoTime();
        LOG.debug("REST API request processing finished in " +
                  TimeUnit.MILLISECONDS.convert(requestFinishProcessing - requestStartProcessing, TimeUnit.NANOSECONDS) + " ms, status code: " +
                  getStatus(response) + ", " + getPluginIdentifyingText());
      }
    }
    return null;
  }

  private static boolean processRequestAuthentication(@NotNull final HttpServletRequest request,
                                                   @NotNull final HttpServletResponse response,
                                                   @NotNull final HttpAuthenticationManager authManager) throws IOException {
      boolean canRedirect = UserAgentUtil.isBrowser(request);
      final HttpAuthenticationResult authResult = authManager.processAuthenticationRequest(request, response, canRedirect);
      if (canRedirect) {
        final String redirectUrl = authResult.getRedirectUrl();
        if (redirectUrl != null) {
          response.sendRedirect(redirectUrl);
          return true;
        }
      }

      if (authResult.getType() != HttpAuthenticationResult.Type.AUTHENTICATED) {
        authManager.processUnauthenticatedRequest(request, response, null, canRedirect);
        return true;
      }
    return false;
  }

  private boolean matches(final String requestURI, final  String[] disabledRequests) {
    for (String requestPattern : disabledRequests) {
      if (requestURI.matches(requestPattern)){
        return true;
      }
    }
    return false;
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

  private boolean processCorsRequest(final HttpServletRequest request, final HttpServletResponse response) {
    final String origin = request.getHeader("Origin");
    if (StringUtil.isNotEmpty(origin)) {
      final String[] originsArray = myAllowedOrigins.getParsedValues(TeamCityProperties.getProperty(REST_CORS_ORIGINS_INTERNAL_PROPERTY_NAME), ",");
      if (ArrayUtil.contains(origin, originsArray)) {
        addOriginHeaderToResponse(response, origin);
        addOtherHeadersToResponse(request, response);
        return true;
      } else if (ArrayUtil.contains("*", originsArray)) {
        LOG.debug("Got CORS request from origin '" + origin + "', but this origin is not allowed. However, '*' is. Replying with '*'." +
                  " Add the origin to '" + REST_CORS_ORIGINS_INTERNAL_PROPERTY_NAME +
                  "' internal property (comma-separated) to trust the applications hosted on the domain. Current allowed origins are: " +
                  Arrays.toString(originsArray));
        addOriginHeaderToResponse(response, "*");
        return true;
      } else {
        LOG.debug("Got CORS request from origin '" + origin + "', but this origin is not allowed. Add the origin to '" +
                  REST_CORS_ORIGINS_INTERNAL_PROPERTY_NAME +
                  "' internal property (comma-separated) to trust the applications hosted on the domain. Current allowed origins are: " +
                  Arrays.toString(originsArray));
      }
    }
    return false;
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

  @NotNull
  public String[] getBasePackages() {
    return new String[]{"org.codehaus.jackson.jaxrs","jetbrains.buildServer.server.rest.request"};
  }

  @NotNull
  public Collection<RESTControllerExtension> getExtensions() {
    return myServer.getExtensions(RESTControllerExtension.class);
  }

  class CachingValuesFromInternalProperty{
    private String myCachedValue;
    private String[] myParsedValues;

    @NotNull
    private synchronized String[] getParsedValues(@NotNull final String currentValue, @Nullable final String delimiter) {
      if (myCachedValue == null || !myCachedValue.equals(currentValue)) {
        myCachedValue = currentValue;
        myParsedValues = currentValue.split(StringUtil.isEmpty(delimiter) ? "," : delimiter);
        for (int i = 0; i < myParsedValues.length; i++) {
          myParsedValues[i] = myParsedValues[i].trim();
        }
      }
      return myParsedValues;
    }
  }

  public void reportRestErrorResponse(@NotNull final HttpServletResponse response,
                                             final int statusCode,
                                             @Nullable final Throwable e,
                                             @Nullable final String message,
                                             final Level level,
                                             @NotNull final HttpServletRequest request) {
    final String responseText =
      ExceptionMapperUtil.getResponseTextAndLogRestErrorErrorMessage(statusCode, e, message, statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR, level, request);
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
