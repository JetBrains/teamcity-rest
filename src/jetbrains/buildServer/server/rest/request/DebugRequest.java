/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import java.io.File;
import java.io.IOException;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.controllers.login.RememberMe;
import jetbrains.buildServer.diagnostic.MemoryUsageMonitor;
import jetbrains.buildServer.diagnostic.web.ThreadDumpsController;
import jetbrains.buildServer.responsibility.ResponsibilityManager;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.model.debug.Session;
import jetbrains.buildServer.server.rest.model.debug.Sessions;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.db.*;
import jetbrains.buildServer.serverSide.db.queries.GenericQuery;
import jetbrains.buildServer.serverSide.db.queries.QueryOptions;
import jetbrains.buildServer.serverSide.impl.BuildPromotionReplacementLog;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.dependency.GraphOptimizer;
import jetbrains.buildServer.serverSide.impl.history.DBBuildHistory;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.OperationRequestor;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Provides some debug abilities for the server. Experimental only. Should be used with caution or better not used if not advised by JetBrains
 * These should never be used for non-debug purposes and the API here can change in future versions of TeamCity without any notice.
 */
@Path(Constants.API_URL + "/debug")
@Api("Debug")
public class DebugRequest {
  public static final String REST_VALID_QUERY_PROPERTY_NAME = "rest.debug.database.allow.query.prefixes";

  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private VcsRootInstanceFinder myVcsRootInstanceFinder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private PermissionChecker myPermissionChecker;
  @Context @NotNull private BeanContext myBeanContext;

  @GET
   @Path("/database/tables")
   @Produces({"text/plain; charset=UTF-8"})
   public String listDBTables() {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    final Set<String> tableNames = myDataProvider.getBean(DBFunctionsProvider.class).withDB(new DBAction<Set<String>>() {
      public Set<String> run(final DBFunctions dbf) throws DBException {
        return dbf.retrieveSchemaTableNames(true, false, false);
      }
    });
    ArrayList<String> sortedNames = new ArrayList<String>(tableNames.size());
    sortedNames.addAll(tableNames);
    Collections.sort(sortedNames, new CaseInsensitiveStringComparator());
    return StringUtil.join(sortedNames, "\n");
  }

