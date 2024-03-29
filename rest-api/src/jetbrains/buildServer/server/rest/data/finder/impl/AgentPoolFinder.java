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

package jetbrains.buildServer.server.rest.data.finder.impl;

import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.util.ComparatorDuplicateChecker;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.util.ItemFilterUtil;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeStorage;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.syntax.AgentPoolDimensions.*;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
@JerseyInjectable
@Component("restAgentPoolsFinder")
public class AgentPoolFinder extends DelegatingFinder<AgentPool> {
  @NotNull private final AgentPoolManager myAgentPoolManager;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final AgentFinder myAgentFinder;

  public AgentPoolFinder(@NotNull final AgentPoolManager agentPoolManager,
                         @NotNull final AgentFinder agentFinder,
                         @NotNull final ServiceLocator serviceLocator) {
    myAgentPoolManager = agentPoolManager;
    myServiceLocator = serviceLocator;
    myAgentFinder = agentFinder;
    setDelegate(new AgentPoolFinderBuilder().build());
  }

  @NotNull
  public static String getLocator(@NotNull final AgentPool agentPool) {
    return Locator.getStringLocator(ID, String.valueOf(agentPool.getAgentPoolId()));
  }

  @NotNull
  public static String getLocator(@NotNull final SProject project) {
    return Locator.getStringLocator(PROJECT, ProjectFinder.getLocator(project));
  }

  private class AgentPoolFinderBuilder extends TypedFinderBuilder<AgentPool> {
    AgentPoolFinderBuilder() {
      name("AgentPoolFinder");
      dimensionLong(SINGLE_VALUE).toItems(dimension -> Collections.singletonList(getAgentPoolById(dimension)));

      dimensionLong(ID)
        .toItems(dimension -> Collections.singletonList(getAgentPoolById(dimension)))
        .valueForDefaultFilter(agentPool -> (long) agentPool.getAgentPoolId());

      dimensionString(NAME).valueForDefaultFilter(agentPool -> agentPool.getName());

      dimensionBoolean(PROJECT_POOL)
        .valueForDefaultFilter(agentPool -> agentPool.isProjectPool());

      dimensionProjects(OWNER_PROJECT, myServiceLocator)
        .valueForDefaultFilter(agentPool -> Util.resolveNull(getPoolOwnerProject(agentPool), ownerProject -> new HashSet<>(CollectionsUtil.setOf(ownerProject.getProject()))));

      dimensionProjects(PROJECT, myServiceLocator)
        .valueForDefaultFilter(agentPool -> new HashSet<>(getPoolProjects(agentPool)));

      dimensionAgents(AGENT, myServiceLocator)
        .valueForDefaultFilter(agentPool -> new HashSet<>(getPoolAgentsInternal(agentPool)));

      dimensionBoolean(ORPHANED_POOL)
        .withDefault("false")
        .valueForDefaultFilter(AgentPoolFinder.this::isOrphanedPool);

      filter(DimensionCondition.ALWAYS, AgentPoolFinder.this::hasPermissionToViewAgentPools);

      fallbackItemRetriever(dimensions -> ItemHolder.of(myAgentPoolManager.getAllAgentPools()));

      locatorProvider(agentPool -> getLocator(agentPool));

      duplicateCheckerSupplier(() -> new ComparatorDuplicateChecker<>(
        Comparator.comparing(AgentPool::getAgentPoolId)
                  .thenComparing(AgentPool::getName)
      ));
    }
  }

  //
  // Helper methods
  //
  private boolean isOrphanedPool(@NotNull AgentPool pool) {
    if(!pool.isProjectPool() || pool.getOwnerProjectId() == null) {
      return false;
    }

    ProjectManager projectManager = myServiceLocator.getSingletonService(ProjectManager.class);
    return !projectManager.isProjectExists(pool.getOwnerProjectId());
  }

  @Nullable
  private ItemFilter<AgentPool> hasPermissionToViewAgentPools(TypedFinderBuilder.DimensionObjects dimensions) {
    final PermissionChecker permissionChecker = myServiceLocator.getSingletonService(PermissionChecker.class);

    final boolean hasPermission =
      permissionChecker.hasGlobalPermission(Permission.VIEW_AGENT_DETAILS) || permissionChecker.hasPermissionInAnyProject(Permission.VIEW_AGENT_DETAILS_FOR_PROJECT);
    if (hasPermission) return null;
    return ItemFilterUtil.dropAll();
  }

  //todo: TeamCity API: what is the due way to do this? http://youtrack.jetbrains.com/issue/TW-33307
  /**
   * Gets all agents (registered and unregistered) of the specified pool excluding cloud agent images.
   */
  @NotNull
  private List<SBuildAgent> getPoolAgentsInternal(@NotNull final AgentPool agentPool) {
    final Collection<Integer> agentTypeIds = myServiceLocator.getSingletonService(AgentTypeStorage.class).getAgentTypeIdsByPool(agentPool.getAgentPoolId());

    //todo: support cloud agents here
    final List<SBuildAgent> allAgents = myAgentFinder.getItems(Locator.getStringLocator(AgentFinder.DEFAULT_FILTERING, "false")).getEntries();
    return CollectionsUtil.filterCollection(allAgents, agent -> agentTypeIds.contains(agent.getAgentTypeId()));
  }

  //todo: TeamCity API: what is the due way to do this? http://youtrack.jetbrains.com/issue/TW-33307
  /**
   * Gets all projects associated with the specified pool.
   */
  @NotNull
  public List<SProject> getPoolProjects(@NotNull final AgentPool agentPool) {
    final Set<String> projectIds = myAgentPoolManager.getPoolProjects(agentPool.getAgentPoolId());
    final ProjectManager projectManager = myServiceLocator.getSingletonService(ProjectManager.class);
    final List<SProject> result = new ArrayList<>(projectIds.size());
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

  @Nullable
  public PontentiallyInaccessibleProject getPoolOwnerProject(@NotNull final AgentPool projectAgentPool) {
    if (!projectAgentPool.isProjectPool() || projectAgentPool.getOwnerProjectId() == null) {
      return null;
    }
    final ProjectManager projectManager = myServiceLocator.getSingletonService(ProjectManager.class);
    try {
      return new PontentiallyInaccessibleProject(projectAgentPool.getOwnerProjectId(), projectManager.findProjectById(projectAgentPool.getOwnerProjectId()));
    } catch (AccessDeniedException e) {
      return new PontentiallyInaccessibleProject(projectAgentPool.getOwnerProjectId(), null);
    }
  }

  public static class PontentiallyInaccessibleProject{
    @NotNull private final String myInternalProjectId;
    @Nullable private final SProject myProject;
    public PontentiallyInaccessibleProject(@NotNull final String internalProjectId, @Nullable final SProject project) {
      myInternalProjectId = internalProjectId;
      myProject = project;
    }
    @NotNull public String getInternalProjectId() { return myInternalProjectId;}
    @Nullable public SProject getProject() { return myProject;}
  }

  @NotNull
  public AgentPool getAgentPoolById(final long id) {
    final AgentPool agentPool = myAgentPoolManager.findAgentPoolById((int)id);
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

  public Collection<AgentPool> getPoolsForProject(@NotNull final SProject project) {
    return getItems(getLocator(project)).getEntries();
  }
}
