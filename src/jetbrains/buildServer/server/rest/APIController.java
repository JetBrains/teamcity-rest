/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.google.common.base.Stopwatch;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.core.util.FeaturesAndProperties;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.servlet.WebComponent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.ext.ExceptionMapper;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.interceptors.PathSet;
import jetbrains.buildServer.controllers.interceptors.auth.HttpAuthenticationManager;
import jetbrains.buildServer.controllers.interceptors.auth.HttpAuthenticationResult;
import jetbrains.buildServer.controllers.interceptors.auth.util.UnauthorizedResponseHelper;
import jetbrains.buildServer.plugins.PluginManager;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.data.RestContext;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperBase;
import jetbrains.buildServer.server.rest.jersey.ExtensionsAwareResourceConfig;
import jetbrains.buildServer.server.rest.jersey.JerseyWebComponent;
import jetbrains.buildServer.server.rest.jersey.WadlGenerator;
import jetbrains.buildServer.server.rest.request.*;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.web.CorsOrigins;
import jetbrains.buildServer.web.impl.RestApiFacade;
import jetbrains.buildServer.web.jsp.RestApiInternalRequestTag;
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

import static jetbrains.buildServer.util.Util.doUnderContextClassLoader;

/**
 * @author Yegor.Yarko
 * Date: 23.03.2009
 */
public class APIController extends BaseController implements ServletContextAware {
  public static final String REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL = "rest.compatibility.allowExternalIdAsInternal";
  public static final String INCLUDE_INTERNAL_ID_PROPERTY_NAME = "rest.beans.includeInternalId";
  public static final String LATEST_REST_API_PLUGIN_NAME = "rest-api";
  public static final String REST_RESPONSE_PRETTYFORMAT = "rest.response.prettyformat";
  public static final String REST_PREFER_OWN_BIND_PATHS = "rest.allow.bind.paths.override.for.plugins";
  private static final String CONTEXT_REQUEST_ARGUMENTS_PREFIX = RestApiInternalRequestTag.REQUEST_ARGUMENTS_PREFIX;
  private static String OUR_FIRST_BIND_PATH = null;
  private final Logger LOG;
  private final boolean myInternalAuthProcessing = TeamCityProperties.getBoolean("rest.cors.optionsRequest.allowUnauthorized");
  private final String[] myPathsWithoutAuth = new String[]{
    BuildRequest.BUILDS_ROOT_REQUEST_PATH + "/*/" + "statusIcon" + "*",
    BuildRequest.BUILDS_ROOT_REQUEST_PATH + "/aggregated" + "/*/" + "statusIcon" + "*",
    ServerRequest.SERVER_REQUEST_PATH + "/" + ServerRequest.SERVER_VERSION_RQUEST_PATH,
    RootApiRequest.VERSION,
    RootApiRequest.API_VERSION,
    Constants.EXTERNAL_APPLICATION_WADL_NAME,
    Constants.EXTERNAL_APPLICATION_WADL_NAME + "/xsd*.xsd",
    "/swagger**",
    NodesRequest.NODES_PATH
  };
  private final JerseyWebComponent myWebComponent;
  private final AtomicBoolean myWebComponentInitialized = new AtomicBoolean(false);
  private final ConfigurableApplicationContext myConfigurableApplicationContext;
  private final SecurityContextEx mySecurityContext;
  private final WebControllerManager myWebControllerManager;
  private final ServerPluginInfo myPluginDescriptor;
  private final AuthorizationInterceptor myAuthorizationInterceptor;
  @NotNull private final HttpAuthenticationManager myAuthManager;
  @NotNull private final PluginManager myPluginManager;
  private final ClassLoader myClassloader;
  private final RequestPathTransformInfo myRequestPathTransformInfo;
  private final PathSet myUnauthenticatedPathSet = new PathSet();

  private final CorsOrigins myAllowedOrigins = new CorsOrigins();
  private final CachingValues myDisabledRequests = new CachingValues();
  private String myAuthToken;

