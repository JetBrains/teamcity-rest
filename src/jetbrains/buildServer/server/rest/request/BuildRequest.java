/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.StreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildsFilter;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.build.Tags;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactHolder;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.FinishedBuildEx;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.statistics.ValueProvider;
import jetbrains.buildServer.serverSide.statistics.build.BuildValue;
import jetbrains.buildServer.serverSide.statistics.build.CompositeVTB;
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

  /**
   *
   * @param buildTypeLocator Deprecated, use "locator" parameter instead
   * @param status   Deprecated, use "locator" parameter instead
   * @param userLocator   Deprecated, use "locator" parameter instead
   * @param includePersonal   Deprecated, use "locator" parameter instead
   * @param includeCanceled   Deprecated, use "locator" parameter instead
   * @param onlyPinned   Deprecated, use "locator" parameter instead
   * @param tags   Deprecated, use "locator" parameter instead
   * @param agentName   Deprecated, use "locator" parameter instead
   * @param sinceBuildLocator   Deprecated, use "locator" parameter instead
   * @param sinceDate   Deprecated, use "locator" parameter instead
   * @param start   Deprecated, use "locator" parameter instead
   * @param count   Deprecated, use "locator" parameter instead
   * @param locator
   * @return
   */
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
                                      null,
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
  @Path("/{buildLocator}/finish-parameters/")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildActualParameters(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    if (!build.isFinished()){
      return null;
    }
    try {
      return new Properties(((FinishedBuildEx)build).getBuildFinishParameters());
    } catch (ClassCastException e) {
      return null;
    }
  }

  //todo: need to expose file name and type?
  @GET
  @Path("/{buildLocator}/artifacts/files/{fileName:.+}")
  @Produces({"application/octet-stream"})
  public StreamingOutput serveArtifact(@PathParam("buildLocator") String buildLocator, @PathParam("fileName") String fileName) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    final BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);
    final BuildArtifactHolder artifact = artifacts.findArtifact(fileName);
    if (!artifact.isAvailable() || artifact.getArtifact().isDirectory()){
      throw new NotFoundException("No artifact found. Relative path: '" + fileName + "'");
    }
    if (!artifact.isAccessible()){
      throw new AuthorizationFailedException("Artifaact is not accessible with current user permissions. Relative path: '" + fileName + "'");
    }
    try {
      final InputStream inputStream = artifact.getArtifact().getInputStream();
      return new StreamingOutput() {
        public void write(final OutputStream output) throws IOException, WebApplicationException {
          StreamUtil.copyStreamContent(inputStream, output);
        }
      };
    } catch (IOException e) {
      throw new OperationException("Error while retrieving file '" + fileName + "': " + e.getMessage(), e);
    }
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
  @Path("/{buildLocator}/statistics/")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildStatisticValues(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    return new Properties(getBuildStatisticsValues(build));
  }

  @GET
  @Path("/{buildLocator}/statistics/{name}")
  @Produces("text/plain")
  public String serveBuildStatisticValue(@PathParam("buildLocator") String buildLocator,
                                         @PathParam("name") String statisticValueName) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);

    return getBuildStatisticValue(build, statisticValueName);
  }

  /*
  //this seems to have no sense as there is no way to retrieve a list of values without registered value provider
  @PUT
  @Path("/{buildLocator}/statistics/{name}")
  @Consumes("text/plain")
  public void addBuildStatisticValue(@PathParam("buildLocator") String buildLocator,
                                     @PathParam("name") String statisticValueName,
                                     String value) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    myDataProvider.getBuildDataStorage().publishValue(statisticValueName, build.getBuildId(), new BigDecimal(value));
  }
  */

  @GET
  @Path("/{buildLocator}/tags/")
  @Produces({"application/xml", "application/json"})
  public Tags serveTags(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);
    return new Tags(build.getTags());
  }

  public String getBuildStatisticValue(final SBuild build, final String statisticValueName) {
    final BuildValue data = getRawBuildStatisticValue(build, statisticValueName);
    if (data == null){
      throw new NotFoundException("No statistics data for key: " + statisticValueName + "' in build " + LogUtil.describe(build));
    }
    return data.getValue().toPlainString();
  }

  private BuildValue getRawBuildStatisticValue(final SBuild build, final String statisticValueName) {
    return myDataProvider.getBuildDataStorage().getData(statisticValueName, null, build.getBuildId(), build.getBuildTypeId());
  }

  public Map<String, String> getBuildStatisticsValues(final SBuild build) {
    final Collection<ValueProvider> valueProviders = myDataProvider.getValueProviderRegistry().getValueProviders();
    final Map<String, String> result = new HashMap<String, String>();

    //todo: this should be based not on curently registered providers, but on the real values published for a build,
    // see also http://youtrack.jetbrains.net/issue/TW-4003
    for (ValueProvider valueProvider : valueProviders) {
      addValueIfPresent(build, valueProvider.getKey(), result);
    }
    for (String statKey: getUnregisteredStatisticKeys()) {
      if (!result.containsKey(statKey)) {
        addValueIfPresent(build, statKey, result);
      }
    }

    return result;
  }

  private Collection<String> getUnregisteredStatisticKeys() {
    final List<String> result = new ArrayList<String>();
    final Collection<CompositeVTB> statisticValues = myServiceLocator.getServices(CompositeVTB.class);
    for (CompositeVTB statisticValue : statisticValues) {
      Collections.addAll(result, statisticValue.getSubKeys());
    }
    result.add("BuildDuration");
    result.add("BuildDurationNetTime");
    result.add("BuildCheckoutTime");
    result.add("BuildArtifactsPublishingTime");
    result.add("ArtifactsResolvingTime");
    result.add("MaxTimeToFixTest");
    result.add("BuildTestStatus");
    return result;
  }

  private void addValueIfPresent(final SBuild build, final String key, final Map<String, String> result) {
    final BuildValue rawBuildStatisticValue = getRawBuildStatisticValue(build, key);
    if (rawBuildStatisticValue != null){
      result.put(key, rawBuildStatisticValue.getValue().toString());
    }
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
