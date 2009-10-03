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

import com.sun.jersey.spi.resource.Singleton;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.server.rest.BuildsFilter;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.PagerData;
import jetbrains.buildServer.server.rest.data.build.Build;
import jetbrains.buildServer.server.rest.data.build.Builds;
import jetbrains.buildServer.server.rest.data.build.Tags;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path(BuildRequest.API_BUILDS_URL)
@Singleton
public class BuildRequest {
  @NotNull
  private final DataProvider myDataProvider;
  public static final String API_BUILDS_URL = Constants.API_URL + "/builds";

  public BuildRequest(@NotNull DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

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
                               @QueryParam("agentName") String agentName,
                               @QueryParam("sinceBuild") String sinceBuildLocator,
                               @QueryParam("sinceDate") String sinceDate,
                               @QueryParam("start") @DefaultValue(value = "0") Long start,
                               @QueryParam("count") @DefaultValue(value = Constants.DEFAULT_PAGE_ITEMS_COUNT) Integer count,
                               @Context UriInfo uriInfo) {
    final List<SFinishedBuild> buildsList = myDataProvider.getBuilds(
      new BuildsFilter(myDataProvider.getBuildTypeIfNotNull(buildTypeLocator),
                       status, myDataProvider.getUserIfNotNull(userLocator),
                       includePersonal, includeCanceled, onlyPinned, agentName,
                       myDataProvider.getRangeLimit(null, sinceBuildLocator, myDataProvider.parseDate(sinceDate)), start, count));
    return new Builds(buildsList, myDataProvider, new PagerData(uriInfo.getRequestUriBuilder(), start, count, buildsList.size()));
  }

  @GET
  @Path("/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuild(@PathParam("buildLocator") String buildLocator) {
    return new Build(myDataProvider.getBuild(null, buildLocator), myDataProvider);
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
  public void replaceTags(@PathParam("buildLocator") String buildLocator, Tags tags) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    build.setTags(tags.tags); //todo: set user
  }

  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  public void addTags(@PathParam("buildLocator") String buildLocator, Tags tags) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    final List<String> resutlingTags = build.getTags();
    resutlingTags.addAll(tags.tags);
    build.setTags(resutlingTags); //todo: set user
  }

  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"text/plain"})
  public void addTag(@PathParam("buildLocator") String buildLocator, String tagName) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    final List<String> tags = build.getTags();
    tags.add(tagName);
    build.setTags(tags); //todo: set user
  }

  //TODO: check permissions!
  /*
  @DELETE
  @Path("/{buildLocator}")
  @Produces("text/plain")
  public void deleteBuild(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    myDataProvider.deleteBuild(build);
  }
  */
}
