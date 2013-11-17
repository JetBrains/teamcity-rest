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

import com.intellij.openapi.diagnostic.Logger;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.ProcessingResult;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildArtifactsFinder;
import jetbrains.buildServer.server.rest.data.BuildFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.*;
import jetbrains.buildServer.server.rest.model.files.File;
import jetbrains.buildServer.server.rest.model.files.Files;
import jetbrains.buildServer.server.rest.model.issue.IssueUsages;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.TriggeredBy;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TCStreamUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.web.artifacts.browser.ArtifactElement;
import jetbrains.buildServer.web.util.SessionUser;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path(BuildRequest.API_BUILDS_URL)
public class BuildRequest {
  private static final Logger LOG = Logger.getInstance(BuildRequest.class.getName());
  public static final String IMG_STATUS_WIDGET_ROOT_DIRECTORY = "/img/statusWidget";
  public static final String STATUS_ICON_REQUEST_NAME = "statusIcon";
  public static final String RELATED_ISSUES = "/relatedIssues";
  public static final String TESTS = "testOccurrences";

  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private BuildArtifactsFinder myBuildArtifactsFinder;

  public static final String BUILDS_ROOT_REQUEST_PATH = "/builds";
  public static final String API_BUILDS_URL = Constants.API_URL + BUILDS_ROOT_REQUEST_PATH;

  public static final String ARTIFACTS = "/artifacts";
  public static final String METADATA = "/metadata";
  public static final String ARTIFACTS_METADATA = ARTIFACTS + METADATA;
  public static final String CONTENT = "/content";
  public static final String ARTIFACTS_CONTENT = ARTIFACTS + CONTENT;
  public static final String CHILDREN = "/children";
  public static final String ARTIFACTS_CHILDREN = ARTIFACTS + CHILDREN;

  @Context
  private ApiUrlBuilder myApiUrlBuilder;
  @Context
  private ServiceLocator myServiceLocator;
  @Context
  private BeanFactory myFactory;

  public static String getBuildHref(SBuild build) {
    return API_BUILDS_URL + "/" + getBuildLocator(build);
  }

  public static String getBuildLocator(final SBuild build) {
    return "id:" + build.getBuildId();  //todo: use locator rendering here
  }

  public static String getBuildIssuesHref(final SBuild build) {
    return getBuildHref(build) + RELATED_ISSUES;
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
                               @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    return myBuildFinder.getBuildsForRequest(myBuildTypeFinder.getBuildTypeIfNotNull(buildTypeLocator), status, userLocator, includePersonal,
                                           includeCanceled, onlyPinned, tags, agentName, sinceBuildLocator, sinceDate, start, count,
                                           locator, "locator", uriInfo, request, myApiUrlBuilder
    );
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
  public Build serveBuild(@PathParam("buildLocator") String buildLocator) {
    return new Build(myBuildFinder.getBuild(null, buildLocator), myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
  }

  @GET
  @Path("/{buildLocator}/resulting-properties/")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildActualParameters(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    myDataProvider.checkProjectPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build.getProjectId());
    return new Properties(build.getParametersProvider().getAll());
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
    myDataProvider.checkProjectPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build.getProjectId());
    if (StringUtil.isEmpty(propertyName)) {
      throw new BadRequestException("Property name should not be empty");
    }
    return build.getParametersProvider().get(propertyName);
  }

  /**
   * More user-friendly URL for "/{buildLocator}/artifacts/children" one.
   */
  @GET
  @Path("/{buildLocator}" + ARTIFACTS)
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public Files getArtifacts(@PathParam("buildLocator") final String buildLocator, @Context UriInfo uriInfo) {
    return getArtifactChildren(buildLocator, "", false, null);
  }

