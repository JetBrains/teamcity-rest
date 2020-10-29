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

import com.intellij.openapi.util.text.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildFinder;
import jetbrains.buildServer.server.rest.data.ChangeFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Entries;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Items;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.Changes;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstance;
import jetbrains.buildServer.server.rest.model.issue.Issues;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;

@Path(ChangeRequest.API_CHANGES_URL)
@Api("Change")
public class ChangeRequest {
  public static final String API_CHANGES_URL = Constants.API_URL + "/changes";
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanFactory myFactory;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private ChangeFinder myChangeFinder;

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
    PagedSearchResult<SVcsModification> buildModifications = myChangeFinder.getItems(locatorText);

    final UriBuilder requestUriBuilder = uriInfo.getRequestUriBuilder();
    requestUriBuilder.replaceQueryParam("count" , null);
    requestUriBuilder.replaceQueryParam("start", null);
    return new Changes(buildModifications.myEntries,
                       new PagerData(requestUriBuilder, request.getContextPath(), buildModifications, locatorText, "locator"),
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
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
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
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
    return new Changes(new ArrayList<SVcsModification>(change.getParentModifications()), null,  new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/parentRevisions")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get parent revisions of the matching change.",nickname="getChangeParentRevisions")
  public Items getChangeParentRevisions(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
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
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
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
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
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
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
    return new Changes(new ArrayList<SVcsModification>(change.getDuplicates()), null,  new Fields(fields), myBeanContext);
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
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
    return new Issues(change.getRelatedIssues());
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get build configurations related to the matching change.",nickname="getChangeRelatedBuildTypes")
  public BuildTypes getRelatedBuildTypes(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                         @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
    return new BuildTypes(BuildTypes.fromBuildTypes(change.getRelatedConfigurations()), null, new Fields(fields), myBeanContext);
  }

 /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/firstBuilds")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get first builds of the matching change.",nickname="getChangeFirstBuilds")
  public Builds getChangeFirstBuilds(@ApiParam(format = LocatorName.CHANGE) @PathParam("changeLocator") String changeLocator,
                                     @QueryParam("fields") String fields) {
    final SVcsModification change = myChangeFinder.getItem(changeLocator);
    return Builds.createFromBuildPromotions(BuildFinder.toBuildPromotions(change.getFirstBuilds().values()), null,  new Fields(fields), myBeanContext);
  }
}