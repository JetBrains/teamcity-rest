/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.ChangeFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.change.CommiterData;
import jetbrains.buildServer.server.rest.data.change.ChangeUtil;
import jetbrains.buildServer.server.rest.data.change.SVcsModificationOrChangeDescriptor;
import jetbrains.buildServer.server.rest.data.problem.scope.ProblemOccurrencesTreeCollector;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeTreeCollector;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Entries;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Items;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.change.*;
import jetbrains.buildServer.server.rest.model.issue.Issues;
import jetbrains.buildServer.server.rest.model.problem.scope.ProblemOccurrencesTree;
import jetbrains.buildServer.server.rest.model.problem.scope.TestScopeTree;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.SplitBuildsFeatureUtil;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.ChangeStatus;
import jetbrains.buildServer.vcs.ChangeStatusProvider;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

@Path(ChangeRequest.API_CHANGES_URL)
@Api("Change")
public class ChangeRequest {
  public static final String API_CHANGES_URL = Constants.API_URL + "/changes";

  private static final String DEFAULT_CHANGES_LOOKUP_LIMIT_FOR_COMMITERS = "1000";
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private ChangeFinder myChangeFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private TestScopeTreeCollector myTestScopeTreeCollector;
  @Context @NotNull private ProblemOccurrencesTreeCollector myProblemOccurrencesTreeCollector;

  public static String getChangeHref(SVcsModification modification) {
    return API_CHANGES_URL + "/" + ChangeFinder.getLocator(modification);
  }

  public static String getChangesHref(@NotNull final String locatorText) {
    return API_CHANGES_URL + "?locator=" + locatorText;
  }

  //todo: use locator here, like for builds with limitLookup, changes from dependencies flag, etc.
  //todo: mark changes from dependencies

