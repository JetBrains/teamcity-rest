/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import graphql.execution.DataFetcherResult;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import jetbrains.buildServer.server.graphql.model.Agent;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.GlobalPermissions;
import jetbrains.buildServer.server.graphql.model.agentPool.ProjectAgentPool;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArgumentsProvider;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agent.AgentsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolsConnection;
import jetbrains.buildServer.server.graphql.model.filter.AgentsFilter;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.data.AgentPoolFinder;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class Query implements GraphQLQueryResolver {
  @NotNull
  private final AgentFinder myAgentFinder;
  @NotNull
  private final AgentPoolFinder myAgentPoolFinder;
  @NotNull
  private final ProjectManager myProjectManager;
  @NotNull
  private final PaginationArgumentsProvider myPaginationArgumentsProvider;
  @NotNull
  private final AgentPoolManager myAgentPoolManager;
  @NotNull
  private final AbstractAgentPoolFactory myPoolFactory;

  public Query(@NotNull AgentFinder agentFinder,
               @NotNull AgentPoolFinder agentPoolFinder,
               @NotNull ProjectManager projectManager,
               @NotNull AgentPoolManager agentPoolManager,
               @NotNull PaginationArgumentsProvider paginationArgumentsProvider,
               @NotNull AbstractAgentPoolFactory poolFactory) {
    myAgentFinder = agentFinder;
    myAgentPoolFinder = agentPoolFinder;
    myProjectManager = projectManager;
    myAgentPoolManager = agentPoolManager;
    myPaginationArgumentsProvider = paginationArgumentsProvider;
    myPoolFactory = poolFactory;
  }

  @NotNull
  public DataFetcherResult<Agent> agent(@NotNull String id, @NotNull DataFetchingEnvironment env) {
    SBuildAgent agent = myAgentFinder.getItem("id:" + id);

    return DataFetcherResult.<Agent>newResult()
                            .data(new Agent(agent))
                            .localContext(agent)
                            .build();
  }

  @NotNull
  public AgentsConnection agents(@Nullable AgentsFilter filter, @NotNull DataFetchingEnvironment env) {
    String locatorText = (filter != null && filter.getAutorized() != null) ? "authorized:" + filter.getAutorized().toString() : null;
    List<SBuildAgent> result = myAgentFinder.getItems(locatorText).myEntries;

    // TODO: implement me
    return null;
  }

  @NotNull
  public DataFetcherResult<AbstractAgentPool> agentPool(@NotNull String id, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = myAgentPoolFinder.getAgentPoolById(Long.parseLong(id));

    return DataFetcherResult.<AbstractAgentPool>newResult()
      .data(pool.isProjectPool() ? new ProjectAgentPool(pool) : new AgentPool(pool))
      .localContext(pool)
      .build();
  }

  @NotNull
  public AgentPoolsConnection agentPools() {
    return new AgentPoolsConnection(myAgentPoolManager.getAllAgentPools(), myPoolFactory::produce, PaginationArguments.everything());
  }

  @NotNull
  public ProjectsConnection projects(@NotNull ProjectsFilter filter, @Nullable Integer first, @Nullable String after, @NotNull DataFetchingEnvironment env) {
    List<SProject> projects;
    if(filter.getArchived() != null) {
      if(filter.getArchived()) {
        projects = myProjectManager.getArchivedProjects();
      } else {
        projects = myProjectManager.getActiveProjects();
      }
    } else {
      projects = myProjectManager.getProjects();
    }

    return new ProjectsConnection(projects, myPaginationArgumentsProvider.get(first, after, PaginationArgumentsProvider.FallbackBehaviour.RETURN_EVERYTHING));
  }

  @NotNull
  public GlobalPermissions globalPermissions() {
    // TODO: actually check
    return new GlobalPermissions(false);
  }
}
