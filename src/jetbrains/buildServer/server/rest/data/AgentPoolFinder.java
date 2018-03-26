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

package jetbrains.buildServer.server.rest.data;

import com.google.common.collect.ComparisonChain;
import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Dimension;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeStorage;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 07.11.13
 */
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
    return Locator.getStringLocator(ID.name, String.valueOf(agentPool.getAgentPoolId()));
  }

  @NotNull
  public static String getLocator(@NotNull final SProject project) {
    return Locator.getStringLocator(PROJECT.name, ProjectFinder.getLocator(project));
  }

  public static final Dimension<Long> ID = new Dimension<>("id");
  public static final Dimension<String> NAME = new Dimension<>("name");
  private static final Dimension<List<SBuildAgent>> AGENT = new Dimension<>("agent");
  private static final Dimension<List<SProject>> PROJECT = new Dimension<>("project");

  private class AgentPoolFinderBuilder extends TypedFinderBuilder<AgentPool> {
    AgentPoolFinderBuilder() {
      dimensionLong(Dimension.single()).description("agent pool id").toItems(dimension -> Collections.singletonList(getAgentPoolById(dimension)));
      dimensionLong(ID).description("agent pool id").toItems(dimension -> Collections.singletonList(getAgentPoolById(dimension))).
        valueForDefaultFilter(agentPool -> (long)agentPool.getAgentPoolId());
      dimensionString(NAME).description("agent pool name").valueForDefaultFilter(agentPool -> agentPool.getName());
      dimensionProjects(PROJECT, myServiceLocator).description("projects associated with the agent pool").
        valueForDefaultFilter(agentPool -> new HashSet<SProject>(getPoolProjects(agentPool)));
      dimensionAgents(AGENT, myServiceLocator).description("agents associated with the agent pool").
        valueForDefaultFilter(agentPool -> new HashSet<SBuildAgent>(getPoolAgentsInternal(agentPool)));

      filter(DimensionCondition.ALWAYS, new ItemFilterFromDimensions<AgentPool>() {
        @Nullable
        @Override
        public ItemFilter<AgentPool> get(@NotNull final DimensionObjects dimensions) {
          final PermissionChecker permissionChecker = myServiceLocator.getSingletonService(PermissionChecker.class);
          final boolean hasPermission = permissionChecker.hasGlobalPermission(Permission.VIEW_AGENT_DETAILS) || permissionChecker.hasPermissionInAnyProject(Permission.VIEW_AGENT_DETAILS_FOR_PROJECT);
          if (hasPermission) return null;
          return new ItemFilter<AgentPool>() {
            @Override
            public boolean shouldStop(@NotNull final AgentPool item) {
              return false;
            }

            @Override
            public boolean isIncluded(@NotNull final AgentPool item) {
              return false;
            }
          };
        }
      });
      multipleConvertToItems(DimensionCondition.ALWAYS, dimensions -> myAgentPoolManager.getAllAgentPools());

      locatorProvider(agentPool -> getLocator(agentPool));
      containerSetProvider(() -> new TreeSet<>(new Comparator<AgentPool>() {
            @Override
            public int compare(final AgentPool o1, final AgentPool o2) {
              return ComparisonChain.start()
                                    .compare(o1.getAgentPoolId(), o2.getAgentPoolId())
                                    .compare(o1.getName(), o2.getName())
                                    .result();
            }
          }));
    }
  }

  //
  // Helper methods
  //

  //todo: TeamCity API: what is the due way to do this? http://youtrack.jetbrains.com/issue/TW-33307
  /**
   * Gets all agents (registered and unregistered) of the specified pool excluding cloud agent images.
   */
  @NotNull
  private List<SBuildAgent> getPoolAgentsInternal(@NotNull final AgentPool agentPool) {
    final Collection<Integer> agentTypeIds = myServiceLocator.getSingletonService(AgentTypeStorage.class).getAgentTypeIdsByPool(agentPool.getAgentPoolId());

    //todo: support cloud agents here
    final List<SBuildAgent> allAgents = myAgentFinder.getItems(Locator.getStringLocator(AgentFinder.DEFAULT_FILTERING, "false")).myEntries;
    return CollectionsUtil.filterCollection(allAgents, new Filter<SBuildAgent>() {
      public boolean accept(@NotNull final SBuildAgent agent) {
        return agentTypeIds.contains(agent.getAgentTypeId());
      }
    });
  }

  //todo: TeamCity API: what is the due way to do this? http://youtrack.jetbrains.com/issue/TW-33307
  /**
   * Gets all projects associated with the specified pool.
   */
  @NotNull
  public List<SProject> getPoolProjects(@NotNull final AgentPool agentPool) {
    final Set<String> projectIds = myAgentPoolManager.getPoolProjects(agentPool.getAgentPoolId());
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
    return getItems(getLocator(project)).myEntries;
  }
}
