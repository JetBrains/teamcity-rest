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

package jetbrains.buildServer.server.graphql.resolver;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.serverSide.ProjectManagerEx;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeStorage;
import jetbrains.buildServer.serverSide.auth.*;
import org.jetbrains.annotations.NotNull;

public class AgentPoolActionsAccessCheckerImpl implements AgentPoolActionsAccessChecker {
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

  @Override
  public boolean canMoveAgentFromItsCurrentPool(int agentTypeId) {
    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();
    AgentType agentType = myAgentTypeStorage.findAgentTypeById(agentTypeId);
    if(agentType == null) {
      return false;
    }

    return getRestrictingProjectsInPoolUnsafe(authHolder, agentType.getAgentPoolId()).count() == 0;
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
  public boolean canManageAgentsInPool(int agentPoolId) {
    return getRestrictingProjectsInPool(agentPoolId).isEmpty();
  }

  @Override
  public boolean canManageAgentsInProjectPool(@NotNull String projectId) {
    return hasPermissionToManageAgentPoolsForProject(mySecurityContext.getAuthorityHolder(), projectId);
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
    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();

    return getRestrictingProjectsInPoolUnsafe(authHolder, agentPoolId).count() == 0;
  }

  private boolean hasPermissionToManageAgentPoolsForProject(@NotNull AuthorityHolder authHolder, @NotNull String projectId) {
    return authHolder.isPermissionGrantedForProject(projectId, Permission.MANAGE_AGENT_POOLS_FOR_PROJECT);
  }
}