  /**
   * Lists changes by the specified locator
   * @param locator             Change locator
   * @param projectLocator      Deprecated, use "locator" parameter instead
   * @param buildTypeLocator    Deprecated, use "locator" parameter instead
   * @param buildLocator        Deprecated, use "locator" parameter instead
   * @param vcsRootInstanceLocator      Deprecated, use "locator" parameter instead. Note that corresponding locator dimension is "vcsRootInstance"
   * @param sinceChangeLocator  Deprecated, use "locator" parameter instead
   * @param start               Deprecated, use "locator" parameter instead
   * @param count               Deprecated, use "locator" parameter instead
   * @param uriInfo             Deprecated, use "locator" parameter instead
   * @param request             Deprecated, use "locator" parameter instead
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all changes.",nickname="getAllChanges")
  public Changes serveChanges(@ApiParam(hidden = true) @QueryParam("project") String projectLocator,
                              @ApiParam(hidden = true) @QueryParam("buildType") String buildTypeLocator,
                              @ApiParam(hidden = true) @QueryParam("build") String buildLocator,
                              @ApiParam(hidden = true) @QueryParam("vcsRoot") String vcsRootInstanceLocator,
                              @ApiParam(hidden = true) @QueryParam("sinceChange") String sinceChangeLocator,
                              @ApiParam(hidden = true) @QueryParam("start") Long start,
                              @ApiParam(hidden = true) @QueryParam("count") Integer count,
                              @ApiParam(format = LocatorName.CHANGE) @QueryParam("locator") String locator,
                              @QueryParam("fields") String fields,
                              @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    Locator actualLocator = locator == null ? Locator.createEmptyLocator() : new Locator(locator);
    if (!actualLocator.isSingleValue()) {
      updateLocatorDimension(actualLocator, "project", projectLocator);
      updateLocatorDimension(actualLocator, "buildType", buildTypeLocator);
      updateLocatorDimension(actualLocator, "build", buildLocator);
      updateLocatorDimension(actualLocator, "vcsRootInstance", vcsRootInstanceLocator);
      updateLocatorDimension(actualLocator, "sinceChange", sinceChangeLocator);
      updateLocatorDimension(actualLocator, "start", start != null ? String.valueOf(start) : null);
      updateLocatorDimension(actualLocator, "count", count != null ? String.valueOf(count) : null);
    }

    final String locatorText = actualLocator.isEmpty() ? null : actualLocator.getStringRepresentation();
    PagedSearchResult<SVcsModificationOrChangeDescriptor> buildModifications = myChangeFinder.getItems(locatorText);

    final UriBuilder requestUriBuilder = uriInfo.getRequestUriBuilder();
    requestUriBuilder.replaceQueryParam("count" , null);
    requestUriBuilder.replaceQueryParam("start", null);
    return new Changes(buildModifications.myEntries,
                       new PagerDataImpl(requestUriBuilder, request.getContextPath(), buildModifications, locatorText, "locator"),
                       new Fields(fields),
                       myBeanContext);
  }

  private void updateLocatorDimension(@NotNull final Locator locator, @NotNull final String dimensionName, @Nullable final String value) {
    if (!StringUtil.isEmpty(value)){
      final String dimensionValue = locator.getSingleDimensionValue(dimensionName);
      if (dimensionValue != null && !dimensionValue.equals(value)){
        throw new BadRequestException("Both parameter '" + dimensionName +"' and same-named dimension in 'locator' parameter are specified. Use locator only.");
      }
      assert value != null;
      locator.setDimension(dimensionName, value);
    }
  }

  @GET
  @Path("/{changeLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get change matching the locator.",nickname="getChange")
  public Change serveChange(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                            @QueryParam("fields") String fields) {
    return new Change(myChangeFinder.getItem(changeLocator),  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{changeLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="Get a field of the matching change.",nickname="getChangeField")
  public String getChangeField(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                               @PathParam("field") String field) {
    final SVcsModificationOrChangeDescriptor change = myChangeFinder.getItem(changeLocator);
    return Change.getFieldValue(change, field);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/parentChanges")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get parent changes of the matching change.",nickname="getChangeParentChanges")
  public Changes getParentChanges(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                  @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();
    return Changes.fromSVcsModifications(change.getParentModifications(), null,  new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/parentRevisions")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get parent revisions of the matching change.",nickname="getChangeParentRevisions")
  public Items getChangeParentRevisions(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();
    return new Items(change.getParentRevisions());
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/vcsRootInstance")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a VCS root instance of the matching change.",nickname="getChangeVcsRoot")
  public VcsRootInstance getChangeVCSRootInstance(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                                  @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();
    return new VcsRootInstance(change.getVcsRoot(), new Fields(fields), myBeanContext);
  }

  /**
   * @deprecated see getChangeVCSRootInstance
   */
  @GET
  @ApiOperation(value = "getChangeVCSRoot", hidden = true)
  @Path("/{changeLocator}/vcsRoot")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstance getChangeVCSRoot(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                          @QueryParam("fields") String fields) {
    return getChangeVCSRootInstance(changeLocator, fields);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/attributes")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get attributes of the matching change.",nickname="getChangeAttributes")
  public Entries getChangeAttributes(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                     @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();
    return new Entries(change.getAttributes(), new Fields(fields));
  }
  
  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/duplicates")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get duplicates of the matching change.",nickname="getChangeDuplicates")
  public Changes getChangeDuplicates(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                     @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();
    return Changes.fromSVcsModifications(change.getDuplicates(), null,  new Fields(fields), myBeanContext);
  }

  //todo: add support for fields, add "issues" element to change bean
  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/issues")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get issues of the matching change.",nickname="getChangeIssue")
  public Issues getChangeIssue(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();
    return new Issues(change.getRelatedIssues());
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get build configurations related to the matching change.", nickname="getRelatedBuildTypes", hidden = true)
  public BuildTypes getRelatedBuildTypes(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                         @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();
    ChangeStatusProvider myStatusProvider = myServiceLocator.getSingletonService(ChangeStatusProvider.class);
    ChangeStatus changeStatus = myStatusProvider.getMergedChangeStatus(change);
    List<BuildTypeOrTemplate> buildTypes = BuildTypes.fromBuildTypes(
      changeStatus.getRelatedConfigurations().stream()
                  .filter(bt -> !SplitBuildsFeatureUtil.isVirtualConfiguration(bt))
                  .collect(Collectors.toList())
    );

    return new BuildTypes(buildTypes, null, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/firstBuilds")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get first builds of the matching change.", nickname="getChangeFirstBuilds", hidden = true)
  public Builds getChangeFirstBuilds(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                     @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();

    ChangeStatusProvider myStatusProvider = myServiceLocator.getSingletonService(ChangeStatusProvider.class);
    ChangeStatus changeStatus = myStatusProvider.getMergedChangeStatus(change);
    List<BuildPromotion> firstBuildsPromotions = changeStatus.getBuildTypesStatusMap().entrySet().stream()
                                                             .filter(entry -> !SplitBuildsFeatureUtil.isVirtualConfiguration(entry.getKey()))
                                                             .map(entry -> entry.getValue())
                                                             .filter(Objects::nonNull)
                                                             .collect(Collectors.toList());

    return Builds.createFromBuildPromotions(firstBuildsPromotions, null,  new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/deploymentConfigurations")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get build configurations where this change could potentially be deployed.", nickname="getDeploymentConfigurations", hidden = true)
  public BuildTypes getDeploymentConfigurations(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                     @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();

    ChangeStatusProvider myStatusProvider = myServiceLocator.getSingletonService(ChangeStatusProvider.class);
    ChangeStatus changeStatus = myStatusProvider.getMergedChangeStatus(change);

    return new BuildTypes(BuildTypes.fromBuildTypes(changeStatus.getDeploymentStatus().keySet()), null, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/deployments")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get deployments with this change.", nickname="getDeployments", hidden = true)
  public Builds getDeployments(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator, @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();

    ChangeStatusProvider myStatusProvider = myServiceLocator.getSingletonService(ChangeStatusProvider.class);
    ChangeStatus changeStatus = myStatusProvider.getMergedChangeStatus(change);

    List<BuildPromotion> promotions = changeStatus.getDeploymentStatus().values().stream().filter(Objects::nonNull).collect(Collectors.toList());
    return Builds.createFromBuildPromotions(promotions, null, new Fields(fields), myBeanContext);
  }

  /**
   *  Experimental, subject to change
   *  @since 2021.2
   */
  @GET
  @Path("/{changeLocator}/testsTree")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get failed tests tree for the matching change.", nickname="getChangeFailedTestsTree", hidden = true)
  public TestScopeTree getChangeFailedTestsTree(
    @ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
    @QueryParam(TestScopeTreeCollector.SUBTREE_ROOT_ID) String subTreeRootId, // todo: remove after ui migration
    @QueryParam("treeLocator") String treeLocatorText,
    @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();

    ChangeStatusProvider myStatusProvider = myServiceLocator.getSingletonService(ChangeStatusProvider.class);
    ChangeStatus changeStatus = myStatusProvider.getMergedChangeStatus(change);

    Stream<BuildPromotion> firstBuildsPromotions = changeStatus.getBuildTypesStatusMap().values().stream().filter(Objects::nonNull);
    Locator treeLocator = Locator.createPotentiallyEmptyLocator(treeLocatorText);
    if(subTreeRootId != null) {
      treeLocator.setDimension(ProblemOccurrencesTreeCollector.SUB_TREE_ROOT_ID, subTreeRootId);
    }
    return new TestScopeTree(myTestScopeTreeCollector.getSlicedTreeFromBuildPromotions(firstBuildsPromotions, treeLocator), new Fields(fields), myBeanContext);
  }

  /**
   *  Experimental, subject to change
   *  @since 2021.2
   */
  @GET
  @Path("/{changeLocator}/problemsTree")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get problems tree for the matching change.", nickname="getChangeProblemsTree", hidden = true)
  public ProblemOccurrencesTree getChangeProblemsTree(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                      @QueryParam(ProblemOccurrencesTreeCollector.SUB_TREE_ROOT_ID) String subTreeRootId, // todo: remove after ui migration
                                      @QueryParam("treeLocator") String treeLocatorText,
                                      @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator).getSVcsModification();

    ChangeStatusProvider myStatusProvider = myServiceLocator.getSingletonService(ChangeStatusProvider.class);
    ChangeStatus changeStatus = myStatusProvider.getMergedChangeStatus(change);

    Stream<BuildPromotion> firstBuildsPromotions = changeStatus.getBuildTypesStatusMap().values().stream().filter(Objects::nonNull);

    Locator treeLocator = Locator.createPotentiallyEmptyLocator(treeLocatorText);
    if(subTreeRootId != null) {
      treeLocator.setDimension(ProblemOccurrencesTreeCollector.SUB_TREE_ROOT_ID, subTreeRootId);
    }

    return new ProblemOccurrencesTree(myProblemOccurrencesTreeCollector.getTreeFromBuildPromotions(firstBuildsPromotions, treeLocator), new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only!
   * @since 2021.1.1
   */
  @GET
  @Path("/{changeLocator}/commiters")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get unique commiters of the matching changes.", nickname="getUniqueCommiters", hidden = true)
  public Commiters getUniqueCommiters(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                      @QueryParam("fields") String fields) {
    Locator patchedChangeLocator = Locator.createPotentiallyEmptyLocator(changeLocator);
    if(!patchedChangeLocator.isAnyPresent(PagerData.COUNT)) {
      String lookupLimit = TeamCityProperties.getProperty("rest.request.changes.committersLookupLimit", DEFAULT_CHANGES_LOOKUP_LIMIT_FOR_COMMITERS);
      patchedChangeLocator.setDimension(PagerData.COUNT, lookupLimit);
    }

    PagedSearchResult<SVcsModificationOrChangeDescriptor> changes = myChangeFinder.getItems(patchedChangeLocator.getStringRepresentation());

    List<CommiterData> commiters = ChangeUtil.getUniqueCommiters(changes.myEntries.stream().map(modOrDesc -> modOrDesc.getSVcsModification()));
    return new Commiters(commiters, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only!
   * todo: Is it better to have this somewhere in Change model? E.g. fields=change(files($filterByBuildType(<buildTypeId>),name,...)))
   * @since 2021.1.1
   */
  @GET
  @Path("/{changeLocator}/files")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get files of the matching change filtered by relation to a given buildType.", nickname="getFilteredFiles", hidden = true)
  public FileChanges getFilteredFiles(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocatorString,
                                      @QueryParam("buildTypeId") String builtTypeId,
                                      @QueryParam("fields") String fields) {
    Locator changeLocator = Locator.createPotentiallyEmptyLocator(changeLocatorString);
    SVcsModification change = myChangeFinder.getItem(changeLocator.getStringRepresentation()).getSVcsModification();

    if(builtTypeId == null) {
      // Convenience method, same as Change.getFileChanges()
      ChangeStatusProvider myStatusProvider = myServiceLocator.getSingletonService(ChangeStatusProvider.class);
      ChangeStatus changeStatus = myStatusProvider.getMergedChangeStatus(change);
      return new FileChanges(new ArrayList<>(changeStatus.getMergedVcsModificationInfo().getChangedFiles()), new Fields(fields));
    }

    SBuildType buildType = myBuildTypeFinder.getItem(builtTypeId).getBuildType();
    if(buildType == null) {
      throw new NotFoundException("Build type not found.");
    }

    if(change.getRelatedConfigurations().stream().noneMatch(relatedBt -> relatedBt.getExternalId().equals(buildType.getExternalId()))) {
      ChangeStatusProvider myStatusProvider = myServiceLocator.getSingletonService(ChangeStatusProvider.class);
      ChangeStatus changeStatus = myStatusProvider.getMergedChangeStatus(change);
      return new FileChanges(new ArrayList<>(changeStatus.getMergedVcsModificationInfo().getChangedFiles()), new Fields(fields));
    }

    return new FileChanges(
      change.getFilteredChanges(buildType).stream().filter(filteredVcsChange -> !filteredVcsChange.isExcluded()).collect(Collectors.toList()),
      new Fields(fields)
    );
  }

  public void initForTests(@NotNull ServiceLocator serviceLocator,
                           @NotNull BeanContext beanContext,
                           @NotNull ChangeFinder changeFinder,
                           @NotNull BuildTypeFinder buildTypeFinder,
                           @Nullable TestScopeTreeCollector testScopeTreeCollector,
                           @Nullable ProblemOccurrencesTreeCollector problemOccurrencesTreeCollector) {
    myServiceLocator = serviceLocator;
    myBeanContext = beanContext;
    myChangeFinder = changeFinder;
    myBuildTypeFinder = buildTypeFinder;
    if(testScopeTreeCollector != null) myTestScopeTreeCollector = testScopeTreeCollector;
    if(problemOccurrencesTreeCollector != null) myProblemOccurrencesTreeCollector = problemOccurrencesTreeCollector;
  }
}