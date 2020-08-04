/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.ServerProvidedProperties;
import jetbrains.buildServer.controllers.FileSecurityUtil;
import jetbrains.buildServer.controllers.HttpDownloadProcessor;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.parameters.ProcessingResult;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.build.TagFinder;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.data.problem.ProblemOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.*;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.build.*;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.change.BuildChanges;
import jetbrains.buildServer.server.rest.model.issue.IssueUsages;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrence;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrences;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.AggregatedBuildArtifactsElementBuilder;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.TriggeredBy;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.impl.BaseBuild;
import jetbrains.buildServer.serverSide.impl.BuildAgentMessagesQueue;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.tags.TagsManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TCStreamUtil;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.web.util.SessionUser;
import jetbrains.buildServer.web.util.WebAuthUtil;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path(BuildRequest.API_BUILDS_URL)
@Api("Build")
public class BuildRequest {
  private static final Logger LOG = Logger.getInstance(BuildRequest.class.getName());
  public static final String IMG_STATUS_WIDGET_ROOT_DIRECTORY = "/img/statusWidget";
  public static final String STATUS_ICON_REQUEST_NAME = "statusIcon";
  public static final String RELATED_ISSUES = "/relatedIssues";
  public static final String TESTS = "testOccurrences";
  public static final String STATISTICS = "/statistics";
  private static final Pattern NON_ALPHA_NUM_PATTERN = Pattern.compile("[^a-zA-Z0-9-#.]+");

  @Context @NotNull public BuildFinder myBuildFinder;
  @Context @NotNull public BuildPromotionFinder myBuildPromotionFinder;
  @Context @NotNull public BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull public PermissionChecker myPermissionChecker;

  public static final String BUILDS_ROOT_REQUEST_PATH = "/builds";
  public static final String API_BUILDS_URL = Constants.API_URL + BUILDS_ROOT_REQUEST_PATH;

  public static final String ARTIFACTS = "/artifacts";
  public static final String AGGREGATED = "/aggregated";

  protected static final String REST_BUILD_REQUEST_DELETE_LIMIT = "rest.buildRequest.delete.limit";

  @Context @NotNull public BeanContext myBeanContext;

  public static String getHref() {
    return API_BUILDS_URL;
  }

  public static String getHref(@NotNull String locator) {
    return API_BUILDS_URL + "/" + locator;
  }

  public static String getBuildHref(@NotNull SBuild build) {
    return getHref(getBuildLocator(build));
  }

  public static String getBuildHref(@NotNull BuildPromotion build) {
    return getHref(getBuildLocator(build));
  }

  @Nullable
  public static String getBuildsHref(@NotNull final BranchData branch, @Nullable final String buildsLocator) {
    SBuildType buildType = branch.getBuildType();
    if (buildType == null) return null;
    return API_BUILDS_URL + "?locator=" + Util.encodeUrlParamValue(BuildPromotionFinder.getLocator(buildType, branch, buildsLocator));
  }

  public static String getBuildLocator(@NotNull final SBuild build) {
    return getBuildLocator(build.getBuildPromotion());
  }

  public static String getBuildLocator(@NotNull final BuildPromotion buildPromotion) {
    return BuildPromotionFinder.getLocator(buildPromotion);
  }

  public static String getBuildIssuesHref(final SBuild build) {
    return getBuildHref(build) + RELATED_ISSUES;
  }

  @NotNull
  public static String getBuildArtifactsHref(final @NotNull BuildPromotion build) {
    return Util.concatenatePath(BuildRequest.getBuildHref(build), ARTIFACTS);
  }

  /**
   * Serves builds matching supplied condition.
   *
   * @param locator           Build locator to filter builds server
   * @param buildTypeLocator  Deprecated, use "locator" parameter instead
   * @param status            Deprecated, use "locator" parameter instead
   * @param userLocator       Deprecated, use "locator" parameter instead
   * @param includePersonal   Deprecated, use "locator" parameter instead
   * @param includeCanceled   Deprecated, use "locator" parameter instead
   * @param onlyPinned        Deprecated, use "locator" parameter instead
   * @param tags              Deprecated, use "locator" parameter instead
   * @param agentName         Deprecated, use "locator" parameter instead
   * @param sinceBuildLocator Deprecated, use "locator" parameter instead
   * @param sinceDate         Deprecated, use "locator" parameter instead
   * @param start             Deprecated, use "locator" parameter instead
   * @param count             Deprecated, use "locator" parameter instead, defaults to 100
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public Builds serveAllBuilds(@ApiParam(hidden = true) @QueryParam("buildType") String buildTypeLocator,
                               @ApiParam(hidden = true) @QueryParam("status") String status,
                               @ApiParam(hidden = true) @QueryParam("triggeredByUser") String userLocator,
                               @ApiParam(hidden = true) @QueryParam("includePersonal") boolean includePersonal,
                               @ApiParam(hidden = true) @QueryParam("includeCanceled") boolean includeCanceled,
                               @ApiParam(hidden = true) @QueryParam("onlyPinned") boolean onlyPinned,
                               @ApiParam(hidden = true) @QueryParam("tag") List<String> tags,
                               @ApiParam(hidden = true) @QueryParam("agentName") String agentName,
                               @ApiParam(hidden = true) @QueryParam("sinceBuild") String sinceBuildLocator,
                               @ApiParam(hidden = true) @QueryParam("sinceDate") String sinceDate,
                               @ApiParam(hidden = true) @QueryParam("start") Long start,
                               @ApiParam(hidden = true) @QueryParam("count") Integer count,
                               @ApiParam(format = LocatorName.BUILD) @QueryParam("locator") String locator,
                               @QueryParam("fields") String fields,
                               @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    return myBuildFinder.getBuildsForRequest(myBuildTypeFinder.getBuildTypeIfNotNull(buildTypeLocator), status, userLocator, includePersonal,
                                           includeCanceled, onlyPinned, tags, agentName, sinceBuildLocator, sinceDate, start, count,
                                           locator, "locator", uriInfo, request,   new Fields(fields), myBeanContext
    );
  }

  /**
   * @deprecated Use DELETE request to .../app/rest/builds/multiple/{locator}
   */
  @DELETE
  @ApiOperation(value = "deleteBuilds", hidden = true)
  @Produces({"application/xml", "application/json"})
  public void deleteBuilds(@ApiParam(format = LocatorName.BUILD) @QueryParam("locator") String locator,
                           @Context HttpServletRequest request) {
    if (locator == null){
      throw new BadRequestException("Empty 'locator' parameter specified.");
    }
    final List<BuildPromotion> builds = myBuildFinder.getBuilds(null, locator).myEntries;
    final int deleteLimit = TeamCityProperties.getInteger(REST_BUILD_REQUEST_DELETE_LIMIT, 10);
    if (builds.size() > deleteLimit){
      throw new BadRequestException("Refusing to delete more than " + deleteLimit + " builds as a precaution measure." +
                                    " The limit is set via '" + REST_BUILD_REQUEST_DELETE_LIMIT + "' internal property on the server.");
    }
    SUser user = SessionUser.getUser(request);
    Map<Long, RuntimeException> errors = deleteBuilds(builds, user, null);
    if (!errors.isEmpty()) {
      throw errors.values().iterator().next();
    }
  }

  /**
   * Experimental support only
   * Changing of some attributes is not supported and can result in strange and "unpredictable" behavior.
   */
  @Path("/{buildLocator}/attributes")
  public ParametersSubResource getAttributes(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                             @QueryParam("fields") String fields) {
    BuildPromotionEx build = (BuildPromotionEx)myBuildPromotionFinder.getItem(buildLocator);
    myPermissionChecker.checkPermission(Permission.EDIT_PROJECT, build);
    return new ParametersSubResource(myBeanContext, new ParametersPersistableEntity() {
      @Override
      public void addParameter(@NotNull final Parameter param) {
        build.setAttribute(param.getName(), param.getValue());
      }

      @Override
      public void removeParameter(@NotNull final String paramName) {
        build.setAttribute(paramName, null);
      }

      @NotNull
      @Override
      public Collection<Parameter> getParametersCollection(@Nullable final Locator locator) {
        return build.getAttributes().entrySet().stream().map(entry -> new SimpleParameter(entry.getKey(), String.valueOf(entry.getValue()))).collect(Collectors.toList());
      }

      @Nullable
      @Override
      public Parameter getParameter(@NotNull final String paramName) {
        return new SimpleParameter(paramName, String.valueOf(build.getAttribute(paramName)));
      }

      @Override
      public void persist(@NotNull String description) {
        //should not need a separate action
      }
    }, getBuildHref(build) + "/attributes");
  }

