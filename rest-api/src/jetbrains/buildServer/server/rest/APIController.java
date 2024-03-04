/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.ext.ExceptionMapper;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.interceptors.PathSet;
import jetbrains.buildServer.controllers.interceptors.auth.HttpAuthenticationManager;
import jetbrains.buildServer.controllers.interceptors.auth.HttpAuthenticationResult;
import jetbrains.buildServer.controllers.interceptors.auth.util.UnauthorizedResponseHelper;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.data.RestContext;
import jetbrains.buildServer.server.rest.jersey.ExceptionMapperBase;
import jetbrains.buildServer.server.rest.jersey.JerseyWebComponent;
import jetbrains.buildServer.server.rest.jersey.JerseyWebComponentInitializer;
import jetbrains.buildServer.server.rest.request.*;
import jetbrains.buildServer.server.rest.util.PluginUtil;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.web.CorsOrigins;
import jetbrains.buildServer.web.impl.RestApiFacade;
import jetbrains.buildServer.web.jsp.RestApiInternalRequestTag;
import jetbrains.buildServer.web.util.SessionUser;
import jetbrains.buildServer.web.util.UserAgentUtil;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.log4j.Level;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.spi.ExceptionMappers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.servlet.ModelAndView;

import static jetbrains.buildServer.util.Util.doUnderContextClassLoader;

/**
 * This class is repsponsible for routing requests between several rest plugin versions, if any.
 * Also triggers Jersey initialization if it's not initialized yet.
 */
@Component
public class APIController extends BaseController implements ServletContextAware {
  public static final String REST_COMPATIBILITY_ALLOW_EXTERNAL_ID_AS_INTERNAL = "rest.compatibility.allowExternalIdAsInternal";
  public static final String INCLUDE_INTERNAL_ID_PROPERTY_NAME = "rest.beans.includeInternalId";
  public static final String REST_RESPONSE_PRETTYFORMAT = "rest.response.prettyformat";
  private static final String CONTEXT_REQUEST_ARGUMENTS_PREFIX = RestApiInternalRequestTag.REQUEST_ARGUMENTS_PREFIX;
  public static final String[] PATHS_WITHOUT_AUTH = new String[] {
    BuildRequest.BUILDS_ROOT_REQUEST_PATH + "/*/" + "statusIcon" + "*",
    BuildRequest.BUILDS_ROOT_REQUEST_PATH + "/aggregated" + "/*/" + "statusIcon" + "*",
    ServerRequest.SERVER_REQUEST_PATH + "/" + ServerRequest.SERVER_VERSION_RQUEST_PATH,
    RootApiRequest.VERSION,
    RootApiRequest.API_VERSION,
    "/swagger**",
    NodesRequest.NODES_PATH
  };

  private final Logger LOG;
  private final boolean myInternalAuthProcessing = TeamCityProperties.getBoolean("rest.cors.optionsRequest.allowUnauthorized");
  private final JerseyWebComponent myWebComponent;
  private final JerseyWebComponentInitializer myWebComponentInitializer;
  private final SecurityContextEx mySecurityContext;
  private final HttpAuthenticationManager myAuthManager;
  private final ClassLoader myClassloader;
  private final RequestPathTransformInfo myRequestPathTransformInfo;
  private final PathSet myUnauthenticatedPathSet = new PathSet();

  private final CorsOrigins myAllowedOrigins = new CorsOrigins();
  private final CachingValues myDisabledRequests = new CachingValues();
  @Nullable private final String myAuthToken; // null if token auth is disabled.

