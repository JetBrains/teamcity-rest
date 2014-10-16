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

package jetbrains.buildServer.server.rest.request;

import com.sun.jersey.spi.resource.Singleton;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildServerEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.db.*;
import jetbrains.buildServer.serverSide.db.queries.GenericQuery;
import jetbrains.buildServer.serverSide.db.queries.QueryOptions;
import jetbrains.buildServer.util.CaseInsensitiveStringComparator;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.VcsRootInstance;
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
  }

  /**
   * Experimental use only!
   */
  @POST
  @Path("/vcsCheckingForChangesQueue")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstances scheduleCheckingForChanges(@QueryParam("locator") String vcsRootInstancesLocator,
                                                     @QueryParam("fields") String fields,
                                                     @Context @NotNull BeanContext beanContext) {
    //todo: check whether permission checks are necessary
    final PagedSearchResult<VcsRootInstance> vcsRootInstances = myVcsRootFinder.getVcsRootInstances(VcsRootFinder.createVcsRootInstanceLocator(vcsRootInstancesLocator));
    myDataProvider.getVcsModificationChecker().forceCheckingFor(vcsRootInstances.myEntries);
    return new VcsRootInstances(vcsRootInstances.myEntries, null, new Fields(fields), beanContext);
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
