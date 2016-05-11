/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.sun.jersey.spi.resource.Singleton;
import io.swagger.annotations.Api;
import java.io.File;
import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.diagnostic.web.ThreadDumpsController;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
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
import jetbrains.buildServer.users.SUser;
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
@Singleton
@Api
public class DebugRequest {
  public static final String REST_VALID_QUERY_PROPERTY_NAME = "rest.debug.database.allow.query.prefixes";

  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private VcsRootInstanceFinder myVcsRootInstanceFinder;
  @Context @NotNull private ServiceLocator myServiceLocator;

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
   */
  @POST
  @Path("/vcsCheckingForChangesQueue")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstances scheduleCheckingForChanges(@QueryParam("locator") final String vcsRootInstancesLocator,
                                                     @QueryParam("fields") final String fields,
                                                     @Context @NotNull final BeanContext beanContext) {
    //todo: check whether permission checks are necessary
    final PagedSearchResult<VcsRootInstance> vcsRootInstances = myVcsRootInstanceFinder.getItems(vcsRootInstancesLocator);
    myDataProvider.getVcsModificationChecker().forceCheckingFor(vcsRootInstances.myEntries, OperationRequestor.COMMIT_HOOK);
    return new VcsRootInstances(CachingValue.simple(((Collection<VcsRootInstance>)vcsRootInstances.myEntries)), null, new Fields(fields), beanContext);
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/requestDetails")
  @Produces({"text/plain"})
  public String getRequestDetails(@Context HttpServletRequest request) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    StringBuilder result = new StringBuilder();
    result.append("Remote address: " ).append(request.getRemoteAddr()).append("\n");
    result.append("Refined remote address and port: ").append(WebUtil.getRemoteAddress(request)).append(" ").append(request.getRemotePort()).append("\n");
    result.append("Local address and port: ").append(request.getLocalAddr()).append(" ").append(request.getLocalPort()).append("\n");
    if (request.getLocalPort() != request.getServerPort()) {
      result.append("Server port: ").append(request.getServerPort()).append("\n");
    }
    result.append("Method: ").append(request.getMethod()).append("\n");
    result.append("Scheme: ").append(request.getScheme()).append("\n");
    result.append("Session id: ").append(request.getSession().getId()).append("\n");
    result.append("\n");
    SUser currentUser = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    if (currentUser != null) {
      result.append("Current TeamCity user: ").append(currentUser.describe(false)).append("\n");
    } else {
      result.append("No current TeamCity user\n");
    }
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
  public String emptyTask(@QueryParam("time") String totalTime, @QueryParam("load") Integer loadPercentage) {
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
      return "Request time: " + (System.currentTimeMillis() - startTime) + "ms";
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
  public String getThreadDump(@QueryParam("lockedMonitors") String lockedMonitors, @QueryParam("lockedSynchronizers") String lockedSynchronizers) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    final Date startTime = Dates.now();
    ThreadInfo[] infos = ManagementFactory.getThreadMXBean().dumpAllThreads(Boolean.getBoolean(lockedMonitors), Boolean.getBoolean(lockedSynchronizers));
    final StringBuilder result = new StringBuilder();
    result.append(ThreadDumpsController.makeServerInfoSummary(myDataProvider.getServer()));
    result.append("\n");
    result.append(new SimpleDateFormat(DiagnosticUtil.THREAD_DUMP_DATE_PATTERN).format(startTime)).append("\n");
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
    DiagnosticUtil.printCpuUsage(printer, new DiagnosticUtil.ThreadDumpData());
    result.append("\n");
    result.append("Dump taken in ").append(TimePrinter.createMillisecondsFormatter().formatTime(Dates.now().getTime() - startTime.getTime()));

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
    if ("md5".equals(method)){
      return EncryptUtil.md5(value);
    }
    throw new BadRequestException("Unknown method '" + method + "'. Supported are: " + "md5" + ".");
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
      buf.append(" owned by \"").append(threadInfo.getLockOwnerName()).append("\" Id=").append(threadInfo.getLockOwnerId());
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
