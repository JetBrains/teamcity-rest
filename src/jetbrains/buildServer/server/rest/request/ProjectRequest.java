/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.agent.AgentPools;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.buildType.NewBuildTypeDescription;
import jetbrains.buildServer.server.rest.model.project.NewProjectDescription;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentPools.NoSuchAgentPoolException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.identifiers.DuplicateExternalIdException;
import jetbrains.buildServer.serverSide.impl.projects.ProjectsLoader;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
 * User: Yegor Yarko
 * Date: 11.04.2009
 */
@Path(ProjectRequest.API_PROJECTS_URL)
@Api
public class ProjectRequest {
  private static final Logger LOG = Logger.getInstance(ProjectRequest.class.getName());
  public static final boolean ID_GENERATION_FLAG = true;

  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private AgentPoolsFinder myAgentPoolsFinder;

  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull public PermissionChecker myPermissionChecker;

  public static final String API_PROJECTS_URL = Constants.API_URL + "/projects";
  protected static final String PARAMETERS = BuildTypeRequest.PARAMETERS;

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

  @GET
  @Produces({"application/xml", "application/json"})
  public Projects serveProjects(@QueryParam("locator") String locator, @QueryParam("fields") String fields,
                                @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<SProject> result = myProjectFinder.getItems(locator);
    final PagerData pager = new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator");
    return new Projects(result.myEntries, pager, new Fields(fields), myBeanContext);
  }

