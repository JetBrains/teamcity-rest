/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.graphql.GraphQLContext;
import jetbrains.buildServer.server.graphql.model.ProjectPermissions;
import jetbrains.buildServer.server.graphql.model.agentPool.ProjectAgentPool;
import jetbrains.buildServer.server.graphql.model.connections.*;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.server.graphql.util.ParentsFetcher;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ProjectResolver extends ModelResolver<Project> {
  @NotNull
  private final AgentPoolManager myAgentPoolManager;
  @NotNull
  private final PaginationArgumentsProvider myPaginationArgumentsProvider;
  @NotNull
  private final AbstractAgentPoolFactory myPoolFactory;

  public ProjectResolver(@NotNull AgentPoolManager agentPoolManager,
                         @NotNull PaginationArgumentsProvider paginationArgumentsProvider,
                         @NotNull AbstractAgentPoolFactory abstractAgentPoolFactory) {
    myAgentPoolManager = agentPoolManager;
    myPaginationArgumentsProvider = paginationArgumentsProvider;
    myPoolFactory = abstractAgentPoolFactory;
  }

  @NotNull
  public BuildTypesConnection buildTypes(@NotNull Project source, @Nullable Integer first, @Nullable String after, @NotNull DataFetchingEnvironment env) {
    SProject self = source.getRealProject();

    return new BuildTypesConnection(self.getBuildTypes(), myPaginationArgumentsProvider.get(first, after, PaginationArgumentsProvider.FallbackBehaviour.RETURN_EVERYTHING));
  }

  @NotNull
  public ProjectsConnection ancestorProjects(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    SProject self = source.getRealProject();

    return new ProjectsConnection(ParentsFetcher.getAncestors(self), PaginationArguments.everything());
  }

  @NotNull
  public ProjectPermissions permissions(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    GraphQLContext ctx = env.getContext();

    SUser user = ctx.getUser();
    if(user == null) {
      return new ProjectPermissions(false);
    }

    SProject self = source.getRealProject();

    return new ProjectPermissions(AuthUtil.hasPermissionToManageAgentPoolsWithProject(user, self.getProjectId()));
  }

  @NotNull
  public ProjectAgentPoolsConnection agentPools(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    SProject self = source.getRealProject();

    List<jetbrains.buildServer.serverSide.agentPools.AgentPool> pools = myAgentPoolManager.getAgentPoolsWithProject(self.getProjectId()).stream()
                                              .map(myAgentPoolManager::findAgentPoolById)
                                              .collect(Collectors.toList());

    return new ProjectAgentPoolsConnection(pools, myPoolFactory::produce);
  }

  @Nullable
  public ProjectAgentPool projectAgentPool(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    SProject self = source.getRealProject();

    AgentPool pool = myAgentPoolManager.findProjectPoolByProjectId(self.getProjectId());
    if(pool == null) return null;

    return new ProjectAgentPool(pool);
  }

  @Override
  public String getIdPrefix() {
    return Project.class.getSimpleName();
  }

  @Override
  public Project findById(String id) {
    return null;
  }
}
