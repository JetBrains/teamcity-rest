/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.buildType.VcsRoots;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootScope;
import org.jetbrains.annotations.NotNull;

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
  public static final String API_VCS_ROOTS_URL = Constants.API_URL + "/vcs-roots";

  public static String getVcsRootHref(final jetbrains.buildServer.vcs.VcsRoot root) {
    return API_VCS_ROOTS_URL + "/id:" + root.getId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public VcsRoots serveRoots() {
    return new VcsRoots(myDataProvider.getAllVcsRoots(), myApiUrlBuilder);
  }

  @POST
  @Produces({"application/xml", "application/json"})
  public VcsRoot serveRoot(VcsRoot vcsRootDescription) {
    checkVcsRootDescription(vcsRootDescription);
    final SVcsRoot newVcsRoot = myDataProvider.getVcsManager()
      .createNewVcsRoot(vcsRootDescription.vcsName, vcsRootDescription.name != null ? vcsRootDescription.name : null,
                        BuildTypeUtil.getMapFromProperties(vcsRootDescription.properties),
                        createScope(vcsRootDescription));
    myDataProvider.getVcsManager().persistVcsRoots();
    return new VcsRoot(newVcsRoot, myDataProvider, myApiUrlBuilder);
  }

  private void checkVcsRootDescription(final VcsRoot description) {
    //might need to check for validity: not specified id, status, lastChecked attributes, etc.
    if (StringUtil.isEmpty(description.vcsName)) {
      throw new BadRequestException("Attribute 'vcsName' must be specified when creating VCS root. Should be a valid VCS support name.");
    }
    if (description.properties == null) {
      throw new BadRequestException("Element 'properties' must be specified when creating VCS root.");
    }
  }

  @NotNull
  private VcsRootScope createScope(final VcsRoot vcsRootDescription) {
    if (vcsRootDescription.shared != null && vcsRootDescription.shared){
      if (vcsRootDescription.project != null){
        throw new BadRequestException("Project should not be specified if the VCS root is shared.");
      }
      return VcsRootScope.globalScope();
    }else{
      return VcsRootScope.projectScope(myDataProvider.getProject(getProjectLocator(vcsRootDescription)).getProjectId());
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
    myDataProvider.getVcsManager().removeVcsRoot(vcsRoot.getId());
    myDataProvider.getVcsManager().persistVcsRoots();
  }
}