  /**
   * Serves a build described by the locator provided searching through those accessible by the current user.
   * See {@link jetbrains.buildServer.server.rest.request.BuildRequest#serveAllBuilds(String, String, String, boolean, boolean, boolean, java.util.List, String, String, String, Long, Integer, String, javax.ws.rs.core.UriInfo, javax.servlet.http.HttpServletRequest)}
   * If several builds are matched, the first one is used (the effect is the same as if ",count:1" locator dimension is added)
   * @param buildLocator
   * @return A build matching the locator
   */
  @GET
  @Path("/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                          @QueryParam("fields") String fields,
                          @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    return new Build(build,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}/resulting-properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildActualParameters(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                               @QueryParam("fields") String fields) {
    BuildPromotion build = myBuildPromotionFinder.getItem(buildLocator);
    myPermissionChecker.checkPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build);
    return new Properties(Build.getBuildResultingParameters(build, myBeanContext.getServiceLocator()).getAll(), null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}/resulting-properties/{propertyName}")
  @Produces({"text/plain"})
  public String getParameter(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                             @PathParam("propertyName") String propertyName) {
    BuildPromotion build = myBuildPromotionFinder.getItem(buildLocator);
    myPermissionChecker.checkPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build);
    return BuildTypeUtil.getParameter(propertyName, Build.getBuildResultingParameters(build, myBeanContext.getServiceLocator()), true, true, myBeanContext.getServiceLocator());
  }

  /**
   * experimental
   * This forces the properties reload from disk, not deletion
   */
  @DELETE
  @Path("/{buildLocator}/caches/finishProperties")
  @Produces({"application/xml", "application/json"})
  public void resetBuildFinishParameters(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    myPermissionChecker.checkPermission(Permission.EDIT_PROJECT, build.getBuildPromotion());
    try {
      ((BaseBuild)build).resetBuildFinalParameters();
    } catch (ClassCastException ignore) {
    }
  }


  /**
   * Experimental only
   */
  @GET
  @Path("/{buildLocator}/resolved/{value}")
  @Produces({"text/plain"})
  public String getResolvedParameter(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                     @PathParam("value") String value) {
    return getResolvedIfNecessary(myBuildPromotionFinder.getItem(buildLocator), value, true);
  }

  @Path("/{buildLocator}" + ARTIFACTS)
  public FilesSubResource getFilesSubResource(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") final String buildLocator,
                                              @QueryParam("resolveParameters") final Boolean resolveParameters,
                                              @QueryParam("logBuildUsage") final Boolean logBuildUsage) {
    final BuildPromotion buildPromotion = myBuildFinder.getBuildPromotion(null, buildLocator);

    final String urlPrefix = getBuildArtifactsHref(buildPromotion);
    //convert anonymous to inner here, implement DownloadProcessor
    return new FilesSubResource(new BuildArtifactsProvider(buildPromotion, resolveParameters, logBuildUsage, urlPrefix), urlPrefix, myBeanContext, true);
  }

  @NotNull
  private String getBuildFileName(@NotNull final BuildPromotion buildPromotion, final @NotNull String path) {
    final SBuild build = buildPromotion.getAssociatedBuild();
    if (build != null) {
      return WebUtil.getFilename(build) + replaceNonAlphaNum(path);
    } else {
      return replaceNonAlphaNum(buildPromotion.getBuildTypeExternalId() + "_id" + buildPromotion.getId() + path);
    }
  }

  private String replaceNonAlphaNum(final String s) {
    return NON_ALPHA_NUM_PATTERN.matcher(s).replaceAll("_");
  }

  @NotNull
  private String getResolvedIfNecessary(@NotNull final BuildPromotion buildPromotion, @Nullable final String value, @Nullable final Boolean resolveSupported) {
    final SBuild build = buildPromotion.getAssociatedBuild();  //TeamCity API issue: no way to resolve params by promotion
    if (build == null || resolveSupported == null || !resolveSupported || StringUtil.isEmpty(value)) {
      return value == null ? "" : value;
    }
    try {
      //TeamCity API issue: ideally, API can check the permission inside build.getValueResolver() and allow to reference properties which are visible to viewer
      myPermissionChecker.checkPermission(Permission.VIEW_BUILD_RUNTIME_DATA, buildPromotion);
    } catch (AuthorizationFailedException e) {
      //handle build number special case since it is visible to project viewer
      String withResolvedBuildNumber = StringUtil.replace(value, ReferencesResolverUtil.makeReference(ServerProvidedProperties.BUILD_NUMBER_PROP), build.getBuildNumber());
      if (!withResolvedBuildNumber.equals(value)) {
        //there was a reference to build number
        if (!build.getValueResolver().resolve(withResolvedBuildNumber).isModified()) {
          //no other references
          return withResolvedBuildNumber;
        }
      }
      ProcessingResult resolveResult = build.getValueResolver().resolve(value);
      if (!resolveResult.isModified() && resolveResult.isFullyResolved()) {
        //no references found
        return value;
      }
      //report original error
      myPermissionChecker.checkPermission(Permission.VIEW_BUILD_RUNTIME_DATA, buildPromotion);
    }

    final ProcessingResult resolveResult = build.getValueResolver().resolve(value);
    return resolveResult.getResult();
  }


