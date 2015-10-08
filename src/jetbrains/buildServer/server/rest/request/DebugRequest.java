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
import java.io.File;
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
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.db.*;
import jetbrains.buildServer.serverSide.db.queries.GenericQuery;
import jetbrains.buildServer.serverSide.db.queries.QueryOptions;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides some debug abilities for the server. Experimental only. Should be used with caution or better not used if not advised by JetBrains
 * These should never be used for non-debug purposes and the API here can change in future versions of TeamCity without any notice.
 */
@Path(Constants.API_URL + "/debug")
@Singleton
public class DebugRequest {
  public static final String REST_VALID_QUERY_PROPERTY_NAME = "rest.debug.database.allow.query.prefixes";

  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private VcsRootFinder myVcsRootFinder;
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
                                   @QueryParam("count") @DefaultValue("1000") int maxRows) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    checkQuery(query);
    final boolean selectQuery = query.trim().toLowerCase().startsWith("select");
    DumpResultSetProcessor processor;
    if (selectQuery) {
      processor = new DumpResultSetProcessor(fieldDelimiter);
    } else {
      processor = null;
    }
    final GenericQuery<List<String>> genericQuery = new GenericQuery<List<String>>(query, processor);
    if (maxRows >= 0 && selectQuery) {
      final QueryOptions options = new QueryOptions();
      options.setMaxRows(maxRows);
      genericQuery.setOptions(options);
    }
    //final SQLRunner sqlRunner = myServiceLocator.getSingletonService(SQLRunner.class);
    //workaround for http://youtrack.jetbrains.com/issue/TW-25260
    try {
      final SQLRunnerEx sqlRunner = myServiceLocator.getSingletonService(BuildServerEx.class).getSQLRunner();
      if (selectQuery) {
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
    final PagedSearchResult<VcsRootInstance> vcsRootInstances = myVcsRootFinder.getVcsRootInstances(VcsRootFinder.createVcsRootInstanceLocator(vcsRootInstancesLocator));
    myDataProvider.getVcsModificationChecker().forceCheckingFor(vcsRootInstances.myEntries);
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
    result.append("Refined remote address: " ).append(WebUtil.getRemoteAddress(request) + ":" + request.getRemotePort()).append("\n");
    return result.toString();
  }

  /**
   * Experimental use only!
   */
  @GET
  @Path("/sessions/summary")
  @Produces({"text/plain"})
  public String getSessions(@Context HttpServletRequest request) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    StringBuilder result = new StringBuilder();
    try {
      MBeanServer serverBean = ManagementFactory.getPlatformMBeanServer();
      Set<ObjectName> managerBeans = serverBean.queryNames(new ObjectName("Catalina:type=Manager,*"), null);
      for (ObjectName managerBean : managerBeans) {
        String activeSessions = String.valueOf(serverBean.getAttribute(managerBean, "activeSessions"));
        String maxActive = String.valueOf(serverBean.getAttribute(managerBean, "maxActive"));
        if (result.length() > 0) {
          result.append(", ");
        }
        result.append("activeSessions: ").append(activeSessions).append(", maxActive: ").append(maxActive);
      }

      return result.toString();
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
    return new Properties(result, null, new Fields(fields));
  }

  /**
   * Experimental use only!
   */
  @POST
  @Path("/emptyTask")
  @Produces({"text/plain"})
  public String emptyTask(@QueryParam("time") Integer totalTime, @QueryParam("load") Integer load) {
    myDataProvider.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    if (totalTime == null) totalTime = 0;
    if (load == null) load = 0;
    long loadMsInSecond = Math.round((Math.max(Math.min(load, 100),0)/100.0)*1000);

    final long startTime = System.currentTimeMillis();
    try {
      while(System.currentTimeMillis() - startTime < totalTime){
        if (loadMsInSecond > 0){
          final long secondPeriodStart = System.currentTimeMillis();
          int i=0;
          while(System.currentTimeMillis() - secondPeriodStart < loadMsInSecond){
            i++;//just load CPU
          }
          Thread.sleep(1000 - loadMsInSecond);
        } else{
          Thread.sleep(totalTime);
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
