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

import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.ChangeFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Items;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.Changes;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstanceRef;
import jetbrains.buildServer.server.rest.model.issue.Issues;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path(ChangeRequest.API_CHANGES_URL)
public class ChangeRequest {
  public static final String API_CHANGES_URL = Constants.API_URL + "/changes";
  public static final String MY_TMP_DIMENSION = "myTmpDimension";
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanFactory myFactory;
  @Context @NotNull private ChangeFinder myChangeFinder;

  public static String getChangeHref(VcsModification modification) {
    return API_CHANGES_URL + "/id:" + modification.getId() + (modification.isPersonal() ? ",personal:true" : "");
  }

  public static String getBuildChangesHref(SBuild build) {
    return API_CHANGES_URL + "?build=id:" + build.getBuildId();
  }

  //todo: use locator here, like for builds with limitLookup, changes from dependencies falg, etc.
  //todo: mark changes from dependencies

  /**
   * Lists changes by the specified locator
   * @param locator             Change locator
   * @param projectLocator      Deprecated, use "locator" parameter instead
   * @param buildTypeLocator    Deprecated, use "locator" parameter instead
   * @param buildLocator        Deprecated, use "locator" parameter instead
   * @param vcsRootLocator      Deprecated, use "locator" parameter instead
   * @param sinceChangeLocator  Deprecated, use "locator" parameter instead
   * @param start               Deprecated, use "locator" parameter instead
   * @param count               Deprecated, use "locator" parameter instead
   * @param uriInfo             Deprecated, use "locator" parameter instead
   * @param request             Deprecated, use "locator" parameter instead
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public Changes serveChanges(@QueryParam("project") String projectLocator,
                              @QueryParam("buildType") String buildTypeLocator,
                              @QueryParam("build") String buildLocator,
                              @QueryParam("vcsRoot") String vcsRootLocator,
                              @QueryParam("sinceChange") String sinceChangeLocator,
                              @QueryParam("start") Long start,
                              @QueryParam("count") Integer count,
                              @QueryParam("locator") String locator,
                              @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    Locator actualLocator;
    if (locator != null){
     actualLocator = new Locator(locator, ChangeFinder.getChangesLocatorSupportedDimensions());
    }else{
      actualLocator = Locator.createEmptyLocator(ChangeFinder.getChangesLocatorSupportedDimensions());
    }
    updateLocatorDimension(actualLocator, "project", projectLocator);
    updateLocatorDimension(actualLocator, "buildType", buildTypeLocator);
    updateLocatorDimension(actualLocator, "build", buildLocator);
    updateLocatorDimension(actualLocator, "vcsRoot", vcsRootLocator);
    updateLocatorDimension(actualLocator, "sinceChange", sinceChangeLocator);
    updateLocatorDimension(actualLocator, "start", start != null ? String.valueOf(start) : null);
    updateLocatorDimension(actualLocator, "count", count != null ? String.valueOf(count) : null);
    updateLocatorDimensionIfNotPresent(actualLocator, "start", String.valueOf(0));
    updateLocatorDimensionIfNotPresent(actualLocator, "count", String.valueOf(Constants.DEFAULT_PAGE_ITEMS_COUNT));

    if (actualLocator.isEmpty()){
      throw new BadRequestException("No 'locator' or other parameters are specified.");
    }

    PagedSearchResult <SVcsModification> buildModifications = myChangeFinder.getModifications(actualLocator);
    actualLocator.checkLocatorFullyProcessed();

    final UriBuilder requestUriBuilder = uriInfo.getRequestUriBuilder();
    requestUriBuilder.replaceQueryParam("count" , null);
    requestUriBuilder.replaceQueryParam("start" , null);
    return new Changes(buildModifications.myEntries,
                       new PagerData(requestUriBuilder, request.getContextPath(), buildModifications.myStart,
                                     buildModifications.myCount, buildModifications.myEntries.size(), actualLocator.getStringRepresentation(), "locator"),
                       myApiUrlBuilder, myFactory);
  }

  private void updateLocatorDimension(@NotNull final Locator locator, @NotNull final String dimensionName, @Nullable final String value) {
    if (!StringUtil.isEmpty(value)){
      if (locator.getSingleDimensionValue(dimensionName) != null){
        throw new BadRequestException("Both parameter '" + dimensionName +"' and same-named dimension in 'locator' parameter are specified. Use locator only.");
      }
      assert value != null;
      locator.setDimension(dimensionName, value);
    }
  }

  private void updateLocatorDimensionIfNotPresent(@NotNull final Locator locator, @NotNull final String dimensionName, @NotNull final String value) {
    if (locator.getSingleDimensionValue(dimensionName) == null) {
      locator.setDimension(dimensionName, value);
    }
  }

  @GET
  @Path("/{changeLocator}")
  @Produces({"application/xml", "application/json"})
  public Change serveChange(@PathParam("changeLocator") String changeLocator) {
    return new Change(myChangeFinder.getChange(changeLocator), myApiUrlBuilder, myFactory);
  }

  @GET
  @Path("/{changeLocator}/{field}")
  @Produces("text/plain")
  public String getChangeField(@PathParam("changeLocator") String changeLocator, @PathParam("field") String field) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return Change.getFieldValue(change, field);
  }

  @GET
  @Path("/{changeLocator}/parent-changes")
  @Produces({"application/xml", "application/json"})
  public Changes getParentChanges(@PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return new Changes(new ArrayList<SVcsModification>(change.getParentModifications()), myApiUrlBuilder, myFactory);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/parent-revisions")
  @Produces({"application/xml", "application/json"})
  public Items getChangeParentRevisions(@PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return new Items(change.getParentRevisions());
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/vcs-root")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstanceRef getChangeVCSRoot(@PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return new VcsRootInstanceRef(change.getVcsRoot(), myApiUrlBuilder);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/attributes")
  @Produces({"application/xml", "application/json"})
  public Properties getChangeAttributes(@PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return new Properties(change.getAttributes());
  }
  
  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/duplicates")
  @Produces({"application/xml", "application/json"})
  public Changes getChangeDuplicates(@PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return new Changes(new ArrayList<SVcsModification>(change.getDuplicates()), myApiUrlBuilder, myFactory);
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/issues")
  @Produces({"application/xml", "application/json"})
  public Issues getChangeIssue(@PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return new Issues(change.getRelatedIssues());
  }

  /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  public BuildTypes getRelatedBuildTypes(@PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return BuildTypes.createFromBuildTypes(new ArrayList<SBuildType>(change.getRelatedConfigurations()), myDataProvider, myApiUrlBuilder);
  }

 /**
   * Experimental support only!
   */
  @GET
  @Path("/{changeLocator}/first-builds")
  @Produces({"application/xml", "application/json"})
  public Builds getChangeFirstBuilds(@PathParam("changeLocator") String changeLocator) {
    final SVcsModification change = myChangeFinder.getChange(changeLocator);
    return new Builds(new ArrayList<SBuild>(change.getFirstBuilds().values()), myDataProvider, null, myApiUrlBuilder);
  }
}