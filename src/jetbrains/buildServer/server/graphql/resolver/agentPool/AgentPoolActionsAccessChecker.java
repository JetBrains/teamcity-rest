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

import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AgentPoolActionsAccessChecker {
  /**
   *
   */
  @Nullable
  ManageAgentsInPoolUnmetRequirements getUnmetRequirementsToManageAgentsInPool(int agentPoolId);

  /**
   * Retrieve a set of visible projects which restrict current user form removing given agent type from its current pool.
   * There may be hidden projects, so canMoveAgentFromItsCurrentPool(id) !== getRestrictingProjectsInAssociatedPool(id).isEmpty().
   */
  @NotNull
  Set<String> getRestrictingProjectsInAssociatedPool(int agentTypeId);

  /**
   * Retrieve a set of visible projects which restrict current user form adding agents to (or removing from) given pool.
   */
  @NotNull
  Set<String> getRestrictingProjectsInPool(int agentPoolId);

  /**
   * Check if current user has enough permissions to move some agent to the specified pool.
   * In order to fully check permissions for the move operation, current pool of the desired agent must be checked too.
   */
  boolean canManageAgentsInPool(int agentPoolId);

  /**
   * Check if current user has enough permissions to move agent to the project pool of the specified project.
   * In order to fully check permissions for the move operation, current pool of the desired agent must be checked too.
   */
  boolean canManageAgentsInProjectPool(@NotNull String projectId);

  /**
   * Check if current user has enough permissions to add/remove projects to the given pool.
   * In order to fully check permissions for project move both source and target pools must be checked.
   */
  boolean canManageProjectsInPool(int agentPoolId);

  /**
   * Check if current user has enough permissions to modify name and limits of the given pool.
   */
  boolean canModifyAgentPool(int agentPoolId);
}
