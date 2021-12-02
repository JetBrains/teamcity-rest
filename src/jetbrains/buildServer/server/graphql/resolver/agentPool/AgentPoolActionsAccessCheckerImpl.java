/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.resolver.agentPool;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeStorage;
import jetbrains.buildServer.serverSide.auth.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgentPoolActionsAccessCheckerImpl implements AgentPoolActionsAccessChecker {
  private static final Logger LOG = Logger.getInstance(AgentPoolActionsAccessCheckerImpl.class.getName());

  private final SecurityContextEx mySecurityContext;
  @NotNull private AgentTypeStorage myAgentTypeStorage;
  @NotNull private ProjectManagerEx myProjectManager;
  @NotNull private AgentPoolManager myAgentPoolManager;

  public AgentPoolActionsAccessCheckerImpl(@NotNull final SecurityContextEx securityContext) {
    mySecurityContext = securityContext;
  }

  public void setAgentTypeStorage(@NotNull AgentTypeStorage agentTypeStorage) {
    myAgentTypeStorage = agentTypeStorage;
  }

  public void setProjectManager(@NotNull ProjectManagerEx projectManager) {
    myProjectManager = projectManager;
  }

  public void setAgentPoolManager(@NotNull AgentPoolManager agentPoolManager) {
    myAgentPoolManager = agentPoolManager;
  }

  @Nullable
  @Override
  public ManageAgentsInPoolUnmetRequirements getUnmetRequirementsToManageAgentsInPool(int agentPoolId) {
    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();
    Stream<String> restrictingProjectsUnfiltered = getRestrictingProjectsInPoolUnsafe(authHolder, agentPoolId);

    List<String> restrictingProjects = new ArrayList<>();
    int[] hiddenCount = new int[1];

    restrictingProjectsUnfiltered.forEach(projectId -> {
      if(!AuthUtil.hasReadAccessTo(authHolder, projectId)) {
        hiddenCount[0]++;
        return;
      }

      restrictingProjects.add(projectId);
    });

    return new ManageAgentsInPoolUnmetRequirements(
      restrictingProjects,
      hiddenCount[0],
      Permission.MANAGE_AGENT_POOLS_FOR_PROJECT,
      Arrays.asList(Permission.MANAGE_AGENT_POOLS, Permission.MANAGE_AGENT_POOLS_FOR_PROJECT)
    );
  }

  @NotNull
  @Override
  public Set<String> getRestrictingProjectsInAssociatedPool(int agentTypeId) {
    AgentType agentType = myAgentTypeStorage.findAgentTypeById(agentTypeId);
    if(agentType == null) {
      return Collections.emptySet();
    }

    return getRestrictingProjectsInPool(agentType.getAgentPoolId());
  }

  @Override
  public boolean canManageAgentsInPool(@NotNull AgentPool targetPool) {
    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();

    return AuthUtil.hasGlobalOrPoolProjectsPermission(authHolder, targetPool, Permission.MANAGE_AGENT_POOLS, Permission.MANAGE_AGENT_POOLS_FOR_PROJECT);
  }

  @NotNull
  @Override
  public Set<String> getRestrictingProjectsInPool(int agentPoolId) {
    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();

    return getRestrictingProjectsInPoolUnsafe(authHolder, agentPoolId)
            .filter(projectId -> !AuthUtil.hasReadAccessTo(authHolder, projectId))
            .collect(Collectors.toSet());
  }

  private Stream<String> getRestrictingProjectsInPoolUnsafe(@NotNull AuthorityHolder authHolder, int agentPoolId) {
    // If authHolder has an appropriate global permission, no need to check on a per-project basis
    if(AuthUtil.hasPermissionToManageAllAgentPoolProjectAssociations(authHolder)) {
      return Stream.empty();
    }

    if(agentPoolId == AgentPool.DEFAULT_POOL_ID && !hasPermissionToManageAgentPoolsForProject(authHolder, BuildProject.ROOT_PROJECT_ID)) {
      return Stream.of(BuildProject.ROOT_PROJECT_ID);
    }

    final Set<String> resultingPoolProjects = myProjectManager.getExistingProjectsIds(myAgentPoolManager.getPoolProjects(agentPoolId));
    return resultingPoolProjects.stream()
                                .filter(projectId -> !hasPermissionToManageAgentPoolsForProject(authHolder, projectId));
  }

  @Override
  public boolean canManageProjectsInPool(int agentPoolId) {
    AgentPool pool = myAgentPoolManager.findAgentPoolById(agentPoolId);
    if(pool == null || pool.isProjectPool()) {
      return false;
    }
    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();

    return getRestrictingProjectsInPoolUnsafe(authHolder, agentPoolId).count() == 0;
  }

  @Override
  public boolean canModifyAgentPool(int agentPoolId) {
    // TODO: implement me
    return false;
  }

  // Duplicates PoolAgentTypeSelectorDescriptor.getManageablePoolIdsForUser,
  // so this is a candidate to be moved into some utility class.
  @NotNull
  public Set<Integer> getManageablePoolIds() {
    AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    final Set<Integer> manageablePoolIds = new HashSet<Integer>();
    try {
      mySecurityContext.runAsSystem(() -> {
        for (final jetbrains.buildServer.serverSide.agentPools.AgentPool pool : myAgentPoolManager.getAllAgentPools()) {
          final int poolId = pool.getAgentPoolId();
          if (AuthUtil.hasPermissionToManageAgentPoolsWithProjects(authorityHolder, myAgentPoolManager.getPoolProjects(poolId))) {
            manageablePoolIds.add(poolId);
          }
        }
      });
    }
    catch (final Throwable e) {
      Loggers.SERVER.warn(e.getMessage(), e);
    }
    return manageablePoolIds;
  }

  private boolean hasPermissionToManageAgentPoolsForProject(@NotNull AuthorityHolder authHolder, @NotNull String projectId) {
    return authHolder.isPermissionGrantedForProject(projectId, Permission.MANAGE_AGENT_POOLS_FOR_PROJECT);
  }
}
