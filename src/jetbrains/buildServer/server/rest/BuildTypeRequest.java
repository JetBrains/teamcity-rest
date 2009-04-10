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

@Path("/httpAuth/api")
@Singleton
public class BuildTypeRequest {
  private static final Logger LOG = Logger.getInstance(BuildTypeRequest.class.getName());
  private final DataProvider myDataProvider;

  public BuildTypeRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  @GET
  @Path("/server/{field}")
  @Produces({"text/plain"})
  public String serveServerVersion(@PathParam("field") String fieldName) {
    return myDataProvider.getServerFieldValue(fieldName);
  }

  @GET
  @Path("/buildTypes")
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesXML() {
    return new BuildTypes(myDataProvider.getServer().getProjectManager().getAllBuildTypes());
  }

  @GET
  @Path("/buildTypes/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeXML(@PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new BuildType(buildType);
  }

  @GET
  @Path("/buildTypes/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeField(@PathParam("btLocator") String buildTypeLocator, @PathParam("field") String fieldName) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return myDataProvider.getFieldValue(buildType, fieldName);
  }

  @GET
  @Path("/buildTypes/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  //todo: add qury params limiting range
  public Builds serveBuilds(@PathParam("btLocator") String buildTypeLocator, @QueryParam("start") Long start, @QueryParam("finish") Long finish) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    return new Builds(myDataProvider.getBuilds(buildType, null, false, true, false, start, finish));
  }

  @GET
  @Path("/buildTypes/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);
    return new Build(build);
  }


  @GET
  @Path("/buildTypes/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildField(@PathParam("btLocator") String buildTypeLocator,
                                @PathParam("buildLocator") String buildLocator,
                                @PathParam("field") String field) {
    SBuildType buildType = myDataProvider.getBuildType(null, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }




  @GET
  @Path("/projects")
  @Produces({"application/xml", "application/json"})
  public Projects serveProjects() {
    return new Projects(myDataProvider.getServer().getProjectManager().getProjects());
  }

  @GET
  @Path("/projects/{projectLocator}")
  @Produces({"application/xml", "application/json"})
  public Project serveProject(@PathParam("projectLocator") String projectLocator) {
    return new Project(myDataProvider.getProject(projectLocator));
  }

  @GET
  @Path("/projects/{projectLocator}/{field}")
  @Produces("text/plain")
  public String serveProjectFiled(@PathParam("projectLocator") String projectLocator,
                                  @PathParam("field") String fieldName) {
    return myDataProvider.getFieldValue(myDataProvider.getProject(projectLocator), fieldName);
  }

  @GET
  @Path("/projects/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesInProject(@PathParam("projectLocator") String projectLocator) {
    SProject project = myDataProvider.getProject(projectLocator);
    return new BuildTypes(project.getBuildTypes());
  }

  @GET
  @Path("/projects/{projectLocator}/buildTypes/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildType(@PathParam("projectLocator") String projectLocator,
                                  @PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);

    return new BuildType(buildType);
  }

  @GET
  @Path("/projects/{projectLocator}/buildTypes/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                               @PathParam("btLocator") String buildTypeLocator,
                                               @PathParam("field") String fieldName) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);

    return myDataProvider.getFieldValue(buildType, fieldName);
  }

  //todo: separate methods to serve running builds

  @GET
  @Path("/projects/{projectLocator}/buildTypes/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  //todo: add qury params limiting range
  public Builds serveBuilds(@PathParam("projectLocator") String projectLocator,
                            @PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);

    return new Builds(buildType.getHistory());
  }

  @GET
  @Path("/projects/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("projectLocator") String projectLocator,
                                     @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return new Build(build);
  }

  @GET
  @Path("/projects/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                           @PathParam("btLocator") String buildTypeLocator,
                                           @PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }


  @GET
  @Path("/{projectLocator}/{btLocator}/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldShort(@PathParam("projectLocator") String projectLocator,
                                     @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator,
                                     @PathParam("field") String field) {
    SProject project = myDataProvider.getProject(projectLocator);
    SBuildType buildType = myDataProvider.getBuildType(project, buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }

  @GET
  @Path("/builds/{buildLocator}")
  @Produces("text/plain")
  public Build serveBuild(@PathParam("buildLocator") String buildLocator) {
    return new Build (myDataProvider.getBuild(null, buildLocator));
  }

  @GET
  @Path("/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuild build = myDataProvider.getBuild(null, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }
}