  public APIController(
    final SBuildServer server,
    final SecurityContextEx securityContext,
    final RequestPathTransformInfo requestPathTransformInfo,
    final ServerPluginInfo pluginDescriptor,
    final JerseyWebComponent jerseyWebComponent,
    final JerseyWebComponentInitializer jerseyWebComponentInitializer,
    final HttpAuthenticationManager authManager
  ) {
    super(server);
    myClassloader = getClass().getClassLoader();
    myWebComponent = jerseyWebComponent;
    myWebComponentInitializer = jerseyWebComponentInitializer;
    myAuthManager = authManager;
    mySecurityContext = securityContext;
    myRequestPathTransformInfo = requestPathTransformInfo;
    LOG = PluginUtil.getLoggerWithPluginName(APIController.class, pluginDescriptor);

    setSupportedMethods(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT, HttpMethod.OPTIONS, HttpMethod.DELETE);

    {
      String myAuthTokenCandidate = null;
      if (TeamCityProperties.getBoolean("rest.use.authToken")) {
        try {
          myAuthTokenCandidate = URLEncoder.encode(UUID.randomUUID().toString() + (new Date()).toString().hashCode(), "UTF-8");
          LOG.info("Authentication token for Super user generated: '" + myAuthTokenCandidate + "' in " + PluginUtil.getIdentifyingText(pluginDescriptor) + "");
        } catch (UnsupportedEncodingException e) {
          LOG.warn(e);
        }
      }
      myAuthToken = myAuthTokenCandidate;
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

  private boolean requestForMyPathNotRequiringAuth(final HttpServletRequest request) {
    return myUnauthenticatedPathSet.matches(WebUtil.getOriginalPathWithoutAuthenticationType(request));
  }

  @Override
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
      myWebComponentInitializer.initJerseyWebComponent(() -> "during request " + WebUtil.getRequestDump(request));
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
        doUnderContextClassLoader(myClassloader, (FuncThrow<Void, Throwable>)() ->
          new RestContext(name -> request.getAttribute(CONTEXT_REQUEST_ARGUMENTS_PREFIX + name))
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
              }));
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
  private static String getRequestType(@NotNull HttpServletRequest request) {
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

    InjectionManager im = myWebComponent.getApplicationHandler().getInjectionManager();
    if (im != null) {
      @SuppressWarnings("rawtypes")
      ExceptionMapper em = im.getInstance(ExceptionMappers.class).find(throwable.getClass());
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

  private static void patchThread(@NotNull final Supplier<String> requestDump, @NotNull final String requestType,
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

  private static boolean shouldLogToDebug(@NotNull final HttpServletRequest request) {
    return !RestApiFacade.isInternal(request) || TeamCityProperties.getBoolean("rest.log.debug.internalRequests");
  }

  private static boolean matches(final String requestURI, final String[] disabledRequests) {
    for (String requestPattern : disabledRequests) {
      if (requestURI.matches(requestPattern)) {
        return true;
      }
    }
    return false;
  }

  private static String getStatus(final HttpServletResponse response) {
    String result = "<unknown>";
    try {
      result = String.valueOf(response.getStatus());
    } catch (NoSuchMethodError ignored) {
      //ignore: this occurs for Servlet API < 3.0
    }
    return result;
  }

  @NotNull
  public static String[] getBasePackages() {
    return new String[] {
      //"org.fasterxml.jackson.jaxrs",
      "jetbrains.buildServer.server.graphql",
      "jetbrains.buildServer.server.rest.jersey",
      "jetbrains.buildServer.server.rest.errors",
      "jetbrains.buildServer.server.rest.request",
      "jetbrains.buildServer.server.rest.data",
      "jetbrains.buildServer.server.rest.swagger",
    };
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
  private static HttpServletRequest patchRequestWithAcceptHeader(@NotNull final HttpServletRequest request) {
    final String newValue = request.getParameter("overrideAccept");
    if (!StringUtil.isEmpty(newValue)) {
      return modifyAcceptHeader(request, newValue);
    }
    return request;
  }

  @NotNull
  private static HttpServletRequest modifyAcceptHeader(@NotNull final HttpServletRequest request, @NotNull final String newValue) {
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

  void setUnathenticatedPaths(@NotNull Set<String> unauthenticatedPathSet) {
    myUnauthenticatedPathSet.clear();
    myUnauthenticatedPathSet.addAll(unauthenticatedPathSet);
  }

  static class CachingValues {
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