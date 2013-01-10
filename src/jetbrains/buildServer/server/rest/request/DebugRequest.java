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

package jetbrains.buildServer.server.rest.request;

import com.sun.jersey.spi.resource.Singleton;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SQLRunner;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.db.queries.GenericQuery;
import jetbrains.buildServer.serverSide.db.queries.QueryOptions;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides some debug abilities for the server. Experimental only. Should be used with caution or better not used if not advised by JetBrains
 * These should never be used for non-debug purposes and the API here can change in future versions of TeamCity without any notice.
 */
@Path(Constants.API_URL + "/debug")
@Singleton
public class DebugRequest {
  public static final String REST_VALID_QUERY_PROPERTY_NAME = "rest.debug.allow.query.prefixes";

  @Context private DataProvider myDataProvider;
  @Context private ServiceLocator myServiceLocator;

  //todo: in addition support text/csv for the response, also support json, make it List<Array<String>>
  //todo: consider requiring POST for write operations
  @GET
  @Path("/database/query/{query}")
  @Produces({"text/plain; charset=UTF-8"})
  public String serveServerVersion(@PathParam("query") String query,
                                   @QueryParam("fieldDelimiter") @DefaultValue(", ") String fieldDelimiter,
                                   @QueryParam("pageSize") @DefaultValue("1000") int pageSize) {
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
    if (pageSize >= 0 && selectQuery) {
      final QueryOptions options = new QueryOptions();
      options.setMaxRows(pageSize);
      genericQuery.setOptions(options);
    }
    //final SQLRunner sqlRunner = myServiceLocator.getSingletonService(SQLRunner.class);
    //workaround for http://youtrack.jetbrains.com/issue/TW-25260
    final SQLRunner sqlRunner = myServiceLocator.getSingletonService(SBuildServer.class).getSQLRunner();
    if (selectQuery) {
      final List<String> result = genericQuery.execute(sqlRunner);
      if (result == null) {
        return "";
      }
      return StringUtil.join(result, "\n");
    }else{
      final int result = genericQuery.executeUpdate(sqlRunner);
      return String.valueOf(result);
    }
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