  @POST
  @Consumes({"text/plain"})
  @Produces({"application/xml", "application/json"})
  public Project createEmptyProject(String name) {
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Project name cannot be empty.");
    }
    final SProject project = myDataProvider.getServer().getProjectManager().createProject(name);
    project.persist();
    return new Project(project, Fields.LONG, myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
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
      } catch (InvalidIdentifierException e) {
        processCreatiedProjectFinalizationError(resultingProject, projectManager, e);
      } catch (DuplicateExternalIdException e) {
        processCreatiedProjectFinalizationError(resultingProject, projectManager, e);
      }
    }

    try {
      resultingProject.persist();
    } catch (PersistFailedException e) {
      processCreatiedProjectFinalizationError(resultingProject, projectManager, e);
    }
    return new Project(resultingProject, Fields.LONG, myBeanContext);
  }

  private void processCreatiedProjectFinalizationError(final SProject resultingProject, final ProjectManager projectManager, final Exception e) {
    try {
      projectManager.removeProject(resultingProject.getProjectId());
    } catch (ProjectRemoveFailedException e1) {
      LOG.warn("Rallback of project creation failed", e1);
      //ignore
    }
    throw new InvalidStateException("Error during project creation finalization", e);
  }

  @GET
  @Path("/{projectLocator}")
  @Produces({"application/xml", "application/json"})
  public Project serveProject(@PathParam("projectLocator") String projectLocator, @QueryParam("fields") String fields) {
    return new Project(myProjectFinder.getItem(projectLocator),  new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{projectLocator}")
  public void deleteProject(@PathParam("projectLocator") String projectLocator) {
    final SProject project = myProjectFinder.getItem(projectLocator);
    myDataProvider.getServer().getProjectManager().removeProject(project.getProjectId());
  }

  @GET
  @Path("/{projectLocator}/{field}")
  @Produces("text/plain")
  public String serveProjectField(@PathParam("projectLocator") String projectLocator, @PathParam("field") String fieldName) {
    return Project.getFieldValue(myProjectFinder.getItem(projectLocator), fieldName);
  }

  @PUT
  @Path("/{projectLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setProjectFiled(@PathParam("projectLocator") String projectLocator, @PathParam("field") String fieldName, String newValue) {
    final SProject project = myProjectFinder.getItem(projectLocator);
    Project.setFieldValueAndPersist(project, fieldName, newValue, myServiceLocator);
    return Project.getFieldValue(project, fieldName);
  }

  @GET
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveBuildTypesInProject(@PathParam("projectLocator") String projectLocator, @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    return new BuildTypes(BuildTypes.fromBuildTypes(project.getOwnBuildTypes()), null, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{projectLocator}/buildTypes")
  @Produces({"application/xml", "application/json"})
  @Consumes({"text/plain"})
  public BuildType createEmptyBuildType(@PathParam("projectLocator") String projectLocator, String name, @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Build type name cannot be empty.");
    }
    final SBuildType buildType = project.createBuildType(name);
    buildType.persist();
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
  public BuildType createBuildType(@PathParam("projectLocator") String projectLocator, NewBuildTypeDescription descriptor, @QueryParam("fields") String fields) {
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
    resultingBuildType.persist();
    return new BuildType(new BuildTypeOrTemplate(resultingBuildType),  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildType(@PathParam("projectLocator") String projectLocator, @PathParam("btLocator") String buildTypeLocator, @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);
    return new BuildType(new BuildTypeOrTemplate(buildType),  new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{projectLocator}/templates")
  @Produces({"application/xml", "application/json"})
  public BuildTypes serveTemplatesInProject(@PathParam("projectLocator") String projectLocator, @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    return new BuildTypes(BuildTypes.fromTemplates(project.getOwnBuildTypeTemplates()), null, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{projectLocator}/templates")
  @Produces({"application/xml", "application/json"})
  @Consumes({"text/plain"})
  public BuildType createEmptyBuildTypeTemplate(@PathParam("projectLocator") String projectLocator, String name, @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator, true);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Build type template name cannot be empty.");
    }
    final BuildTypeTemplate buildType = project.createBuildTypeTemplate(name);
    buildType.persist();
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
  public BuildType createBuildTypeTemplate(@PathParam("projectLocator") String projectLocator, NewBuildTypeDescription descriptor, @QueryParam("fields") String fields) {
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
    resultingBuildType.persist();
    return new BuildType(new BuildTypeOrTemplate(resultingBuildType),  new Fields(fields), myBeanContext);
  }


  @GET
  @Path("/{projectLocator}/templates/{btLocator}")
  @Produces({"application/xml", "application/json"})
  public BuildType serveBuildTypeTemplates(@PathParam("projectLocator") String projectLocator, @PathParam("btLocator") String buildTypeLocator, @QueryParam("fields") String fields) {
    BuildTypeTemplate buildType = myBuildTypeFinder.getBuildTemplate(myProjectFinder.getItem(projectLocator, true), buildTypeLocator, true);
    return new BuildType(new BuildTypeOrTemplate(buildType),  new Fields(fields), myBeanContext);
  }

  @Path("/{projectLocator}" + PARAMETERS)
  public ParametersSubResource getParametersSubResource(@PathParam("projectLocator") String projectLocator){
    SProject project = myProjectFinder.getItem(projectLocator, true);
    return new ParametersSubResource(myServiceLocator, new ParametersSubResource.ProjectEntityWithParameters(project), getParametersHref(project));
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildTypeFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                               @PathParam("btLocator") String buildTypeLocator,
                                               @PathParam("field") String fieldName) {
    BuildTypeOrTemplate buildType = myBuildTypeFinder.getBuildTypeOrTemplate(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);
    return buildType.getFieldValue(fieldName);
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
  public Builds serveBuilds(@PathParam("projectLocator") String projectLocator,
                            @PathParam("btLocator") String buildTypeLocator,
                            @QueryParam("status") String status,
                            @QueryParam("triggeredByUser") String userLocator,
                            @QueryParam("includePersonal") boolean includePersonal,
                            @QueryParam("includeCanceled") boolean includeCanceled,
                            @QueryParam("onlyPinned") boolean onlyPinned,
                            @QueryParam("tag") List<String> tags,
                            @QueryParam("agentName") String agentName,
                            @QueryParam("sinceBuild") String sinceBuildLocator,
                            @QueryParam("sinceDate") String sinceDate,
                            @QueryParam("start") Long start,
                            @QueryParam("count") Integer count,
                            @QueryParam("locator") String locator,
                            @QueryParam("fields") String fields,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);
    return myBuildFinder.getBuildsForRequest(buildType, status, userLocator, includePersonal, includeCanceled, onlyPinned, tags, agentName,
                                             sinceBuildLocator, sinceDate, start, count, locator, "locator", uriInfo, request,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build serveBuildWithProject(@PathParam("projectLocator") String projectLocator,
                                     @PathParam("btLocator") String buildTypeLocator,
                                     @PathParam("buildLocator") String buildLocator,
                                     @QueryParam("fields") String fields) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);

    return new Build(myBuildFinder.getBuildPromotion(buildType, buildLocator),  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/buildTypes/{btLocator}/builds/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldWithProject(@PathParam("projectLocator") String projectLocator,
                                           @PathParam("btLocator") String buildTypeLocator,
                                           @PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SBuildType buildType = myBuildTypeFinder.getBuildType(myProjectFinder.getItem(projectLocator), buildTypeLocator, false);
    return Build.getFieldValue(myBuildFinder.getBuildPromotion(buildType, buildLocator), field, myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/parentProject")
  @Produces({"application/xml", "application/json"})
  public Project getParentProject(@PathParam("projectLocator") String projectLocator, @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    final SProject actulParentProject = project.getParentProject();
    return actulParentProject == null
           ? null
           : new Project(actulParentProject,  new Fields(fields), myBeanContext);
  }

  @PUT
  @Path("/{projectLocator}/parentProject")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  public Project setParentProject(@PathParam("projectLocator") String projectLocator, Project parentProject) {
    SProject project = myProjectFinder.getItem(projectLocator);
    project.moveToProject(parentProject.getProjectFromPosted(myProjectFinder));
    project.persist();
    return new Project(project, Fields.LONG, myBeanContext);
  }

  @GET
  @Path("/{projectLocator}/agentPools")
  @Produces({"application/xml", "application/json"})
  public AgentPools getProjectAgentPools(@PathParam("projectLocator") String projectLocator, @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    return new AgentPools(myAgentPoolsFinder.getPoolsForProject(project), null, new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{projectLocator}/agentPools/{agentPoolLocator}")
  public void deleteProjectAgentPools(@PathParam("projectLocator") String projectLocator, @PathParam("agentPoolLocator") String agentPoolLocator) {
    SProject project = myProjectFinder.getItem(projectLocator);
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    try {
      agentPoolManager.dissociateProjectsFromPool(agentPoolId, Collections.singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      throw new IllegalStateException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
  }

  @PUT
  @Path("/{projectLocator}/agentPools")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  public AgentPools setProjectAgentPools(@PathParam("projectLocator") String projectLocator, AgentPools pools, @QueryParam("fields") String fields) {
    SProject project = myProjectFinder.getItem(projectLocator);
    myDataProvider.setProjectPools(project, pools.getPoolsFromPosted(myAgentPoolsFinder));
    return new AgentPools(myAgentPoolsFinder.getPoolsForProject(project), null, new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{projectLocator}/agentPools")
  @Produces({"application/xml", "application/json"})
  @Consumes({"application/xml", "application/json"})
  public AgentPool setProjectAgentPools(@PathParam("projectLocator") String projectLocator, AgentPool pool) {
    SProject project = myProjectFinder.getItem(projectLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPoolFromPosted = pool.getAgentPoolFromPosted(myAgentPoolsFinder);
    final int agentPoolId = agentPoolFromPosted.getAgentPoolId();
    try {
      agentPoolManager.associateProjectsWithPool(agentPoolId, Collections.singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      throw new IllegalStateException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
    return new AgentPool(agentPoolFromPosted, Fields.LONG, myBeanContext);
  }

  /**
   * For compatibility with experimental feature of 8.0
   */
  @GET
  @Path("/{projectLocator}/newProjectDescription")
  @Produces({"application/xml", "application/json"})
  public NewProjectDescription getExampleNewProjectDescriptionCompatibilityVersion1(@PathParam("projectLocator") String projectLocator, @QueryParam("id") String newId) {
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
  @Path("/{projectLocator}/example/newProjectDescription")
  @Produces({"application/xml", "application/json"})
  public NewProjectDescription getExampleNewProjectDescription(@PathParam("projectLocator") String projectLocator, @QueryParam("id") String newId) {
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
                                     getNullOrCollection(idsMaps.getVcsRootIdsMap()));
  }


  /**
   * Experimental support only
   */
  @GET
  @Path("/{projectLocator}/settingsFile")
  @Produces({"text/plain"})
  public String getSettingsFile(@PathParam("projectLocator") String projectLocator) {
    myPermissionChecker.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    final SProject project = myProjectFinder.getItem(projectLocator);
    return project.getConfigurationFile().getAbsolutePath();
  }

  /**
   * Experimental use only!
   */
  //until @Path("/{projectLocator}/loadingErrors") is implemented
  @GET
  @Path("/{projectLocator}/latest")
  public Project reloadSettingsFile (@PathParam("projectLocator") String projectLocator, @QueryParam("fields") String fields) {
    myPermissionChecker.checkGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
    final SProject project = myProjectFinder.getItem(projectLocator);
    final String projectConfigFile = project.getConfigurationFile().getAbsolutePath();
    final List<File> emptyList = Collections.<File>emptyList();
    myBeanContext.getSingletonService(ProjectsLoader.class).reloadProjects(emptyList, Collections.singleton(new File(projectConfigFile)), emptyList);
    return new Project(myProjectFinder.getItem(projectLocator), new Fields(fields), myBeanContext);
  }

  @Nullable
  private Map<String, String> getNullOrCollection(final @NotNull Map<String, String> map) {
    return map.size() > 0 ? map : null;
  }
}
