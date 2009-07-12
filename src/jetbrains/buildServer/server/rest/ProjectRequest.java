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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;

/**
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path("/httpAuth/api/projects")
@Singleton
public class ProjectRequest {
  private final DataProvider myDataProvider;

  public ProjectRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  public static String getProjectHref(SProject project) {
    return "/httpAuth/api/projects/id:" + project.getProjectId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Projects serveProjects() {
    return new Projects(myDataProvider.getServer().getProjectManager().getProjects());
  }

  @GET
  @Path("/{projectLocator}")
  @Produces({"application/xml", "application/json"})
  public Project serveProject(@PathParam("projectLocator") String projectLocator) {
    return new Project(myDataProvider.getProject(projectLocator));
  }

  @GET
  @Path("/{projectLocator}/{field}")
  @Produces("text/plain")
  public String serveProjectFiled(@PathParam("projectLocator") String projectLocator,
                                  @PathParam("field") String fieldName) {
    return myDataProvider.getFieldValue(myDataProvider.getProject(projectLocator), fieldName);
  }

  @GET
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesInProject(@PathParam("projectLocator") String projectLocator) {
    SProject project = myDataProvider.getProject(projectLocator);
    return new BuildTypes(project.getBuildTypes());
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildType(@PathParam("projectLocator") String projectLocator,
                                  @PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);

    return new BuildType(buildType);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                               @PathParam("btLocator") String buildTypeLocator,
                                               @PathParam("field") String fieldName) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);

    return myDataProvider.getFieldValue(buildType, fieldName);
  }

  //todo: separate methods to serve running builds

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  //todo: add qury params limiting range
  public Builds serveBuilds(@PathParam("projectLocator") String projectLocator,
                            @PathParam("btLocator") String buildTypeLocator) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);

    return new Builds(buildType.getHistory());
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("projectLocator") String projectLocator,
                                     @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return new Build(build);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                           @PathParam("btLocator") String buildTypeLocator,
                                           @PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuildType buildType = myDataProvider.getBuildType(myDataProvider.getProject(projectLocator), buildTypeLocator);
    SBuild build = myDataProvider.getBuild(buildType, buildLocator);

    return myDataProvider.getFieldValue(build, field);
  }
}
