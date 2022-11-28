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

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.parameters.MapBackedEntityWithModifiableParameters;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.agent.AgentPools;
import jetbrains.buildServer.server.rest.model.build.Branches;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.buildType.NewBuildTypeDescription;
import jetbrains.buildServer.server.rest.model.project.*;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentPools.NoSuchAgentPoolException;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.dependency.CyclicDependencyFoundException;
import jetbrains.buildServer.serverSide.identifiers.DuplicateExternalIdException;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.projects.ProjectsLoader;
import jetbrains.buildServer.serverSide.impl.xml.XmlConstants;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletPartUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

/**
 * @author Yegor Yarko
 * @date 11.04.2009
 */
@Path(ProjectRequest.API_PROJECTS_URL)
@Api("Project")
public class ProjectRequest {
  private static final Logger LOG = Logger.getInstance(ProjectRequest.class.getName());
  public static final boolean ID_GENERATION_FLAG = true;

  @SuppressWarnings("NotNullFieldNotInitialized") // initialized by dependency injection via setter.
  @NotNull private ServiceLocator myServiceLocator;
  @SuppressWarnings("NotNullFieldNotInitialized") // initialized by dependency injection via serviceLocator setter.
  @NotNull private ConfigActionFactory myConfigActionFactory;
  @SuppressWarnings("NotNullFieldNotInitialized") // initialized by dependency injection via serviceLocator setter.
  @NotNull private ServerSshKeyManager myServerSshKeyManager;

  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private AgentPoolFinder myAgentPoolFinder;
  @Context @NotNull private BranchFinder myBranchFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;

  @Context @NotNull private PermissionChecker myPermissionChecker;

  @Context
  public void setServiceLocator(@NotNull ServiceLocator serviceLocator) {
    myServiceLocator = serviceLocator;

    {
      /*
       * This is the workaround to make these fields be injected.
       * Since injection of beans from ServiceLocator is not supported
       * (except for beans explicitly defined using Jersey's Provider)
       * you can not inject this bean directly and workaround is required.
       * TODO inject using @Autowired or @Context
       */
      //noinspection ConstantConditions
      if (myConfigActionFactory == null) {
        myConfigActionFactory = serviceLocator.findSingletonService(ConfigActionFactory.class);
      }
      //noinspection ConstantConditions
      if (myServerSshKeyManager == null) {
        myServerSshKeyManager = serviceLocator.findSingletonService(ServerSshKeyManager.class);
      }
    }
  }

  public ProjectRequest(
    @NotNull BeanContext beanContext,
    ServiceLocator serviceLocator,
    @NotNull DataProvider dataProvider,
    BuildFinder buildFinder,
    BuildTypeFinder buildTypeFinder,
    ProjectFinder projectFinder,
    AgentPoolFinder agentPoolFinder,
    BranchFinder branchFinder,
    ApiUrlBuilder apiUrlBuilder,
    PermissionChecker permissionChecker,
    ConfigActionFactory configActionFactory,
    ServerSshKeyManager serverSshKeyManager
  ) {
    myBeanContext = beanContext;
    myDataProvider = dataProvider;
    setServiceLocator(firstNonNull(serviceLocator, beanContext.getServiceLocator()));
    myBuildFinder = firstNonNull(buildFinder, myServiceLocator.findSingletonService(BuildFinder.class));
    myBuildTypeFinder = firstNonNull(buildTypeFinder, myServiceLocator.findSingletonService(BuildTypeFinder.class));
    myProjectFinder = firstNonNull(projectFinder, myServiceLocator.findSingletonService(ProjectFinder.class));
    myAgentPoolFinder = firstNonNull(agentPoolFinder, myServiceLocator.findSingletonService(AgentPoolFinder.class));
    myBranchFinder = firstNonNull(branchFinder, myServiceLocator.findSingletonService(BranchFinder.class));
    myApiUrlBuilder = firstNonNull(apiUrlBuilder, myServiceLocator.findSingletonService(ApiUrlBuilder.class));
    myPermissionChecker = firstNonNull(permissionChecker, myServiceLocator.findSingletonService(PermissionChecker.class));
    myConfigActionFactory = firstNonNull(configActionFactory, myServiceLocator.findSingletonService(ConfigActionFactory.class));
    myServerSshKeyManager = firstNonNull(serverSshKeyManager, myServiceLocator.findSingletonService(ServerSshKeyManager.class));
  }

  /**
   * For dependency injection frameworks only
   */
  @Deprecated
  public ProjectRequest() {
  }

  public static final String API_PROJECTS_URL = Constants.API_URL + "/projects";
  protected static final String PARAMETERS = BuildTypeRequest.PARAMETERS;
  protected static final String FEATURES = "/projectFeatures";

  @NotNull
  public static String getHref() {
    return API_PROJECTS_URL;
  }

  @NotNull
  public static String getProjectHref(SProject project) {
    return API_PROJECTS_URL + "/" + ProjectFinder.getLocator(project);
  }

  @NotNull
  public static String getParametersHref(final SProject project) {
    return getProjectHref(project) + PARAMETERS;
  }

  @NotNull
  public static String getFeaturesHref(@NotNull final SProject project) {
    return getProjectHref(project) + FEATURES;
  }

