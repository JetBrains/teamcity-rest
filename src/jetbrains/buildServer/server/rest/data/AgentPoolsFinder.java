/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeStorage;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
public class AgentPoolsFinder {
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final AgentFinder myAgentFinder;

  public AgentPoolsFinder(@NotNull final ServiceLocator serviceLocator, final @NotNull AgentFinder agentFinder) {
    myServiceLocator = serviceLocator;
    myAgentFinder = agentFinder;
  }

  //todo: TeamCity API: what is the due way to do this? http://youtrack.jetbrains.com/issue/TW-33307
  /**
   * Gets all agents (registered and unregistered) of the specified pool excluding cloud agent images.
   */
  @NotNull
  public List<SBuildAgent> getPoolAgents(@NotNull final AgentPool agentPool) {
    final Collection<Integer> agentTypeIds = myServiceLocator.getSingletonService(AgentTypeStorage.class).getAgentTypeIdsByPool(agentPool.getAgentPoolId());

    final ArrayList<SBuildAgent> result = new ArrayList<SBuildAgent>(agentTypeIds.size());

    //todo: support cloud agents here
    final Collection<SBuildAgent> allAgents = myAgentFinder.getAllItems();
    for (SBuildAgent agent : allAgents) {
      if (agentTypeIds.contains(agent.getAgentTypeId())) {
        result.add(agent);
      }
    }
    return result;
  }

  //todo: TeamCity API: what is the due way to do this? http://youtrack.jetbrains.com/issue/TW-33307
  /**
   * Gets all projects associated with the specified pool.
   */
  @NotNull
  public List<SProject> getPoolProjects(@NotNull final AgentPool agentPool) {
    final Set<String> projectIds = myServiceLocator.getSingletonService(AgentPoolManager.class).getPoolProjects(agentPool.getAgentPoolId());
    final ProjectManager projectManager = myServiceLocator.getSingletonService(ProjectManager.class);
    final List<SProject> result = new ArrayList<SProject>(projectIds.size());
    for (String projectId : projectIds) {
      final SProject project = projectManager.findProjectById(projectId);
      if (project != null) {
        result.add(project);
      } else {
        //todo: include not present projects into the list
      }
    }
    return result;
  }

  @NotNull
  public AgentPool getAgentPoolById(final long id) {
    final AgentPool agentPool = myServiceLocator.getSingletonService(AgentPoolManager.class).findAgentPoolById((int)id);
    if (agentPool == null) {
      throw new NotFoundException("No agent pool is found by id '" + id + "'.");
    }
    return agentPool;
  }

  @NotNull
  public AgentPool getAgentPool(final SBuildAgent agent) {
    BuildAgentEx agentEx = (BuildAgentEx)agent;
    return agentEx.getAgentType().getAgentPool();
  }

  @NotNull
  public AgentPool getAgentPool(final String locatorText) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty agent pool locator is not supported.");
    }
    final Locator locator = new Locator(locatorText, "id", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's an id
      locator.checkLocatorFullyProcessed();
      //noinspection ConstantConditions
      return getAgentPoolById(locator.getSingleValueAsLong());
    }
    Long id = locator.getSingleDimensionValueAsLong("id");
    if (id != null) {
      locator.checkLocatorFullyProcessed();
      return getAgentPoolById(id);
    }
    locator.checkLocatorFullyProcessed();
    throw new NotFoundException("Agent pool locator '" + locatorText + "' is not supported.");
  }

  public Collection<AgentPool> getPoolsForProject(final SProject project) {
    final AgentPoolManager poolManager = myServiceLocator.getSingletonService(AgentPoolManager.class);
    final Set<Integer> projectPoolsIds = poolManager.getProjectPools(project.getProjectId());
    final ArrayList<AgentPool> result = new ArrayList<AgentPool>(projectPoolsIds.size());
    for (Integer poolId : projectPoolsIds) {
      result.add(getAgentPoolById(poolId));
    }
    return result;
  }
}
