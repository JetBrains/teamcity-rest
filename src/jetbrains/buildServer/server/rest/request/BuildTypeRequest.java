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
import jetbrains.buildServer.server.rest.BuildsFilterSettings;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.PagerData;
import jetbrains.buildServer.server.rest.data.build.Build;
import jetbrains.buildServer.server.rest.data.build.Builds;
import jetbrains.buildServer.server.rest.data.buildType.BuildType;
import jetbrains.buildServer.server.rest.data.buildType.BuildTypes;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;

/**
 * User: Yegor Yarko
 * Date: 22.03.2009
 */

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produses 404
*/

@Path("/httpAuth/api/buildTypes")
@Singleton
public class BuildTypeRequest {
  private final DataProvider myDataProvider;

  public BuildTypeRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  public static String getBuildTypeHref(SBuildType buildType) {
    return "/httpAuth/api/buildTypes/id:" + buildType.getBuildTypeId();
  }


  public static String getBuildsHref(final SBuildType buildType) {
    return getBuildTypeHref(buildType) + "/builds/";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesXML() {
    return new BuildTypes(myDataProvider.getServer().getProjectManager().getAllBuildTypes());
  }

  @GET
  @Path("/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeXML(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new BuildType(buildType, myDataProvider);
  }

  @GET
  @Path("/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return myDataProvider.getFieldValue(buildType, fieldName);
  }

  @GET
  @Path("/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  //todo: add qury params limiting range
  public Builds serveBuilds(@PathParam("btLocator") String buildTypeLocator,
                            @QueryParam("status") String status,
                            @QueryParam("triggeredByUser") String userLocator,
                            @QueryParam("includePersonal") boolean includePersonal,
                            @QueryParam("includeCanceled") boolean includeCanceled,
                            @QueryParam("onlyPinned") boolean onlyPinned,
                            @QueryParam("agentName") String agentName,
                            @QueryParam("sinceBuild") String sinceBuildLocator,
                            @QueryParam("sinceDate") String sinceDate,
                            @QueryParam("start") @DefaultValue(value = "0") Long start,
                            @QueryParam("count") @DefaultValue(value = Constants.DEFAULT_PAGE_ITEMS_COUNT) Integer count) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);

    final List<SFinishedBuild> buildsList = myDataProvider.getBuilds(
      new BuildsFilterSettings(buildType, status, myDataProvider.getUserIfNotNull(userLocator),
                               includePersonal, includeCanceled, onlyPinned, agentName,
                               myDataProvider.getRangeLimit(buildType, sinceBuildLocator, myDataProvider.parseDate(sinceDate)), start,
                               count));
    return new Builds(buildsList,
                      myDataProvider,
                      new PagerData(getUrl("/httpAuth/api/buildTypes/" + buildTypeLocator + "/builds"), start, count, buildsList.size()));
  }

  //todo: should contain all parameters of the original request
  private String getUrl(final String s) {
    return s;
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);
    return new Build(build, myDataProvider);
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
