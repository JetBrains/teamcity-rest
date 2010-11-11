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
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildsFilter;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.build.Tags;
import jetbrains.buildServer.server.rest.util.BeanFactory;
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

  @Context private ApiUrlBuilder myApiUrlBuilder;
  @Context private ServiceLocator myServiceLocator;
  @Context private BeanFactory myFactory;

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
                               @QueryParam("locator") String locator,
                               @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    BuildsFilter buildsFilter;
    if (locator != null) {
      buildsFilter = myDataProvider.getBuildsFilterByLocator(null, new Locator(locator));
    } else {
      // preserve 5.0 logic for personal/canceled/pinned builds
      buildsFilter = new BuildsFilter(myDataProvider.getBuildTypeIfNotNull(buildTypeLocator),
                                      status,
                                      myDataProvider.getUserIfNotNull(userLocator),
                                      includePersonal ? null : false, includeCanceled ? null : false,
                                      false, onlyPinned ? true : null, tags, agentName,
                                      myDataProvider
                                        .getRangeLimit(null, sinceBuildLocator, myDataProvider.parseDate(sinceDate)),
                                      start, count);
    }
    final List<SBuild> buildsList = myDataProvider.getBuilds(buildsFilter);
    return new Builds(buildsList, myDataProvider, new PagerData(uriInfo.getRequestUriBuilder(), request, start, count, buildsList.size()),
                      myApiUrlBuilder);
  }

  @GET
  @Path("/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuild(@PathParam("buildLocator") String buildLocator) {
    return new Build(myDataProvider.getBuild(null, buildLocator), myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
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

  /**
   * Replaces build's tags.
   * @param buildLocator build locator
   */
  @PUT
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  public void replaceTags(@PathParam("buildLocator") String buildLocator, Tags tags, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    build.setTags(SessionUser.getUser(request), tags.tags);
  }

  /**
   * Adds a set of tags to a build
   * @param buildLocator build locator
   */
  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  public void addTags(@PathParam("buildLocator") String buildLocator, Tags tags, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    final List<String> resutlingTags = build.getTags();
    resutlingTags.addAll(tags.tags);
    build.setTags(SessionUser.getUser(request), resutlingTags);
  }

  /**
   * Adds a single tag to a build
   * @param buildLocator build locator
   * @param tagName name of a tag to add
   */
  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"text/plain"})
  public void addTag(@PathParam("buildLocator") String buildLocator, String tagName, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    final List<String> tags = build.getTags();
    tags.add(tagName);
    build.setTags(SessionUser.getUser(request), tags);
  }

  /**
   * Fetches current build pinned status.
   * @param buildLocator build locator
   * @return "true" is the build is pinned, "false" otherwise
   */
  @GET
  @Path("/{buildLocator}/pin/")
  @Produces({"text/plain"})
  public String getPinned(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    return Boolean.toString(build.isPinned());
  }

  /**
   * Pins a build
   * @param buildLocator build locator
   */
  @PUT
  @Path("/{buildLocator}/pin/")
  @Consumes({"text/plain"})
  public void pinBuild(@PathParam("buildLocator") String buildLocator, String comment, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    if (!build.isFinished()){
      throw new BadRequestException("Cannot pin build that is not finished.");
    }
    SFinishedBuild finishedBuild = (SFinishedBuild)build;
    finishedBuild.setPinned(true, SessionUser.getUser(request), comment);
  }

  /**
   * Unpins a build
   * @param buildLocator build locator
   */
  @DELETE
  @Path("/{buildLocator}/pin/")
  @Consumes({"text/plain"})
  public void unpinBuild(@PathParam("buildLocator") String buildLocator, String comment, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    if (!build.isFinished()){
      throw new BadRequestException("Cannot pin build that is not finished.");
    }
    SFinishedBuild finishedBuild = (SFinishedBuild)build;
    finishedBuild.setPinned(false, SessionUser.getUser(request), comment);
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
  /**
   * May not work for non-personal builds: http://youtrack.jetbrains.net/issue/TW-9858
   */
  public void deleteBuild(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);

    final SUser user = SessionUser.getUser(request);  //todo: support "run as system" case
    // workaround for http://youtrack.jetbrains.net/issue/TW-10538
    if (!isPersonalUserBuild(build, user)) {
      myDataProvider.checkProjectPermission(Permission.EDIT_PROJECT, build.getProjectId());
    }
    myDataProvider.deleteBuild(build);
  }

  private boolean isPersonalUserBuild(final SBuild build, @NotNull final SUser user) {
    return user.equals(build.getOwner());
  }
}