  public APIController(final SBuildServer server,
                       WebControllerManager webControllerManager,
                       final ConfigurableApplicationContext configurableApplicationContext,
                       final SecurityContextEx securityContext,
                       final RequestPathTransformInfo requestPathTransformInfo,
                       final ServerPluginInfo pluginDescriptor,
                       @NotNull final JerseyWebComponent jerseyWebComponent,
                       final AuthorizationInterceptor authorizationInterceptor,
                       @NotNull final HttpAuthenticationManager authManager,
                       @NotNull final PluginManager pluginManager) {
    super(server);
    LOG = Logger.getInstance(APIController.class.getName() + "/" + pluginDescriptor.getPluginName());
    myClassloader = getClass().getClassLoader();
    myWebControllerManager = webControllerManager;
    myPluginDescriptor = pluginDescriptor;
    myWebComponent = jerseyWebComponent;
    myAuthorizationInterceptor = authorizationInterceptor;
    myPluginManager = pluginManager;
    myAuthManager = authManager;
    setSupportedMethods(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT, HttpMethod.OPTIONS, HttpMethod.DELETE);

    myConfigurableApplicationContext = configurableApplicationContext;
    mySecurityContext = securityContext;
    myRequestPathTransformInfo = requestPathTransformInfo;

    server.addListener(new BuildServerAdapter() {
      @Override
      public void pluginsLoaded() {
        initializeController();
        //todo: check if REST plugin is actually loaded and throw dedicated error otherwise
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

  private static void addEntries(final Map<String, String> map, final List<String> keys, final String value) {
    for (String key : keys) {
      map.put(key, value);
    }
  }

  private static boolean processRequestAuthentication(@NotNull final HttpServletRequest request,
                                                      @NotNull final HttpServletResponse response,
                                                      @NotNull final HttpAuthenticationManager authManager) throws IOException {
    if (WebUtil.isAjaxRequest(request)) { // do not try to authenticate ajax requests, see TW-56019, TW-35022
      new UnauthorizedResponseHelper(response, false).send(request, null);
      return true;
    }

    boolean canRedirect = canRedirect(request);
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

  /**
   * see {@link jetbrains.buildServer.controllers.interceptors.AuthorizationInterceptorImpl.preHandle()}
   *
   * @param request
   * @return
   */
  private static boolean canRedirect(@NotNull HttpServletRequest request) {
    return UserAgentUtil.isBrowser(request) && !WebUtil.isWebSocketUpgradeRequest(request);
  }

  @Nullable
  public static String getFirstBindPath() {
    return OUR_FIRST_BIND_PATH;
  }

  public void initializeController() {
    final List<String> unfilteredOriginalBindPaths = getBindPaths(myPluginDescriptor);
    if (unfilteredOriginalBindPaths.isEmpty()) {
      final String message = "Error while initializing REST API " + getPluginIdentifyingText() + ": No bind paths found. Corrupted plugin?";
      LOG.error(message + " Reporting plugin load error.");
      throw new RuntimeException(message);
    }
    OUR_FIRST_BIND_PATH = unfilteredOriginalBindPaths.get(0);

    final List<String> originalBindPaths = filterOtherPlugins(unfilteredOriginalBindPaths);
    if (originalBindPaths.isEmpty()) {
      final String message = "Error while initializing REST API " + getPluginIdentifyingText() + ": No unique bind paths found. Conflicting plugins set is installed.";
      LOG.error(message + " Reporting plugin load error.");
      throw new RuntimeException(message);
    }

    LOG.info("Listening for paths " + originalBindPaths + " in " + getPluginIdentifyingText());

    // For GraphQL API we don't want path-related features that we have for REST paths.
    List<String> graphqlPaths = originalBindPaths.stream().filter(path -> path.startsWith(Constants.GRAPHQL_API_URL)).collect(Collectors.toList());
    originalBindPaths.removeAll(graphqlPaths);

    List<String> bindPaths = new ArrayList<>(originalBindPaths);
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.HTTP_AUTH_PREFIX)));
    bindPaths.addAll(addPrefix(originalBindPaths, StringUtil.removeTailingSlash(WebUtil.GUEST_AUTH_PREFIX)));

    Map<String, String> transformBindPaths = new HashMap<>();
    addEntries(transformBindPaths, bindPaths, Constants.API_URL);
    addEntries(transformBindPaths, addWadlSuffix(bindPaths), Constants.JERSEY_APPLICATION_WADL_NAME);

    myRequestPathTransformInfo.setPathMapping(transformBindPaths);
    LOG.debug("Will use request mapping: " + myRequestPathTransformInfo);

    originalBindPaths.addAll(graphqlPaths);
    registerController(originalBindPaths);

    if (LATEST_REST_API_PLUGIN_NAME.equals(myPluginDescriptor.getPluginName())) {
      //initialize on start only the latest plugin; others will be initialized on first request
      initJerseyWebComponentAsync();
    }
  }

  private void initJerseyWebComponentAsync() {
    new NamedDaemonThreadFactory("REST API initializer for " + getPluginIdentifyingText()).newThread(() -> {
      initJerseyWebComponent(() -> "via background initial initialization");
    }).start();
  }

  private void initJerseyWebComponent(@NotNull final ValueWithDefault.Value<String> contextDetails) {
    if (!myWebComponentInitialized.get()) {
      NamedThreadFactory.executeWithNewThreadName("Initializing Jersey for " + getPluginIdentifyingText() + " " + contextDetails.get(), () -> {
        synchronized (myWebComponentInitialized) {
          if (myWebComponentInitialized.get()) return;

          try {
            // workaround for http://jetbrains.net/tracker/issue2/TW-7656
            doUnderContextClassLoader(myClassloader, new FuncThrow<Void, Throwable>() {
              public Void apply() throws Throwable {
                final Set<ConfigurableApplicationContext> contexts = new HashSet<>();
                contexts.add(myConfigurableApplicationContext);
                for (RESTControllerExtension extension : getExtensions()) {
                  contexts.add(extension.getContext());
                }
                myWebComponent.setContexts(contexts);
                // ExtensionsAwareResourceConfig not initialized yet. We should wait for all extensions to load first.
                // Now it's time to initialize and scan for extensions.
                final ExtensionsAwareResourceConfig config = getApplicationContext().getBean(ExtensionsAwareResourceConfig.class);
                config.onReload();
                myWebComponent.init(createJerseyConfig());
                return null;
              }
            });

            myWebComponentInitialized.set(true);
          } catch (Throwable e) {
            LOG.error("Error initializing REST API " + contextDetails.get() + ": " + e + ExceptionMapperBase.addKnownExceptionsData(e, ""), e);
            ExceptionUtil.rethrowAsRuntimeException(e);
          }
        }
      });
    }
  }

  private List<String> filterOtherPlugins(final List<String> bindPaths) {
    final String pluginNames =
      TeamCityProperties.getProperty(REST_PREFER_OWN_BIND_PATHS, LATEST_REST_API_PLUGIN_NAME); //by default allow only the latest/main plugin paths to be overriden
    final String[] pluginNamesList = pluginNames.split(",");

    final String ownPluginName = myPluginDescriptor.getPluginName();
    boolean overridesAllowed = false;
    for (String pluginName : pluginNamesList) {
      if (ownPluginName.equals(pluginName.trim())) {
        overridesAllowed = true;
        break;
      }
    }
    if (!overridesAllowed) {
      return bindPaths;
    }
    final ArrayList<String> result = new ArrayList<>(bindPaths);

    final Collection<PluginInfo> allPlugins = myPluginManager.getDetectedPlugins();
    //is the plugin actually loaded? Might need to check only the successfully loaded plugins
    for (PluginInfo plugin : allPlugins) {
      if (ownPluginName.equals(plugin.getPluginName())) {
        continue;
      }
      if (plugin instanceof ServerPluginInfo) {
        final PluginDescriptor pluginDescriptor = (ServerPluginInfo)plugin; //TeamCity API issue: cast
        String bindPath = pluginDescriptor.getParameterValue(Constants.BIND_PATH_PROPERTY_NAME);
        if (!StringUtil.isEmpty(bindPath)) {
          final List<String> pathToExclude = getBindPaths(pluginDescriptor);
          if (result.removeAll(pathToExclude)) {
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

  public String getPluginIdentifyingText() {
    return "plugin '" + myPluginDescriptor.getPluginName() + "'";
  }

  private List<String> addPrefix(final List<String> paths, final String prefix) {
    List<String> result = new ArrayList<>(paths.size());
    for (String path : paths) {
      result.add(prefix + path);
    }
    return result;
  }

  private List<String> addWadlSuffix(final List<String> paths) {
    List<String> result = new ArrayList<>(paths.size());
    for (String path : paths) {
      result.add(path + Constants.EXTERNAL_APPLICATION_WADL_NAME);
    }
    return result;
  }

  private void registerController(final List<String> bindPaths) {
    try {
      for (String controllerBindPath : bindPaths) {
        LOG.debug("Binding REST API " + getPluginIdentifyingText() + " to path '" + controllerBindPath + "'");
        myWebControllerManager.registerController(controllerBindPath + "/**", this);
        if (myInternalAuthProcessing &&
            !controllerBindPath.equals(
              Constants.API_URL)) {// this is a special case as it contains paths of other plugins under it. Thus, it cannot be registered as not requiring auth
          myAuthorizationInterceptor.addPathNotRequiringAuth(controllerBindPath + "/**");
          for (String path : myPathsWithoutAuth) {
            myUnauthenticatedPathSet.addPath(controllerBindPath + path);
          }
        } else {
          for (String path : myPathsWithoutAuth) {
            myAuthorizationInterceptor.addPathNotRequiringAuth(controllerBindPath + path);
            myUnauthenticatedPathSet.addPath(controllerBindPath + path);
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
      LOG.error("No property '" + Constants.BIND_PATH_PROPERTY_NAME + "' found in plugin descriptor file in " + getPluginIdentifyingText() + ". Corrupted plugin?");
      return Collections.emptyList();
    }

    final String[] bindPaths = bindPath.split(",");

    if (bindPath.length() == 0) {
      LOG.error("Invalid REST API bind path in plugin descriptor: '" + bindPath + "'in " + getPluginIdentifyingText() + ". Corrupted plugin?");
      return Collections.emptyList();
    }

    return Arrays.asList(bindPaths);
  }

  private FilterConfig createJerseyConfig() {
    return new FilterConfig() {
      private final Map<String, String> initParameters = new HashMap<>();

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

      public Enumeration<String> getInitParameterNames() {
        return new Vector<>(initParameters.keySet()).elements();
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

    final Stopwatch requestStart = Stopwatch.createStarted();
    boolean shouldLogToDebug = shouldLogToDebug(request);

    String requestType = getRequestType(request);

    if (shouldLogToDebug && TeamCityProperties.getBoolean("rest.log.debug.requestStart") && LOG.isDebugEnabled()) {
      LOG.debug(() -> "REST API " + requestType + " request received: " + WebUtil.getRequestDump(request));
    }

    try {
      initJerseyWebComponent(() -> "during request " + WebUtil.getRequestDump(request));
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

    final AtomicBoolean errorEncountered = new AtomicBoolean(false);
    final boolean runAsSystemActual = runAsSystem;
    try {

      final boolean corsRequest = myAllowedOrigins.processCorsOriginHeaders(request, response, LOG);
      if (corsRequest && request.getMethod().equalsIgnoreCase("OPTIONS")) {
        //handling browser pre-flight requests
        LOG.debug("Pre-flight OPTIONS request detected, replying with status 204");
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return null;
      }
      if (myInternalAuthProcessing && SessionUser.getUser(request) == null && !requestForMyPathNotRequiringAuth(request)) {
        if (processRequestAuthentication(request, response, myAuthManager)) {
          return null;
        }
        //TeamCity API issue: SecurityContext.getAuthorityHolder is "SYSTEM" if request is not authorized
        final boolean notAuthorizedRequest = mySecurityContext.isSystemAccess();
        if (notAuthorizedRequest) {
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.getWriter().write("TeamCity core was unable to handle authentication (no current user).");
          LOG.warn("TeamCity core was unable to handle authentication (no current user), replying with 401 status. Request details: " + WebUtil.getRequestDump(request));
          return null;
        }
      }

      patchThread(() -> WebUtil.getRequestDump(request), requestType, () -> {
        // workaround for http://jetbrains.net/tracker/issue2/TW-7656
        doUnderContextClassLoader(myClassloader, new FuncThrow<Void, Throwable>() {
          public Void apply() throws Throwable {
            return new RestContext(name -> request.getAttribute(CONTEXT_REQUEST_ARGUMENTS_PREFIX + name))
              .run(() -> {
                // patching request
                final HttpServletRequest actualRequest =
                  new RequestWrapper(patchRequestWithAcceptHeader(request), myRequestPathTransformInfo);

                if (runAsSystemActual) {
                  if (shouldLogToDebug && LOG.isDebugEnabled()) LOG.debug("Executing request with system security level");
                  mySecurityContext.runAsSystem(() -> {
                    myWebComponent.doFilter(actualRequest, response, null);
                  });
                } else {
                  myWebComponent.doFilter(actualRequest, response, null);
                }
                return null;
              });
          }
        });
        return null;
      });
    } catch (Throwable throwable) {
      errorEncountered.set(true);
      processException(request, response, throwable);
    } finally {
      if (shouldLogToDebug && LOG.isDebugEnabled()) {
        LOG.debug(() -> "REST API " + requestType + " request processing finished in " +
                        TimePrinter.createMillisecondsFormatter().formatTime(requestStart.elapsed(TimeUnit.MILLISECONDS)) +
                        (errorEncountered.get() ? " with errors, original " : ", ") + "status code: " + getStatus(response) + ", request: " + WebUtil.getRequestDump(request));
      }
    }
    return null;
  }

  @NotNull
  private String getRequestType(@NotNull HttpServletRequest request) {
    String requestType = "";
    if (RestApiFacade.isInternal(request)) requestType = "internal";
    if (request.getHeader("X-TeamCity-Essential") != null) {
      requestType = requestType.isEmpty() ? "essential" : requestType + " " + "essential";
    }
    return requestType;
  }

  private void processException(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final Throwable throwable) {
    // Sometimes Jersey throws IllegalArgumentException and probably other without utilizing ExceptionMappers
    // also exceptions during serialization seem to not pass through the mappers (too late already?) - see TW-56461
    // forcing plain text error reporting

    // see ContainerResponse.mapException
    WebApplication wa = myWebComponent.getWebApplication();
    if (wa != null) {
      @SuppressWarnings("rawtypes") ExceptionMapper em = wa.getExceptionMapperContext().find(throwable.getClass());
      if (em instanceof ExceptionMapperBase) {
        @SuppressWarnings("rawtypes") ExceptionMapperBase mapper = (ExceptionMapperBase)em;
        ExceptionMapperBase.ResponseData responseData;
        try {
          //noinspection unchecked
          responseData = mapper.getResponseData(throwable);
        } catch (Exception e) {
          LOG.warnAndDebugDetails("Error while trying to retrieve mapped exception message", e);
          reportRestErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable, null, Level.WARN, request);
          return;
        }
        reportRestErrorResponse(response, responseData.getResponseStatus(), throwable, responseData.getMessage(), Level.WARN, request);
        return;
      }
    }
    reportRestErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, throwable, null, Level.WARN, request);
  }

  private void patchThread(@NotNull final Supplier<String> requestDump, @NotNull final String requestType,
                           @NotNull final FuncThrow<Void, Throwable> action) throws Throwable {
    if (TeamCityProperties.getBoolean("rest.debug.APIController.patchThread")) {
      StringBuilder activityName = new StringBuilder();
      activityName.append("Processing REST");
      if (!requestType.isEmpty()) {
        activityName.append(" ").append(requestType);
      }
      activityName.append(" request ");
      activityName.append(requestDump.get());

      NamedThreadFactory.executeWithNewThreadNameFuncThrow(activityName.toString(), action);
    } else {
      action.apply();
    }
  }

  private boolean shouldLogToDebug(@NotNull final HttpServletRequest request) {
    return !RestApiFacade.isInternal(request) || TeamCityProperties.getBoolean("rest.log.debug.internalRequests");
  }

  private boolean matches(final String requestURI, final String[] disabledRequests) {
    for (String requestPattern : disabledRequests) {
      if (requestURI.matches(requestPattern)) {
        return true;
      }
    }
    return false;
  }

  private String getStatus(final HttpServletResponse response) {
    String result = "<unknown>";
    try {
      result = String.valueOf(response.getStatus());
    } catch (NoSuchMethodError ignored) {
      //ignore: this occurs for Servlet API < 3.0
    }
    return result;
  }

  @NotNull
  public String[] getBasePackages() {
    return new String[]{
      "org.fasterxml.jackson.jaxrs",
      "jetbrains.buildServer.server.rest.request",
      "jetbrains.buildServer.server.rest.data",
      "jetbrains.buildServer.server.rest.swagger",
    };
  }

  @NotNull
  public Collection<RESTControllerExtension> getExtensions() {
    return myServer.getExtensions(RESTControllerExtension.class);
  }


  //todo: move to RequestWrapper

  public void reportRestErrorResponse(@NotNull final HttpServletResponse response,
                                      final int statusCode,
                                      @Nullable final Throwable e,
                                      @Nullable final String message,
                                      final Level level,
                                      @NotNull final HttpServletRequest request) {
    final String responseText =
      ExceptionMapperBase.getResponseTextAndLogRestErrorErrorMessage(statusCode, e, message, statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR, level, request);

    try {
      response.setStatus(statusCode);
      response.setContentType("text/plain");
      response.getWriter().print(responseText);
    } catch (Throwable nestedException) {
      final String message1 = "Error while adding error description into response: " + nestedException;
      if (!ExceptionMapperBase.isCommonExternalError(nestedException)) {
        LOG.warn(message1);
      }
      LOG.debug(message1, nestedException);
    }
  }

  @NotNull
  private HttpServletRequest patchRequestWithAcceptHeader(@NotNull final HttpServletRequest request) {
    final String newValue = request.getParameter("overrideAccept");
    if (!StringUtil.isEmpty(newValue)) {
      return modifyAcceptHeader(request, newValue);
    }
    return request;
  }

  @NotNull
  private HttpServletRequest modifyAcceptHeader(@NotNull final HttpServletRequest request, @NotNull final String newValue) {
    return new HttpServletRequestWrapper(request) {
      @Override
      public String getHeader(final String name) {
        if ("Accept".equalsIgnoreCase(name)) {
          return newValue;
        }
        return super.getHeader(name);
      }

      @Override
      public Enumeration<String> getHeaders(final String name) {
        if ("Accept".equalsIgnoreCase(name)) {
          return Collections.enumeration(Collections.singletonList(newValue));
        }
        return super.getHeaders(name);
      }
    };
  }

  private String getAuthToken() {
    return myAuthToken;
  }

  class CachingValues {
    private String myCachedValue;
    private String myCachedDelimiter;
    private String[] myParsedValues;

    @NotNull
    private synchronized String[] getParsedValues(@NotNull final String currentValue, @Nullable final String delimiter) {
      if (!Objects.equals(myCachedValue, currentValue) || !Objects.equals(myCachedDelimiter, delimiter)) {
        myCachedValue = currentValue;
        myCachedDelimiter = delimiter;
        myParsedValues = currentValue.split(StringUtil.isEmpty(delimiter) ? "," : delimiter);
        for (int i = 0; i < myParsedValues.length; i++) {
          myParsedValues[i] = myParsedValues[i].trim();
        }
      }
      return myParsedValues;
    }
  }
}
