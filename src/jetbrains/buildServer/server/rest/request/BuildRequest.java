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
import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.agent.ServerProvidedProperties;
import jetbrains.buildServer.controllers.FileSecurityUtil;
import jetbrains.buildServer.controllers.HttpDownloadProcessor;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.parameters.ProcessingResult;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.build.TagFinder;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.*;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.BuildCancelRequest;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.build.Tags;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.change.BuildChanges;
import jetbrains.buildServer.server.rest.model.issue.IssueUsages;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrences;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.AggregatedBuildArtifactsElementBuilder;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.LoginConfiguration;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.tags.TagsManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TCStreamUtil;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.web.util.SessionUser;
import jetbrains.buildServer.web.util.WebAuthUtil;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static String getBuildHref(@NotNull SBuild build) {
    return API_BUILDS_URL + "/" + getBuildLocator(build);
  }

  public static String getBuildHref(@NotNull BuildPromotion build) {
    return API_BUILDS_URL + "/" + getBuildLocator(build);
  }

  @Nullable
  public static String getBuildsHref(@NotNull final BranchData branch, @Nullable final String buildsLocator) {
    SBuildType buildType = branch.getBuildType();
    if (buildType == null) return null;
    return API_BUILDS_URL + "?locator=" + BuildPromotionFinder.getLocator(buildType, branch, buildsLocator);
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
                               @QueryParam("start") Long start,
                               @QueryParam("count") Integer count,
                               @QueryParam("locator") String locator,
                               @QueryParam("fields") String fields,
                               @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    return myBuildFinder.getBuildsForRequest(myBuildTypeFinder.getBuildTypeIfNotNull(buildTypeLocator), status, userLocator, includePersonal,
                                           includeCanceled, onlyPinned, tags, agentName, sinceBuildLocator, sinceDate, start, count,
                                           locator, "locator", uriInfo, request,   new Fields(fields), myBeanContext
    );
  }

  @DELETE
  @Produces({"application/xml", "application/json"})
  public void deleteBuilds(@QueryParam("locator") String locator, @Context HttpServletRequest request) {
    if (locator == null){
      throw new BadRequestException("Empty 'locator' parameter specified.");
    }
    final List<BuildPromotion> builds = myBuildFinder.getBuilds(null, locator).myEntries;
    final int deleteLimit = TeamCityProperties.getInteger(REST_BUILD_REQUEST_DELETE_LIMIT, 10);
    if (builds.size() > deleteLimit){
      throw new BadRequestException("Refusing to delete more than " + deleteLimit + " builds as a precaution measure." +
                                    " The limit is set via '" + REST_BUILD_REQUEST_DELETE_LIMIT + "' internal property on the server.");
    }
    for (BuildPromotion build : builds) {
      deleteBuild(request, build);
    }
  }

  /**
   * Experimental support only
   * Changing of some attributes is not supported and can result in strange and "unpredictable" behavior.
   */
  @Path("/{buildLocator}/attributes")
  public ParametersSubResource getAttributes(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
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
      public void persist() {
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
  public Build serveBuild(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields, @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    return new Build(build,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}/resulting-properties/")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildActualParameters(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    myPermissionChecker.checkPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build.getBuildPromotion());
    return new Properties(Build.getBuildResultingParameters(build.getBuildPromotion(), myBeanContext.getServiceLocator()).getAll(), null, new Fields(fields), myBeanContext);
    /* alternative
    try {
      return new Properties(((FinishedBuildEx)build).getBuildFinishParameters());
    } catch (ClassCastException e) {
      return null;
    }
    */
  }

  @GET
  @Path("/{buildLocator}/resulting-properties/{propertyName}")
  @Produces({"text/plain"})
  public String getParameter(@PathParam("buildLocator") String buildLocator, @PathParam("propertyName") String propertyName) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    myPermissionChecker.checkPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build.getBuildPromotion());
    return BuildTypeUtil.getParameter(propertyName, Build.getBuildResultingParameters(build.getBuildPromotion(), myBeanContext.getServiceLocator()), true, true, myBeanContext.getServiceLocator());
  }

  /**
   * Experimental only
   */
  @GET
  @Path("/{buildLocator}/resolved/{value}")
  @Produces({"text/plain"})
  public String getResolvedParameter(@PathParam("buildLocator") String buildLocator, @PathParam("value") String value) {
    return getResolvedIfNecessary(myBuildPromotionFinder.getItem(buildLocator), value, true);
  }

  @Path("/{buildLocator}" + ARTIFACTS)
  public FilesSubResource getFilesSubResource(@PathParam("buildLocator") final String buildLocator,
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
  public Response serveSourceFile(@PathParam("buildLocator") final String buildLocator, @PathParam("fileName") final String fileName) {
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
  @Path("/{buildLocator}/related-issues")
  @Produces({"application/xml", "application/json"})
  public IssueUsages serveBuildRelatedIssuesOld(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    return serveBuildRelatedIssues(buildLocator, fields);
  }

  @GET
  @Path("/{buildLocator}" + RELATED_ISSUES)
  @Produces({"application/xml", "application/json"})
  public IssueUsages serveBuildRelatedIssues(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return new IssueUsages(build,  new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {

    return Build.getFieldValue(myBuildFinder.getBuildPromotion(null, buildLocator), field, myBeanContext);
  }

  @GET
  @Path("/{buildLocator}" + STATISTICS + "/")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildStatisticValues(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return new Properties(Build.getBuildStatisticsValues(build), null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}" + STATISTICS + "/{name}")
  @Produces("text/plain")
  public String serveBuildStatisticValue(@PathParam("buildLocator") String buildLocator,
                                         @PathParam("name") String statisticValueName) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);

    return Build.getBuildStatisticValue(build, statisticValueName);
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


  // see also corresponding methods in BuildQueueRequest
  @GET
  @Path("/{buildLocator}/tags/")
  @Produces({"application/xml", "application/json"})
  public Tags serveTags(@PathParam("buildLocator") String buildLocator, @QueryParam("locator") String tagLocator, @QueryParam("fields") String fields) {
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
  public Tags replaceTags(@PathParam("buildLocator") String buildLocator, @QueryParam("locator") String tagLocator, Tags tags,
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
  public Tags addTags(@PathParam("buildLocator") String buildLocator, Tags tags, @QueryParam("fields") String fields) {
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
  public String addTag(@PathParam("buildLocator") String buildLocator, String tagName, @Context HttpServletRequest request) {
    if (StringUtil.isEmpty(tagName)) { //check for empty tags: http://youtrack.jetbrains.com/issue/TW-34426
      throw new BadRequestException("Cannot apply empty tag, should have non empty request body");
    }
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);

    tagsManager.addTagDatas(build, Collections.singleton(TagData.createPublicTag(tagName)));
    return tagName;
  }
//todo: add GET (true/false) and DELETE, amy be PUT (true/false) for a single tag

//todo: rework .../pin to have consistent GET/PUT, see also agents/.../enabled
  /**
   * Fetches current build pinned status.
   *
   * @param buildLocator build locator
   * @return "true" is the build is pinned, "false" otherwise
   */
  @GET
  @Path("/{buildLocator}/pin/")
  @Produces({"text/plain"})
  public String getPinned(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return Boolean.toString(build.isPinned());
  }

  /**
   * Pins a build
   *
   * @param buildLocator build locator
   */
  @PUT
  @Path("/{buildLocator}/pin/")
  @Consumes({"text/plain"})
  public void pinBuild(@PathParam("buildLocator") String buildLocator, String comment, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    if (!(build instanceof SFinishedBuild)) {
      throw new BadRequestException("Cannot pin build that is not finished.");
    }
    SFinishedBuild finishedBuild = (SFinishedBuild) build;
    finishedBuild.setPinned(true, SessionUser.getUser(request), comment);
  }

  /**
   * Unpins a build
   *
   * @param buildLocator build locator
   */
  @DELETE
  @Path("/{buildLocator}/pin/")
  @Consumes({"text/plain"})
  public void unpinBuild(@PathParam("buildLocator") String buildLocator, String comment, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    if (!(build instanceof SFinishedBuild)) {
      throw new BadRequestException("Cannot unpin build that is not finished.");
    }
    SFinishedBuild finishedBuild = (SFinishedBuild) build;
    finishedBuild.setPinned(false, SessionUser.getUser(request), comment);
  }

  @PUT
  @Path("/{buildLocator}/comment")
  @Consumes({"text/plain"})
  public void replaceComment(@PathParam("buildLocator") String buildLocator, String text, @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final SUser user = SessionUser.getUser(request);
    if (user == null){ //TeamCity API issue: SBuild and BuildPromotion has different behavior here
      throw new BadRequestException("Cannot add comment when there is no current user");
    }
    build.setBuildComment(user, text);
  }

  @DELETE
  @Path("/{buildLocator}/comment")
  public void deleteComment(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final SUser user = SessionUser.getUser(request);
    if (user == null){
      throw new BadRequestException("Cannot add comment when there is no current user");
    }
    build.setBuildComment(user, null);
  }

  @GET
  @Path("/{buildLocator}/" + Build.CANCELED_INFO)
  @Produces({"application/xml", "application/json"})
  public Comment getCanceledInfo(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return Build.getCanceledComment(build,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}/example/buildCancelRequest")
  @Produces({"application/xml", "application/json"})
  public BuildCancelRequest cancelBuild(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    return new BuildCancelRequest("example build cancel comment", false);
  }

  @GET
  @Path("/{buildLocator}/problemOccurrences")
  @Produces({"application/xml", "application/json"})
  public ProblemOccurrences getProblems(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final List<BuildProblem> buildProblems = ((BuildPromotionEx)build).getBuildProblems();//todo: (TeamCity) is this OK to use?
    return new ProblemOccurrences(buildProblems, ProblemOccurrenceRequest.getHref(build), null,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}/" + TESTS)
  @Produces({"application/xml", "application/json"})
  public TestOccurrences getTests(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    //todo: investigate test repeat counts support
    return new TestOccurrences(TestOccurrenceFinder.getBuildStatistics(build).getAllTests(), TestOccurrenceRequest.getHref(build), null, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only
   */
  @GET
  @Path("/{buildLocator}/artifactsDirectory")
  @Produces({"text/plain"})
  public String getArtifactsDirectory(@PathParam("buildLocator") String buildLocator) {
    myPermissionChecker.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    return build.getArtifactsDirectory().getAbsolutePath();
  }

  /**
   * Experimental support only
   */
  @GET
  @Path("/{buildLocator}/artifactDependencyChanges")
  @Produces({"application/xml", "application/json"})
  public BuildChanges getArtifactDependencyChanges(@PathParam("buildLocator") String buildLocator, @QueryParam("fields") String fields) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    return Build.getBuildChanges(build, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{buildLocator}")
  @Consumes({"application/xml", "application/json"})
  public Build cancelBuild(@PathParam("buildLocator") String buildLocator,
                           BuildCancelRequest cancelRequest,
                           @QueryParam("fields") String fields,
                           @Context HttpServletRequest request) {
    BuildPromotion build = myBuildFinder.getBuildPromotion(null, buildLocator);
    final SRunningBuild runningBuild = Build.getRunningBuild(build, myBeanContext.getServiceLocator());
    if (runningBuild == null){
      throw new BadRequestException("Cannot cancel not running build.");
    }
    final SUser currentUser = SessionUser.getUser(request);
    runningBuild.stop(currentUser, cancelRequest.comment);
    if (cancelRequest.readdIntoQueue){
      if (currentUser == null){
        throw new BadRequestException("Cannot readd build into queue when no current user is present. Please make sure the operation is performed uinder a regular user.");
      }
      restoreInQueue(runningBuild, currentUser);
    }
    final SBuild associatedBuild = build.getAssociatedBuild();
    if (associatedBuild == null){
      return null;
    }
    return new Build(associatedBuild,  new Fields(fields), myBeanContext);
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
  public void deleteBuild(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    deleteBuild(request, myBuildFinder.getBuildPromotion(null, buildLocator));
  }

  private void deleteBuild(@NotNull final HttpServletRequest request, @NotNull final BuildPromotion build) {
    final SQueuedBuild queuedBuild = build.getQueuedBuild();
    final SUser currentUser = SessionUser.getUser(request);
    if (queuedBuild != null){
      final jetbrains.buildServer.serverSide.BuildQueue buildQueue = myBeanContext.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class);
      buildQueue.removeItems(Collections.singleton(queuedBuild.getItemId()), currentUser, null);
    }

    SBuild finishedBuild = build.getAssociatedBuild();
    if (finishedBuild != null) {
      if (!finishedBuild.isFinished()) {
        final SRunningBuild runningBuild = Build.getRunningBuild(build, myBeanContext.getServiceLocator());
        if (runningBuild != null) {
          runningBuild.stop(currentUser, null);
          finishedBuild = build.getAssociatedBuild();
          if (finishedBuild == null) {
            throw new OperationException("Cannot find associated build for promotion '" + runningBuild.getBuildPromotion().getId() + "'.");
          }
        }
      }
      DataProvider.deleteBuild(finishedBuild, myBeanContext.getSingletonService(BuildHistory.class));
    }
  }

  // Note: authentication for this request is disabled in APIController configuration
  @GET
  @Path("/{buildLocator}/" + STATUS_ICON_REQUEST_NAME + "{suffix:(.*)?}")
  public Response serveBuildStatusIcon(@PathParam("buildLocator") final String buildLocator, @PathParam("suffix") final String suffix, @Context HttpServletRequest request) {
    //todo: may also use HTTP 304 for different resources in order to make it browser-cached
    //todo: return something appropriate when in maintenance

    final BuildIconStatus stateName = getStatus(buildLocator);
    return processIconRequest(stateName.getIconName(), suffix, request);
  }

  // Note: authentication for this request is disabled in APIController configuration
  @GET
  @Path(AGGREGATED + "/{buildLocator}/" + STATUS_ICON_REQUEST_NAME + "{suffix:(.*)?}")
  public Response serveAggregatedBuildStatusIcon(@PathParam("buildLocator") String locator, @PathParam("suffix") final String suffix, @Context HttpServletRequest request) {
    final BuildIconStatus stateName = getAggregatedStatus(locator);
    return processIconRequest(stateName.getIconName(), suffix, request);
  }

  @GET
  @Path(AGGREGATED + "/{buildLocator}/" + "status")
  public String serveAggregatedBuildStatus(@PathParam("buildLocator") String locator) {
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
  public FilesSubResource serveAggregatedBuildArtifacts(@PathParam("buildLocator") final String locator,
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

  private Response processIconRequest(final String stateName, final @PathParam("suffix") String suffix, final @Context HttpServletRequest request) {
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
    if (WebUtil.isIE(request)) {
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