  @GET
  @Path("/{buildLocator}/sources/files/{fileName:.+}") //todo: use "content" like for artifacts here
  @Produces({"application/octet-stream"})
  public Response serveSourceFile(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") final String buildLocator,
                                  @PathParam("fileName") final String fileName) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    byte[] fileContent;
    try {
      fileContent = myBeanContext.getSingletonService(VcsManager.class).getFileContent(build, fileName); //TeamCity API issue: allow that by BuildPromotion
    } catch (VcsException e) {
      throw new OperationException("Error while retrieving file content from VCS", e);
    }
    return Response.ok().entity(fileContent).build();
  }

  /**
   * @deprecated Preserved for compatibility with TeamCity 7.1 and will be removed i the future versions
   * @return
   */
  @GET
  @ApiOperation(value = "serveBuildRelatedIssuesOld", hidden = true)
  @Path("/{buildLocator}/related-issues")
  @Produces({"application/xml", "application/json"})
  public IssueUsages serveBuildRelatedIssuesOld(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                @QueryParam("fields") String fields) {
    return serveBuildRelatedIssues(buildLocator, fields);
  }

  @GET
  @Path("/{buildLocator}" + RELATED_ISSUES)
  @Produces({"application/xml", "application/json"})
  public IssueUsages serveBuildRelatedIssues(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                             @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return new IssueUsages(build,  new Fields(fields), myBeanContext);
  }

  @Nullable
  private SBuild getBuild(@NotNull final BuildPromotion promotion) {
    SQueuedBuild queuedBuild = promotion.getQueuedBuild();
    return queuedBuild != null ? null : promotion.getAssociatedBuild();
  }

  @GET
  @Path("/{buildLocator}/number")
  @Produces("text/plain")
  public String getBuildNumber(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator) {
    SBuild build = getBuild(myBuildFinder.getBuildPromotion(null, buildLocator));
    return build == null ? null : build.getBuildNumber();
  }

  @PUT
  @Path("/{buildLocator}/number")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setBuildNumber(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator, String value) {
    SRunningBuild runningBuild = Build.getRunningBuild(myBuildFinder.getBuildPromotion(null, buildLocator), myBeanContext.getServiceLocator());
    if (runningBuild == null) {
      throw new BadRequestException("Cannot set number for a build which is not running");
    }
    try {
      runningBuild.setBuildNumber(value);
    } catch (UnsupportedOperationException e) {
      throw new BadRequestException("Cannot set build number for the build: " + e.getMessage());
    }
    Loggers.ACTIVITIES.info("Build number is changed via REST request by user " + myPermissionChecker.getCurrentUserDescription() + ". Build: " + LogUtil.describe(runningBuild));
    return runningBuild.getBuildNumber();
  }

  @GET
  @Path("/{buildLocator}/statusText")
  @Produces("text/plain")
  public String getBuildStatusText(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator) {
    SBuild build = getBuild(myBuildFinder.getBuildPromotion(null, buildLocator));
    return build == null ? null : build.getStatusDescriptor().getText();
  }

  @PUT
  @Path("/{buildLocator}/statusText")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setBuildStatusText(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator, String value) {
    RunningBuildEx runningBuild = (RunningBuildEx)Build.getRunningBuild(myBuildFinder.getBuildPromotion(null, buildLocator), myBeanContext.getServiceLocator());
    if (runningBuild == null) {
      throw new BadRequestException("Cannot set status text for a build which is not running");
    }
    runningBuild.setCustomStatusText(value);
    Loggers.ACTIVITIES.info("Build status text is changed via REST request by user " + myPermissionChecker.getCurrentUserDescription() + ". Build: " + LogUtil.describe(runningBuild));
    return runningBuild.getStatusDescriptor().getText();
  }

  @GET
  @Path("/{buildLocator}" + STATISTICS + "/")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildStatisticValues(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return new Properties(Build.getBuildStatisticsValues(build), null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}" + STATISTICS + "/{name}")
  @Produces("text/plain")
  public String serveBuildStatisticValue(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                         @PathParam("name") String statisticValueName) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);

    return Build.getBuildStatisticValue(build, statisticValueName);
  }

  /*
  //this seems to have no sense as there is no way to retrieve a list of values without registered value provider
  //can also add delete to workaround issues like https://youtrack.jetbrains.com/issue/TW-61084
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


  // see also corresponding methods in BuildQueueRequest
  @GET
  @Path("/{buildLocator}/tags/")
  @Produces({"application/xml", "application/json"})
  public Tags serveTagsFromBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                 @ApiParam(format = LocatorName.TAG) @QueryParam("locator") String tagLocator,
                                 @QueryParam("fields") String fields) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    return new Tags(new TagFinder(myBeanContext.getSingletonService(UserFinder.class), build).getItems(tagLocator, TagFinder.getDefaultLocator()).myEntries,
                    new Fields(fields), myBeanContext);
  }

  /**
   * Replaces the build's tags designated by the tags 'locator' to the set of tags passed.
   */
  @PUT
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Tags replaceTagsOnBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                 @ApiParam(format = LocatorName.TAG) @QueryParam("locator") String tagLocator,
                                 Tags tags,
                                 @QueryParam("fields") String fields, @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final TagFinder tagFinder = new TagFinder(myBeanContext.getSingletonService(UserFinder.class), build);
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);

    tagsManager.removeTagDatas(build, tagFinder.getItems(tagLocator, TagFinder.getDefaultLocator()).myEntries);
    List<TagData> postedTagDatas = tags.getFromPosted(myBeanContext.getSingletonService(UserFinder.class));
    tagsManager.addTagDatas(build, postedTagDatas);

    return new Tags(postedTagDatas, new Fields(fields), myBeanContext); // returning the tags posted as the default locator might not match the tags just created
  }

  /**
   * Adds a set of tags to a build
   *
   * @param buildLocator build locator
   */
  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Tags addTagsToBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                             Tags tags,
                             @QueryParam("fields") String fields) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);

    final List<TagData> tagsPosted = tags.getFromPosted(myBeanContext.getSingletonService(UserFinder.class));
    tagsManager.addTagDatas(build, tagsPosted);
    return new Tags(tagsPosted, new Fields(fields), myBeanContext);
  }

  /**
   * Adds a single tag to a build
   *
   * @param buildLocator build locator
   * @param tagName      name of a tag to add
   */
  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(hidden = true, value = "Use addTags instead")
  public String addTag(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                       String tagName,
                       @Context HttpServletRequest request) {
    if (StringUtil.isEmpty(tagName)) { //check for empty tags: http://youtrack.jetbrains.com/issue/TW-34426
      throw new BadRequestException("Cannot apply empty tag, should have non empty request body");
    }
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);

    tagsManager.addTagDatas(build, Collections.singleton(TagData.createPublicTag(tagName)));
    return tagName;
  }
