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

import java.util.HashMap;
import java.util.HashSet;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.model.buildType.VcsRoots;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstance;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(VcsRootRequest.API_VCS_ROOTS_URL)
public class VcsRootRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private VcsRootFinder myVcsRootFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;

  public static final String API_VCS_ROOTS_URL = Constants.API_URL + "/vcs-roots";

  public static String getVcsRootHref(final jetbrains.buildServer.vcs.VcsRoot root) {
    return API_VCS_ROOTS_URL + "/id:" + root.getId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public VcsRoots serveRoots(@QueryParam("locator") String locatorText, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<SVcsRoot> vcsRoots = myVcsRootFinder.getVcsRoots(locatorText != null ? new Locator(locatorText) : null);
    return new VcsRoots(vcsRoots.myEntries,
                        new PagerData(uriInfo.getRequestUriBuilder(),
                                      request.getContextPath(), vcsRoots.myStart, vcsRoots.myCount, vcsRoots.myEntries.size(), locatorText,
                                      "locator"),
                        myApiUrlBuilder);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VcsRoot addRoot(VcsRoot vcsRootDescription) {
    checkVcsRootDescription(vcsRootDescription);
    BeanContext ctx = new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder);
    final SVcsRoot newVcsRoot = getVcsRootProject(vcsRootDescription, ctx).createNewVcsRoot(
      vcsRootDescription.vcsName,
      vcsRootDescription.name != null ? vcsRootDescription.name : null,
      vcsRootDescription.properties.getMap());
    if (vcsRootDescription.modificationCheckInterval != null) {
      newVcsRoot.setModificationCheckInterval(vcsRootDescription.modificationCheckInterval);
    }
    newVcsRoot.persist();
    return new VcsRoot(newVcsRoot, myDataProvider, myApiUrlBuilder);
  }

  public static final String INSTANCES_PATH = "instances";

  @GET
  @Path("/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  public VcsRoot serveRoot(@PathParam("vcsRootLocator") String vcsRootLocator) {
    return new VcsRoot(myVcsRootFinder.getVcsRoot(vcsRootLocator), myDataProvider, myApiUrlBuilder);
  }

  @DELETE
  @Path("/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  public void deleteRoot(@PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    try {
      SProject project = vcsRoot.getProject();
      project.removeOwnVcsRoot(vcsRoot);
    } catch (ProjectNotFoundException e) {
      throw new NotFoundException("Could not find project for VCS root: " + vcsRoot.getName());
    }
  }

  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH)
  @Produces({"application/xml", "application/json"})
  public VcsRootInstances serveRootInstances(@PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    final HashSet<jetbrains.buildServer.vcs.VcsRootInstance> result = new HashSet<jetbrains.buildServer.vcs.VcsRootInstance>();
    for (SBuildType buildType : vcsRoot.getUsages().keySet()) {
      final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = buildType.getVcsRootInstanceForParent(vcsRoot);
      if (rootInstance!=null){
        result.add(rootInstance);
      }
    }
    return new VcsRootInstances(result, null, myApiUrlBuilder);
  }

  /**
   * @param vcsRootLocator this is effectively ignored as vcsRootInstanceLocator should specify instance fully
   * @param vcsRootInstanceLocator
   * @return
   */
  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}")
  @Produces({"application/xml", "application/json"})
  public VcsRootInstance serveRootInstance(@PathParam("vcsRootLocator") String vcsRootLocator,
                                           @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator) {
    return new VcsRootInstance(myVcsRootFinder.getVcsRootInstance(vcsRootInstanceLocator), myDataProvider, myApiUrlBuilder);
  }

  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveRootInstanceProperties(@PathParam("vcsRootLocator") String vcsRootLocator,
                                           @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator) {
    return new Properties(myVcsRootFinder.getVcsRootInstance(vcsRootInstanceLocator).getProperties());
  }


  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/{field}")
  @Produces("text/plain")
  public String serveInstanceField(@PathParam("vcsRootLocator") String vcsRootLocator,
                                   @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                   @PathParam("field") String fieldName) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootFinder.getVcsRootInstance(vcsRootInstanceLocator);
    return VcsRootInstance.getFieldValue(rootInstance, fieldName, myDataProvider);
  }

  @PUT
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/{field}")
  @Consumes("text/plain")
  public void setInstanceField(@PathParam("vcsRootLocator") String vcsRootLocator,
                               @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                               @PathParam("field") String fieldName, String newValue) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootFinder.getVcsRootInstance(vcsRootInstanceLocator);
    VcsRootInstance.setFieldValue(rootInstance, fieldName, newValue, myDataProvider);
    rootInstance.getParent().persist();
  }


  @GET
  @Path("/{vcsRootLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveProperties(@PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    return new Properties(vcsRoot.getProperties());
  }

  @PUT
  @Path("/{vcsRootLocator}/properties")
  @Consumes({"application/xml", "application/json"})
  public void changProperties(@PathParam("vcsRootLocator") String vcsRootLocator, Properties properties) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    VcsRoot.updateVCSRoot(vcsRoot, properties.getMap(), null);
    vcsRoot.persist();
  }

  @DELETE
  @Path("/{vcsRootLocator}/properties")
  public void deleteAllProperties(@PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    VcsRoot.updateVCSRoot(vcsRoot, new HashMap<String, String>(), null);
    vcsRoot.persist();
  }

  @GET
  @Path("/{vcsRootLocator}/properties/{name}")
  @Produces("text/plain")
  public String serveProperty(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("name") String parameterName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    return BuildTypeUtil.getParameter(parameterName, VcsRoot.getUserParametersHolder(vcsRoot));
  }

  @PUT
  @Path("/{vcsRootLocator}/properties/{name}")
  @Consumes("text/plain")
  public void putParameter(@PathParam("vcsRootLocator") String vcsRootLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    BuildTypeUtil.changeParameter(parameterName, newValue, VcsRoot.getUserParametersHolder(vcsRoot),
                                  myServiceLocator);
    vcsRoot.persist();
  }

  @DELETE
  @Path("/{vcsRootLocator}/properties/{name}")
  @Produces("text/plain")
  public void deleteParameter(@PathParam("vcsRootLocator") String vcsRootLocator,
                                       @PathParam("name") String parameterName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    BuildTypeUtil.deleteParameter(parameterName, VcsRoot.getUserParametersHolder(vcsRoot));
    vcsRoot.persist();
  }

  @GET
  @Path("/{vcsRootLocator}/{field}")
  @Produces("text/plain")
  public String serveField(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("field") String fieldName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    return VcsRoot.getFieldValue(vcsRoot, fieldName, myDataProvider);
  }

  @PUT
  @Path("/{vcsRootLocator}/{field}")
  @Consumes("text/plain")
  public void setField(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("field") String fieldName, String newValue) {
    @NotNull final SVcsRoot vcsRoot = myVcsRootFinder.getVcsRoot(vcsRootLocator);
    VcsRoot.setFieldValue(vcsRoot, fieldName, newValue, myDataProvider, myProjectFinder);
    vcsRoot.persist();
  }

  private void checkVcsRootDescription(final VcsRoot description) {
    //might need to check for validity: not specified id, status, lastChecked attributes, etc.
    if (StringUtil.isEmpty(description.vcsName)) {
      //todo: include list of available supports here
      throw new BadRequestException("Attribute 'vcsName' must be specified when creating VCS root. Should be a valid VCS support name.");
    }
    if (description.properties == null) {
      throw new BadRequestException("Element 'properties' must be specified when creating VCS root.");
    }
  }

  // see also BuildTypeUtil.getVcsRoot
  @NotNull
  private SProject getVcsRootProject(@NotNull final VcsRoot description, @NotNull final BeanContext context) {
    if (!StringUtil.isEmpty(description.projectLocator)){
      if (description.project != null){
        throw new BadRequestException("Only one from 'projectLocator' attribute and 'project' element should be specified.");
      }
      return myProjectFinder.getProject(description.projectLocator);
    }else{
      if (description.project == null){
        if (TeamCityProperties.getBoolean("rest.compatibility.allowNoProjectOnVcsRootCreation")){
          return context.getSingletonService(ProjectManager.class).getRootProject();
        }
        throw new BadRequestException("Either 'project' element or 'projectLocator' attribute should be specified.");
      }
      final String internalIdFromPosted = description.project.getInternalIdFromPosted(context);
      if (internalIdFromPosted == null){
        throw new NotFoundException("Could not find project internal id defined by 'project' element.");
      }
      SProject project = myDataProvider.getServer().getProjectManager().findProjectById(internalIdFromPosted);
      if (project == null) {
        throw new NotFoundException("Could not find project by internal id defined by 'project' element.");
      }
      return project;
    }  }
}