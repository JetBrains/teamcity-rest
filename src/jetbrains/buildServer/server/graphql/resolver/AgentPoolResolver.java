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

import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPoolPermissions;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolAgentsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolCloudImagesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolProjectsConnection;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class AgentPoolResolver implements GraphQLResolver<AgentPool> {
  private final ProjectManager myProjectManager;
  private final AgentPoolActionsAccessChecker myPoolActionsAccessChecker;
  private final SecurityContextEx mySecurityContext;

  public AgentPoolResolver(@NotNull ProjectManager projectManager,
                           @NotNull AgentPoolActionsAccessChecker poolActionsAccessChecker,
                           @NotNull final SecurityContextEx securityContext) {
    myProjectManager = projectManager;
    myPoolActionsAccessChecker = poolActionsAccessChecker;
    mySecurityContext = securityContext;
  }

  @NotNull
  public AgentPoolAgentsConnection agents(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    // TODO: implement
    jetbrains.buildServer.serverSide.agentPools.AgentPool realPool = env.getLocalContext();

    return null;
  }

  @NotNull
  public AgentPoolProjectsConnection projects(@NotNull AgentPool pool, @NotNull ProjectsFilter filter, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.serverSide.agentPools.AgentPool realPool = env.getLocalContext();

    Collection<String> projectIds = realPool.getProjectIds();
    Stream<SProject> projects = myProjectManager.findProjects(projectIds).stream();
    if(filter.getArchived() != null) {
      projects = projects.filter(p -> p.isArchived() == filter.getArchived());
    }

    return new AgentPoolProjectsConnection(projects.collect(Collectors.toList()), PaginationArguments.everything());
  }

  @NotNull
  public AgentPoolPermissions permissions(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.serverSide.agentPools.AgentPool realPool = env.getLocalContext();
    int poolId = realPool.getAgentPoolId();
    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();

    boolean canAuthorizeUnauthorizeAgent = AuthUtil.hasPermissionToAuthorizeAgentsInPool(authHolder, realPool);
    boolean canEnableDisableAgent = AuthUtil.hasPermissionToEnableAgentsInPool(authHolder, realPool);
    boolean canManageProjectPoolAssociations = myPoolActionsAccessChecker.canManageProjectsInPool(poolId);
    boolean canRemoveAgents = myPoolActionsAccessChecker.canManageAgentsInPool(poolId);

    return new AgentPoolPermissions(canAuthorizeUnauthorizeAgent, canManageProjectPoolAssociations, canEnableDisableAgent, canRemoveAgents);
  }

  @NotNull
  public AgentPoolCloudImagesConnection cloudImages(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    // TODO: implement

    return null;
  }
}