//to improve: add GET (true/false) and DELETE, may be PUT (true/false) for a single tag

  /**
   * Gets build's current pin data
   */
  @GET
  @Path("/{buildLocator}/pinInfo/")
  @Produces({"application/xml", "application/json"})
  public PinInfo getPinData(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                            @QueryParam("fields") String fields,
                            @Context HttpServletRequest request) {
    SBuild build = myBuildPromotionFinder.getItem(buildLocator).getAssociatedBuild();
    return new PinInfo(build, new Fields(fields), myBeanContext);
  }

  /**
   * Sets build pin status
   * @param buildLocator build locator
   */
  @PUT
  @Path("/{buildLocator}/pinInfo/")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public PinInfo setBuildPinData(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                 PinInfo pinStatus,
                                 @QueryParam("fields") String fields,
                                 @Context HttpServletRequest request) {
    Boolean newStatus = pinStatus.getStatusFromPosted();
    if (newStatus == null) throw new BadRequestException("Pin status should be specified in the payload");
    BuildPromotion buildPromotion = myBuildPromotionFinder.getItem(buildLocator);
    pinBuild(buildPromotion, SessionUser.getUser(request), pinStatus.getCommentTextFromPosted(), newStatus);
    SBuild build = buildPromotion.getAssociatedBuild();
    return new PinInfo(build, new Fields(fields), myBeanContext);
  }

  /**
   * Fetches current build pinned status.
   *
   * @deprecated use getPinData
   * @param buildLocator build locator
   * @return "true" is the build is pinned, "false" otherwise
   */
  @GET
  @ApiOperation(value = "getPinned", hidden = true)
  @Path("/{buildLocator}/pin/")
  @Produces({"text/plain"})
  public String getPinned(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                          @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return Boolean.toString(build.isPinned());
  }

  /**
   * Pins a build
   * @deprecated use setBuildPinData
   * @param buildLocator build locator
   */
  @PUT
  @ApiOperation(value = "pinBuild", hidden = true)
  @Path("/{buildLocator}/pin/")
  @Consumes({"text/plain"})
  public void pinBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                       String comment,
                       @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    pinBuild(build.getBuildPromotion(), SessionUser.getUser(request), comment, true);
  }

  private void pinBuild(@NotNull final BuildPromotion buildPromotion, @Nullable final SUser user, @Nullable final String comment, final boolean newPinState) {
    SBuild build = buildPromotion.getAssociatedBuild();
    if (!(build instanceof SFinishedBuild)) {
      throw new BadRequestException("Cannot " + (newPinState ? "pin" : "unpin") + " build that is not finished.");
    }
    ((SFinishedBuild)build).setPinned(newPinState, user, comment);
  }

  /**
   * Unpins a build
   * @deprecated use setBuildPinData
   * @param buildLocator build locator
   */
  @DELETE
  @ApiOperation(value = "unpinBuild", hidden = true)
  @Path("/{buildLocator}/pin/")
  @Consumes({"text/plain"})
  public void unpinBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                         String comment,
                         @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    pinBuild(build.getBuildPromotion(), SessionUser.getUser(request), comment, false);
  }

  @PUT
  @Path("/{buildLocator}/comment")
  @Consumes({"text/plain"})
  public void replaceComment(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                             String text,
                             @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final SUser user = SessionUser.getUser(request);
    setBuildComment(build, text, user);
  }

  @DELETE
  @Path("/{buildLocator}/comment")
  public void deleteComment(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                            @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final SUser user = SessionUser.getUser(request);
    setBuildComment(build, null, user);
  }

  private void setBuildComment(@NotNull final BuildPromotion build, @Nullable final String text, @Nullable final SUser user) {
    if (user == null) { //TeamCity API issue: SBuild and BuildPromotion has different behavior here
      throw new BadRequestException("Cannot change comment when there is no current user");
    }
    build.setBuildComment(user, text);
  }

  @GET
  @Path("/{buildLocator}/" + Build.CANCELED_INFO)
  @Produces({"application/xml", "application/json"})
  public Comment getCanceledInfo(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                 @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return Build.getCanceledComment(build,  new Fields(fields), myBeanContext);
  }

  @GET
  @ApiOperation(value = "getBuildCancelRequest", hidden = true)
  @Path("/{buildLocator}/example/buildCancelRequest")
  @Produces({"application/xml", "application/json"})
  public BuildCancelRequest getBuildCancelRequest(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                  @Context HttpServletRequest request) {
    return new BuildCancelRequest("example build cancel comment", false);
  }

  @GET
  @Path("/{buildLocator}/problemOccurrences")
  @Produces({"application/xml", "application/json"})
  public ProblemOccurrences getProblems(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                        @QueryParam("fields") String fields) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final List<BuildProblem> buildProblems = ((BuildPromotionEx)build).getBuildProblems();//todo: (TeamCity) is this OK to use?
    return new ProblemOccurrences(buildProblems, ProblemOccurrenceRequest.getHref(build), null,  new Fields(fields), myBeanContext);
  }

  /**
   * Experimental.
   * Adds a build problem with given details. The same as marking the build as failed from UI.
   */
  @POST
  @Path("/{buildLocator}/problemOccurrences")
  @Consumes({"text/plain"})
  @Produces({"application/xml", "application/json"})
  public ProblemOccurrence addProblemToBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                             String problemDetails,
                                             @QueryParam("fields") String fields) {
    BuildPromotion buildPromotion = myBuildFinder.getBuildPromotion(null, buildLocator);
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null) {
      throw new NotFoundException("No finished build associated with promotion id " + buildPromotion.getId());
    }
    User user = myPermissionChecker.getCurrent().getAssociatedUser();
    if (user == null) {
      throw new BadRequestException("Cannot perform operation: no current user");
    }
    BuildProblemData problemData = build.addUserBuildProblem((SUser)user, problemDetails);
    return new ProblemOccurrence(myBeanContext.getSingletonService(ProblemOccurrenceFinder.class).getProblem(build, problemData), myBeanContext, new Fields(fields));
  }

  /**
   * Experimental.
   * Allows to add a build problem to the build. The only used attributes of the submitted problem are identity, type, details, additionalData
   */
  @POST
  @Path("/{buildLocator}/problemOccurrences")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public ProblemOccurrence addProblem(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                      ProblemOccurrence problemOccurrence,
                                      @QueryParam("fields") String fields) {
    BuildPromotion buildPromotion = myBuildFinder.getBuildPromotion(null, buildLocator);
    checkBuildOperationPermission(buildPromotion);
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null) {
      throw new NotFoundException("No finished build associated with promotion id " + buildPromotion.getId());
    }
    BuildProblemData problemDetails = problemOccurrence.getFromPosted(myBeanContext.getServiceLocator());
    build.addBuildProblem(problemDetails);
    return new ProblemOccurrence(myBeanContext.getSingletonService(ProblemOccurrenceFinder.class).getProblem(build, problemDetails), myBeanContext, new Fields(fields));
  }

  @GET
  @Path("/{buildLocator}/" + TESTS)
  @Produces({"application/xml", "application/json"})
  public TestOccurrences getTests(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                  @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    //todo: investigate test repeat counts support
    return new TestOccurrences(TestOccurrenceFinder.getBuildStatistics(build, null).getAllTests(), TestOccurrenceRequest.getHref(build), null, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only
   */
  @GET
  @Path("/{buildLocator}/artifactsDirectory")
  @Produces({"text/plain"})
  public String getArtifactsDirectory(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator) {
    myPermissionChecker.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS);
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    return build.getArtifactsDirectory().getAbsolutePath();
  }

  /**
   * Experimental support only
   */
  @GET
  @Path("/{buildLocator}/artifactDependencyChanges")
  @Produces({"application/xml", "application/json"})
  public BuildChanges getArtifactDependencyChanges(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                   @QueryParam("fields") String fields) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    return Build.getArtifactDependencyChangesNode(build, new Fields(fields), myBeanContext);
  }

  // todo: depricate in favor of posting to .../canceledInfo
  @POST
  @Path("/{buildLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Build cancelBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                           BuildCancelRequest cancelRequest,
                           @QueryParam("fields") String fields,
                           @Context HttpServletRequest request) {
    final SUser currentUser = SessionUser.getUser(request);
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    LinkedHashMap<Long, RuntimeException> errors = cancelBuilds(Collections.singletonList(build), cancelRequest, currentUser);
    if (!errors.isEmpty()) {
      throw errors.entrySet().iterator().next().getValue();
    }
    final SBuild associatedBuild = build.getAssociatedBuild();
    if (associatedBuild == null) {
      return null;
    }
    return new Build(associatedBuild, new Fields(fields), myBeanContext);
  }

  private void restoreInQueue(final SRunningBuild runningBuild, final User user) {
    //todo: TeamCity openAPI expose in the API. THis one is copy-paste from jetbrains.buildServer.controllers.actions.StopBuildAction.restoreInQueue
    final SAgentRestrictor agentRestrictor = ((RunningBuildEx)runningBuild).getQueuedAgentRestrictor();
    final TriggeredBy origTriggeredBy = runningBuild.getTriggeredBy();
    BuildPromotionEx promotionEx = (BuildPromotionEx)runningBuild.getBuildPromotion();

    TriggeredByBuilder tbb = new TriggeredByBuilder();
    tbb.addParameters(origTriggeredBy.getParameters());
    tbb.addParameter(TriggeredByBuilder.RE_ADDED_AFTER_STOP_NAME, String.valueOf(user.getId()));
    tbb.addParameter(TriggeredByBuilder.TYPE_PARAM_NAME, "reAddedOnStop");
    tbb.addParameter("origin", "rest");

    myBeanContext.getSingletonService(BuildQueueEx.class).restoreInQueue(promotionEx, agentRestrictor, tbb.toString());
  }

  @DELETE
  @Path("/{buildLocator}")
  public void deleteBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                          @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    Map<Long, RuntimeException> errors = deleteBuilds(Collections.singletonList(build), SessionUser.getUser(request), null);
    if (!errors.isEmpty()) {
      throw errors.get(build.getId());
    }
  }

  /**
   * @return map of errors for each build promotion id which encountered an error
   */
  private Map<Long, RuntimeException> deleteBuilds(@NotNull final List<BuildPromotion> builds, @Nullable final SUser user, @Nullable final String comment) {
    BuildHistoryEx buildHistory = (BuildHistoryEx)myBeanContext.getSingletonService(BuildHistory.class);

    LinkedHashMap<Long, RuntimeException> errors = new LinkedHashMap<>();
    errors.putAll(cancelBuilds(builds.stream().filter(buildPromotion -> {
      SBuild build = buildPromotion.getAssociatedBuild();
      return build == null || !build.isFinished();
    }).collect(Collectors.toList()), new BuildCancelRequest(comment, false), user));

    //delete finished (and those canceled earlier)
    for (BuildPromotion build : builds) {
      SBuild associatedBuild = build.getAssociatedBuild();
      if (associatedBuild != null) {
        if (associatedBuild.isFinished()) {
          errors.remove(build.getId()); //clear any cancel errors, if any
          try {
            if (associatedBuild instanceof SFinishedBuild) {
              buildHistory.removeEntry((SFinishedBuild)associatedBuild, comment);
            } else {
              buildHistory.removeEntry(associatedBuild.getBuildId(), comment);
            }
          } catch (RuntimeException e) {
            if (build.getAssociatedBuild() != null) {
              errors.putIfAbsent(build.getId(), e);
            }
          }
        } else {
          errors.putIfAbsent(build.getId(), new OperationException("Failed to delete running build"));
        }
      } else {
        if (build.getQueuedBuild() != null) {
          errors.putIfAbsent(build.getId(),  new AuthorizationFailedException("Failed to delete queued build. Probably not sufficient permissions."));
        }
      }
    }
    return errors;
  }

  @NotNull
  private LinkedHashMap<Long, RuntimeException> cancelBuilds(@NotNull final List<BuildPromotion> builds, @NotNull final BuildCancelRequest cancelRequest, @Nullable final SUser currentUser) {
    LinkedHashMap<Long, RuntimeException> errors = new LinkedHashMap<>();

    if (cancelRequest.readdIntoQueue) {
      if (currentUser == null) {
        throw new BadRequestException("Cannot re-add build into queue when no current user is present. Please make sure the operation is performed under a regular user.");
      }
    }

    final jetbrains.buildServer.serverSide.BuildQueueEx buildQueue = (BuildQueueEx)myBeanContext.getSingletonService(BuildQueue.class);
    Set<String> queuedBuildIds = builds.stream().map(b -> b.getQueuedBuild()).filter(Objects::nonNull).map(b -> b.getItemId()).collect(Collectors.toSet());
    if (!queuedBuildIds.isEmpty()) {
      buildQueue.removeItems(queuedBuildIds, currentUser, cancelRequest.comment);
    }

    List<SRunningBuild> stoppedBuilds = new ArrayList<>();
    RunningBuildsManager runningBuildsManager = myBeanContext.getSingletonService(RunningBuildsManager.class);
    for (BuildPromotion build : builds) {
      final SBuild sBuild = build.getAssociatedBuild();
      if (sBuild == null) {
        if (build.getQueuedBuild() != null) {
          if (currentUser != null && !AuthUtil.hasPermissionToStopBuild(currentUser, build)) {
            errors.put(build.getId(), new AuthorizationFailedException("You do not have enough permissions to cancel the build"));
          } else {
            errors.putIfAbsent(build.getId(), new OperationException("Failed to cancel queued build"));
          }
        }
        continue;
      }
      if (sBuild.isFinished()) {
        if (sBuild.getCanceledInfo() == null) {
          errors.put(build.getId(), new BadRequestException("Cannot cancel finished build"));
        }
        continue;
      }
      
      SRunningBuild runningBuild = runningBuildsManager.findRunningBuildById(sBuild.getBuildId());
      if (runningBuild == null) {
        errors.put(build.getId(), new BadRequestException("Cannot cancel not running build"));
        continue;
      }

      try {
        if (cancelRequest.readdIntoQueue) {
          stoppedBuilds.add(runningBuild);
        }
        runningBuild.stop(currentUser, cancelRequest.comment == null ? "Canceled via REST API" : cancelRequest.comment);
      } catch (RuntimeException e) {
        errors.putIfAbsent(build.getId(), e);
      }
    }


    if (cancelRequest.readdIntoQueue && !stoppedBuilds.isEmpty()) {
        stoppedBuilds.forEach(build -> {
          try {
            restoreInQueue(build, currentUser);
          } catch (RuntimeException e) {
            errors.putIfAbsent(build.getBuildPromotion().getId(), e);
          }
        });
    }
    return errors;
  }

  /**
   * Experimental alias for .../app/rest/builds?locator={buildLocator}
   */
  @GET
  @Path("/multiple/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Builds getMultiple(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                            @QueryParam("fields") String fields,
                            @Context UriInfo uriInfo,
                            @Context HttpServletRequest request) {
    final PagedSearchResult<BuildPromotion> pagedResult = myBuildPromotionFinder.getItems(buildLocator);
    UriBuilder uriBuilder = uriInfo.getRequestUriBuilder();
    UriBuilder mainRequestUriBuilder = uriBuilder.replacePath(uriBuilder.build().getPath().replace("/multiple/" + buildLocator, "")).queryParam("locator", buildLocator);
    final PagerData pagerData = new PagerData(mainRequestUriBuilder, request.getContextPath(), pagedResult, buildLocator, "locator");
    return Builds.createFromBuildPromotions(pagedResult.myEntries, pagerData, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental.
   * @return List of error messages with associated entities, if any
   */
  @DELETE
  @Path("/multiple/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public MultipleOperationResult deleteMultiple(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                @QueryParam("fields") String fields,
                                                @Context HttpServletRequest request) {
    if (buildLocator == null) {
      throw new BadRequestException("Empty locator specified.");
    }
    List<BuildPromotion> builds = myBuildPromotionFinder.getItems(buildLocator).myEntries;
    return new MultipleOperationResult(getResultData(builds, deleteBuilds(builds, SessionUser.getUser(request), null)), new Fields(fields), myBeanContext);
  }

  /**
   * Creates result data for the set of builds and errors passed
   */
  @NotNull
  private MultipleOperationResult.Data getResultData(@NotNull final List<BuildPromotion> allBuilds, @NotNull final Map<Long, RuntimeException> reportedErrors) {
    int[] errorsCount = {0};
    return new MultipleOperationResult.Data(allBuilds.stream().map(build -> {
      RuntimeException exception = reportedErrors.get(build.getId());
      if (exception == null) {
        return OperationResult.Data.createSuccess(new RelatedEntity.Entity(build));
      } else {
        errorsCount[0]++;
        return OperationResult.Data.createError(exception.getMessage(), new RelatedEntity.Entity(build));
      }
    }).collect(Collectors.toList()), errorsCount[0]);
  }

  /**
   * Experimental.
   * Pins multiple builds
   * @return List of error messages with associated entities, if any
   */
  @PUT
  @Path("/multiple/{buildLocator}/pinInfo/")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public MultipleOperationResult pinMultiple(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                             PinInfo pinStatus,
                                             @QueryParam("fields") String fields,
                                             @Context HttpServletRequest request) {
    Boolean newStatus = pinStatus.getStatusFromPosted();
    if (newStatus == null) throw new BadRequestException("Pin status should be specified in the payload");
    String commentText = pinStatus.getCommentTextFromPosted();
    SUser user = SessionUser.getUser(request);
    return processMultiple(buildLocator, (build) -> pinBuild(build, user, commentText, newStatus), new Fields(fields));
  }

  /**
   * Experimental.
   * Adds a set of tags to multiple builds
   * @return List of error messages with associated entities, if any
   */
  @POST
  @Path("/multiple/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public MultipleOperationResult addTagsMultipleToBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                        Tags tags,
                                                        @QueryParam("fields") String fields) {
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);
    final List<TagData> tagsPosted = tags.getFromPosted(myBeanContext.getSingletonService(UserFinder.class));
    return processMultiple(buildLocator, (build) -> tagsManager.addTagDatas(build, tagsPosted), new Fields(fields));
  }

  /**
   * Experimental.
   * Deletes a set of tags from multiple builds
   * @return List of error messages with associated entities, if any
   */
  @DELETE
  @Path("/multiple/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public MultipleOperationResult removeTagsMultiple(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                    Tags tags,
                                                    @QueryParam("fields") String fields) {
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);
    final List<TagData> tagsPosted = tags.getFromPosted(myBeanContext.getSingletonService(UserFinder.class));
    return processMultiple(buildLocator, (build) -> tagsManager.removeTagDatas(build, tagsPosted), new Fields(fields));
  }

  /**
   * Experimental.
   * Adds comments to multiple builds
   * @return List of error messages with associated entities, if any
   */
  @PUT
  @Path("/multiple/{buildLocator}/comment")
  @Consumes({"text/plain"})
  @Produces({"application/xml", "application/json"})
  public MultipleOperationResult replaceCommentMultiple(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                        String text,
                                                        @QueryParam("fields") String fields,
                                                        @Context HttpServletRequest request) {
    final SUser user = SessionUser.getUser(request);
    return processMultiple(buildLocator, (build) -> setBuildComment(build, text, user), new Fields(fields));
  }

  /**
   * Experimental.
   * Removes comment from multiple builds
   * @return List of error messages with associated entities, if any
   */
  @DELETE
  @Path("/multiple/{buildLocator}/comment")
  @Produces({"application/xml", "application/json"})
  public MultipleOperationResult deleteCommentMultiple(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                       @QueryParam("fields") String fields,
                                                       @Context HttpServletRequest request) {
    final SUser user = SessionUser.getUser(request);
    return processMultiple(buildLocator, (build) -> setBuildComment(build, null, user), new Fields(fields));
  }

  /**
   * Experimental.
   * Cancels multiple builds
   * @return List of error messages with associated entities, if any
   */
  @POST
  @Path("/multiple/{buildLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public MultipleOperationResult cancelMultiple(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                                BuildCancelRequest cancelRequest,
                                                @QueryParam("fields") String fields,
                                                @Context HttpServletRequest request) {
    if (buildLocator == null) {
      throw new BadRequestException("Empty locator specified.");
    }
    List<BuildPromotion> builds = myBuildPromotionFinder.getItems(buildLocator).myEntries;
    return new MultipleOperationResult(getResultData(builds, cancelBuilds(builds, cancelRequest, SessionUser.getUser(request))), new Fields(fields), myBeanContext);
  }

  @NotNull
  private MultipleOperationResult processMultiple(@Nullable final String buildLocator, @NotNull Consumer<BuildPromotion> action, @NotNull final Fields fields) {
    return new MultipleOperationResult(MultipleOperationResult.Data.process(buildLocator, myBuildPromotionFinder, action), fields, myBeanContext);
  }

  // Note: authentication for this request is disabled in APIController configuration
  @GET
  @Path("/{buildLocator}/" + STATUS_ICON_REQUEST_NAME + "{suffix:(.*)?}")
  public Response serveBuildStatusIcon(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") final String buildLocator,
                                       @PathParam("suffix") final String suffix,
                                       @Context HttpServletRequest request) {
    //todo: may also use HTTP 304 for different resources in order to make it browser-cached
    //todo: return something appropriate when in maintenance

    final BuildIconStatus stateName = getStatus(buildLocator);
    return processIconRequest(stateName.getIconName(), suffix, request);
  }

  // Note: authentication for this request is disabled in APIController configuration
  @GET
  @Path(AGGREGATED + "/{buildLocator}/" + STATUS_ICON_REQUEST_NAME + "{suffix:(.*)?}")
  public Response serveAggregatedBuildStatusIcon(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String locator,
                                                 @PathParam("suffix") final String suffix,
                                                 @Context HttpServletRequest request) {
    final BuildIconStatus stateName = getAggregatedStatus(locator);
    return processIconRequest(stateName.getIconName(), suffix, request);
  }

  @GET
  @Path(AGGREGATED + "/{buildLocator}/" + "status")
  public String serveAggregatedBuildStatus(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String locator) {
    final PagedSearchResult<BuildPromotion> builds = myBuildPromotionFinder.getItems(locator);
    Status resultingStatus = Status.UNKNOWN;
    for (BuildPromotion buildPromotion : builds.myEntries) {
      final SBuild build = buildPromotion.getAssociatedBuild();
      if (build != null) {
        final Status status = build.getStatusDescriptor().getStatus();
        resultingStatus = Status.getWorstStatus(resultingStatus, status);
      }
    }
    return resultingStatus.getText();
  }

  @Path(AGGREGATED + "/{buildLocator}" + ARTIFACTS)
  public FilesSubResource serveAggregatedBuildArtifacts(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") final String locator,
                                                        @QueryParam("logBuildUsage") final Boolean logBuildUsage) {
    if (logBuildUsage != null && logBuildUsage) {
      throw new BadRequestException("Logging usage of the artifacts is not supported for aggregated build request");
    }
    final PagedSearchResult<BuildPromotion> builds = myBuildPromotionFinder.getItems(locator);
    final String urlPrefix = Util.concatenatePath(myBeanContext.getApiUrlBuilder().transformRelativePath(API_BUILDS_URL), AGGREGATED, locator, ARTIFACTS); //consider URL-escaping locator here
    return new FilesSubResource(new FilesSubResource.Provider() {
      @Override
      @NotNull
      public Element getElement(@NotNull final String path) {
        return AggregatedBuildArtifactsElementBuilder.getBuildAggregatedArtifactElement(path, builds.myEntries, myBeanContext.getServiceLocator());
      }

      @NotNull
      @Override
      public String getArchiveName(@NotNull final String path) {
        return "aggregated_" + builds.myEntries.size() + "_builds" + "_artifacts";
      }

    }, urlPrefix, myBeanContext, true);
  }

  /**
   * Experimental support for marking a queued agent-less build as started.
   * Use with caution: this API is not yet stable and is subject to change.
   */
  //todo: add GET .../runningData for consistency
    @PUT
    @Path("/{buildLocator}/runningData")
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @ApiOperation("Starts the queued build as an agent-less build and returns the corresponding running build.")
    public Build markBuildAsRunning(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                    String requestor,
                                    @QueryParam("fields") String fields) {
      BuildPromotion buildPromotion = myBuildPromotionFinder.getBuildPromotion(null, buildLocator);
      checkBuildOperationPermission(buildPromotion);
      QueuedBuildEx build = (QueuedBuildEx)buildPromotion.getQueuedBuild();
      if (build != null) {
        try {
          build.startBuild(requestor);
        } catch (IllegalStateException e) {
          throw new BadRequestException("Error on attempt to mark the build as running: " + e.getMessage(), e);
        }
      } else {
        throw new BadRequestException("Build with promotion id " + buildPromotion.getId() + " is not a queued build");
      }
      return new Build(buildPromotion, new Fields(fields), myBeanContext);
    }

  /**
   * Experimental support for logging a message to a running build.
   * Use with caution: this API is not yet stable and is subject to change.
   */
  @POST
  @Path("/{buildLocator}/log")
  @Consumes({MediaType.TEXT_PLAIN})
  @ApiOperation("Adds a message to the build log. Service messages are accepted.")
  public void addLogMessage(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                            String logLines,
                            @QueryParam("fields") String fields) {
    BuildPromotion buildPromotion = myBuildPromotionFinder.getBuildPromotion(null, buildLocator);
    checkBuildOperationPermission(buildPromotion);
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null) {
      throw new NotFoundException("Build with id " + buildPromotion.getId() + " is not in the runing or finished state");
    }
    //check for running?
    logMessage(build, logLines);
    //return next command?
  }

  /**
   * Experimental support for streaming messages to a running build.
   * Use with caution: this API is not yet stable and is subject to change.
   *   this is an experiment to try to support for reading streamed input
   *   Can be used with a command like:
   *   curl -H "Transfer-Encoding: chunked" -H "Content-Type: text/plain" -X POST -T -  .../app/rest/runningBuilds/XXX/log/stream
   */
  /*
  @POST
  @Path("/{buildLocator}/log/stream")
  @Consumes({MediaType.TEXT_PLAIN})
  @ApiOperation(hidden = true, value = "Experimental ability to stream build log as request body")
  public void addLogMessage(@PathParam("buildLocator") String buildLocator, InputStream requestBody) {
    //ideally, this should be async not to waste the thread on input waiting
    BuildPromotion buildPromotion = myBuildPromotionFinder.getBuildPromotion(null, buildLocator);
    checkBuildOperationPermission(buildPromotion);
    // check for running?
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null) {
      throw new NotFoundException("Build with id " + buildPromotion.getId() + " is not in the runing or finished state");
    }

    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
      String line = reader.readLine();
      while (line != null) {
        logMessage(build, line);
        line = reader.readLine();
      }
    } catch (IOException e) {
      //todo: log
      throw new OperationException("Error reading request body: " + e.toString(), e);
    }
  }
  */

  //todo: ideally, should put all the data from the same client into the same flow in the build
  //can also try to put it into a dedicated block...
  private void logMessage(@NotNull final SBuild build, final String lines) {
//    build.getBuildLog().message(lines, Status.NORMAL, MessageAttrs.attrs());
    if (build.isFinished() || !(build instanceof RunningBuildEx)) {
      throw new NotFoundException("Build with id " + build.getBuildId() + " is already finished");
    }
    RunningBuildEx runningBuild = (RunningBuildEx)build;
    try {
      myBeanContext.getSingletonService(BuildAgentMessagesQueue.class).processMessages(runningBuild, Collections.singletonList(DefaultMessagesInfo.createTextMessage(lines)));
    } catch (InterruptedException e) {
      throw new OperationException("Got interrupted", e); //todo
    } catch (BuildAgentMessagesQueue.BuildMessagesQueueFullException e) {
      throw new OperationException("Failed to add messages as the queue is full", e); //todo
    }
  }

  /**
   * Experimental support for finishing the build. The actual build finish process can be long and can finish after the request has returned.
   * Use with caution: this API is not yet stable and is subject to change.
   */
  @PUT
  @Path("/{buildLocator}/finishDate")
  @Consumes({MediaType.TEXT_PLAIN})
  @Produces({MediaType.TEXT_PLAIN})
  @ApiOperation("Marks the running build as finished by passing agent time of the build finish. An empty finish date means \"now\".")
  public String setFinishedTime(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                String date) {
    BuildPromotion buildPromotion = myBuildPromotionFinder.getBuildPromotion(null, buildLocator);
    checkBuildOperationPermission(buildPromotion);
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null) {
      throw new NotFoundException("Build with id " + buildPromotion.getId() + " is not in the runing or finished state");
    }
    if (build.isFinished() || !(build instanceof RunningBuildEx)) {
      throw new NotFoundException("Build with id " + buildPromotion.getId() + " is already finished");
    }
    RunningBuildEx runningBuild = (RunningBuildEx)build;
    TimeService timeService = myBeanContext.getSingletonService(TimeService.class);
    Date finishTime = !StringUtil.isEmpty(date) ? TimeWithPrecision.parse(date, timeService).getTime() : new Date(timeService.now());
    logMessage(build, "Build finish request received via REST endpoint");
    myBeanContext.getSingletonService(BuildAgentMessagesQueue.class).buildFinished(runningBuild, finishTime, false);
    return Util.formatTime(finishTime);
  }

  private void checkBuildOperationPermission(@NotNull final BuildPromotion buildPromotion) {
    AuthorityHolder user = myPermissionChecker.getCurrent();
    Long buildId = ServerAuthUtil.getBuildIdIfBuildAuthority(user);
    if (buildId != null) {
      SBuild build = buildPromotion.getAssociatedBuild();
      if (build != null && buildId == build.getBuildId()) {
        return;
      }
    }
    try {
      myPermissionChecker.checkPermission(Permission.EDIT_PROJECT, buildPromotion);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedException("Should use build authentication or have \"" + Permission.EDIT_PROJECT + "\" permission to do the build operation", e);
    }
  }

  @GET
  @Path("/{buildLocator}/finishDate")
  @Produces("text/plain")
  public String getBuildFinishDate(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator) {
    return serveBuildFieldFromBuildOnly(buildLocator, "finishDate");
  }

  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldFromBuildOnly(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                             @PathParam("field") String field) {
    return Build.getFieldValue(myBuildFinder.getBuildPromotion(null, buildLocator), field, myBeanContext);
  }

  @NotNull
  private BuildIconStatus getAggregatedStatus(@Nullable final String multipleBuildsLocator) {
    BuildIconStatus resultState = BuildIconStatus.NOT_FOUND;
    boolean[] hasNext = new boolean[1];
    hasNext[0] = true;
    final BuildIconStatus.Value<BuildPromotion> buildPromotionRetriever = new BuildIconStatus.Value<BuildPromotion>() {
      private List<BuildPromotion> myBuilds;
      private int currentIndex = 0;

      @NotNull
      @Override
      public BuildPromotion get() {
        if (myBuilds == null) {
          hasNext[0] = false;
          myBuilds = myBuildPromotionFinder.getItems(multipleBuildsLocator).myEntries;
          if (myBuilds.isEmpty()) {
            throw new NotFoundException("No builds found");
          }
        }
        hasNext[0] = currentIndex < myBuilds.size() - 1;
        return myBuilds.get(currentIndex++);
      }
    };
    while (hasNext[0]) {
      final BuildIconStatus stateName = BuildIconStatus.create(myBeanContext, buildPromotionRetriever);
      if (resultState.compareTo(stateName) < 0) {
        resultState = stateName;
      }
    }
    return resultState;
  }

  @NotNull
  private BuildIconStatus getStatus(@Nullable final String buildLocator) {
    return BuildIconStatus.create(myBeanContext, new BuildIconStatus.Value<BuildPromotion>() {
      @NotNull
      @Override
      public BuildPromotion get() {
        return myBuildFinder.getBuildPromotion(null, buildLocator);
      }
    });
  }

  private Response processIconRequest(final String stateName, final String suffix, final @Context HttpServletRequest request) {
    final String iconFileName = IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/" + stateName + (StringUtil.isEmpty(suffix) ? ".png" : suffix);
    final String resultIconFileName;
    try {
      resultIconFileName = getRealFileName(iconFileName);
    } catch (AccessDeniedException e) {
      if (StringUtil.isEmpty(suffix)){
        throw new InvalidStateException("Error retrieving requested resource", e);
      } else{
        throw new BadRequestException("Wrong suffix '" + suffix + "' is specified. Try omitting it."); //todo: list extensions, see below
      }
    }

    if (resultIconFileName == null || !new File(resultIconFileName).isFile()) {
      LOG.debug("Resource file not found: '" + iconFileName + "'" + (StringUtil.isEmpty(suffix) ? "" : ", using custom suffix '" + suffix + "' from request"));
      throw new NotFoundException("There is no resource file with relative path '" + iconFileName + "'" +
                                  (StringUtil.isEmpty(suffix) ? " (installation corrupted?)" : ", try omitting '" + suffix + "' suffix"));
      //todo: list extensions in file under IMG_STATUS_WIDGET_ROOT_DIRECTORY, see also above
    }

    final File resultIconFile = new File(resultIconFileName);
    final StreamingOutput streamingOutput = new StreamingOutput() {
      public void write(final OutputStream output) throws WebApplicationException {
        InputStream inputStream = null;
        try {
          inputStream = new BufferedInputStream(new FileInputStream(resultIconFile));
          TCStreamUtil.writeBinary(inputStream, output);
        } catch (IOException e) {
          //todo add better processing
          throw new OperationException("Error while retrieving file '" + resultIconFile.getName() + "': " + e.getMessage(), e);
        } finally {
          FileUtil.close(inputStream);
        }
      }
    };

    final String mediaType = WebUtil.getMimeType(request, resultIconFileName);
    final Response.ResponseBuilder response = Response.ok(streamingOutput, mediaType).header("Cache-Control", "no-cache, private");
    //see also setting no caching headers in jetbrains.buildServer.server.rest.request.FilesSubResource.getContentByStream()
    response.header("ETag", "W/\"" + EncryptUtil.md5(String.valueOf(stateName)) + "\"");  //mark ETag as "weak"
    // see jetbrains.buildServer.web.util.WebUtil.addCacheHeadersForIE and http://youtrack.jetbrains.com/issue/TW-9821 for details)
    if (WebUtil.isIE10OrLower(request)) {
      response.header("Cache-Control", "private,must-revalidate");
      response.header("Pragma", "private");
    }
    return response.build();
  }

  enum BuildIconStatus {
    NOT_FOUND("not_found"),
    INTERNAL_ERROR("internal_error"),
    PERMISSION("permission"),
    CANCELED("canceled"),
    RUNNING("running"),
    SUCCESSFUL("successful"),
    ERROR("error"),
    FAILED("failed");

    private final String myIconName;

    BuildIconStatus(String iconName) {
      myIconName = iconName;
    }

    public String getIconName() {
      return myIconName;
    }

   @NotNull
    private static BuildIconStatus create(final BeanContext beanContext, final Value<BuildPromotion> buildPromotionRetriever) {
      BuildIconStatus[] result = new BuildIconStatus[1];
      try {
        final SecurityContextEx securityContext = beanContext.getSingletonService(SecurityContextEx.class);
        final AuthorityHolder currentUserAuthorityHolder = securityContext.getAuthorityHolder();
        try {
          securityContext.runAsSystem(new SecurityContextEx.RunAsAction() {
            public void run() throws Throwable {
              BuildPromotion buildPromotion = buildPromotionRetriever.get();
              if (!hasPermissionsToViewStatus(buildPromotion, currentUserAuthorityHolder, beanContext)) {
                LOG.info("No permissions to access requested build. Either authenticate as user with appropriate permissions, or ensure 'guest' user has appropriate permissions " +
                         "or enable external status widget for the build configuration.");
                result[0] = PERMISSION;
                return;
              }
              final SBuild build = buildPromotion.getAssociatedBuild();
              //todo: support queued builds
              if (build == null){
                result[0] = NOT_FOUND;
                return;
              }
              if (!build.isFinished()) {
                result[0] = RUNNING;  //todo: support running/failing and may be running/last failed
                return;
              }
              if (build.getCanceledInfo() != null) {
                result[0] = CANCELED;
                return;
              }
              if (build.getStatusDescriptor().isSuccessful()) {
                result[0] = SUCCESSFUL;
                return;
              }
              if (build.isInternalError()) {
                result[0] = ERROR;
                return;
              }
              result[0] = FAILED;
            }
          });
          return result[0];
        } catch (NotFoundException e) {
          if (TeamCityProperties.getBoolean("rest.buildRequest.statusIcon.enableNotFoundResponsesWithoutPermissions") ||
              hasPermissionsToViewStatusGlobally(securityContext, beanContext)) {
            LOG.debug("Cannot find build for status icon under system, returning 'not_found': " + e.getMessage());
            return NOT_FOUND;
          } else {
            //should return the same error as when no permissions in order not to expose build existence
            LOG.debug("Cannot find build for status icon under system, returning 'permission': " + e.getMessage());
            return PERMISSION;
          }
        } catch (Throwable throwable) {
          LOG.info("Error while retrieving build under system, returning 'internal_error'': " + throwable.toString(), throwable);
          return INTERNAL_ERROR; //todo: use separate icon for errors (most importantly, wrong request)
        }
      } catch (AccessDeniedException e) {
        LOG.warn("Unexpected access denied error encountered while retrieving build, returning 'permission': " + e.toString());
        return PERMISSION;
      }
    }

    public interface Value<S> {
      @NotNull
      S get();
    }

    private static boolean hasPermissionsToViewStatus(@NotNull final BuildPromotion build, @NotNull final AuthorityHolder authorityHolder, final BeanContext beanContext) {
      final SBuildType buildType = build.getBuildType();
      if (buildType == null) {
        throw new OperationException("No build type found for build.");
      }

      if (buildType.isAllowExternalStatus() && TeamCityProperties.getBooleanOrTrue("rest.buildRequest.statusIcon.enableWithStatusWidget")) {
        return true;
      }

      //todo: how to distinguish no user from system? Might check for system to support authToken requests...
      if (authorityHolder.getAssociatedUser() != null &&
          authorityHolder.isPermissionGrantedForProject(buildType.getProjectId(), Permission.VIEW_PROJECT)) {
        return true;
      }

      final SUser guestUser = beanContext.getSingletonService(UserModel.class).getGuestUser();
      return beanContext.getSingletonService(LoginConfiguration.class).isGuestLoginAllowed() &&
             guestUser.isPermissionGrantedForProject(buildType.getProjectId(), Permission.VIEW_PROJECT);
    }

    private static boolean hasPermissionsToViewStatusGlobally(@NotNull final SecurityContextEx securityContext, final BeanContext beanContext) {
      final AuthorityHolder authorityHolder = securityContext.getAuthorityHolder();
      //todo: how to distinguish no user from system? Might check for system to support authToken requests...
      if (authorityHolder.getAssociatedUser() != null &&
          authorityHolder.isPermissionGrantedGlobally(Permission.VIEW_PROJECT)) {
        return true;
      }
      final SUser guestUser = beanContext.getSingletonService(UserModel.class).getGuestUser();
      return beanContext.getSingletonService(LoginConfiguration.class).isGuestLoginAllowed() &&
             guestUser.isPermissionGrantedGlobally(Permission.VIEW_PROJECT);
    }
  }

  private String getRealFileName(final String relativePath) {
    final ServletContext servletContext = myBeanContext.getSingletonService(ServletContext.class);
    final String realPath = servletContext.getRealPath(relativePath);
    FileSecurityUtil.checkInsideDirectory(new File(realPath), new File(servletContext.getRealPath("/")));
    return realPath;
  }

  void initForTests(@NotNull final BeanContext beanContext) {
    myBeanContext = beanContext;
    myBuildFinder = myBeanContext.getSingletonService(BuildFinder.class);
    myBuildPromotionFinder = myBeanContext.getSingletonService(BuildPromotionFinder.class);
    myBuildTypeFinder = myBeanContext.getSingletonService(BuildTypeFinder.class);
    myPermissionChecker = myBeanContext.getSingletonService(PermissionChecker.class);
  }

  private class BuildArtifactsProvider extends FilesSubResource.Provider implements FilesSubResource.DownloadProcessor{
    private final BuildPromotion myBuildPromotion;
    private final Boolean myResolveParameters;
    private final Boolean myLogBuildUsage;
    private final String myUrlPrefix;

    public BuildArtifactsProvider(final BuildPromotion buildPromotion, final Boolean resolveParameters, final Boolean logBuildUsage, final String urlPrefix) {
      myBuildPromotion = buildPromotion;
      myResolveParameters = resolveParameters;
      myLogBuildUsage = logBuildUsage;
      myUrlPrefix = urlPrefix;
    }

    @Override
    @NotNull
    public Element getElement(@NotNull final String path) {
      return BuildArtifactsFinder.getArtifactElement(myBuildPromotion, path, myBeanContext.getServiceLocator());
    }

    @NotNull
    @Override
    public String preprocess(@Nullable final String path) {
      return getResolvedIfNecessary(myBuildPromotion, path, myResolveParameters);
    }

    @NotNull
    @Override
    public String getArchiveName(@NotNull final String path) {
      return getBuildFileName(myBuildPromotion, path) + "_artifacts";
    }

    @Override
    public boolean fileContentServed(@Nullable final String path, @NotNull final HttpServletRequest request) {
      if (myLogBuildUsage == null || myLogBuildUsage) {
        //see RepositoryUtil.logArtifactDownload
        Long authenticatedBuild = WebAuthUtil.getAuthenticatedBuildId(request);
        if (authenticatedBuild != null) {
          myBeanContext.getSingletonService(DownloadedArtifactsLogger.class).logArtifactDownload(authenticatedBuild, BuildPromotionFinder.getBuildId(myBuildPromotion), path);
          return true;
        } else {
          if (myLogBuildUsage != null) {
            throw new BadRequestException("No build authentication found while 'logBuildUsage' parameter is set");
          }
        }
      }
      return false;
    }

    @Override
    public boolean processDownload(@NotNull final Element element, @NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
      boolean useCoreLogic = TeamCityProperties.getBooleanOrTrue("rest.buildRequest.artifacts.download.useCoreDownloadProcessor");
      if (!useCoreLogic) return false;

      if (element instanceof BuildArtifactsFinder.BuildHoldingElement){
        BuildArtifactsFinder.BuildHoldingElement buildArtifact = (BuildArtifactsFinder.BuildHoldingElement)element;
        try {
          SBuild build = buildArtifact.getBuildPromotion().getAssociatedBuild();
          if (build == null) return false; //TeamCity API issue: cannot download artifacts from a queued build

          boolean setContentDisposition = FilesSubResource.getSetContentDisposition(element, request, response);
          String eTag = null;
          if (!TeamCityProperties.getBoolean("rest.buildRequest.artifacts.download.useCoreETag")) {
            eTag = FilesSubResource.getETag(element, myUrlPrefix);
          }
          myBeanContext.getSingletonService(HttpDownloadProcessor.class)
                       .processArtifactDownload(build, buildArtifact.getBuildArtifact(), setContentDisposition, eTag, request, response);
          return true;
        } catch (IOException e) {
          //TeamCity API issue: not clear what can be done here.
          throw new OperationException("Error processing build artifact download" + e.toString(), e);
        }
      }
      return false;
    }
  }
}
