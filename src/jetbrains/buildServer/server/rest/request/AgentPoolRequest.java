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

import com.intellij.util.containers.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.data.AgentPoolsFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.agent.AgentPools;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolCannotBeDeletedException;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolCannotBeRenamedException;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentPools.NoSuchAgentPoolException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@Path(AgentPoolRequest.API_AGENT_POOLS_URL)
public class AgentPoolRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private AgentPoolsFinder myAgentPoolsFinder;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private AgentFinder myAgentFinder;

  public static final String API_AGENT_POOLS_URL = Constants.API_URL + "/agentPools";

  public static String getHref() {
    return API_AGENT_POOLS_URL;
  }

  public static String getAgentPoolHref(@NotNull final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPoolgent) {
    return API_AGENT_POOLS_URL + "/id:" + agentPoolgent.getAgentPoolId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public AgentPools getPools() {
    return new AgentPools(myServiceLocator.getSingletonService(AgentPoolManager.class).getAllAgentPools(), myApiUrlBuilder);
  }

  @GET
  @Path("/{agentPoolLocator}")
  @Produces({"application/xml", "application/json"})
  public AgentPool getPool(@PathParam("agentPoolLocator") String agentPoolLocator, @QueryParam("fields") String fields) {
    return new AgentPool(myAgentPoolsFinder.getAgentPool(agentPoolLocator), new Fields(fields, Fields.DEFAULT_FIELDS),
                         new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder));
  }

  @DELETE
  @Path("/{agentPoolLocator}")
  public void deletePool(@PathParam("agentPoolLocator") String agentPoolLocator) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
    try {
      myServiceLocator.getSingletonService(AgentPoolManager.class).deleteAgentPool(agentPool.getAgentPoolId());
    } catch (NoSuchAgentPoolException e) {
      throw new IllegalStateException("Agent pool with id \'" + agentPool.getAgentPoolId() + "' does not exist.", e);
    } catch (AgentPoolCannotBeDeletedException e) {
      throw new IllegalStateException("Cannot delete agent pool with id \'" + agentPool.getAgentPoolId() + "'.", e);
    }
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public AgentPool setPools(AgentPool agentPool) {
    if (agentPool.agents != null){
      throw new BadRequestException("Creating an agent pool with agents is not suppotrted. Please add agents after the pool creation");
    }
    int newAgentPoolId;
    try {
      newAgentPoolId = myServiceLocator.getSingletonService(AgentPoolManager.class).createNewAgentPool(agentPool.name);
    } catch (AgentPoolCannotBeRenamedException e) {
      throw new IllegalStateException("Agent pool with name \'" + agentPool.name + "' already exists.");
    }
    if (agentPool.projects != null){
      replaceProjects("id:" + newAgentPoolId, agentPool.projects);
    }
    return new AgentPool(myAgentPoolsFinder.getAgentPoolById(newAgentPoolId), Fields.DEFAULT_FIELDS,
                         new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{agentPoolLocator}/projects")
  @Produces({"application/xml", "application/json"})
  public Projects getPoolProjects(@PathParam("agentPoolLocator") String agentPoolLocator, @QueryParam("fields") String fields) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
    return new Projects(myAgentPoolsFinder.getPoolProjects(agentPool), new Fields(fields, Fields.DEFAULT_FIELDS),
                        new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder));
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
  public Projects replaceProjects(@PathParam("agentPoolLocator") String agentPoolLocator, Projects projects) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
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
      throw new IllegalStateException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
    return new Projects(myAgentPoolsFinder.getPoolProjects(agentPool), Fields.DEFAULT_FIELDS, new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder));
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
  public Project addProject(@PathParam("agentPoolLocator") String agentPoolLocator, Project project) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    final SProject postedProject = project.getProjectFromPosted(myProjectFinder);
    try {
      agentPoolManager.associateProjectsWithPool(agentPoolId, Collections.singleton(postedProject.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      throw new IllegalStateException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
    return new Project(postedProject, Fields.DEFAULT_FIELDS, new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{agentPoolLocator}/projects/{projectLocator}")
  @Produces({"application/xml", "application/json"})
  public Project getPoolProject(@PathParam("agentPoolLocator") String agentPoolLocator, @PathParam("projectLocator") String projectLocator, @QueryParam("fields") String fields) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
    final SProject project = myProjectFinder.getProject(projectLocator);
    return new Project(project, new Fields(fields, Fields.DEFAULT_FIELDS), new BeanContext(myDataProvider.getBeanFactory(), myServiceLocator, myApiUrlBuilder));
  }

  @DELETE
  @Path("/{agentPoolLocator}/projects/{projectLocator}")
  @Produces({"application/xml", "application/json"})
  public void deletePoolProject(@PathParam("agentPoolLocator") String agentPoolLocator, @PathParam("projectLocator") String projectLocator) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
    final SProject project = myProjectFinder.getProject(projectLocator);
    final AgentPoolManager agentPoolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final int agentPoolId = agentPool.getAgentPoolId();
    try {
      agentPoolManager.dissociateProjectsFromPool(agentPoolId, Collections.singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      throw new IllegalStateException("Agent pool with id \'" + agentPoolId + "' is not found.");
    }
  }

  @GET
  @Path("/{agentPoolLocator}/agents")
  @Produces({"application/xml", "application/json"})
  public Agents getPoolAgents(@PathParam("agentPoolLocator") String agentPoolLocator) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
    return new Agents(myAgentPoolsFinder.getPoolAgents(agentPool), null, null, myApiUrlBuilder);
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
  public Agent addAgent(@PathParam("agentPoolLocator") String agentPoolLocator, Agent agent) {
    final jetbrains.buildServer.serverSide.agentPools.AgentPool agentPool = myAgentPoolsFinder.getAgentPool(agentPoolLocator);
    SBuildAgent postedAgent = agent.getAgentFromPosted(myAgentFinder);
    myDataProvider.addAgentToPool(agentPool, postedAgent);
    return new Agent(postedAgent, myAgentPoolsFinder, myApiUrlBuilder);
  }
}

