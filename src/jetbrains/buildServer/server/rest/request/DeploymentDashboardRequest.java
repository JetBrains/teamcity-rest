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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.finder.impl.AgentFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.agent.AgentPools;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.*;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@Path(DeploymentDashboardRequest.API_DEPLOYMENT_DASHBOARDS_URL)
@Api("DeploymentDashboard")
public class DeploymentDashboardRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private DeploymentDashboardFinder myDeploymentDashboardFinder;

  public static final String API_DEPLOYMENT_DASHBOARDS_URL = Constants.API_URL + "/deploymentDashboards";

  public static String getHref() {
    return API_DEPLOYMENT_DASHBOARDS_URL;
  }

  public static String getAgentPoolHref(@NotNull final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPoolgent) {
    return API_DEPLOYMENT_DASHBOARDS_URL + "/id:" + agentPoolgent.getAgentPoolId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all agent pools.",nickname="getAllAgentPools")
  public AgentPools getPools(@ApiParam(format = LocatorName.AGENT_POOL) @QueryParam("locator") String locator,
                             @QueryParam("fields") String fields,
                             @Context UriInfo uriInfo,
                             @Context HttpServletRequest request) {
    PagedSearchResult<jetbrains.buildServer.serverSide.agentPools.AgentPool> result = myAgentPoolFinder.getItems(locator);
    final PagerData pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator");
    return new AgentPools(result.getEntries(), pager, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{agentPoolLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get the agent pool matching the locator.",nickname="getAgentPool")
  public AgentPool getPool(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                           @QueryParam("fields") String fields) {
    return new AgentPool(myAgentPoolFinder.getItem(agentPoolLocator), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{agentPoolLocator}")
  @ApiOperation(value="Delete the agent pool matching the locator.",nickname="deleteAgentPool")
  public void deletePool(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    try {
      myServiceLocator.getSingletonService(AgentPoolManager.class).deleteAgentPool(agentPool.getAgentPoolId());
    } catch (NoSuchAgentPoolException e) {
      throw new BadRequestException("Agent pool with id \'" + agentPool.getAgentPoolId() + "' does not exist.", e);
    } catch (AgentPoolCannotBeDeletedException e) {
      throw new BadRequestException("Cannot delete agent pool with id \'" + agentPool.getAgentPoolId() + "'.", e);
    }
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Create a new agent pool.",nickname="createAgentPool")
  public AgentPool createPool(AgentPool agentPool) {
    if (agentPool.agents != null){
      throw new BadRequestException("Creating an agent pool with agents is not supported. Please add agents after the pool creation");
    }
    final jetbrains.buildServer.serverSide.agentPools.AgentPool newAgentPool;
    try {
      AgentPoolLimits agentDetails = AgentPoolLimits.DEFAULT;
      if (agentPool.maxAgents != null) {
        agentDetails = new AgentPoolLimitsImpl(AgentPoolLimits.DEFAULT.getMinAgents(),
                                                agentPool.maxAgents != null ? Integer.valueOf(agentPool.maxAgents) : AgentPoolLimits.DEFAULT.getMaxAgents());
      }
      newAgentPool = myServiceLocator.getSingletonService(AgentPoolManager.class).createNewAgentPool(agentPool.name, agentDetails);
    } catch (AgentPoolCannotBeRenamedException e) {
      throw new BadRequestException("Agent pool with name \'" + agentPool.name + "' already exists.");
    }
    if (agentPool.projects != null){
      replaceProjects("id:" + newAgentPool, agentPool.projects);
    }
    return new AgentPool(newAgentPool, Fields.LONG, myBeanContext);
  }

  @GET
  @Path("/{agentPoolLocator}/projects")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all projects of the matching agent pool.",nickname="getAllProjectsFromAgentPool")
  public Projects getPoolProjects(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                                  @QueryParam("fields") String fields) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    return new Projects(myAgentPoolFinder.getPoolProjects(agentPool), null, new Fields(fields), myBeanContext);
  }

  /**
   * Associates the posted set of projects with the pool which replaces earlier associations on this pool
   * @param agentPoolLocator
   * @param projects
   * @return
   */
  @PUT
  @Path("/{agentPoolLocator}/projects")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update projects of the matching agent pool.",nickname="setAgentPoolProjects")
  public Projects replaceProjects(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                                  Projects projects) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    final List<SProject> projectsList = projects.getProjectsFromPosted(myProjectFinder);
    final Set<String> projectIds = new HashSet<String>(projectsList.size());
    for (SProject project : projectsList) {
      projectIds.add(project.getProjectId());
    }
    try {
      agentPoolManager.dissociateProjectsFromPool(agentPoolId, agentPoolManager.getPoolProjects(agentPoolId));
      agentPoolManager.associateProjectsWithPool(agentPoolId, projectIds);
    } catch (NoSuchAgentPoolException e) {
      throw new BadRequestException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
    return new Projects(myAgentPoolFinder.getPoolProjects(agentPool), null, Fields.LONG, myBeanContext);
  }

  /**
   * Adds the posted project to the pool associated projects
   * @param agentPoolLocator
   * @param projects
   * @return
   */
  @POST
  @Path("/{agentPoolLocator}/projects")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Assign the project to the matching agent pool.",nickname="addProjectToAgentPool")
  public Project addProject(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                            Project project) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    final SProject postedProject = project.getProjectFromPosted(myProjectFinder);
    try {
      agentPoolManager.associateProjectsWithPool(agentPoolId, Collections.singleton(postedProject.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      throw new BadRequestException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
    return new Project(postedProject, Fields.LONG, myBeanContext);
  }

  /**
   * Removed all the projects from the pool
   */
  @DELETE
  @Path("/{agentPoolLocator}/projects")
  @ApiOperation(value="Unassign all projects from the matching agent pool.",nickname="deleteAllProjectsFromAgentPool")
  public void deleteProjects(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    try {
      agentPoolManager.dissociateProjectsFromPool(agentPoolId, agentPoolManager.getPoolProjects(agentPoolId));
    } catch (NoSuchAgentPoolException e) {
      throw new BadRequestException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
  }

  @GET
  @Path("/{agentPoolLocator}/projects/{projectLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="getPoolProject",hidden=true)
  public Project getPoolProject(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                                @ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator,
                                @QueryParam("fields") String fields) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    final SProject project = myProjectFinder.getItem(projectLocator);
    return new Project(project,  new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{agentPoolLocator}/projects/{projectLocator}")
  @ApiOperation(value="Unassign the project from the matching agent pool.",nickname="deleteProjectFromAgentPool")
  public void deletePoolProject(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                                @ApiParam(format = LocatorName.PROJECT) @PathParam("projectLocator") String projectLocator) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    final SProject project = myProjectFinder.getItem(projectLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    try {
      agentPoolManager.dissociateProjectsFromPool(agentPoolId, Collections.singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      throw new BadRequestException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
  }

  @GET
  @Path("/{agentPoolLocator}/agents")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get the agent of the matching agent pool.",nickname="getAllAgentsFromAgentPool")
  public Agents getPoolAgents(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                              @ApiParam(format = LocatorName.AGENT) @QueryParam("locator") String locator,
                              @QueryParam("fields") String fields) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    return new Agents(Locator.merge(AgentFinder.getLocator(agentPool), locator), null, new Fields(fields), myBeanContext);
  }

  /**
   * Moves the agent posted to the pool
   * @param agentPoolLocator
   * @param agentRef
   * @return
   */
  @POST
  @Path("/{agentPoolLocator}/agents")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Assign the agent to the matching agent pool.",nickname="addAgentToAgentPool")
  public Agent addAgent(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                        Agent agent,
                        @QueryParam("fields") String fields) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    myDataProvider.addAgentToPool(agentPool, agent.getAgentTypeIdFromPosted(myServiceLocator));
    try {
      return new Agent(agent.getAgentFromPosted(myAgentFinder), new Fields(fields), myBeanContext);
    } catch (Exception e) {
      //ignore for agent type
      return null;
    }
  }

  @GET
  @Path("/{agentPoolLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="Get a field of the matching agent pool.",nickname="getFieldFromAgentPool")
  public String getField(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                         @PathParam("field") String fieldName) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    return AgentPool.getFieldValue(agentPool, fieldName);
  }

  @PUT
  @Path("/{agentPoolLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(value="Update a field of the matching agent pool.",nickname="setAgentPoolField")
  public String setField(@ApiParam(format = LocatorName.AGENT_POOL) @PathParam("agentPoolLocator") String agentPoolLocator,
                         @PathParam("field") String fieldName,
                         String newValue) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolFinder.getItem(agentPoolLocator);
    AgentPool.setFieldValue(agentPool, fieldName, newValue, myBeanContext);
    return AgentPool.getFieldValue(myAgentPoolFinder.getItem(agentPoolLocator), fieldName); //need to find the pool again to get a refreshed version
  }
}

