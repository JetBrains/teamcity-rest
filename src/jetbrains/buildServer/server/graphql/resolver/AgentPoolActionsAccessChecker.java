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

import java.util.Set;
import org.jetbrains.annotations.NotNull;

public interface AgentPoolActionsAccessChecker {

  boolean canAuthorizeAgentsInPool(int agentPoolId);

  boolean canEnableAgentsInPool(int agentPoolId);

  /**
   * Check if current user has enough permissions to move agent from its current pool.
   */
  boolean canMoveAgentFromItsCurrentPool(int agentTypeId);

  /**
   * Retrieve a set of visible projects for which user does not have enough permissions, so they restrict removing given agent type from pool.
   * There may be hidden projects
   */
  @NotNull
  Set<String> getRestrictingProjectsInAssociatedPool(int agentTypeId);

  /**
   * Retrieve a set of those projects for which user does not have enough permissions, so they restrict adding to/removing from pool.
   */
  @NotNull
  Set<String> getRestrictingProjectsInPool(int agentPoolId);

  /**
   * Check if current user has enough permissions to move some agent to the specified pool. In order to fully check permissions,
   * source pool of the desired agent must also be checked.
   */
  boolean canManageAgentsInPool(int agentPoolId);

  /**
   * Check if current user has enough permissions to move agent to the project pool of the specified project.
   */
  boolean canManageAgentsInProjectPool(@NotNull String projectId);

  /**
   * Check if current user has enough permissions to add/remove projects to the given pool. In order to fully check permissions,
   * source pool of the project must be checked.
   */
  boolean canManageProjectsInPool(int agentPoolId);
}
