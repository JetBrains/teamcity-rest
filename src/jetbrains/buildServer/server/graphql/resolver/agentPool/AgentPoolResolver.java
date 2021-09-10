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

import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPoolPermissions;
import jetbrains.buildServer.server.graphql.model.agentPool.actions.*;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolAgentsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolCloudImagesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolProjectsConnection;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class AgentPoolResolver implements GraphQLResolver<AgentPool> {
  private final AbstractAgentPoolResolver myDelegate;
  private final AgentPoolActionsAccessChecker myPoolActionsAccessChecker;
  private final ProjectManager myProjectManager;

  public AgentPoolResolver(@NotNull AbstractAgentPoolResolver delegate,
                           @NotNull AgentPoolActionsAccessChecker agentPoolActionsAccessChecker,
                           @NotNull ProjectManager projectManager) {
    myDelegate = delegate;
    myPoolActionsAccessChecker = agentPoolActionsAccessChecker;
    myProjectManager = projectManager;
  }

  @NotNull
  public AgentPoolAgentsConnection agents(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.agents(pool, env);
  }

  @NotNull
  public AgentPoolProjectsConnection projects(@NotNull AgentPool pool, @NotNull ProjectsFilter filter, @NotNull DataFetchingEnvironment env) {
    return myDelegate.projects(pool, filter, env);
  }

  @NotNull
  public AgentPoolPermissions permissions(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.permissions(pool, env);
  }

  @NotNull
  public AgentPoolCloudImagesConnection cloudImages(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.cloudImages(pool, env);
  }

  @NotNull
  public AgentPoolActions actions(@NotNull AgentPool pool) {
    ManageAgentsInPoolUnmetRequirements unmetMoveReqs = myPoolActionsAccessChecker.getUnmetRequirementsToManageAgentsInPool(pool.getId());
    AgentPoolActionStatus moveAgentsActionStatus;
    if(unmetMoveReqs == null) {
      moveAgentsActionStatus = AgentPoolActionStatus.available();
    } else {
      moveAgentsActionStatus = AgentPoolActionStatus.unavailable(new MissingGlobalOrPerProjectPermission(
        unmetMoveReqs.getGlobalPermissions().stream().map(Permission::getName).collect(Collectors.toList()),
        unmetMoveReqs.getPerProjectPermission().getName(),
        new ProjectsConnection(myProjectManager.findProjects(unmetMoveReqs.getProjectsMissingPermission()), PaginationArguments.everything()),
        unmetMoveReqs.getHiddenProjectsMissingPermission()
      ));
    }

    return new AgentPoolActions(
      moveAgentsActionStatus,
      AgentPoolActionStatus.unavailable(null),
      AgentPoolActionStatus.unavailable(null),
      AgentPoolActionStatus.unavailable(null),
      AgentPoolActionStatus.unavailable(null),
      AgentPoolActionStatus.unavailable(null)
    );
  }
}
