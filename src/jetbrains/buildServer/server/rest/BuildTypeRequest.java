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

package jetbrains.buildServer.server.rest;

import com.sun.jersey.spi.resource.Singleton;
import com.intellij.openapi.diagnostic.Logger;

import javax.ws.rs.*;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.server.rest.data.*;

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
    return new BuildType(buildType);
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
  public Builds serveBuilds(@PathParam("btLocator") String buildTypeLocator, @QueryParam("start") Long start, @QueryParam("finish") Long finish) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new Builds(myDataProvider.getBuilds(buildType, null, false, true, false, start, finish));
  }

  @GET
  @Path("/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);
    return new Build(build);
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
