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
import jetbrains.buildServer.server.graphql.resolver.agentPool.AgentPoolActionsAccessChecker;
import jetbrains.buildServer.server.graphql.resolver.agentPool.ManageAgentsInPoolUnmetRequirements;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgentPoolAccessCheckerForTests implements AgentPoolActionsAccessChecker {
  private ManageAgentsInPoolUnmetRequirements myUnmetRequirements = null;
  private Set<String> myRestrictingProjects = Collections.emptySet();
  private boolean myCanManageAgentsInPool = true;
  private boolean myCanManageAgentsInProjectPool = true;
  private boolean myCanManageProjectsInPool = true;
  private boolean myCanModifyAgentPool = true;

  @Nullable
  @Override
  public ManageAgentsInPoolUnmetRequirements getUnmetRequirementsToManageAgentsInPool(int agentPoolId) {
    return myUnmetRequirements;
  }

  @NotNull
  @Override
  public Set<String> getRestrictingProjectsInAssociatedPool(int agentTypeId) {
    return myRestrictingProjects;
  }

  @NotNull
  @Override
  public Set<String> getRestrictingProjectsInPool(int agentPoolId) {
    return myRestrictingProjects;
  }

  @NotNull
  @Override
  public Set<Integer> getManageablePoolIds() {
    return Collections.emptySet();
  }

  @Override
  public boolean canManageAgentsInPool(@NotNull AgentPool agentPoolId) {
    return myCanManageAgentsInPool;
  }

  @Override
  public boolean canManageProjectsInPool(int agentPoolId) {
    return myCanManageProjectsInPool;
  }

  @Override
  public boolean canModifyAgentPool(int agentPoolId) {
    return myCanModifyAgentPool;
  }

  public void setUnmetRequirementsToManageAgentsInPool(@Nullable ManageAgentsInPoolUnmetRequirements requirements) {
    myUnmetRequirements = requirements;
  }

  public void setRestrictingProjects(@NotNull Set<String> restrictingProjects) {
    myRestrictingProjects = restrictingProjects;
  }

  public void setCanManageAgentsInPool(boolean canManageAgentsInPool) {
    myCanManageAgentsInPool = canManageAgentsInPool;
  }

  public void setCanManageAgentsInProjectPool(boolean canManageAgentsInProjectPool) {
    myCanManageAgentsInProjectPool = canManageAgentsInProjectPool;
  }

  public void setCanManageProjectsInPool(boolean canManageProjectsInPool) {
    myCanManageProjectsInPool = canManageProjectsInPool;
  }

  public void setCanModifyAgentPool(boolean canModifyAgentPool) {
    myCanModifyAgentPool = canModifyAgentPool;
  }
}
