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

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import java.util.*;
import jetbrains.buildServer.server.graphql.model.ProjectPermissions;
import jetbrains.buildServer.server.graphql.model.agentPool.ProjectAgentPool;
import jetbrains.buildServer.server.graphql.model.connections.*;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.server.graphql.util.Context;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.server.graphql.util.ParentsFetcher;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
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
    GraphQLContext ctx = env.getGraphQlContext();

    SUser user = ctx.get(Context.CURRENT_USER);
    if(user == null) {
      return new ProjectPermissions(false);
    }

    SProject self = source.getRealProject();

    return new ProjectPermissions(AuthUtil.hasPermissionToManageAgentPoolsWithProject(user, self.getProjectId()));
  }

  @NotNull
  public ProjectAgentPoolsConnection agentPools(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    SProject self = source.getRealProject();
    Map<Integer, AgentPool> prefetchedPools = getPrefetchedPools(env);

    Set<Integer> poolIdsWithCurrentProject = myAgentPoolManager.getAgentPoolsWithProject(self.getProjectId());
    List<jetbrains.buildServer.serverSide.agentPools.AgentPool> resultData = new ArrayList<>(poolIdsWithCurrentProject.size());
    if(prefetchedPools != null) {
      for(Integer poolId : poolIdsWithCurrentProject) {
        resultData.add(prefetchedPools.get(poolId));
      }
    } else {
      for(Integer poolId : poolIdsWithCurrentProject) {
        resultData.add(myAgentPoolManager.findAgentPoolById(poolId));
      }
    }

    return new ProjectAgentPoolsConnection(resultData, myPoolFactory::produce);
  }

  @Nullable
  public ProjectAgentPool projectAgentPool(@NotNull Project source, @NotNull DataFetchingEnvironment env) {
    SProject self = source.getRealProject();

    AgentPool pool = myAgentPoolManager.findProjectPoolByProjectId(self.getProjectId());
    if(pool == null) return null;

    return new ProjectAgentPool(pool);
  }

  @Nullable
  private Map<Integer, AgentPool> getPrefetchedPools(@NotNull DataFetchingEnvironment env) {
    Object context = env.getLocalContext();
    if(context == null) {
      return null;
    }

    try {
      return (Map<Integer, AgentPool>) context;
    } catch (ClassCastException e) {
      return null;
    }
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
