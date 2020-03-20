/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.buildType.VcsRoots;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstance;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produses 404
*/

@Path(VcsRootRequest.API_VCS_ROOTS_URL)
public class VcsRootRequest {
  @Context
  private DataProvider myDataProvider;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;
  @Context
  private ServiceLocator myServiceLocator;

  public static final String API_VCS_ROOTS_URL = Constants.API_URL + "/vcs-roots";

  public static String getVcsRootHref(final jetbrains.buildServer.vcs.VcsRoot root) {
    return API_VCS_ROOTS_URL + "/id:" + root.getId();
  }

  public static String getVcsRootInstanceHref(final jetbrains.buildServer.vcs.VcsRootInstance vcsRootInstance) {
    return API_VCS_ROOTS_URL + "/id:" + vcsRootInstance.getParentId() + "/instances/id:" + vcsRootInstance.getId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public VcsRoots serveRoots() {
    return new VcsRoots(myDataProvider.getAllVcsRoots(), myApiUrlBuilder);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VcsRoot addRoot(VcsRoot vcsRootDescription) {
    checkVcsRootDescription(vcsRootDescription);

    SProject targetProject;
    if (vcsRootDescription.shared != null && vcsRootDescription.shared){
      if (vcsRootDescription.project != null){
        throw new BadRequestException("Project should not be specified if the VCS root is shared.");
      }
      targetProject = myDataProvider.getProjectById(SProject.ROOT_PROJECT_ID);
    } else{
      targetProject = myDataProvider.getProject(getProjectLocator(vcsRootDescription), true);
    }

    final SVcsRoot newVcsRoot = targetProject.createVcsRoot(vcsRootDescription.vcsName, vcsRootDescription.name, vcsRootDescription.properties.getMap());
    newVcsRoot.persist();

    return new VcsRoot(newVcsRoot, myDataProvider, myApiUrlBuilder);
  }

  private void checkVcsRootDescription(final VcsRoot description) {
    //might need to check for validity: not specified id, status, lastChecked attributes, etc.
    if (StringUtil.isEmpty(description.vcsName)) {
      //todo: include list of avaialble supports here
      throw new BadRequestException("Attribute 'vcsName' must be specified when creating VCS root. Should be a valid VCS support name.");
    }
    if (description.properties == null) {
      throw new BadRequestException("Element 'properties' must be specified when creating VCS root.");
    }
  }

  // see also BuildTypeUtil.getVcsRoot
  private String getProjectLocator(final VcsRoot description) {
    if (!StringUtil.isEmpty(description.projectLocator)){
      if (description.project != null){
        throw new BadRequestException("Only one from projectLocator attribute and project element should be specified.");
      }
      return description.projectLocator;
    }else{
      if (description.project == null){
        throw new BadRequestException("Either projectLocator attribute or project element should be specified.");
      }
      final String projectHref = description.project.href;
      if (StringUtil.isEmpty(projectHref)){
        throw new BadRequestException("project element should have valid href attribute.");
      }
      return BuildTypeUtil.getLastPathPart(projectHref);
    }  }


  @GET
  @Path("/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  public VcsRoot serveRoot(@PathParam("vcsRootLocator") String vcsRootLocator) {
    return new VcsRoot(myDataProvider.getVcsRoot(vcsRootLocator), myDataProvider, myApiUrlBuilder);
  }

  @DELETE
  @Path("/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  public void deleteRoot(@PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    vcsRoot.remove();
  }

  @GET
  @Path("/{vcsRootLocator}/instances/{vcsRootInstanceLocator}")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstance serveRootInstance(@PathParam("vcsRootLocator") String vcsRootLocator,
                                           @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator) {
    return new VcsRootInstance(myDataProvider.getVcsRootInstance(vcsRootInstanceLocator), myDataProvider, myApiUrlBuilder);
  }


  @GET
  @Path("/{vcsRootLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveProperties(@PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    return new Properties(vcsRoot.getProperties());
  }

  @GET
  @Path("/{vcsRootLocator}/properties/{name}")
  @Produces("text/plain")
  public String serveProperty(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("name") String parameterName) {
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    return BuildTypeUtil.getParameter(parameterName, VcsRoot.getUserParametersHolder(vcsRoot));
  }

  @PUT
  @Path("/{vcsRootLocator}/properties/{name}")
  @Consumes("text/plain")
  public void putParameter(@PathParam("vcsRootLocator") String vcsRootLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    BuildTypeUtil.changeParameter(parameterName, newValue, VcsRoot.getUserParametersHolder(vcsRoot), myServiceLocator);
    vcsRoot.persist();
  }

  @DELETE
  @Path("/{vcsRootLocator}/properties/{name}")
  @Produces("text/plain")
  public void deleteParameter(@PathParam("vcsRootLocator") String vcsRootLocator,
                                       @PathParam("name") String parameterName) {
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    BuildTypeUtil.deleteParameter(parameterName, VcsRoot.getUserParametersHolder(vcsRoot));
    vcsRoot.persist();
  }

  @GET
  @Path("/{vcsRootLocator}/{field}")
  @Produces("text/plain")
  public String serveField(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("field") String fieldName) {
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    return VcsRoot.getFieldValue(vcsRoot, fieldName);
  }

  @PUT
  @Path("/{vcsRootLocator}/{field}")
  @Consumes("text/plain")
  public void seteField(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("field") String fieldName, String newValue) {
    final SVcsRoot vcsRoot = myDataProvider.getVcsRoot(vcsRootLocator);
    VcsRoot.setFieldValue(vcsRoot, fieldName, newValue, myDataProvider);
    vcsRoot.persist();
  }

}