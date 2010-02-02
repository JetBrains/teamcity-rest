/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.ChangesFilter;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.Changes;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsModification;

@Path(ChangeRequest.API_CHANGES_URL)
public class ChangeRequest {
  public static final String API_CHANGES_URL = Constants.API_URL + "/changes";
  @Context
  private DataProvider myDataProvider;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;

  public static String getChangeHref(VcsModification modification) {
    return API_CHANGES_URL + "/id:" + modification.getId();
  }

  public static String getBuildChangesHref(SBuild build) {
    return API_CHANGES_URL + "?build=id:" + build.getBuildId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Changes serveChanges(@QueryParam("project") String projectLocator,
                              @QueryParam("buildType") String buildTypeLocator,
                              @QueryParam("build") String buildLocator,
                              @QueryParam("vcsRoot") String vcsRootLocator,
                              @QueryParam("sinceChange") String sinceChangeLocator,
                              @QueryParam("start") @DefaultValue(value = "0") Long start,
                              @QueryParam("count") @DefaultValue(value = Constants.DEFAULT_PAGE_ITEMS_COUNT) Integer count,
                              @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    List<SVcsModification> buildModifications;

    final SProject project = myDataProvider.getProjectIfNotNull(projectLocator);
    final SBuildType buildType = myDataProvider.getBuildTypeIfNotNull(buildTypeLocator);
    buildModifications = myDataProvider.getModifications(
      new ChangesFilter(project,
                        buildType,
                        myDataProvider.getBuildIfNotNull(buildType, buildLocator),
                        myDataProvider.getVcsRootIfNotNull(vcsRootLocator),
                        myDataProvider.getChangeIfNotNull(sinceChangeLocator),
                        start,
                        count));

    return new Changes(buildModifications,
                       new PagerData(uriInfo.getRequestUriBuilder(), request, start, count, buildModifications.size()),
                       myApiUrlBuilder);
  }

  @GET
  @Path("/{changeLocator}")
  @Produces({"application/xml", "application/json"})
  public Change serveChange(@PathParam("changeLocator") String changeLocator) {
    return new Change(myDataProvider.getChange(changeLocator), myApiUrlBuilder);
  }
}