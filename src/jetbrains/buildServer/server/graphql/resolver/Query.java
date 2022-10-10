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
import graphql.execution.DataFetcherResult;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.graphql.model.Agent;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.GlobalPermissions;
import jetbrains.buildServer.server.graphql.model.agentPool.ProjectAgentPool;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArgumentsProvider;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agent.AgentTypesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agent.AgentsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolsConnection;
import jetbrains.buildServer.server.graphql.model.filter.AgentsFilter;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.server.graphql.util.Context;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.server.graphql.util.ObjectIdentificationNode;
import jetbrains.buildServer.server.rest.data.Finder;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Query implements GraphQLQueryResolver {
  @Autowired
  @NotNull
  private Finder<SBuildAgent> myAgentFinder;

  @Autowired
  @NotNull
  private ProjectManager myProjectManager;

  @Autowired
  @NotNull
  private PaginationArgumentsProvider myPaginationArgumentsProvider;

  @Autowired
  @NotNull
  private AgentPoolManager myAgentPoolManager;

  @Autowired
  @NotNull
  private AbstractAgentPoolFactory myPoolFactory;

  @Autowired
  private List<ModelResolver<?>> myModelResolvers;

  @Autowired
  private AgentTypeFinder myAgentTypeFinder;

  void initForTests(@NotNull Finder<SBuildAgent> agentFinder,
                    @NotNull ProjectManager projectManager,
                    @NotNull AgentPoolManager agentPoolManager,
                    @NotNull PaginationArgumentsProvider paginationArgumentsProvider,
                    @NotNull AbstractAgentPoolFactory poolFactory) {
    myAgentFinder = agentFinder;
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
    String locatorText = (filter != null && filter.getAuthorized() != null) ? "authorized:" + filter.getAuthorized().toString() : null;
    List<SBuildAgent> result = myAgentFinder.getItems(locatorText).myEntries;

    // TODO: implement me
    return null;
  }

  @NotNull
  public AgentTypesConnection agentTypes(@NotNull DataFetchingEnvironment env) {
    List<SAgentType> data = new ArrayList<>();
    data.addAll(myAgentTypeFinder.getActiveAgentTypes());

    return new AgentTypesConnection(data, PaginationArguments.everything());
  }

  @NotNull
  public DataFetcherResult<AbstractAgentPool> agentPool(@NotNull Integer id, @NotNull DataFetchingEnvironment env) {
    DataFetcherResult.Builder<AbstractAgentPool> result = DataFetcherResult.newResult();

    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = myAgentPoolManager.findAgentPoolById(id);
    if(pool == null) {
      return result.build();
    }

    return result.data(pool.isProjectPool() ? new ProjectAgentPool(pool) : new AgentPool(pool))
                 .localContext(pool)
                 .build();
  }

  @NotNull
  public AgentPoolsConnection agentPools() {
    return new AgentPoolsConnection(myAgentPoolManager.getAllAgentPools(), myPoolFactory::produce, PaginationArguments.everything());
  }

  @NotNull
  public DataFetcherResult<ProjectsConnection> projects(@NotNull ProjectsFilter filter, @Nullable Integer first, @Nullable String after, @NotNull DataFetchingEnvironment env) {
    DataFetcherResult.Builder<ProjectsConnection> result = DataFetcherResult.newResult();

    List<SProject> resultData;
    if(filter.getArchived() != null) {
      if(filter.getArchived()) {
        resultData = myProjectManager.getArchivedProjects();
      } else {
        resultData = myProjectManager.getActiveProjects();
      }
    } else {
      resultData = myProjectManager.getProjects();
    }

    if(filter.getVirtual() != null) {
      // need to create another list until Stream<ITEM> is not implemented in PagintatingConnection and ProjectConnection
      List<SProject> filteredResult = new ArrayList<>();
      for(SProject project : resultData) {
        if(filter.getVirtual().equals(project.isVirtual())) {
          filteredResult.add(project);
        }
      }

      resultData = filteredResult;
    }

    boolean shouldPrefetchPools = resultData.size() > TeamCityProperties.getInteger("teamcity.graphql.resolvers.query.agentPoolPrefetchThreshold", 10);
    if(env.getSelectionSet().contains("edges/node/agentPools") && shouldPrefetchPools) {
      Map<Integer, jetbrains.buildServer.serverSide.agentPools.AgentPool> poolIdToPool =
        myAgentPoolManager.getAllAgentPoolsEx(true).stream()
                          .collect(Collectors.toMap(pool -> pool.getAgentPoolId(), pool -> pool));

      result.localContext(poolIdToPool);
    }

    result.data(new ProjectsConnection(resultData, myPaginationArgumentsProvider.get(first, after, PaginationArgumentsProvider.FallbackBehaviour.RETURN_EVERYTHING)));

    return result.build();
  }

  @NotNull
  public GlobalPermissions globalPermissions(@NotNull DataFetchingEnvironment env) {
    GraphQLContext ctx = env.getGraphQlContext();

    SUser user = ctx.get(Context.CURRENT_USER);
    if(user == null) {
      return new GlobalPermissions(false);
    }

    return new GlobalPermissions(user.getGlobalPermissions().contains(Permission.MANAGE_AGENT_POOLS));
  }

  @Nullable
  public ObjectIdentificationNode node(@NotNull String id) {
    int prefixEnd = id.indexOf(ModelResolver.SEPARATOR);
    if(prefixEnd == -1) {
      return null;
    }

    String prefix = id.substring(0, prefixEnd);
    ModelResolver<?> targetResolver = null;
    for(ModelResolver<?> resolver : myModelResolvers) {
      if (resolver.getIdPrefix().equals(prefix)) {
        targetResolver = resolver;
        break;
      }
    }

    return targetResolver == null ? null : targetResolver.findById(id);
  }
}
