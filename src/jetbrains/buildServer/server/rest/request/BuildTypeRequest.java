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
import jetbrains.buildServer.server.rest.data.BuildsFilter;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.util.StringUtil;

/**
 * User: Yegor Yarko
 * Date: 22.03.2009
 */

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produses 404
*/

@Path(BuildTypeRequest.API_BUILD_TYPES_URL)
public class BuildTypeRequest {
  @Context
  private DataProvider myDataProvider;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;

  public static final String API_BUILD_TYPES_URL = Constants.API_URL + "/buildTypes";

  public static String getBuildTypeHref(SBuildType buildType) {
    return API_BUILD_TYPES_URL + "/id:" + buildType.getBuildTypeId();
  }


  public static String getBuildsHref(final SBuildType buildType) {
    return getBuildTypeHref(buildType) + "/builds/";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesXML() {
    return new BuildTypes(myDataProvider.getServer().getProjectManager().getAllBuildTypes(), myDataProvider, myApiUrlBuilder);
  }

  @GET
  @Path("/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeXML(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new BuildType(buildType, myDataProvider, myApiUrlBuilder);
  }

  @GET
  @Path("/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return myDataProvider.getFieldValue(buildType, fieldName);
  }

  @GET
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public String serveBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator, @PathParam("name") String parameterName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    if (!buildType.getBuildParameters().containsKey(parameterName)) {
      throw new NotFoundException("No parameter with name '" + parameterName + "' is found.");
    }
    return buildType.getBuildParameter(parameterName);
  }

  @PUT
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public void putBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    if (StringUtil.isEmpty(newValue)) {
      throw new BadRequestException("Parameter value cannot be empty.");
    }
    buildType.addBuildParameter(new SimpleParameter(parameterName, newValue));
    buildType.getProject().persist();
  }

  @DELETE
  @Path("/{btLocator}/parameters/{name}")
  @Produces("text/plain")
  public void deleteBuildTypeParameter(@PathParam("btLocator") String buildTypeLocator,
                                       @PathParam("name") String parameterName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    buildType.removeBuildParameter(parameterName);
    buildType.getProject().persist();
  }

  @PUT
  @Path("/{btLocator}/runParameters/{name}")
  @Produces("text/plain")
  public void putBuildTypeRunParameter(@PathParam("btLocator") String buildTypeLocator,
                                       @PathParam("name") String parameterName,
                                       String newValue) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    if (StringUtil.isEmpty(newValue)) {
      throw new BadRequestException("Parameter value cannot be empty.");
    }
    buildType.addRunParameter(new SimpleParameter(parameterName, newValue));
    buildType.getProject().persist();
  }

  @GET
  @Path("/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  public Builds serveBuilds(@PathParam("btLocator") String buildTypeLocator,
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
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);

    final List<SFinishedBuild> buildsList = myDataProvider.getBuilds(
      new BuildsFilter(buildType, status, myDataProvider.getUserIfNotNull(userLocator),
                       includePersonal, includeCanceled, onlyPinned, tags, agentName,
                       myDataProvider.getRangeLimit(buildType, sinceBuildLocator, myDataProvider.parseDate(sinceDate)), start,
                       count));
    return new Builds(buildsList,
                      myDataProvider,
                      new PagerData(uriInfo.getRequestUriBuilder(), request, start, count, buildsList.size()),
                      myApiUrlBuilder);
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);
    return new Build(build, myDataProvider, myApiUrlBuilder);
  }


  @GET
  @Path("/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildField(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }
}
