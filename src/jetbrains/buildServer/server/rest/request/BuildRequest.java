/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.BuildsFilter;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.PagerData;
import jetbrains.buildServer.server.rest.data.build.Build;
import jetbrains.buildServer.server.rest.data.build.Builds;
import jetbrains.buildServer.server.rest.data.build.Tags;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path(BuildRequest.API_BUILDS_URL)
public class BuildRequest {
  @Context
  @NotNull
  private DataProvider myDataProvider;
  public static final String API_BUILDS_URL = Constants.API_URL + "/builds";

  @Context
  private ApiUrlBuilder myApiUrlBuilder;

  public static String getBuildHref(SBuild build) {
    return API_BUILDS_URL + "/id:" + build.getBuildId();
  }


  @GET
  @Produces({"application/xml", "application/json"})
  public Builds serveAllBuilds(@QueryParam("buildType") String buildTypeLocator,
                               @QueryParam("status") String status,
                               @QueryParam("triggeredByUser") String userLocator,
                               @QueryParam("includePersonal") boolean includePersonal,
                               @QueryParam("includeCanceled") boolean includeCanceled,
                               @QueryParam("onlyPinned") boolean onlyPinned,
                               @QueryParam("tag") List<String> tags,
                               @QueryParam("agentName") String agentName,
                               @QueryParam("sinceBuild") String sinceBuildLocator,
                               @QueryParam("sinceDate") String sinceDate,
                               @QueryParam("start") @DefaultValue(value = "0") Long start,
                               @QueryParam("count") @DefaultValue(value = Constants.DEFAULT_PAGE_ITEMS_COUNT) Integer count,
                               @Context UriInfo uriInfo) {
    final List<SFinishedBuild> buildsList = myDataProvider.getBuilds(
      new BuildsFilter(myDataProvider.getBuildTypeIfNotNull(buildTypeLocator),
                       status, myDataProvider.getUserIfNotNull(userLocator),
                       includePersonal, includeCanceled, onlyPinned, tags, agentName,
                       myDataProvider.getRangeLimit(null, sinceBuildLocator, myDataProvider.parseDate(sinceDate)), start, count));
    return new Builds(buildsList, myDataProvider, new PagerData(uriInfo.getRequestUriBuilder(), start, count, buildsList.size()),
                      myApiUrlBuilder);
  }

  @GET
  @Path("/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuild(@PathParam("buildLocator") String buildLocator) {
    return new Build(myDataProvider.getBuild(null, buildLocator), myDataProvider, myApiUrlBuilder);
  }

  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }

  @GET
  @Path("/{buildLocator}/tags/")
  @Produces({"application/xml", "application/json"})
  public Tags serveTags(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    return new Tags(build.getTags());
  }

  @PUT
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  public void replaceTags(@PathParam("buildLocator") String buildLocator, Tags tags, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    build.setTags(SessionUser.getUser(request), tags.tags);
  }

  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  public void addTags(@PathParam("buildLocator") String buildLocator, Tags tags, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    final List<String> resutlingTags = build.getTags();
    resutlingTags.addAll(tags.tags);
    build.setTags(SessionUser.getUser(request), resutlingTags);
  }

  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"text/plain"})
  public void addTag(@PathParam("buildLocator") String buildLocator, String tagName, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    final List<String> tags = build.getTags();
    tags.add(tagName);
    build.setTags(SessionUser.getUser(request), tags);
  }

  @PUT
  @Path("/{buildLocator}/comment")
  @Consumes({"text/plain"})
  public void replaceComment(@PathParam("buildLocator") String buildLocator, String text, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    build.setBuildComment(SessionUser.getUser(request), text);
  }

  @DELETE
  @Path("/{buildLocator}/comment")
  public void deleteComment(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    build.setBuildComment(SessionUser.getUser(request), null);
  }

  @DELETE
  @Path("/{buildLocator}")
  @Produces("text/plain")
  public void deleteBuild(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    final SUser user = SessionUser.getUser(request);  //todo: support "run as system" case
    if (user == null) {
      throw new BadRequestException("No current user.");
    }
    final String projectId = build.getProjectId();
    if (user.isPermissionGrantedForProject(projectId, Permission.EDIT_PROJECT) || isPersonalUserBuild(build, user)) {
      myDataProvider.deleteBuild(build);
    } else {
      throw new AuthorizationFailedException("User " + user + " cannot delete build " + build +
                                             ": The user does not have EDIT_PROJECT permission and it is not user's personal build");
    }
  }

  private boolean isPersonalUserBuild(final SBuild build, @NotNull final SUser user) {
    return user.equals(build.getOwner());
  }
}