  //todo: in addition support text/csv for the response, also support json, make it List<Array<String>>
  //todo: consider requiring POST for write operations
  @GET
  @Path("/database/query/{query}")
  @Produces({"text/plain; charset=UTF-8"})
  public String executeDBQuery(@PathParam("query") String query,
                               @QueryParam("fieldDelimiter") @DefaultValue(", ") String fieldDelimiter,
                               @QueryParam("dataRetrieveQuery") String dataRetrieveQuery,
                               @QueryParam("count") @DefaultValue("1000") int maxRows) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    checkQuery(query);
    final boolean treatAsDataRetrieveQuery = dataRetrieveQuery != null ? Boolean.valueOf(dataRetrieveQuery) : isDataRetrieveQuery(query);
    DumpResultSetProcessor processor;
    if (treatAsDataRetrieveQuery) {
      processor = new DumpResultSetProcessor(fieldDelimiter);
    } else {
      processor = null;
    }
    final GenericQuery<List<String>> genericQuery = new GenericQuery<List<String>>(query, processor);
    if (maxRows >= 0 && query.trim().toLowerCase().startsWith("select")) {
      final QueryOptions options = new QueryOptions();
      options.setMaxRows(maxRows);
      genericQuery.setOptions(options);
    }
    //final SQLRunner sqlRunner = myServiceLocator.getSingletonService(SQLRunner.class);
    //workaround for http://youtrack.jetbrains.com/issue/TW-25260
    try {
      final SQLRunnerEx sqlRunner = myServiceLocator.getSingletonService(BuildServerEx.class).getSQLRunner();
      if (treatAsDataRetrieveQuery) {
        final List<String> result = genericQuery.execute(sqlRunner);
        if (result == null) {
          return "";
        }
        String comment = (maxRows >= 0 && result.size() >= maxRows) ? "# First " + maxRows + " rows are served. Add '?count=N' parameter to change the number of rows to return.\n" : "";
        return comment + StringUtil.join(result, "\n");
      }else{
        final int result = genericQuery.executeUpdate(sqlRunner);
        return String.valueOf(result);
      }
    } catch (UnexpectedDBException e) {
      throw new BadRequestException("Error while executing SQL query: " + e.getMessage(), e);
    }
  }

  private boolean isDataRetrieveQuery(@NotNull final String query) {
    final String normalizedQuery = query.trim().toLowerCase();
    for (String prefix : new String[]{"select", "show", "explain"}) {
      if (normalizedQuery.startsWith(prefix)) return true;
    }
    return false;
  }

  /**
   * Experimental use only!
   * @deprecated Use .../app/rest/vcs-root-instances/vcsCheckingForChangesQueue or .../app/rest/vcs-root-instances/commitHookNotification
   */
  @POST
  @Path("/vcsCheckingForChangesQueue")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstances scheduleCheckingForChanges(@QueryParam("locator") final String vcsRootInstancesLocator,
                                                     @QueryParam("requestor") final String requestor,
                                                     @QueryParam("fields") final String fields,
                                                     @Context @NotNull final BeanContext beanContext) {
    //todo: check whether permission checks are necessary
    final PagedSearchResult<VcsRootInstance> vcsRootInstances = myVcsRootInstanceFinder.getItems(vcsRootInstancesLocator);
    myDataProvider.getVcsModificationChecker().forceCheckingFor(vcsRootInstances.myEntries, getRequestor(requestor));
    return new VcsRootInstances(CachingValue.simple(vcsRootInstances.myEntries), null, new Fields(fields), beanContext);
  }

  @NotNull
  private OperationRequestor getRequestor(@Nullable final String requestorText) {
    //TeamCity API: ideally, should be possible to pass custom value as requestor to allow debugging the origin of the request
    if (StringUtil.isEmpty(requestorText)) return OperationRequestor.COMMIT_HOOK; //todo: seems like should be unknown or user by default
    return OperationRequestor.valueOf(requestorText.toUpperCase());
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/currentRequest/details"+ "{extra:(/.*)?}") //"extra" here is to allow checking arbitrary chars in the URL path
  @Produces({"text/plain"})
  public String getRequestDetails(@PathParam("extra") final String extra, @Context HttpServletRequest request) {
    if (!TeamCityProperties.getBoolean("rest.debug.currentRequest.details.allowUnauthorized")) {
      myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    }

    StringBuilder result = new StringBuilder();
    result.append("Remote address: " ).append(WebUtil.hostAndPort(request.getRemoteAddr(), request.getRemotePort())).append("\n");
    result.append("Refined remote address: ").append(WebUtil.hostAndPort(WebUtil.getRemoteAddress(request), request.getRemotePort())).append("\n");
    result.append("Local address: ").append(WebUtil.hostAndPort(request.getLocalAddr(), request.getLocalPort())).append("\n");
    if (request.getLocalPort() != request.getServerPort()) {
      result.append("Server port: ").append(request.getServerPort()).append("\n");
    }
    result.append("Method: ").append(request.getMethod()).append("\n");
    result.append("Scheme: ").append(request.getScheme()).append("\n");
    result.append("Path and query: ").append(WebUtil.getRequestUrl(request)).append("\n");
    result.append("Session id: ").append(request.getSession().getId()).append("\n");
    result.append("Current TeamCity user: ").append(myServiceLocator.getSingletonService(PermissionChecker.class).getCurrentUserDescription()).append("\n");
    result.append("\n");
    result.append("Headers:\n");
    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      Enumeration<String> headers = request.getHeaders(headerName);
      while (headers.hasMoreElements()) {
        String header = headers.nextElement();
        result.append(headerName).append(": ").append(header).append("\n");
      }
    }
    result.append("\n");
    try {
      //consider using byte-to-byte copy method
      StringUtil.processLines(request.getInputStream(), new StringUtil.LineProcessor() {
        @Override
        public boolean processLine(final String line) {
          result.append(line);
          return true;
        }
      });
    } catch (IOException e) {
      throw new OperationException("Error reading request body: " + e.getMessage(), e);
    }
    return result.toString();
  }

  @POST
  @Path("/currentRequest/details"+ "{extra:(/.*)?}")
  @Produces({"text/plain"})
  public String postRequestDetails(@PathParam("extra") final String extra, @Context HttpServletRequest request) {
    return getRequestDetails(extra, request);
  }

  @PUT
  @Path("/currentRequest/details"+ "{extra:(/.*)?}")
  @Produces({"text/plain"})
  public String putRequestDetails(@PathParam("extra") final String extra, @Context HttpServletRequest request) {
    return getRequestDetails(extra, request);
  }

  @GET
  @Path("/currentRequest/session")
  @Produces({"application/xml", "application/json"})
  public Session getCurrentSession(@Context HttpServletRequest request, @QueryParam("fields") final String fields, @Context @NotNull final BeanContext beanContext) {
    User currentUser = myServiceLocator.getSingletonService(PermissionChecker.class).getCurrent().getAssociatedUser();
    HttpSession session = request.getSession();
    return new Session(session.getId(), currentUser != null ? currentUser.getId() : null,
                       new Date(session.getCreationTime()), new Date(session.getLastAccessedTime()), new Fields(fields), beanContext);
  }

  @GET
  @Path("/currentRequest/session/maxInactiveSeconds")
  @Produces("text/plain")
  public String getCurrentSessionMaxInactiveInterval(@Context HttpServletRequest request, @Context @NotNull final BeanContext beanContext) {
    HttpSession session = request.getSession();
    return String.valueOf(session.getMaxInactiveInterval());
  }

  @PUT
  @Path("/currentRequest/session/maxInactiveSeconds")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setCurrentSessionMaxInactiveInterval(String maxInactiveSeconds, @Context HttpServletRequest request,
                                                     @Context @NotNull final BeanContext beanContext) {
    if (!TeamCityProperties.getBoolean("rest.debug.currentRequest.session.maxInactiveSeconds.allowChange")){
      throw new AuthorizationFailedException("Set " + "rest.debug.currentRequest.session.maxInactiveSeconds.allowChange" + " server internal property to enable request");
    }
    HttpSession session = request.getSession();
    session.setMaxInactiveInterval(Integer.valueOf(maxInactiveSeconds));
    return String.valueOf(session.getMaxInactiveInterval());
  }

  @DELETE
  @Path("/currentRequest/session")
  public void invalidateCurrentSession(@Context HttpServletRequest request, @Context @NotNull final BeanContext beanContext) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }

  /**
   * Experimental use only
   */
  @POST
  @Path("/currentRequest/rememberMe")
  @Produces("text/plain")
  public String newRememberMe(@Context HttpServletRequest request, @Context @NotNull final BeanContext beanContext) {
    if (!TeamCityProperties.getBoolean("rest.debug.currentRequest.rememberMe.allowCreate")){
      throw new AuthorizationFailedException("Set " + "rest.debug.currentRequest.rememberMe.allowCreate" + " server internal property to enable request");
    }
    User currentUser = myServiceLocator.getSingletonService(PermissionChecker.class).getCurrent().getAssociatedUser();
    if (currentUser == null) throw new BadRequestException("No current user");
    return beanContext.getSingletonService(RememberMe.class).createUserCookie(currentUser, request).getValue();
  }

  /**
   * Experimental use only
   */
  @DELETE
  @Path("/currentRequest/rememberMe")
  public void deleteCurrentRememberMe(@Context HttpServletRequest request, @Context @NotNull final BeanContext beanContext) {
    beanContext.getSingletonService(RememberMe.class).forgetUserAndGetResponseCookie(request);
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/sessions")
  @Produces({"application/xml", "application/json"})
  public Sessions getSessions(@Context HttpServletRequest request, @QueryParam("manager") final Long managerNum,
                              @QueryParam("fields") final String fields, @Context @NotNull final BeanContext beanContext) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    try {
      MBeanServer serverBean = ManagementFactory.getPlatformMBeanServer();
      Set<ObjectName> managerBeans = serverBean.queryNames(new ObjectName("Catalina:type=Manager,*"), null);
      if (managerBeans.isEmpty()) {
        throw new OperationException("No manager beans found. Not a Tomcat server or not a supported version of Tomcat?");
      }
      if (managerBeans.size() > 1 && managerNum == null) {
        throw new OperationException("Several manager beans found (" + managerBeans.size() + "). Specify '" + "manager" + "' query parameter with the 0-based number.");
      }
      final Iterator<ObjectName> it = managerBeans.iterator();
      if (managerNum != null) {
        for (int i = 0; i < managerNum; i++) {
          it.next();
        }
      }
      ObjectName managerBean = it.next();
      return new Sessions(serverBean, managerBean, new Fields(fields), beanContext);
    } catch (Exception e) {
      throw new OperationException("Could not get sessions data: " + e.toString(), e);
    }
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/jvm/systemProperties")
  @Produces({"application/xml", "application/json"})
  public Properties getSystemProperties(@Context HttpServletRequest request, @QueryParam("fields") final String fields) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    final Set<Map.Entry<Object, Object>> entries = System.getProperties().entrySet();
    final HashMap<String, String> result = new HashMap<String, String>(entries.size());
    for (Map.Entry<Object, Object> entry : entries) {
      result.put(entry.getKey().toString(), entry.getValue().toString());
    }
    return new Properties(result, null, new Fields(fields), myServiceLocator);
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/jvm/environmentVariables")
  @Produces({"application/xml", "application/json"})
  public Properties getEnvironmentVariables(@Context HttpServletRequest request, @QueryParam("fields") final String fields) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    return new Properties(System.getenv(), null, new Fields(fields), myServiceLocator);
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/date/{dateLocator}")
  @Produces({"text/plain"})
  public String getDate(@PathParam("dateLocator") String dateLocator, @QueryParam("format") String format, @QueryParam("timezone") final String timezone) {
    Date limitingDate;
    try {
      limitingDate = new Date(Long.valueOf(dateLocator));
    } catch (NumberFormatException e) {
      limitingDate = myServiceLocator.getSingletonService(TimeCondition.class).getTimeCondition(dateLocator).getLimitingSinceDate();
    }

    if (format != null) {
      myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
      if (StringUtil.isEmpty(format)) {
        format = jetbrains.buildServer.server.rest.model.Constants.TIME_FORMAT;
      }
      DateTimeFormatter formatter = DateTimeFormat.forPattern(format).withLocale(Locale.ENGLISH);
      if (timezone != null) {
        try {
          formatter = formatter.withZone(DateTimeZone.forID(timezone));
        } catch (IllegalArgumentException e) {
          throw new BadRequestException("Wrong timezone '" + timezone + "' specified. Error: " + e.getMessage() +
                                        ". Supported are:\n" + StringUtil.join("\n", DateTimeZone.getAvailableIDs()), e);
        }
      }
      return formatter.print(new DateTime(limitingDate));
    }
    return Util.formatTime(limitingDate);
  }

  /**
   * Experimental use only!
   */
  @POST
  @Path("/emptyTask")
  @Produces({"text/plain"})
  public String emptyTask(@QueryParam("time") String totalTime, @QueryParam("load") Integer loadPercentage,
                          @QueryParam("memory") Integer memoryToAllocateBytes, @QueryParam("memoryChunks") @DefaultValue("1") Integer memoryChunksCount) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    long totalTimeMs = 0;
    if (totalTime != null) {
      try {
        totalTimeMs = Long.valueOf(totalTime);
      } catch (NumberFormatException e) {
        totalTimeMs = TimeWithPrecision.getMsFromRelativeTime(totalTime);
      }
    }
    if (loadPercentage == null) loadPercentage = 0;
    long loadMsInSecond = Math.round((Math.max(Math.min(loadPercentage, 100), 0) / 100.0) * 1000);

    final long startTime = System.currentTimeMillis();
    List<byte[]> memoryHog = null;
    if (memoryToAllocateBytes != null) {
      memoryHog = new ArrayList<>(memoryChunksCount);
      for (int i = 0; i < memoryChunksCount; i++) {
        memoryHog.add(i, new byte[memoryToAllocateBytes/memoryChunksCount]);
      }
    }
    try {
      while (System.currentTimeMillis() - startTime < totalTimeMs) {
        if (loadMsInSecond > 0){
          final long secondPeriodStart = System.currentTimeMillis();
          int i=0;
          while(System.currentTimeMillis() - secondPeriodStart < loadMsInSecond){
            i++;//just load CPU
          }
          Thread.sleep(1000 - loadMsInSecond);
        } else{
          Thread.sleep(totalTimeMs);
        }
      }
      StringBuilder resultMessage = new StringBuilder();
      resultMessage.append("Request time: ").append(System.currentTimeMillis() - startTime).append("ms");
      if (loadPercentage > 0) {
        resultMessage.append(", generated CPU load of ").append(loadPercentage).append("%");
      }
      if (memoryToAllocateBytes != null) {
        resultMessage.append(", allocated ").append(StringUtil.formatFileSize(memoryToAllocateBytes));
        if (memoryChunksCount > 1) {
          resultMessage.append(" in ").append(memoryChunksCount).append(" chunks");
        }
      }
      return resultMessage.toString();
    } catch (InterruptedException e) {
      return "Interrupted. Request time: " + (System.currentTimeMillis() - startTime) + "ms";
    }
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/currentUserPermissions")
  @Produces({"text/plain"})
  public String getCurrentUserPermissions(@Context HttpServletRequest request) {
    if (!TeamCityProperties.getBoolean("rest.debug.currentUserPermissions.enable")) {
      throw new BadRequestException("Request is not enabled. Set \"rest.debug.currentUserPermissions.enable\" internal property to enable.");
    }

    final AuthorityHolder authorityHolder = myServiceLocator.getSingletonService(SecurityContext.class).getAuthorityHolder();
    return getRolesStringPresentation(authorityHolder, myServiceLocator.getSingletonService(ProjectManager.class));
  }

  @NotNull
  public static String getRolesStringPresentation(@NotNull final AuthorityHolder authorityHolder, @NotNull final ProjectManager projectManager) {
    StringBuilder result = new StringBuilder();
    final Permission[] globalPermissions = authorityHolder.getGlobalPermissions().toArray();
    if (globalPermissions.length > 0) {
      result.append("Global:\n");
      for (Permission p : globalPermissions) {
        result.append("\t").append(p.getName()).append("\n");
      }
    }
    for (Map.Entry<String, Permissions> permissionsEntry : authorityHolder.getProjectsPermissions().entrySet()) {
      SProject projectById = null;
      try {
        projectById = projectManager.findProjectById(permissionsEntry.getKey());
      } catch (Exception e) {
        //ignore
      }
      if (projectById != null){
        result.append("Project ").append(projectById.describe(false)).append("\n");
      } else{
        result.append("Project internal id: ").append(permissionsEntry.getKey()).append("\n");
      }
      for (Permission p : permissionsEntry.getValue().toArray()) {
        result.append("\t").append(p.getName()).append("\n");
      }
    }
    return result.toString();
  }

  /**
   * Experimental use only!
   * Related to https://youtrack.jetbrains.com/issue/TW-37419
   */
  @GET
  @Path("/buildChainOptimizationLog/{buildLocator}")
  @Produces({"text/plain"})
  public String getBuildChainOptimizationLog(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    final BuildPromotion build = myServiceLocator.getSingletonService(BuildPromotionFinder.class).getItem(buildLocator);
    myPermissionChecker.checkPermission(Permission.EDIT_PROJECT, build);

    StringBuffer log = new StringBuffer();
    log.append("Optimization log for ").append(LogUtil.describe(build)).append('\n');

    GraphOptimizer optimizer = new GraphOptimizer((BuildPromotionEx)build, myServiceLocator.getSingletonService(BuildPromotionReplacementLog.class));
    optimizer.dryRunOptimization(new GraphOptimizer.OptimizationListener() {
      @Override
      public void equivalentBuildPromotionIgnored(@NotNull final BuildPromotionEx promotion, @NotNull final String reason) {
        log.append("equivalent build promotion ignored ").append(LogUtil.describe(promotion)).append(", reason: ").append(reason).append('\n');
      }

      @Override
      public void buildPromotionReplaced(@NotNull final BuildPromotionEx orig, @NotNull final BuildPromotionEx replacement) {
        log.append("replaced ").append(LogUtil.describe(orig)).append(" -> ").append(LogUtil.describe(replacement)).append('\n');
      }

      @Override
      public void buildPromotionCannotBeReplaced(@NotNull final BuildPromotionEx promotion, @NotNull final String reason) {
        log.append("cannot be replaced ").append(LogUtil.describe(promotion)).append(", reason: ").append(reason).append('\n');
      }

      @Override
      public void equivalentBuildPromotionsFound(@NotNull final BuildPromotionEx orig, @NotNull final List<BuildPromotionEx> equivalentPromotions) {
        if (!equivalentPromotions.isEmpty()){
          log.append("found equivalent for ")
             .append(LogUtil.describe(orig)).append(" == ").append(equivalentPromotions.stream().map(p -> LogUtil.describe(p)).collect(Collectors.toList())).append('\n');
        } else {
          log.append("found no equivalent for ")
             .append(LogUtil.describe(orig)).append('\n');
        }
      }
    });
    return log.toString();
  }

  /**
   * Experimental use only!
   */
  @POST
  @Path("/memory/dumps")
  @Produces({"text/plain"})
  public String saveMemoryDump(@QueryParam("archived") Boolean archived, @Context HttpServletRequest request) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    final File logsPath = myServiceLocator.getSingletonService(ServerPaths.class).getLogsPath();
    try {
      final File memoryDumpFile;
      if (archived == null || archived){
        memoryDumpFile = DiagnosticUtil.memoryDumpZipped(new File(logsPath, "memoryDumps"));
      }else{
        memoryDumpFile = DiagnosticUtil.memoryDump(new File(logsPath, "memoryDumps"));
      }
      return memoryDumpFile.getAbsolutePath();
    } catch (Exception e) {
      throw new OperationException("Error saving memory dump", e);
    }
  }


  /**
   * Experimental use only!
   */
  @GET
  @Path("/threadDump")
  @Produces({"text/plain"})
  public String getThreadDump(@QueryParam("lockedMonitors") String lockedMonitors, @QueryParam("lockedSynchronizers") String lockedSynchronizers,
                              @QueryParam("detectLocks") String detectLocks) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    final Date startTime = Dates.now();
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    ThreadInfo[] infos = threadMXBean.dumpAllThreads(Boolean.getBoolean(lockedMonitors), Boolean.getBoolean(lockedSynchronizers));
    final StringBuilder result = new StringBuilder();
    result.append(ThreadDumpsController.makeServerInfoSummary(myDataProvider.getServer()));
    result.append("\n");
    result.append(DiagnosticUtil.getThreadDumpDateFormat().format(startTime)).append("\n");
    result.append("Full thread dump ").append(System.getProperty("java.vm.name"));
    result.append(" (").append(System.getProperty("java.vm.version")).append(" ").append(System.getProperty("java.vm.info")).append("):").append("\n");
    result.append("\n");
    //todo: sort threads by the first start time and then by status
    for (ThreadInfo threadInfo : infos) {
      appendThreadEntry(result, threadInfo);
    }
    result.append("\n");
    final DiagnosticUtil.Printer printer = new DiagnosticUtil.Printer() {
      public void println(@NotNull final String text) {
        result.append(text).append("\n");
      }

      public void println() {
        result.append("\n");
      }

      public void print(@NotNull final String text) {
        result.append(text);
      }
    };
    DiagnosticUtil.printMemoryUsage(printer);
    result.append("\n");
    DiagnosticUtil.ThreadDumpData data;
    MemoryUsageMonitor memoryUsageMonitor = myServiceLocator.findSingletonService(MemoryUsageMonitor.class);
    if (memoryUsageMonitor != null){
      data = memoryUsageMonitor.getThreadDumpData();
    } else {
      data = new DiagnosticUtil.ThreadDumpData();
    }
    DiagnosticUtil.printCpuUsage(printer, data, startTime);
    result.append("\n");
    result.append("Dump taken in ").append(TimePrinter.createMillisecondsFormatter().formatTime(Dates.now().getTime() - startTime.getTime()));

    if (Boolean.getBoolean(detectLocks)) {
      long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
      if (deadlockedThreads != null) {
        result.append("Found ").append(deadlockedThreads.length).append(" deadlocked threads with ids: ").append(Arrays.toString(deadlockedThreads));
      }
    }

    return result.toString();
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/values/password/scrambled")
  @Produces({"text/plain"})
  public String getScrambled(@QueryParam("value") String value) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    return EncryptUtil.scramble(value);
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/values/password/unscrambled")
  @Produces({"text/plain"})
  public String getUnscrambled(@QueryParam("value") String value) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    return EncryptUtil.unscramble(value);
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/values/transform/{method}")
  @Produces({"text/plain"})
  public String getHashed(@PathParam("method") String method, @QueryParam("value") String value) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    if (value == null) {
      throw new BadRequestException("Mandatory parameter 'value' is missing");
    }
    if ("md5".equalsIgnoreCase(method)){
      return EncryptUtil.md5(value);
    }
    if ("base64".equalsIgnoreCase(method) || "encodeBase64".equalsIgnoreCase(method)){
      return new String(Base64.getEncoder().encode(value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
    if ("decodeBase64".equalsIgnoreCase(method)){
      try {
        return new String(Base64.getUrlDecoder().decode(value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        return new String(Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
      }
    }
    if ("base64url".equalsIgnoreCase(method) || "encodeBase64Url".equalsIgnoreCase(method)){
      return new String(Base64.getUrlEncoder().encode(value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
    throw new BadRequestException("Unknown method '" + method + "'. Supported are: " + "md5"+ ", " + "encodeBase64Url" + ", " + "decodeBase64" + ".");
  }

  /**
   * experimental use only.
   * Allow to get raw investigations without filtering by no longer present projects, etc.
   */
  @GET
  @Path("/investigations")
  @Produces({"application/xml", "application/json"})
  public Investigations getRawInvestigations(@QueryParam("fields") final String fields) {
    myPermissionChecker.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    List<InvestigationWrapper> investigations =
      myServiceLocator.getSingletonService(ResponsibilityManager.class).getAllEntries(null).stream().map(r -> new InvestigationWrapper(r)).collect(Collectors.toList());
    return new Investigations(investigations, null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/caches/builds/stats")
  @Produces({"application/xml", "application/json"})
  public Properties getCachedBuildsStat(@QueryParam("fields") final String fields) {
    myPermissionChecker.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    Map<String, String> cacheStat = myServiceLocator.getSingletonService(DBBuildHistory.class).getCacheStat();
    if ("0".equals(cacheStat.get("hitCount"))) {
      throw new NotFoundException("Build cache statistics does not seem enabled on server start via \"teamcity.buildhistory.buildsCache.statistics.enabled\" property");
    }
    return new Properties(cacheStat, null, new Fields(fields), myServiceLocator);
  }

  /**
   * Tries to provide output consistent with java.lang.management.ThreadInfo#toString() (which cuts frames)
   * See also jetbrains.buildServer.util.DiagnosticUtil.threadDumpInternal()
   */
  static StringBuilder appendThreadEntry(final StringBuilder buf, final ThreadInfo threadInfo) {
    buf.append("\"").append(threadInfo.getThreadName()).append("\"");
    buf.append(" Id=").append(threadInfo.getThreadId()).append(" ").append(threadInfo.getThreadState());
    if (threadInfo.getLockName() != null) {
      buf.append(" on ").append(threadInfo.getLockName());
    }
    if (threadInfo.getLockOwnerName() != null) {
      buf.append("\n       "); //buf.append(" owned");  using non-standard newline, indent and "by" (instead of standard "owned by") for easier reading
      buf.append(" by \"").append(threadInfo.getLockOwnerName()).append("\" Id=").append(threadInfo.getLockOwnerId());
    }
    if (threadInfo.isSuspended()) {
      buf.append(" (suspended)");
    }
    if (threadInfo.isInNative()) {
      buf.append(" (in native)");
    }
    buf.append('\n');
    int i = 0;
    for (; i < threadInfo.getStackTrace().length; i++) {
      StackTraceElement ste = threadInfo.getStackTrace()[i];
      buf.append("    at ").append(ste.toString());
      buf.append('\n');
      if (i == 0 && threadInfo.getLockInfo() != null) {
        Thread.State ts = threadInfo.getThreadState();
        buf.append("    -  ");
        switch (ts) {
          case BLOCKED:
            buf.append("blocked on ");
            break;
          case WAITING:
            buf.append("waiting on ");
            break;
          case TIMED_WAITING:
            buf.append("waiting on ");
            break;
          default:
        }
        buf.append(threadInfo.getLockInfo()).append('\n');
      }

      for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
        if (mi.getLockedStackDepth() == i) {
          buf.append("    -  locked ").append(mi);
          buf.append('\n');
        }
      }
    }

    LockInfo[] locks = threadInfo.getLockedSynchronizers();
    if (locks.length > 0) {
      buf.append("\n    Number of locked synchronizers = ").append(locks.length);
      buf.append('\n');
      for (LockInfo lockInfo : locks) {
        buf.append("    - ").append(lockInfo);
        buf.append('\n');
      }
    }
    buf.append('\n');
    return buf;
  }


  private void checkQuery(final String query) {
    final String validQueryPrefixes = TeamCityProperties.getProperty(REST_VALID_QUERY_PROPERTY_NAME);
    if (StringUtil.isEmpty(validQueryPrefixes)) {
      throw new BadRequestException("Query execution is turned off. To allow select queries, add internal TeamCity property:\n" +
                                    REST_VALID_QUERY_PROPERTY_NAME + "=select\nMore values can be added (comma-delimited).");
    }
    final List<String> prefixesList = StringUtil.split(validQueryPrefixes, ",");
    final String first = CollectionsUtil.findFirst(prefixesList, new Filter<String>() {
      public boolean accept(@NotNull final String data) {
        return query.trim().toLowerCase().startsWith(data.trim().toLowerCase());
      }
    });
    if (first == null) {
      throw new BadRequestException("Only queries starting with '" + validQueryPrefixes + "' are allowed. " +
                                    "Change internal TeamCity property " + REST_VALID_QUERY_PROPERTY_NAME + " to allow more prefixes.");
    }
  }

  private class DumpResultSetProcessor implements GenericQuery.ResultSetProcessor<List<String>> {
    private final String myFieldDelimiter;

    public DumpResultSetProcessor(final String fieldDelimiter) {
      myFieldDelimiter = fieldDelimiter;
    }

    @Nullable
    public List<String> process(final ResultSet rs) throws SQLException {
      final ArrayList<String> result = new ArrayList<String>();
      final ResultSetMetaData metaData = rs.getMetaData();
      final int columnCount = metaData.getColumnCount();
      if (columnCount <= 0) {
        return result;
      }

      result.add(getTitleRow(metaData));

      while (rs.next()) {
        String row = "";
        try {
          row = rs.getString(1);
          for (int i = 2; i < columnCount + 1; i++) {
            row = row + myFieldDelimiter + rs.getString(i);
          }
        } catch (SQLException e) {
          //just continue
        }
        result.add(row);
      }
      //now drop title if single value
      if (result.size() == 2 && metaData.getColumnCount() == 1) {
        result.remove(0);
      }
      return result;
    }

    private String getTitleRow(final ResultSetMetaData metaData) {
      String row = "";
      try {
        row = metaData.getColumnName(1);
        for (int i = 2; i < metaData.getColumnCount() + 1; i++) {
          row = row + myFieldDelimiter + metaData.getColumnName(i);
        }
      } catch (SQLException e) {
        //just continue
      }
      return row;
    }
  }
}
