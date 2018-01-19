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

import io.swagger.annotations.Api;
import java.util.Collection;
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
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootInstances;
import jetbrains.buildServer.server.rest.model.buildType.VcsRoots;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;
import jetbrains.buildServer.server.rest.model.change.VcsRootInstance;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(VcsRootRequest.API_VCS_ROOTS_URL)
@Api("VcsRoot")
public class VcsRootRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private VcsRootFinder myVcsRootFinder;
  @Context @NotNull private VcsRootInstanceFinder myVcsRootInstanceFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull public PermissionChecker myPermissionChecker;

  public static final String API_VCS_ROOTS_URL = Constants.API_URL + "/vcs-roots";

  public static String getHref() {
    return API_VCS_ROOTS_URL;
  }

  public static String getVcsRootHref(final jetbrains.buildServer.vcs.SVcsRoot root) {
    return API_VCS_ROOTS_URL + "/" + VcsRootFinder.getLocator(root);
  }

  public static String getHref(@NotNull final SProject project) {
    return API_VCS_ROOTS_URL + "?locator=" + VcsRootFinder.getLocator(project);
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public VcsRoots serveRoots(@QueryParam("locator") String locatorText, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<SVcsRoot> vcsRoots = myVcsRootFinder.getItems(locatorText);
    return new VcsRoots(vcsRoots.myEntries,
                        new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), vcsRoots, locatorText, "locator"),
                        new Fields(fields),
                        myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public VcsRoot addRoot(VcsRoot vcsRootDescription, @QueryParam("fields") String fields) {
    checkVcsRootDescription(vcsRootDescription);
    BeanContext ctx = new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder);
    //todo: TeamCity openAPI: not consistent methods for creating VCS root with/without id
    final SVcsRoot newVcsRoot = getVcsRootProject(vcsRootDescription, ctx).createVcsRoot(
      vcsRootDescription.vcsName,
      vcsRootDescription.name != null ? vcsRootDescription.name : null,
      vcsRootDescription.properties.getMap());
    if (vcsRootDescription.id != null) {
      newVcsRoot.setExternalId(vcsRootDescription.id);
    }
    if (vcsRootDescription.modificationCheckInterval != null) {
      newVcsRoot.setModificationCheckInterval(vcsRootDescription.modificationCheckInterval);
    }
    newVcsRoot.persist();
    return new VcsRoot(newVcsRoot, new Fields(fields), myBeanContext);
  }

  public static final String INSTANCES_PATH = "instances";

  @GET
  @Path("/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  public VcsRoot serveRoot(@PathParam("vcsRootLocator") String vcsRootLocator, @QueryParam("fields") String fields) {
    return new VcsRoot(myVcsRootFinder.getItem(vcsRootLocator), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{vcsRootLocator}")
  public void deleteRoot(@PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    try {
      vcsRoot.remove();
    } catch (ProjectNotFoundException e) {
      throw new NotFoundException("Could not find project for VCS root: " + vcsRoot.getName());
    }
  }

  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH)
  @Produces({"application/xml", "application/json"})
  public VcsRootInstances serveRootInstances(@PathParam("vcsRootLocator") final String vcsRootLocator, @QueryParam("fields") final String fields) {
    //todo: use VcsRootFinder here
    return new VcsRootInstances(new CachingValue<Collection<jetbrains.buildServer.vcs.VcsRootInstance>>() {
      @NotNull
      @Override
      protected Collection<jetbrains.buildServer.vcs.VcsRootInstance> doGet() {
        final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
        final HashSet<jetbrains.buildServer.vcs.VcsRootInstance> result = new HashSet<jetbrains.buildServer.vcs.VcsRootInstance>();
        for (SBuildType buildType : vcsRoot.getUsagesInConfigurations()) {
          final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = buildType.getVcsRootInstanceForParent(vcsRoot);
          if (rootInstance != null) {
            result.add(rootInstance);
          }
        }
        return result;
      }
    }, null, new Fields(fields), myBeanContext);
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
                                           @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                           @QueryParam("fields") String fields) {
    return new VcsRootInstance(myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator),new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveRootInstanceProperties(@PathParam("vcsRootLocator") String vcsRootLocator,
                                           @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                           @QueryParam("fields") String fields) {
    return new Properties(myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator).getProperties(), null, new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/{field}")
  @Produces("text/plain")
  public String serveInstanceField(@PathParam("vcsRootLocator") String vcsRootLocator,
                                   @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                   @PathParam("field") String fieldName) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    return VcsRootInstance.getFieldValue(rootInstance, fieldName, myDataProvider);
  }

  @PUT
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setInstanceField(@PathParam("vcsRootLocator") String vcsRootLocator,
                               @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                               @PathParam("field") String fieldName, String newValue) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    VcsRootInstance.setFieldValue(rootInstance, fieldName, newValue, myDataProvider);
    rootInstance.getParent().persist();
    return VcsRootInstance.getFieldValue(rootInstance, fieldName, myDataProvider);
  }


  @GET
  @Path("/{vcsRootLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveProperties(@PathParam("vcsRootLocator") String vcsRootLocator, @QueryParam("fields") String fields) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    return new Properties(vcsRoot.getProperties(), null, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{vcsRootLocator}/properties")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Properties changeProperties(@PathParam("vcsRootLocator") String vcsRootLocator, Properties properties, @QueryParam("fields") String fields) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    vcsRoot.setProperties(properties.getMap());
    vcsRoot.persist();
    return new Properties(vcsRoot.getProperties(), null, new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{vcsRootLocator}/properties")
  public void deleteAllProperties(@PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    vcsRoot.setProperties(new HashMap<String, String>());
    vcsRoot.persist();
  }

  @GET
  @Path("/{vcsRootLocator}/properties/{name}")
  @Produces("text/plain")
  public String serveProperty(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("name") String parameterName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    return BuildTypeUtil.getParameter(parameterName, VcsRoot.getEntityWithParameters(vcsRoot), true, true, myServiceLocator);
  }

  @PUT
  @Path("/{vcsRootLocator}/properties/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String putParameter(@PathParam("vcsRootLocator") String vcsRootLocator,
                                    @PathParam("name") String parameterName,
                                    String newValue) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    BuildTypeUtil.changeParameter(parameterName, newValue, VcsRoot.getEntityWithParameters(vcsRoot),
                                  myServiceLocator);
    vcsRoot.persist();
    return BuildTypeUtil.getParameter(parameterName, VcsRoot.getEntityWithParameters(vcsRoot), false, true, myServiceLocator);
  }

  @DELETE
  @Path("/{vcsRootLocator}/properties/{name}")
  public void deleteParameter(@PathParam("vcsRootLocator") String vcsRootLocator,
                                       @PathParam("name") String parameterName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    BuildTypeUtil.deleteParameter(parameterName, VcsRoot.getEntityWithParameters(vcsRoot));
    vcsRoot.persist();
  }

  @GET
  @Path("/{vcsRootLocator}/{field}")
  @Produces("text/plain")
  public String serveField(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("field") String fieldName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    return VcsRoot.getFieldValue(vcsRoot, fieldName, myDataProvider);
  }

  @PUT
  @Path("/{vcsRootLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setField(@PathParam("vcsRootLocator") String vcsRootLocator, @PathParam("field") String fieldName, String newValue) {
    @NotNull final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    VcsRoot.setFieldValue(vcsRoot, fieldName, newValue, myDataProvider, myProjectFinder);
    vcsRoot.persist();
    return VcsRoot.getFieldValue(vcsRoot, fieldName, myDataProvider);
  }

  /**
   * Experimental support only
   */
  @GET
  @Path("/{vcsRootLocator}/settingsFile")
  @Produces({"text/plain"})
  public String getSettingsFile(@PathParam("vcsRootLocator") String vcsRootLocator) {
    myPermissionChecker.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    @NotNull final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    return vcsRoot.getConfigurationFile().getAbsolutePath();
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

  @NotNull
  private SProject getVcsRootProject(@NotNull final VcsRoot description, @NotNull final BeanContext context) {
    if (!StringUtil.isEmpty(description.projectLocator)){
      if (description.project != null){
        throw new BadRequestException("Only one from 'projectLocator' attribute and 'project' element should be specified.");
      }
      return myProjectFinder.getItem(description.projectLocator);
    }else{
      if (description.project == null){
        if (TeamCityProperties.getBoolean("rest.compatibility.allowNoProjectOnVcsRootCreation")){
          return context.getSingletonService(ProjectManager.class).getRootProject();
        }
        throw new BadRequestException("Either 'project' element or 'projectLocator' attribute should be specified.");
      }
      return description.project.getProjectFromPosted(context.getSingletonService(ProjectFinder.class));
    }
  }
}