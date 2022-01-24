/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

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
  @ApiOperation(value="Get all VCS roots.",nickname="getAllVcsRoots")
  public VcsRoots serveRoots(@ApiParam(format = LocatorName.VCS_ROOT) @QueryParam("locator") String locatorText,
                             @QueryParam("fields") String fields,
                             @Context UriInfo uriInfo,
                             @Context HttpServletRequest request) {
    final PagedSearchResult<SVcsRoot> vcsRoots = myVcsRootFinder.getItems(locatorText);
    return new VcsRoots(vcsRoots.myEntries,
                        new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), vcsRoots, locatorText, "locator"),
                        new Fields(fields),
                        myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add a new VCS root.",nickname="addVcsRoot")
  public VcsRoot addRoot(VcsRoot vcsRootDescription, @QueryParam("fields") String fields) {
    checkVcsRootDescription(vcsRootDescription);
    BeanContext ctx = new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder);
    //todo: TeamCity openAPI: not consistent methods for creating VCS root with/without id
    final SVcsRoot newVcsRoot = getVcsRootProject(vcsRootDescription, ctx).createVcsRoot(
      vcsRootDescription.vcsName,
      vcsRootDescription.name,
      vcsRootDescription.properties.getMap());
    if (vcsRootDescription.id != null) {
      newVcsRoot.setExternalId(vcsRootDescription.id);
    }
    if (vcsRootDescription.modificationCheckInterval != null) {
      newVcsRoot.setModificationCheckInterval(vcsRootDescription.modificationCheckInterval);
    }
    newVcsRoot.schedulePersisting("A new VCS root created");
    return new VcsRoot(newVcsRoot, new Fields(fields), myBeanContext);
  }

  public static final String INSTANCES_PATH = "instances";

  @GET
  @Path("/{vcsRootLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get root endpoints.",nickname="getRootEndpoints")
  public VcsRoot serveRoot(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                           @QueryParam("fields") String fields) {
    return new VcsRoot(myVcsRootFinder.getItem(vcsRootLocator), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{vcsRootLocator}")
  @ApiOperation(value="Remove VCS root matching the locator.",nickname="deleteVcsRoot")
  public void deleteRoot(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator) {
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
  @ApiOperation(value="Get all VCS root instances of the matching VCS root.",nickname="getVcsRootInstances")
  public VcsRootInstances serveRootInstances(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") final String vcsRootLocator,
                                             @QueryParam("fields") final String fields) {
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
  @ApiOperation(value="Get instance of the matching VCS root.",nickname="getVcsRootInstance", hidden=true)
  public VcsRootInstance serveRootInstance(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                           @ApiParam(format = LocatorName.VCS_ROOT_INSTANCE) @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                           @QueryParam("fields") String fields) {
    return new VcsRootInstance(myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator),new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/properties")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all properties of the matching VCS root instance.",nickname="getVcsRootInstanceProperties", hidden=true)
  public Properties serveRootInstanceProperties(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                                @ApiParam(format = LocatorName.VCS_ROOT_INSTANCE) @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                                @QueryParam("fields") String fields) {
    return new Properties(myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator).getProperties(), null, new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="Get a field of the matching VCS root instance.",nickname="getVcsRootInstanceField", hidden=true)
  public String serveInstanceField(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                   @ApiParam(format = LocatorName.VCS_ROOT_INSTANCE) @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                   @PathParam("field") String fieldName) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    return VcsRootInstance.getFieldValue(rootInstance, fieldName, myDataProvider);
  }

  @PUT
  @Path("/{vcsRootLocator}/" + INSTANCES_PATH + "/{vcsRootInstanceLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(value="Get a field of the matching VCS root.",nickname="setVcsRootInstanceField", hidden=true)
  public String setInstanceField(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                 @ApiParam(format = LocatorName.VCS_ROOT_INSTANCE) @PathParam("vcsRootInstanceLocator") String vcsRootInstanceLocator,
                                 @PathParam("field") String fieldName, String newValue) {
    final jetbrains.buildServer.vcs.VcsRootInstance rootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
    myVcsRootInstanceFinder.checkPermission(Permission.EDIT_PROJECT, rootInstance);
    VcsRootInstance.setFieldValue(rootInstance, fieldName, newValue, myBeanContext);
    rootInstance.getParent().schedulePersisting("VCS root changed");
    return VcsRootInstance.getFieldValue(rootInstance, fieldName, myDataProvider);
  }


  @GET
  @Path("/{vcsRootLocator}/properties")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all properties of the matching VCS root.",nickname="getAllVcsRootProperties")
  public Properties serveProperties(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                    @QueryParam("fields") String fields) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    return new Properties(vcsRoot.getProperties(), null, new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{vcsRootLocator}/properties")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update all properties of the matching VCS root.",nickname="setVcsRootProperties")
  public Properties changeProperties(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                                     Properties properties,
                                     @QueryParam("fields") String fields) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    vcsRoot.setProperties(properties.getMap());
    vcsRoot.schedulePersisting("VCS root changed");
    return new Properties(vcsRoot.getProperties(), null, new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{vcsRootLocator}/properties")
  @ApiOperation(value="Delete all properties of the matching VCS root.",nickname="deleteAllVcsRootProperties")
  public void deleteAllProperties(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    vcsRoot.setProperties(new HashMap<String, String>());
    vcsRoot.schedulePersisting("All VCS root properties removed");
  }

  @GET
  @Path("/{vcsRootLocator}/properties/{name}")
  @Produces("text/plain")
  @ApiOperation(value="Get a property on the matching VCS root.",nickname="getVcsRootProperty")
  public String serveProperty(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                              @PathParam("name") String parameterName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    return BuildTypeUtil.getParameter(parameterName, VcsRoot.getEntityWithParameters(vcsRoot), true, true, myServiceLocator);
  }

  @PUT
  @Path("/{vcsRootLocator}/properties/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(value="Update a property of the matching VCS root.",nickname="setVcsRootProperty")
  public String putParameter(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                             @PathParam("name") String parameterName,
                             String newValue) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    BuildTypeUtil.changeParameter(parameterName, newValue, VcsRoot.getEntityWithParameters(vcsRoot),
                                  myServiceLocator);
    vcsRoot.schedulePersisting("New property with name " + parameterName + " added to VCS root");
    return BuildTypeUtil.getParameter(parameterName, VcsRoot.getEntityWithParameters(vcsRoot), false, true, myServiceLocator);
  }

  @DELETE
  @Path("/{vcsRootLocator}/properties/{name}")
  @ApiOperation(value="Delete a property of the matching VCS root.",nickname="deleteVcsRootProperty")
  public void deleteParameter(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                              @PathParam("name") String parameterName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    BuildTypeUtil.deleteParameter(parameterName, VcsRoot.getEntityWithParameters(vcsRoot));
    vcsRoot.schedulePersisting("Property with name " + parameterName + " deleted from VCS root");
  }

  @GET
  @Path("/{vcsRootLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="Get a field of the matching VCS root.",nickname="getVcsRootField")
  public String serveField(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                           @PathParam("field") String fieldName) {
    final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    return VcsRoot.getFieldValue(vcsRoot, fieldName, myDataProvider);
  }

  @PUT
  @Path("/{vcsRootLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(value="Update a field of the matching VCS root.",nickname="setVcsRootField")
  public String setField(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator,
                         @PathParam("field") String fieldName,
                         String newValue) {
    @NotNull final SVcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
    VcsRoot.setFieldValue(vcsRoot, fieldName, newValue, myProjectFinder);
    vcsRoot.schedulePersisting("Field with name " + fieldName + " changed in VCS root");
    return VcsRoot.getFieldValue(vcsRoot, fieldName, myDataProvider);
  }

  /**
   * Experimental support only
   */
  @GET
  @Path("/{vcsRootLocator}/settingsFile")
  @Produces({"text/plain"})
  @ApiOperation(value="Get the settings file of the matching VCS root.",nickname="getVcsRootSettingsFile")
  public String getSettingsFile(@ApiParam(format = LocatorName.VCS_ROOT) @PathParam("vcsRootLocator") String vcsRootLocator) {
    myPermissionChecker.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS);
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