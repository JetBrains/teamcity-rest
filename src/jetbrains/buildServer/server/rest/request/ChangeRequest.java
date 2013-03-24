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

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.Changes;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsModification;
import org.jetbrains.annotations.NotNull;

@Path(ChangeRequest.API_CHANGES_URL)
public class ChangeRequest {
  public static final String API_CHANGES_URL = Constants.API_URL + "/changes";
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private VcsRootFinder myVcsRootFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanFactory myFactory;

  public static String getChangeHref(VcsModification modification) {
    return API_CHANGES_URL + "/id:" + modification.getId() + (modification.isPersonal() ? ",personal:true" : "");
  }

  public static String getBuildChangesHref(SBuild build) {
    return API_CHANGES_URL + "?build=id:" + build.getBuildId();
  }

  //todo: use locator here, like for builds with limitLookup, changes from dependencies falg, etc.
  //todo: mark changes from dependencies
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

    final SProject project = myProjectFinder.getProjectIfNotNull(projectLocator);
    final SBuildType buildType = myBuildTypeFinder.getBuildTypeIfNotNull(buildTypeLocator);
    buildModifications = myDataProvider.getModifications(
      new ChangesFilter(project,
                        buildType,
                        myBuildFinder.getBuildIfNotNull(buildType, buildLocator),
                        vcsRootLocator == null ? null : myVcsRootFinder.getVcsRootInstance(vcsRootLocator),
                        myDataProvider.getChangeIfNotNull(sinceChangeLocator),
                        start,
                        count));

    return new Changes(buildModifications,
                       new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), start, count, buildModifications.size(),
                                     null, null),
                       myApiUrlBuilder, myFactory);
  }

  @GET
  @Path("/{changeLocator}")
  @Produces({"application/xml", "application/json"})
  public Change serveChange(@PathParam("changeLocator") String changeLocator) {
    return new Change(myDataProvider.getChange(changeLocator), myApiUrlBuilder, myFactory);
  }
}