  @GET
  @Path("/{buildLocator}" + ARTIFACTS_METADATA + "{path:(/.*)?}")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public File getArtifactMetadata(@PathParam("buildLocator") final String buildLocator,
                                  @PathParam("path") final String path,
                                  @QueryParam("resolveParameters") final Boolean resolveParameters,
                                  @QueryParam("locator") final String locator) {
    final SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return myBuildArtifactsFinder.getFile(build, getResolvedIfNecessary(build, path, resolveParameters), locator, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{buildLocator}" + ARTIFACTS_CHILDREN + "{path:(/.*)?}")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  public Files getArtifactChildren(@PathParam("buildLocator") final String buildLocator,
                                   @PathParam("path") @DefaultValue("") final String path,
                                   @QueryParam("resolveParameters") final Boolean resolveParameters,
                                   @QueryParam("locator") final String locator) {
    final SBuild build = myBuildFinder.getBuild(null, buildLocator);
    final String resolvedPath = getResolvedIfNecessary(build, path, resolveParameters);
    return myBuildArtifactsFinder.getFiles(build, resolvedPath, locator, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{buildLocator}" + ARTIFACTS_CONTENT + "{path:(/.*)?}")
  @Produces({MediaType.WILDCARD})
  public Response getArtifactContent(@PathParam("buildLocator") final String buildLocator,
                                     @PathParam("path") final String path,
                                     @QueryParam("resolveParameters") final Boolean resolveParameters,
                                     @Context HttpServletRequest request) {
    final SBuild build = myBuildFinder.getBuild(null, buildLocator);
    final String resolvedPath = getResolvedIfNecessary(build, path, resolveParameters);
    final BuildArtifact artifact = BuildArtifactsFinder.getBuildArtifact(build, resolvedPath, BuildArtifactsViewMode.VIEW_ALL_WITH_ARCHIVES_CONTENT);
    if (artifact.isDirectory()) {
      throw new NotFoundException("Cannot provide content for directory '" + resolvedPath + "'. To get children use '" +
                                  BuildArtifactsFinder.fileApiUrlBuilderForBuild(myApiUrlBuilder, build, null).getChildrenHref(new ArtifactElement(artifact)) + "'.");
    }

    final StreamingOutput output = BuildArtifactsFinder.getStreamingOutput(artifact);

    Response.ResponseBuilder builder = Response.ok();
    if (TeamCityProperties.getBooleanOrTrue("rest.build.artifacts.setMimeType")) {
      builder = builder.type(WebUtil.getMimeType(request, resolvedPath));
    } else{
      builder = builder.type(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }
    if (TeamCityProperties.getBooleanOrTrue("rest.build.artifacts.forceContentDisposition.Attachment")) {
      // make sure the file is not displayed in the browser (TW-27206)
      builder = builder.header("Content-Disposition", WebUtil.getContentDispositionValue(request, "attachment", artifact.getName()));
    } else {
      builder = builder.header("Content-Disposition", WebUtil.getContentDispositionValue(request, null, artifact.getName()));
    }
    //todo: log build downloading artifacts (also consider an option), see RepositoryDownloadController
    return builder.entity(output).build();
  }

  /**
   * @deprecated Compatibility. Use #getArtifactContent instead.
   */
  @Deprecated
  @GET
  @Path("/{buildLocator}" + ARTIFACTS + "/files{path:(/.*)?}")
  @Produces({MediaType.WILDCARD})
  public Response getArtifactFilesContent(@PathParam("buildLocator") final String buildLocator, @PathParam("path") final String fileName, @Context HttpServletRequest request) {
    return getArtifactContent(buildLocator, fileName, false, request);
  }


  @NotNull
  private String getResolvedIfNecessary(@NotNull final SBuild build, @Nullable final String value, @Nullable final Boolean resolveSupported) {
    if (resolveSupported == null || !resolveSupported || StringUtil.isEmpty(value)) {
      return value == null ? "" : value;
    }
    assert value != null;
    myDataProvider.checkProjectPermission(Permission.VIEW_BUILD_RUNTIME_DATA, build.getProjectId());
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
      fileContent = myServiceLocator.getSingletonService(VcsManager.class).getFileContent(build, fileName);
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
  public IssueUsages serveBuildRelatedIssuesOld(@PathParam("buildLocator") String buildLocator) {
    return serveBuildRelatedIssues(buildLocator);
  }

  @GET
  @Path("/{buildLocator}" + RELATED_ISSUES)
  @Produces({"application/xml", "application/json"})
  public IssueUsages serveBuildRelatedIssues(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return new IssueUsages(build, true, myApiUrlBuilder, myFactory);
  }


  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);

    return Build.getFieldValue(build, field);
  }

  @GET
  @Path("/{buildLocator}/statistics/")
  @Produces({"application/xml", "application/json"})
  public Properties serveBuildStatisticValues(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return new Properties(getBuildStatisticsValues(build));
  }

  @GET
  @Path("/{buildLocator}/statistics/{name}")
  @Produces("text/plain")
  public String serveBuildStatisticValue(@PathParam("buildLocator") String buildLocator,
                                         @PathParam("name") String statisticValueName) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);

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
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return new Tags(build.getTags());
  }

  public String getBuildStatisticValue(final SBuild build, final String statisticValueName) {
    Map<String, String> stats = getBuildStatisticsValues(build);
    String val = stats.get(statisticValueName);
    if (val == null) {
      throw new NotFoundException("No statistics data for key: " + statisticValueName + "' in build " + LogUtil.describe(build));
    }
    return val;
  }

  /**
   * Replaces build's tags.
   *
   * @param buildLocator build locator
   */
  @PUT
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Tags replaceTags(@PathParam("buildLocator") String buildLocator, Tags tags, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    build.setTags(SessionUser.getUser(request), tags.tags != null ? tags.tags : Collections.<String>emptyList());
    return new Tags(build.getTags());
  }

  /**
   * Adds a set of tags to a build
   *
   * @param buildLocator build locator
   */
  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  public void addTags(@PathParam("buildLocator") String buildLocator, Tags tags, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    if (tags.tags == null || tags.tags.isEmpty()) {
      // Nothing to add
      return;
    }
    final List<String> resultingTags = new ArrayList<String>(build.getTags());
    resultingTags.addAll(tags.tags);
    build.setTags(SessionUser.getUser(request), resultingTags);
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
  public String addTag(@PathParam("buildLocator") String buildLocator, String tagName, @Context HttpServletRequest request) {
    this.addTags(buildLocator, new Tags(Arrays.asList(tagName)), request);
    return tagName;
  }
//todo: add GET (true/false) and DELETE, amy be PUT (true/false) for a single tag

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
    if (!build.isFinished()) {
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
    if (!build.isFinished()) {
      throw new BadRequestException("Cannot unpin build that is not finished.");
    }
    SFinishedBuild finishedBuild = (SFinishedBuild) build;
    finishedBuild.setPinned(false, SessionUser.getUser(request), comment);
  }

  @PUT
  @Path("/{buildLocator}/comment")
  @Consumes({"text/plain"})
  public void replaceComment(@PathParam("buildLocator") String buildLocator, String text, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    build.setBuildComment(SessionUser.getUser(request), text);
  }

  @DELETE
  @Path("/{buildLocator}/comment")
  public void deleteComment(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    build.setBuildComment(SessionUser.getUser(request), null);
  }

  @GET
  @Path("/{buildLocator}/" + Build.CANCELED_INFO)
  @Produces({"application/xml", "application/json"})
  public Comment getCanceledInfo(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return Build.getCanceledComment(build, myApiUrlBuilder, myServiceLocator);
  }

  @GET
  @Path("/{buildLocator}/example/buildCancelRequest")
  @Produces({"application/xml", "application/json"})
  public BuildCancelRequest cancelBuild(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    return new BuildCancelRequest("example build cancel comment", false);
  }

  /*
  //todo: list occurrencies here, not types
  @GET
  @Path("/{buildLocator}/problems")
  @Produces({"application/xml", "application/json"})
  public Problems getProblems(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    final List<BuildProblem> buildProblems = ((BuildPromotionEx)build.getBuildPromotion()).getBuildProblems();//todo: (TeamCity) is this OK to use?
    return new Problems(buildProblems, null, myServiceLocator, myApiUrlBuilder);
  }
  */

  @GET
  @Path("/{buildLocator}/" + TESTS)
  @Produces({"application/xml", "application/json"})
  public TestOccurrences getTests(@PathParam("buildLocator") String buildLocator) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    final List<STestRun> allTests = build.getFullStatistics().getAllTests();
//todo: investigate test repeat counts support
    return new TestOccurrences(allTests, null, new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @POST
  @Path("/{buildLocator}")
  @Consumes({"application/xml", "application/json"})
  public Build cancelBuild(@PathParam("buildLocator") String buildLocator, BuildCancelRequest cancelRequest, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    final SRunningBuild runningBuild = Build.getRunningBuild(build, myServiceLocator);
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
    final SBuild associatedBuild = build.getBuildPromotion().getAssociatedBuild();
    if (associatedBuild == null){
      return null;
    }
    return new Build(associatedBuild, myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
  }

  @GET
  @Path("/{buildLocator}/example/buildTask")
  @Produces({"application/xml", "application/json"})
  public BuildTask getExampleBuildTask(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    return BuildTask.getExampleBuildTask(build, myServiceLocator, myApiUrlBuilder);
  }

  private void restoreInQueue(final SRunningBuild runningBuild, final User user) {
    //todo: TeamCity openAPI expose in the API. THis one is copy-paste from jetbrains.buildServer.controllers.actions.StopBuildAction.restoreInQueue
    final SAgentRestrictor agentRestrictor = ((RunningBuildEx)runningBuild).getQueuedAgentRestrictor();
    final TriggeredBy origTriggeredBy = runningBuild.getTriggeredBy();
    BuildPromotionEx promotionEx = (BuildPromotionEx)runningBuild.getBuildPromotion();

    TriggeredByBuilder tbb = new TriggeredByBuilder();
    tbb.addParameters(origTriggeredBy.getParameters());
    tbb.addParameter(TriggeredByBuilder.RE_ADDED_AFTER_STOP_NAME, String.valueOf(user.getId()));

    myServiceLocator.getSingletonService(BuildQueueEx.class).restoreInQueue(promotionEx, agentRestrictor, tbb.toString());
  }

  @DELETE
  @Path("/{buildLocator}")
  @Produces("text/plain")
  /**
   * May not work for non-personal builds: http://youtrack.jetbrains.net/issue/TW-9858
   */
  public void deleteBuild(@PathParam("buildLocator") String buildLocator, @Context HttpServletRequest request) {
    SBuild build = myBuildFinder.getBuild(null, buildLocator);
    if (!build.isFinished()) {
      final SRunningBuild runningBuild = Build.getRunningBuild(build, myServiceLocator);
      if (runningBuild != null) {
        final SUser currentUser = SessionUser.getUser(request);
        runningBuild.stop(currentUser, null);
        build = runningBuild.getBuildPromotion().getAssociatedBuild();
        if (build == null) {
          throw new OperationException("Cannot find associated build for promotion '" + runningBuild.getBuildPromotion().getId() + "'.");
        }
      }
    }
    myDataProvider.deleteBuild(build);
  }

  private boolean isPersonalUserBuild(final SBuild build, @NotNull final SUser user) {
    return user.equals(build.getOwner());
  }

  // Note: authentication for this request is disabled in APIController configuration
  @GET
  @Path("/{buildLocator}/" + STATUS_ICON_REQUEST_NAME)
  public Response serveBuildStatusIcon(@PathParam("buildLocator") final String buildLocator, @Context HttpServletRequest request) {
    //todo: may also use HTTP 304 for different resources in order to make it browser-cached
    //todo: return something appropriate when in maintenance
    //todo: separate icons no build found, etc.

    final String iconFileName = getIconFileName(buildLocator);
    final String resultIconFileName = getRealFileName(iconFileName);

    if (resultIconFileName == null || !new java.io.File(resultIconFileName).isFile()) {
      LOG.debug("Failed to find resource file: " + iconFileName);
      throw new NotFoundException("Error finding resource file '" + iconFileName + "' (installation corrupted?)");
    }

    final java.io.File resultIconFile = new java.io.File(resultIconFileName);
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
    return Response.ok(streamingOutput, mediaType).header("Cache-Control", "no-cache").build();
    //todo: consider using ETag for better caching/cache resets, might also use "Expires" header
  }

  @NotNull
  private String getIconFileName(@Nullable final String buildLocator) {
    final boolean holderHasPermission[] = new boolean[1];
    final boolean holderFinished[] = new boolean[1];
    final boolean holderSuccessful[] = new boolean[1];
    final boolean holderInternalError[] = new boolean[1];
    final boolean holderCanceled[] = new boolean[1];

    try {
      final SecurityContextEx securityContext = myServiceLocator.getSingletonService(SecurityContextEx.class);
      final AuthorityHolder currentUserAuthorityHolder = securityContext.getAuthorityHolder();
      try {
        securityContext.runAsSystem(new SecurityContextEx.RunAsAction() {
          public void run() throws Throwable {
            SBuild build = myBuildFinder.getBuild(null, buildLocator);
            holderHasPermission[0] = hasPermissionsToViewStatus(build, currentUserAuthorityHolder);
            holderFinished[0] = build.isFinished();
            holderSuccessful[0] = build.getStatusDescriptor().isSuccessful();
            holderInternalError[0] = build.isInternalError();
            holderCanceled[0] = build.getCanceledInfo() != null;
          }
        });
      } catch (NotFoundException e) {
        LOG.info("Cannot find build by build locator '" + buildLocator + "': " + e.getMessage());
        if (TeamCityProperties.getBoolean("rest.buildRequest.statusIcon.enableNotFoundResponsesWithoutPermissions") || hasPermissionsToViewStatusGlobally(securityContext)) {
          return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/not_found.png";
        }
        //should return the same error as when no permissions in order not to expose build existence
        return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/permission.png";
      } catch (Throwable throwable) {
        final String message = "Error while retrieving build under system by build locator '" + buildLocator + "': " + throwable.getMessage();
        LOG.info(message);
        LOG.debug(message, throwable);
        return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/internal_error.png"; //todo: use separate icon for errors (most importantly, wrong request)
      }

      if (!holderHasPermission[0]) {
        LOG.info("No permissions to access requested build with locator'" + buildLocator + "'" +
            ". Either authenticate as user with appropriate permissions, or ensure 'guest' user has appropriate permissions " +
            "or enable external status widget for the build configuration.");
        return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/permission.png";
      }

      if (!holderFinished[0]) {
        return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/running.png";  //todo: support running/failing and may be running/last failed
      }
      if (holderSuccessful[0]) {
        return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/successful.png";
      }
      if (holderInternalError[0]) {
        return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/error.png";
      }
      if (holderCanceled[0]) {
        return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/canceled.png";
      }
      return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/failed.png";
    } catch (AccessDeniedException e) {
      LOG.warn("Unexpected access denied error encountered while retrieving build by build locator '" + buildLocator + "': " + e.getMessage(), e);
      return IMG_STATUS_WIDGET_ROOT_DIRECTORY + "/permission.png";
    }
  }

  private boolean hasPermissionsToViewStatus(@NotNull final SBuild build, @NotNull final AuthorityHolder authorityHolder) {
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

    final SUser guestUser = myServiceLocator.getSingletonService(UserModel.class).getGuestUser();
    return myDataProvider.getServer().getLoginConfiguration().isGuestLoginAllowed() &&
           guestUser.isPermissionGrantedForProject(buildType.getProjectId(), Permission.VIEW_PROJECT);
  }

  private boolean hasPermissionsToViewStatusGlobally(@NotNull final SecurityContextEx securityContext) {
    final AuthorityHolder authorityHolder = securityContext.getAuthorityHolder();
    //todo: how to distinguish no user from system? Might check for system to support authToken requests...
    if (authorityHolder.getAssociatedUser() != null &&
        authorityHolder.isPermissionGrantedGlobally(Permission.VIEW_PROJECT)) {
      return true;
    }
    final SUser guestUser = myServiceLocator.getSingletonService(UserModel.class).getGuestUser();
    return myDataProvider.getServer().getLoginConfiguration().isGuestLoginAllowed() &&
           guestUser.isPermissionGrantedGlobally(Permission.VIEW_PROJECT);
  }

  private String getRealFileName(final String relativePath) {
    return myServiceLocator.getSingletonService(ServletContext.class).getRealPath(relativePath);
  }

  public Map<String, String> getBuildStatisticsValues(final SBuild build) {
    final Map<String,BigDecimal> values = build.getStatisticValues();

    final Map<String, String> result = new HashMap<String, String>(values.size());
    for (Map.Entry<String,BigDecimal> entry : values.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      result.put(entry.getKey(), entry.getValue().toPlainString());
    }

    return result;
  }
}