  @NotNull
  public static String getFeatureHref(@NotNull final SProject project, @NotNull final SProjectFeatureDescriptor descriptor) {
    return getFeaturesHref(project) + "/" + PropEntityProjectFeature.ProjectFeatureFinder.getLocator(descriptor);
  }

  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all projects.",nickname="getAllProjects")
  public Projects serveProjects(@QueryParam("locator") String locator, @QueryParam("fields") String fields,
                                @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<SProject> result = myProjectFinder.getItems(locator);
    final PagerData pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator");
    return new Projects(result.myEntries, pager, new Fields(fields), myBeanContext);
  }

  @POST
  @Consumes({"text/plain"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(hidden = true, value = "Use createProject instead")
  public Project createEmptyProject(String name) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Project name cannot be empty.");
    }
    final SProject project = myDataProvider.getServer().getProjectManager().createProject(name);
    project.schedulePersisting("A new project was created");
    return new Project(project, Fields.LONG, myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Create a new project.",nickname="addProject")
  public Project createProject(NewProjectDescription descriptor) {
    if (StringUtil.isEmpty(descriptor.name)) {
      throw new BadRequestException("Project name cannot be empty.");
    }
    SProject resultingProject;
    @Nullable SProject sourceProject = descriptor.getSourceProject(myServiceLocator);
    final ProjectManager projectManager = myDataProvider.getServer().getProjectManager();
    final SProject parentProject = descriptor.getParentProject(myServiceLocator);
    if (sourceProject == null) {
      resultingProject = parentProject.createProject(descriptor.getId(myServiceLocator), descriptor.name);
    } else {
      final CopyOptions copyOptions = descriptor.getCopyOptions();
      //see also getExampleNewProjectDescription which prepares NewProjectDescription
      copyOptions.addProjectExternalIdMapping(Collections.singletonMap(sourceProject.getExternalId(), descriptor.getId(myServiceLocator)));
      copyOptions.setGenerateExternalIdsBasedOnOriginalExternalIds(ID_GENERATION_FLAG);
      if (descriptor.name != null) copyOptions.setNewProjectName(descriptor.name);
      try {
        resultingProject = projectManager.copyProject(sourceProject, parentProject, copyOptions);
      } catch (MaxNumberOfBuildTypesReachedException e) {
        throw new BadRequestException("Build configurations number limit is reached", e);
      } catch (NotAllIdentifiersMappedException e) {
        throw new BadRequestException("Not all ids are mapped", e);
      } catch (InvalidNameException e) {
        throw new BadRequestException("Invalid name", e);
      } catch (DuplicateExternalIdException e) {
        throw new BadRequestException("Duplicate id", e);
      }
      try {
        if (descriptor.name != null) resultingProject.setName(descriptor.name);
        //todo: TeamCity api: is this necessary? http://youtrack.jetbrains.com/issue/TW-28495
        resultingProject.setExternalId(descriptor.getId(myServiceLocator));
      } catch (InvalidIdentifierException | DuplicateExternalIdException e) {
        processCreatedProjectFinalizationError(resultingProject, projectManager, e);
      } catch (AccessDeniedException e) {
        LOG.debug(() -> "Got access denied while setting project parameters. This is most probably because the token permissions don't propagate to a newly created child project", e);
      }
    }

    try {
      resultingProject.schedulePersisting("A new project was created");
    } catch (PersistFailedException e) {
      processCreatedProjectFinalizationError(resultingProject, projectManager, e);
    }
    return new Project(resultingProject, Fields.LONG, myBeanContext);
  }

  private void processCreatedProjectFinalizationError(final SProject resultingProject, final ProjectManager projectManager, final Exception e) {
    try {
      projectManager.removeProject(resultingProject.getProjectId());
    } catch (ProjectRemoveFailedException e1) {
      LOG.warn("Rollback of project creation failed", e1);
      //ignore
    }
    throw new InvalidStateException("Error during project creation finalization", e);
  }

  @GET
  @Path("/{projectLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get project matching the locator.",nickname="getProject")
  public Project serveProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                              @QueryParam("fields") String fields) {
    return new Project(myProjectFinder.getItem(projectLocator),  new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{projectLocator}")
  @ApiOperation(value="Delete project matching the locator.",nickname="deleteProject")
  public void deleteProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator) {
    final SProject project = myProjectFinder.getItem(projectLocator);
    myDataProvider.getServer().getProjectManager().removeProject(project.getProjectId());
  }

  @GET
  @Path("/{projectLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="Get a field of the matching project.",nickname="getProjectField")
  public String serveProjectField(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                  @PathParam("field") String fieldName) {
    return Project.getFieldValue(myProjectFinder.getItem(projectLocator), fieldName);
  }

  @PUT
  @Path("/{projectLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(value="Update a field of the matching project.",nickname="setProjectField")
  public String setProjectField(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                @PathParam("field") String fieldName,
                                String newValue) {
    final SProject project = myProjectFinder.getItem(projectLocator);
    Project.setFieldValueAndPersist(project, fieldName, newValue, myServiceLocator);
    return Project.getFieldValue(project, fieldName);
  }

  /*
  @GET
  @Path("/{projectLocator}/readOnlyUI/value")
  @Produces("text/plain")
  public String getReadOnlyUiEnabled(@PathParam("projectLocator") String projectLocator) {
    return Project.getFieldValue(myProjectFinder.getItem(projectLocator), "readOnlyUI");
  }

  @PUT
  @Path("/{projectLocator}/readOnlyUI/value")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setReadOnlyUiEnabled(@PathParam("projectLocator") String projectLocator, String newValue) {
    final SProject project = myProjectFinder.getItem(projectLocator);
    Project.setFieldValueAndPersist(project, "readOnlyUI", String.valueOf(newValue), myServiceLocator);
    return Project.getFieldValue(myProjectFinder.getItem(projectLocator), "readOnlyUI");
  }
  */

  @GET
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="serveBuildTypesInProject",hidden=true)
  public BuildTypes serveBuildTypesInProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                             @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    return new BuildTypes(BuildTypes.fromBuildTypes(project.getOwnBuildTypes()), null, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  @Consumes({"text/plain"})
  @ApiOperation(hidden = true, value = "Use createBuildType instead")
  public BuildType createEmptyBuildType(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                        String name,
                                        @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Build type name cannot be empty.");
    }
    final SBuildType buildType = project.createBuildType(name);
    buildType.schedulePersisting("A new build configuration is created");
    return new BuildType(new BuildTypeOrTemplate(buildType),  new Fields(fields), myBeanContext);
  }

  /**
   * Creates a new build configuration by copying existing one.
   *
   * @param projectLocator
   * @param descriptor     reference to the build configuration to copy and copy options.
   *                       e.g. <newBuildTypeDescription name='Conf Name' id='ProjectId_ConfId' copyAllAssociatedSettings='true'><sourceBuildType id='sourceConfId'/></newBuildTypeDescription>
   * @return the build configuration created
   */
  @POST
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  @ApiOperation(value="Add a build configuration to the matching project.",nickname="addBuildType")
  public BuildType createBuildType(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                   NewBuildTypeDescription descriptor,
                                   @QueryParam("fields") String fields) {
    @NotNull SProject project = myProjectFinder.getItem(projectLocator);
    SBuildType resultingBuildType;
    @Nullable final BuildTypeOrTemplate sourceBuildType = descriptor.getSourceBuildTypeOrTemplate(myServiceLocator);
    if (sourceBuildType == null) {
      resultingBuildType = project.createBuildType(descriptor.getId(myServiceLocator, project), descriptor.getName());
    } else {
      if (sourceBuildType.isBuildType()) {
        resultingBuildType =
          project.copyBuildType(sourceBuildType.getBuildType(), descriptor.getId(myServiceLocator, project), descriptor.getName(), descriptor.getCopyOptions());
      } else {
        throw new BadRequestException("Could not create build type as a copy of a template.");
      }
    }
    resultingBuildType.schedulePersisting("A new build configuration is created");
    return new BuildType(new BuildTypeOrTemplate(resultingBuildType),  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="serveBuildType",hidden=true)
  public BuildType serveBuildType(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                  @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                  @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);
    return new BuildType(new BuildTypeOrTemplate(buildType),  new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{projectLocator}/templates")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all templates of the matching project.",nickname="getProjectTemplates")
  public BuildTypes serveTemplatesInProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                            @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    return new BuildTypes(BuildTypes.fromTemplates(project.getOwnBuildTypeTemplates()), null, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{projectLocator}/templates")
  @Produces({"application/xml", "application/json"})
  @Consumes({"text/plain"})
  @ApiOperation(hidden = true, value = "Use createBuildTypeTemplate instead")
  public BuildType createEmptyBuildTypeTemplate(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                                String name,
                                                @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Build type template name cannot be empty.");
    }
    final BuildTypeTemplate buildType = project.createBuildTypeTemplate(name);
    buildType.schedulePersisting("A new build configuration template is created");
    return new BuildType(new BuildTypeOrTemplate(buildType),  new Fields(fields), myBeanContext);
  }

  /**
   * Creates a new build configuration template by copying existing one.
   *
   * @param projectLocator
   * @param descriptor     reference to the build configuration template to copy and copy options.
   *                       e.g. <newBuildTypeDescription name='Conf Name' id='ProjectId_ConfId' copyAllAssociatedSettings='true'><sourceBuildType id='sourceConfId'/></newBuildTypeDescription>
   * @return the build configuration created
   */
  @POST
  @Path("/{projectLocator}/templates")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  @ApiOperation(value="Add a build configuration template to the matching project.",nickname="addTemplate")
  public BuildType createBuildTypeTemplate(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                           NewBuildTypeDescription descriptor,
                                           @QueryParam("fields") String fields) {
    @NotNull SProject project = myProjectFinder.getItem(projectLocator, true);
    BuildTypeTemplate resultingBuildType;
    @Nullable final BuildTypeOrTemplate sourceBuildType = descriptor.getSourceBuildTypeOrTemplate(myServiceLocator);
    if (sourceBuildType == null) {
      resultingBuildType = project.createBuildTypeTemplate(descriptor.getId(myServiceLocator, project), descriptor.getName());
    } else {
      if (sourceBuildType.isBuildType()) {
        resultingBuildType =
          project.extractBuildTypeTemplate(sourceBuildType.getBuildType(), descriptor.getId(myServiceLocator, project), descriptor.getName());
      } else {
        resultingBuildType =
          project.copyBuildTypeTemplate(sourceBuildType.getTemplate(), descriptor.getId(myServiceLocator, project), descriptor.getName());
      }
    }
    resultingBuildType.schedulePersisting("A new build configuration template is created");
    return new BuildType(new BuildTypeOrTemplate(resultingBuildType),  new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{projectLocator}/templates/{btLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="serveBuildTypeTemplates",hidden=true)
  public BuildType serveBuildTypeTemplates(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                           @PathParam("btLocator") String buildTypeLocator,
                                           @QueryParam("fields") String fields) {
    BuildTypeTemplate buildType = myBuildTypeFinder.getBuildTemplate(myProjectFinder.getItem(projectLocator, true), buildTypeLocator, true);
    return new BuildType(new BuildTypeOrTemplate(buildType),  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/defaultTemplate")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get the default template of the matching project.",nickname="getDefaultTemplate")
  public BuildType getDefaultTemplate(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                      @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    BuildType result = Project.getDefaultTemplate(project, new Fields(fields), myBeanContext);
    if (result == null) throw new NotFoundException("No default template present");
    return result;
  }

  @PUT
  @Path("/{projectLocator}/defaultTemplate")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update the default template of the matching project.",nickname="setDefaultTemplate")
  public BuildType setDefaultTemplate(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                      BuildType defaultTemplate,
                                      @QueryParam("fields") String fields) {
    ProjectEx project = (ProjectEx)myProjectFinder.getItem(projectLocator, true);
    if (defaultTemplate == null) throw new BadRequestException("No payload found while template is expected");
    BuildTypeOrTemplate newDefaultTemplate = defaultTemplate.getBuildTypeFromPosted(myBuildTypeFinder);

    BuildTypeTemplate result = newDefaultTemplate.getTemplate();
    if (result == null) {
      throw new BadRequestException("Found build type when template is expected: " + LogUtil.describe(newDefaultTemplate.getBuildType()));
    }
    Boolean inherited = newDefaultTemplate.isInherited();
    BuildTypeTemplate currentDefaultTemplate = project.getDefaultTemplate();
    if (inherited == null || !inherited || (currentDefaultTemplate != null && !currentDefaultTemplate.getInternalId().equals(newDefaultTemplate.getInternalId()))) {
      try {
        project.setDefaultTemplate(result);
      } catch (CyclicDependencyFoundException e) {
        throw new BadRequestException(e.getMessage());
      }
      project.schedulePersisting("Default template changed");
    }
    BuildType template = Project.getDefaultTemplate(project, new Fields(fields), myBeanContext);
    if (template == null) throw new NotFoundException("No default template present");
    return template;
  }

  @DELETE
  @Path("/{projectLocator}/defaultTemplate")
  @ApiOperation(value="Remove the default template from the matching project.",nickname="removeDefaultTemplate")
  public void removeDefaultTemplate(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                    @QueryParam("fields") String fields) {
    ProjectEx project = (ProjectEx)myProjectFinder.getItem(projectLocator, true);
    if (project.getOwnDefaultTemplate() == null) throw new NotFoundException("No own default template present");
    project.setDefaultTemplate(null);
    project.schedulePersisting("Default template removed");
  }

  @Path("/{projectLocator}" + PARAMETERS)
  @ApiOperation(value="getParametersSubResource",nickname="getParametersSubResource")
  public TypedParametersSubResource getParametersSubResource(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator){
    SProject project = myProjectFinder.getItem(projectLocator, true);
    return new TypedParametersSubResource(myBeanContext, Project.createEntity(project), getParametersHref(project));
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="serveBuildTypeFieldWithProject",hidden=true)
  public String serveBuildTypeFieldWithProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                               @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                               @PathParam("field") String fieldName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);
    return buildType.getFieldValue(fieldName, myBeanContext);
  }

  //todo: separate methods to serve running builds

  /**
   * Serves builds matching supplied condition.
   *
   * @param locator           Build locator to filter builds
   * @param buildTypeLocator  Deprecated, use "locator" parameter instead
   * @param status            Deprecated, use "locator" parameter instead
   * @param userLocator       Deprecated, use "locator" parameter instead
   * @param includePersonal   Deprecated, use "locator" parameter instead
   * @param includeCanceled   Deprecated, use "locator" parameter instead
   * @param onlyPinned        Deprecated, use "locator" parameter instead
   * @param tags              Deprecated, use "locator" parameter instead
   * @param agentName         Deprecated, use "locator" parameter instead
   * @param sinceBuildLocator Deprecated, use "locator" parameter instead
   * @param sinceDate         Deprecated, use "locator" parameter instead
   * @param start             Deprecated, use "locator" parameter instead
   * @param count             Deprecated, use "locator" parameter instead, defaults to 100
   * @return
   */
  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="serveBuilds",hidden=true)
  public Builds serveBuilds(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                            @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                            @ApiParam(hidden = true) @QueryParam("status") String status,
                            @ApiParam(hidden = true) @QueryParam("triggeredByUser") String userLocator,
                            @ApiParam(hidden = true) @QueryParam("includePersonal") boolean includePersonal,
                            @ApiParam(hidden = true) @QueryParam("includeCanceled") boolean includeCanceled,
                            @ApiParam(hidden = true) @QueryParam("onlyPinned") boolean onlyPinned,
                            @ApiParam(hidden = true) @QueryParam("tag") List<String> tags,
                            @ApiParam(hidden = true) @QueryParam("agentName") String agentName,
                            @ApiParam(hidden = true) @QueryParam("sinceBuild") String sinceBuildLocator,
                            @ApiParam(hidden = true) @QueryParam("sinceDate") String sinceDate,
                            @ApiParam(hidden = true) @QueryParam("start") Long start,
                            @ApiParam(hidden = true) @QueryParam("count") Integer count,
                            @ApiParam(hidden = true) @QueryParam("locator") String locator,
                            @QueryParam("fields") String fields,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);
    return myBuildFinder.getBuildsForRequest(buildType, status, userLocator, includePersonal, includeCanceled, onlyPinned, tags, agentName,
                                             sinceBuildLocator, sinceDate, start, count, locator, "locator", uriInfo, request,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="serveBuildWithProject",hidden=true)
  public Build serveBuildWithProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                     @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                     @ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                     @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);

    return new Build(myBuildFinder.getBuildPromotion(buildType, buildLocator),  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="serveBuildFieldWithProject",hidden=true)
  public String serveBuildFieldWithProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                           @ApiParam(format = LocatorName.BUILD_TYPE) @PathParam("btLocator") String buildTypeLocator,
                                           @ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);
    return Build.getFieldValue(myBuildFinder.getBuildPromotion(buildType, buildLocator), field, myBeanContext);
  }

//todo: add vcs roots and others

  @Path("/{projectLocator}" + FEATURES)
  public ProjectFeatureSubResource getFeatures(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator) {
    final SProject project = myProjectFinder.getItem(projectLocator, true);
    return new ProjectFeatureSubResource(myBeanContext, new FeatureSubResource.Entity<PropEntitiesProjectFeature, PropEntityProjectFeature>() {

        @Override
        public String getHref() {
          return myBeanContext.getApiUrlBuilder().transformRelativePath(getFeaturesHref(project));
        }

        @Override
        public void persist() {
          project.schedulePersisting("Project features changed");
        }

        @NotNull
        @Override
        public PropEntityProjectFeature getSingle(@NotNull final String featureLocator, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
          final SProjectFeatureDescriptor projectFeature = PropEntityProjectFeature.getFeatureByLocator(project, featureLocator);
          return new PropEntityProjectFeature(project, projectFeature, fields, beanContext);
        }

        @Override
        public void delete(@NotNull final String featureLocator, @NotNull final ServiceLocator serviceLocator) {
          project.removeFeature(PropEntityProjectFeature.getFeatureByLocator(project, featureLocator).getId());
        }

        @NotNull
        @Override
        public String replace(@NotNull final String featureLocator, final @NotNull PropEntityProjectFeature newFeature, @NotNull final ServiceLocator serviceLocator) {
          return newFeature.replaceIn(project, PropEntityProjectFeature.getFeatureByLocator(project, featureLocator), serviceLocator).getId(); //todo: return id form the method!
        }

        @NotNull
        @Override
        public String add(@NotNull final PropEntityProjectFeature entityToAdd, @NotNull final ServiceLocator serviceLocator) {
          return entityToAdd.addTo(project, myServiceLocator).getId();
        }

        @NotNull
        @Override
        public PropEntitiesProjectFeature get(final String locator, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
          return new PropEntitiesProjectFeature(project, locator, fields, myBeanContext);
        }

        @Override
        public void replaceAll(@NotNull final PropEntitiesProjectFeature newEntities, @NotNull final ServiceLocator serviceLocator) {
          newEntities.setTo(project, serviceLocator);
        }

        @Override
        public ParametersPersistableEntity getParametersHolder(@NotNull final String featureLocator) {
          return new ProjectFeatureDescriptionUserParametersHolder(project, featureLocator);
        }

        //@Override
        //public String setSetting(@NotNull final String featureLocator, @NotNull final String settingName, @Nullable final String newValue) {
        //  return null;
        //}
        //
        //@Override
        //public String getSetting(@NotNull final String featureLocator, @NotNull final String settingName) {
        //  return null;
        //}
      });
  }

  @GET
  @Path("/{projectLocator}/parentProject")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get the parent project of the matching project.",nickname="getProjectParentProject")
  public Project getParentProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                  @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    final SProject actualParentProject = project.getParentProject();
    return actualParentProject == null
           ? null
           : new Project(actualParentProject,  new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{projectLocator}/parentProject")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  @ApiOperation(value="Update the parent project of the matching project.",nickname="setParentProject")
  public Project setParentProject(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                  Project parentProject,
                                  @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    project.moveToProject(parentProject.getProjectFromPosted(myProjectFinder));
    return new Project(project, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/agentPools")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get agent pools appointed to the matching project.",nickname="getAgentPoolsProject")
  public AgentPools getProjectAgentPools(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                         @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    return new AgentPools(myAgentPoolFinder.getPoolsForProject(project), null, new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{projectLocator}/agentPools/{agentPoolLocator}")
  @ApiOperation(value="Unassign a project from the matching agent pool.",nickname="removeProjectFromAgentPool")
  public void deleteProjectAgentPools(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                      @PathParam("agentPoolLocator") String agentPoolLocator) {
    SProject project = myProjectFinder.getItem(projectLocator);
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    try {
      agentPoolManager.dissociateProjectsFromPool(agentPoolId, singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      throw new BadRequestException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
  }

  @PUT
  @Path("/{projectLocator}/agentPools")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  @ApiOperation(value="Update agent pools apppointed to the matching project.",nickname="setAgentPoolsProject")
  public AgentPools setProjectAgentPools(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                         AgentPools pools,
                                         @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    myDataProvider.setProjectPools(project, pools.getPoolsFromPosted(myAgentPoolFinder));
    return new AgentPools(myAgentPoolFinder.getPoolsForProject(project), null, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{projectLocator}/agentPools")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  @ApiOperation(value="Assign the matching project to the agent pool.",nickname="addAgentPoolsProject")
  public AgentPool setProjectAgentPools(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                        AgentPool pool) {
    SProject project = myProjectFinder.getItem(projectLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPoolFromPosted = pool.getAgentPoolFromPosted(myAgentPoolFinder);
    final int agentPoolId = agentPoolFromPosted.getAgentPoolId();
    try {
      agentPoolManager.associateProjectsWithPool(agentPoolId, singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      throw new BadRequestException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
    return new AgentPool(agentPoolFromPosted, Fields.LONG, myBeanContext);
  }

  /**
   * Creates token for the value submitted. The token can then be used in the raw settings files to represent a secure value like password.
   * The kind of the token generated can depend on the project settings
   */
  @POST
  @Path("/{projectLocator}/secure/tokens")
  @Produces({"text/plain"})
  @Consumes({"text/plain"})
  @ApiOperation(value="Create a new secure token for the matching project.",nickname="addSecureToken")
  public String createSecureToken(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                  String secureValue) {
    SProject project = myProjectFinder.getItem(projectLocator);
    myPermissionChecker.checkProjectPermission(Permission.EDIT_PROJECT, project.getProjectId());
    return ((ProjectEx)project).getOrCreateToken(secureValue, "Requested via REST");
  }

  /* TeamCity API note:
    It is also worth supporting for tokens:
    check if token exists for the value: GET /{projectLocator}/secure/tokens/{secureValue}
    list tokens: GET /{projectLocator}/secure/tokens
    delete token and associated secure value: DELETE /{projectLocator}/secure/tokens/{token}
    (may be) update secure value for the specific token: PUT /{projectLocator}/secure/tokens/{token} - but this should probably be done for all the projects, tbd
   */

  /**
   * Experimental support only.
   */
  @GET
  @Path("/{projectLocator}/secure/values/{token}")
  @Produces({"text/plain"})
  @Consumes({"text/plain"})
  @ApiOperation(value="Get a secure token of the matching project.",nickname="getSecureValue")
  public String getSecureValue(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                               @PathParam("token") String token) {
    myPermissionChecker.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS); //checking global admin for now
    SProject project = myProjectFinder.getItem(projectLocator);
    return getSecureValueByToken(project, token);
  }

  @NotNull
  private synchronized String getSecureValueByToken(@NotNull final SProject project, String token) {
    // synchronized with timeout to reduce brute-forcing ability in case this will ever be exposed to non-server admins
    try {
      Thread.sleep(TeamCityProperties.getLong("rest.projectRequest.secureValueByTokenDelay", 5000));
    } catch (InterruptedException e) {
      //ignore
    }
    return ((ProjectEx)project).getSecureValue(token, "Requested via REST");
  }

  /**
   * Empty collection means no custom ordering
   */
  @GET
  @Path("/{projectLocator}/order/projects")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all subprojects of the matching project, with custom ordering applied.",nickname="getAllSubprojectsOrdered")
  public Projects getProjectsOrder(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                   @QueryParam("field") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    return new Projects(((ProjectEx)project).getOwnProjectsOrder(), null, new Fields(fields), myBeanContext);
  }

  /**
   * Put empty collection to remove custom ordering
   */
  @PUT
  @Path("/{projectLocator}/order/projects")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update custom ordering of subprojects of the matching project.",nickname="setSubprojectsOrder")
  public Projects setProjectsOrder(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                   Projects projects,
                                   @QueryParam("field") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    if (projects.projects != null) {
      for (Project postedProject : projects.projects) {
        final String locatorFromPosted = postedProject.getLocatorFromPosted();
        List<SProject> items = myProjectFinder.getItems(project, locatorFromPosted).myEntries;
        if (items.isEmpty()) {
          throw new BadRequestException("No direct sub-projects in project found by locator '" + locatorFromPosted + "'");
        }
        for (SProject item : items) {
          ids.add(item.getProjectId());
        }
      }
    }
    ((ProjectEx)project).setOwnProjectsOrder(new ArrayList<>(ids));

    return new Projects(((ProjectEx)project).getOwnProjectsOrder(), null, new Fields(fields), myBeanContext);
  }

  /**
   * Empty collection means no custom ordering
   */
  @GET
  @Path("/{projectLocator}/order/buildTypes")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all build configurations from the matching project, with custom ordering applied.", nickname="getAllBuildTypesOrdered")
  public BuildTypes getBuildTypesOrder(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                       @QueryParam("field") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    return new BuildTypes(BuildTypes.fromBuildTypes(((ProjectEx)project).getOwnBuildTypesOrder()), null, new Fields(fields), myBeanContext);
  }

  /**
   * Put empty collection to remove custom ordering
   */
  @PUT
  @Path("/{projectLocator}/order/buildTypes")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update custom ordering of build configurations of the matching project.",nickname="setBuildTypesOrder")
  public BuildTypes setBuildTypesOrder(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                       BuildTypes buildTypes,
                                       @QueryParam("field") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    if (buildTypes.buildTypes != null) {
      for (BuildType buildType : buildTypes.buildTypes) {
        String locatorFromPosted = buildType.getLocatorFromPosted();
        List<SBuildType> items = myBuildTypeFinder.getBuildTypes(project, locatorFromPosted);
        if (items.isEmpty()) {
          throw new BadRequestException("No build types in project found by locator '" + locatorFromPosted + "'");
        }
        for (SBuildType item : items) {
          ids.add(item.getInternalId());
        }
      }
    }
    ((ProjectEx)project).setOwnBuildTypesOrder(new ArrayList<>(ids));
    //see serveBuildTypesInProject()
    return new BuildTypes(BuildTypes.fromBuildTypes(((ProjectEx)project).getOwnBuildTypesOrder()), null, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental support only.
   * Lists branches from the build configurations of the project
   * @param branchesLocator experimental use only!
   * @return
   */
  @GET
  @Path("/{projectLocator}/branches")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all branches of the matching project.",nickname="getAllBranches")
  public Branches getBranches(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                              @QueryParam("locator") String branchesLocator,
                              @QueryParam("fields") String fieldsSpec) {
    final SProject project = myProjectFinder.getItem(projectLocator);
    String updatedBranchLocator = BranchFinder.patchLocatorWithBuildType(branchesLocator, BuildTypeFinder.patchLocator(null, project));
    Fields fields = new Fields(fieldsSpec);
    return new Branches(myBranchFinder.getItems(updatedBranchLocator).myEntries, null, fields, myBeanContext);
  }

  /**
   * Experimental support
   */
  @GET
  @Path("/{projectLocator}/branches/{branchLocator}/exists")
  @Produces("text/plain")
  @ApiOperation(value="Check if exists branch satisfying given locator in the matching build configuration.", nickname="checkIfBranchExists", hidden = true)
  public String checkIfBranchExists(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                    @ApiParam(format = LocatorName.BRANCH) @PathParam("branchLocator") String branchLocator) {
    final SProject project = myProjectFinder.getItem(projectLocator);
    String updatedBranchLocator = BranchFinder.patchLocatorWithBuildType(branchLocator, BuildTypeFinder.patchLocator(null, project));

    return Boolean.toString(myBranchFinder.itemsExist(new Locator(updatedBranchLocator)));
  }

  /**
   * For compatibility with experimental feature of 8.0
   */
  @GET
  @ApiOperation(value = "getExampleNewProjectDescriptionCompatibilityVersion", hidden = true)
  @Path("/{projectLocator}/newProjectDescription")
  @Produces({"application/xml", "application/json"})
  public NewProjectDescription getExampleNewProjectDescriptionCompatibilityVersion(@PathParam("projectLocator") String projectLocator, @QueryParam("id") String newId) {
    return getExampleNewProjectDescription(projectLocator, newId);
  }

  /**
   * Experimental support only.
   * Use this to get an example of the bean to be posted to the /projects request to create a new project
   *
   * @param projectLocator
   * @return
   */
  @GET
  @ApiOperation(value = "getExampleNewProjectDescription", hidden = true)
  @Path("/{projectLocator}/example/newProjectDescription")
  @Produces({"application/xml", "application/json"})
  public NewProjectDescription getExampleNewProjectDescription(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                                               @QueryParam("id") String newId) {
    final SProject project = myProjectFinder.getItem(projectLocator);
    final SProject parentProject = project.getParentProject();
    final Project parentProjectRef =
      parentProject != null ? new Project(parentProject, Fields.SHORT, myBeanContext) : null;
    @NotNull final String newNotEmptyId = StringUtil.isEmpty(newId) ? project.getExternalId() : newId;
    final ProjectManagerEx.IdsMaps idsMaps =
      ((ProjectManagerEx)myDataProvider.getServer().getProjectManager()).generateDefaultExternalIds(project, newNotEmptyId, ID_GENERATION_FLAG, true);
    final Map<String, String> projectIdsMap = idsMaps.getProjectIdsMap();
    projectIdsMap.remove(project.getExternalId()); // remove ptoject's own id to make the object more clean
    return new NewProjectDescription(project.getName(), newNotEmptyId, new Project(project, Fields.SHORT, myBeanContext),
                                     parentProjectRef, true,
                                     getNullOrCollection(projectIdsMap),
                                     getNullOrCollection(idsMaps.getBuildTypeIdsMap()),
                                     getNullOrCollection(idsMaps.getVcsRootIdsMap()),
                                     myBeanContext);
  }


  /**
   * Experimental support only
   */
  @GET
  @Path("/{projectLocator}/settingsFile")
  @Produces({"text/plain"})
  @ApiOperation(value="Get the settings file of the matching build configuration.",nickname="getProjectSettingsFile")
  public String getSettingsFile(@ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator) {
    myPermissionChecker.checkGlobalPermission(Permission.VIEW_SERVER_SETTINGS);
    final SProject project = myProjectFinder.getItem(projectLocator);
    return project.getConfigurationFile().getAbsolutePath();
  }

  /**
   * Adds new SSH key to the specific project.
   *
   * @since 2022
   */
  @POST
  @Path("/{projectLocator}/sshKeys")
  @Consumes({"text/plain" /* TODO check mime type */})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Upload ssh key", hidden = false)
  public void addSshKey(
    @ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator")
    String projectLocator,
    @ApiParam @QueryParam("fileName")
    String fileName,
    @Context
    HttpServletRequest request
  ) throws IOException {
    ConfigAction configAction = myConfigActionFactory.createAction("New SSH key uploaded");

    StandardServletPartUtils.getParts(request);

    byte[] privateKey = IOUtils.toByteArray(request.getInputStream());

    if (privateKey == null) {
      throw new BadRequestException("No private key file in request");
    }

    try {
      validateKey(privateKey);
    } catch (Exception e) {
      throw new BadRequestException("Invalid key file: " + e.getCause(), e);
    }

    SProject project = myProjectFinder.getItem(projectLocator);

    myServerSshKeyManager.addKey(project, fileName, privateKey, configAction);
  }

  /**
   * Adds new SSH key to the specific project.
   *
   * @since 2022
   */
  @GET
  @Path("/{projectLocator}/sshKeys")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "List ssh keys")
  public SshKeys getSshKeys(
    @ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator")
    String projectLocator,
    @ApiParam @QueryParam("fileName")
    String fileName, // TODO remove?
    @Context
    HttpServletRequest request
  ) {
    SProject project = myProjectFinder.getItem(projectLocator);

    List<TeamCitySshKey> keys = myServerSshKeyManager.getKeys(project);

    if (keys == null) {
      return new SshKeys();
    }

    List<SshKey> sshKeys = keys.stream().map(key -> {
      SshKey sshKey = new SshKey();
      sshKey.setName(key.getName());
      sshKey.setEncrypted(key.isEncrypted());
      return sshKey;
    }).collect(Collectors.toList());

    SshKeys result = new SshKeys();
    result.setSshKeys(sshKeys);
    return result;
  }

  private void validateKey(byte[] privateKey) {
    if (privateKey == null || privateKey.length == 0) {
      throw new IllegalArgumentException("Empty key file");
    }
    // TODO @vshefer validate key file
  }

  /**
   * Experimental use only!
   */
  //until @Path("/{projectLocator}/loadingErrors") is implemented
  @GET
  @Path("/{projectLocator}/latest")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "reloadSettingsFile", hidden = true)
  public Project reloadSettingsFile(
    @ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
    @QueryParam("fields") String fields
  ) {
    myPermissionChecker.checkGlobalPermission(Permission.MANAGE_SERVER_INSTALLATION);

    try {
      SProject project = myProjectFinder.getItem(projectLocator);
      String projectConfigFile = project.getConfigurationFile().getAbsolutePath();
      myBeanContext.getSingletonService(ProjectsLoader.class).reloadProjects(emptyList(), Collections.singleton(new File(projectConfigFile)), emptyList());
    } catch (NotFoundException e) {
      //this server doesn't see this project
      if (!projectLocator.contains("id:")) {
        throw e;
      }
      File projectsDir = myServiceLocator.getSingletonService(ServerPaths.class).getProjectsDir();
      File projectDir = new File(projectsDir, projectLocator.substring(projectLocator.indexOf("id:") + 3));
      String projectConfigFile = new File(projectDir, XmlConstants.PROJECT_CONFIG_FILENAME).getAbsolutePath();
      myBeanContext.getSingletonService(ProjectsLoader.class).reloadProjects(singleton(new File(projectConfigFile)), emptyList(), emptyList());
    }

    return new Project(myProjectFinder.getItem(projectLocator), new Fields(fields), myBeanContext);
  }

  @Nullable
  private static Map<String, String> getNullOrCollection(final @NotNull Map<String, String> map) {
    return map.size() > 0 ? map : null;
  }

  private static class ProjectFeatureDescriptionUserParametersHolder extends MapBackedEntityWithModifiableParameters implements ParametersPersistableEntity {
    @NotNull private final SProject myProject;

    public ProjectFeatureDescriptionUserParametersHolder(@NotNull final SProject project, @NotNull final String featureLocator) {
      super(new PropProxy() {
        @Override
        public Map<String, String> get() {
          return getFeature().getParameters();
        }

        @Override
        public void set(final Map<String, String> params) {
          SProjectFeatureDescriptor feature = getFeature();
          project.updateFeature(feature.getId(), feature.getType(), params);

        }

        @NotNull
        private SProjectFeatureDescriptor getFeature() {
          return PropEntityProjectFeature.getFeatureByLocator(project, featureLocator);
        }
      });
      myProject = project;
    }

    @Override
    public void persist(@NotNull String description) {
      myProject.schedulePersisting(description);
    }
  }


  @NotNull
  private static <T> T firstNonNull(T... values) {
    for (T value : values) {
      if (value != null) {
        return value;
      }
    }
    throw new NoSuchElementException("All values were null");
  }
}
