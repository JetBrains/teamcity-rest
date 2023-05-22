/*
 * Copyright 2000-2023 JetBrains s.r.o.
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
import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.controllers.project.VersionedSettingsActions;
import jetbrains.buildServer.controllers.project.VersionedSettingsBean;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.server.rest.data.versionedSettings.VersionedSettingsBeanCollector;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsConfig;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsContextParameters;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsStatus;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsTokens;
import jetbrains.buildServer.server.rest.service.versionedSettings.VersionedSettingsConfigsService;
import jetbrains.buildServer.server.rest.service.versionedSettings.VersionedSettingsTokensService;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;


@Api("VersionedSettings")
@Path(VersionedSettingsRequest.API_PROJECT_VERSIONED_SETTINGS_URL)
public class VersionedSettingsRequest {

  public static final String API_PROJECT_VERSIONED_SETTINGS_URL = Constants.API_URL + "/projects/{locator}/versionedSettings";

  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private VersionedSettingsBeanCollector myVersionedSettingsBeanCollector;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanFactory myFactory;
  @Context @NotNull private VersionedSettingsTokensService myVersionedSettingsTokensService;
  @Context @NotNull private PermissionChecker myPermissionChecker;
  @Context @NotNull private VersionedSettingsConfigsService myVersionedSettingsConfigsService;


  @GET
  @Path("/status")
  @ApiOperation(value = "Get current status of Versioned Settings.", nickname = "getVersionedSettingsStatus")
  public VersionedSettingsStatus getStatus(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                           @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    VersionedSettingsBean versionedSettingsBean = myVersionedSettingsBeanCollector.getItem(project);
    VersionedSettingsBean.VersionedSettingsStatusBean status = versionedSettingsBean.getStatus();
    if (status == null) {
      throw new BadRequestException("Versioned Settings have never been enabled in this project.");
    }
    return new VersionedSettingsStatus(versionedSettingsBean.getStatus(), new Fields(fields));
  }


  @GET
  @Path("/contextParameters")
  @ApiOperation(value = "Get Versioned Settings Context Parameters.", nickname = "getVersionedSettingsContextParameters")
  public VersionedSettingsContextParameters getContextParameters(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    Map<String, String> paramsMap = getVersionedSettingsManager().readConfig(project).getDslContextParameters();
    return new VersionedSettingsContextParameters(paramsMap);
  }


  @POST
  @Path("/contextParameters")
  @ApiOperation(value = "Set Versioned Settings Context Parameters.", nickname = "setVersionedSettingsContextParameters")
  public VersionedSettingsContextParameters setContextParameters(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                                                 VersionedSettingsContextParameters versionedSettingsContextParameters) {
    SProject project = myProjectFinder.getItem(projectLocator);
    getVersionedSettingsManager().setContextParameters(project, versionedSettingsContextParameters.toMap());
    return getContextParameters(projectLocator);
  }


  @GET
  @Path("/tokens")
  @ApiOperation(value = "Get Versioned Settings Tokens.", nickname = "getVersionedSettingsTokens")
  public VersionedSettingsTokens getTokens(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                           @QueryParam("status") String status) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    return myVersionedSettingsTokensService.getTokens(project, status);
  }


  @POST
  @Path("/tokens")
  @ApiOperation(value = "Add Versioned Settings Tokens.", nickname = "addVersionedSettingsTokens")
  public VersionedSettingsTokens setTokens(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                           VersionedSettingsTokens versionedSettingsTokens) {
    SProject project = myProjectFinder.getItem(projectLocator);
    myVersionedSettingsTokensService.setTokens(project, versionedSettingsTokens);
    return getTokens(projectLocator, null);
  }


  @DELETE
  @Path("/tokens")
  @ApiOperation(value = "Delete Versioned Settings Tokens.", nickname = "deleteVersionedSettingsTokens")
  public VersionedSettingsTokens deleteTokens(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                              VersionedSettingsTokens versionedSettingsTokens) {
    SProject project = myProjectFinder.getItem(projectLocator);
    myVersionedSettingsTokensService.deleteTokens(project, versionedSettingsTokens);
    return getTokens(projectLocator, null);
  }


  @GET
  @Path("/affectedProjects")
  @ApiOperation(value = "Get a list of projects that are affected by Load Settings from VCS action.", nickname = "getVersionedSettingsProjectsToLoad")
  public Projects getProjectsToLoad(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                    @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    myVersionedSettingsConfigsService.checkEnabled(project);
    try {
      Set<SProject> projectsToLoadSettingsFromVcs = getVersionedSettingsActions().getProjectsToLoadSettingsFromVcs(project);
      return new Projects(new ArrayList<>(projectsToLoadSettingsFromVcs), null, new Fields(fields), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
    } catch (VersionedSettingsActions.VersionedSettingsActionException e) {
      throw new OperationException(e.getMessage());
    }
  }


  @POST
  @Path("/loadSettings")
  @ApiOperation(value = "Perform Versioned Settings action: Load Setting from VCS.", nickname = "loadSettingsFromVCS")
  public Projects loadProjectsFromVcs(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                      @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    User user = myPermissionChecker.getCurrent().getAssociatedUser();
    try {
      getVersionedSettingsActions().loadSettingsFromVcs(user, project);
    } catch (VersionedSettingsActions.VersionedSettingsActionException e) {
      throw new OperationException(e.getMessage());
    }
    return getProjectsToLoad(projectLocator, fields);
  }


  @POST
  @Path("/commitCurrentSettings")
  @ApiOperation(value = "Perform Versioned Settings action: Commit current settings to VCS.", nickname = "commitCurrentSettings")
  public void commitCurrentSettings(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator) {
    SProject project = myProjectFinder.getItem(projectLocator);
    User user = myPermissionChecker.getCurrent().getAssociatedUser();
    try {
      getVersionedSettingsActions().commitSettingsToVcs((SUser) user, project);
    } catch (VersionedSettingsActions.VersionedSettingsActionException e) {
      throw new OperationException(e.getMessage());
    }
  }


  @POST
  @Path("/checkForChanges")
  @ApiOperation(value = "Check for changes in Versioned Settings.", nickname = "checkForVersionedSettingsChanges")
  public void checkForChanges(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator) {
    SProject project = myProjectFinder.getItem(projectLocator);
    myVersionedSettingsConfigsService.checkEnabled(project);
    try {
      getVersionedSettingsActions().checkForChanges(project);
    } catch (VersionedSettingsActions.VersionedSettingsActionException e) {
      throw new OperationException(e.getMessage());
    }
  }


  @GET
  @Path("/config")
  @ApiOperation(value = "Get Versioned Settings config.", nickname = "getVersionedSettingsConfig")
  public VersionedSettingsConfig getVersionedSettingsConfig(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                                            @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    return myVersionedSettingsConfigsService.getVersionedSettingsConfig(project, new Fields(fields));
  }


  @PUT
  @Path("/config")
  @ApiOperation(value = "Set Verseioned Settings config.", nickname = "setVersionedSettingsConfig")
  public VersionedSettingsConfig setVersionedSettingsConfig(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                                            VersionedSettingsConfig versionedSettingsConfig,
                                                            @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    myVersionedSettingsConfigsService.setVersionedSettingsConfig(project, versionedSettingsConfig);
    return myVersionedSettingsConfigsService.getVersionedSettingsConfig(project, new Fields(fields));
  }


  @GET
  @Path("/config/parameters/{name}")
  @Produces("text/plain")
  @ApiOperation(value = "Get Versioned Settings config parameter value.", nickname = "getVersionedSettingsConfigParameter")
  public String getVersionedSettingsConfigParameter(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                                    @PathParam("name") String paramName) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    VersionedSettingsConfig versionedSettingsConfig = myVersionedSettingsConfigsService.getVersionedSettingsConfig(project, new Fields(null));
    if (versionedSettingsConfig == null) {
      throw new OperationException("Versioned Settings feature is not found for project " + project.getExternalId());
    }
    return versionedSettingsConfig.getFieldValue(paramName);
  }


  @PUT
  @Path("/config/parameters/{name}")
  @Produces("text/plain")
  @Consumes("text/plain")
  @ApiOperation(value = "Set Versioned Settings config parameter value.", nickname = "setVersionedSettingsConfigParameter")
  public String setVersionedSettingsConfigParameter(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                                    @PathParam("name") String paramName,
                                                    String value) {
    SProject project = myProjectFinder.getItem(projectLocator);
    VersionedSettingsConfig versionedSettingsConfig = myVersionedSettingsConfigsService.getVersionedSettingsConfig(project, new Fields(null));
    if (versionedSettingsConfig == null) {
      throw new OperationException("Versioned Settings feature is not found for project " + project.getExternalId());
    }
    versionedSettingsConfig.setFieldValue(paramName, value);
    myVersionedSettingsConfigsService.setVersionedSettingsConfig(project, versionedSettingsConfig);
    String updateParamValue = getVersionedSettingsConfigParameter(projectLocator, paramName);
    if (!Objects.equals(value, updateParamValue)) {
      throw new OperationException("Value is not updated. Perhabs parameter is not updatable, e.g. Versioned Settings is disabled or parameter is not allowed to be changed.");
    }
    return updateParamValue;
  }


  @DELETE
  @Path("/config/parameters/{name}")
  @ApiOperation(value = "Delete Versioned Settings config parameter value.", nickname = "deleteVersionedSettingsConfigParameter")
  public void deleteVersionedSettingsConfigParameter(@ApiParam(format = LocatorName.PROJECT) @PathParam("locator") String projectLocator,
                                                     @PathParam("name") String paramName) {
    setVersionedSettingsConfigParameter(projectLocator, paramName, null);
  }


  protected VersionedSettingsManager getVersionedSettingsManager() {
    return myServiceLocator.getSingletonService(VersionedSettingsManager.class);
  }

  private VersionedSettingsActions getVersionedSettingsActions() {
    return myServiceLocator.getSingletonService(VersionedSettingsActions.class);
  }

  protected void initForTests(@NotNull ApiUrlBuilder apiUrlBuilder,
                              @NotNull ServiceLocator serviceLocator,
                              @NotNull BeanFactory beanFactory,
                              @NotNull ProjectFinder projectFinder,
                              @NotNull VersionedSettingsBeanCollector versionedSettingsBeanCollector,
                              @NotNull PermissionChecker permissionChecker,
                              @NotNull VersionedSettingsTokensService versionedSettingsTokensService,
                              @NotNull VersionedSettingsConfigsService versionedSettingsConfigProvider) {
    myApiUrlBuilder = apiUrlBuilder;
    myServiceLocator = serviceLocator;
    myFactory = beanFactory;
    myProjectFinder = projectFinder;
    myVersionedSettingsBeanCollector = versionedSettingsBeanCollector;
    myPermissionChecker = permissionChecker;
    myVersionedSettingsTokensService = versionedSettingsTokensService;
    myVersionedSettingsConfigsService = versionedSettingsConfigProvider;
  }

}
