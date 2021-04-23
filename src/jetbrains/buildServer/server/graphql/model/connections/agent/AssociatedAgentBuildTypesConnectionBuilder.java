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

package jetbrains.buildServer.server.graphql.model.connections.agent;

import graphql.execution.DataFetcherResult;
import graphql.relay.Connection;
import graphql.relay.PageInfo;
import graphql.relay.SimpleListConnection;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.graphql.model.AgentRunPolicy;
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;

public class AssociatedAgentBuildTypesConnectionBuilder {
  @NotNull
  private final List<BuildType> myEdges;
  @NotNull
  private final AgentRunPolicy myAgentRunPolicy;
  @NotNull
  private final SBuildAgent myRealAgent;

  public AssociatedAgentBuildTypesConnectionBuilder(@NotNull List<BuildType> edges, @NotNull AgentRunPolicy agentRunPolicy, @NotNull SBuildAgent realAgent) {
    myEdges = edges;
    myAgentRunPolicy = agentRunPolicy;
    myRealAgent = realAgent;
  }

  @NotNull
  public DiassociatedAgentBuildTypesConnection get(DataFetchingEnvironment environment) {
    return new ConnectionImpl(new SimpleListConnection<>(myEdges).get(environment));
  }

  private class ConnectionImpl implements DiassociatedAgentBuildTypesConnection {
    @NotNull
    private final List<AgentBuildTypeEdge> myEdges;
    @NotNull
    private final PageInfo myPageInfo;

    public ConnectionImpl(@NotNull Connection<BuildType> delegate) {
      myEdges = delegate.getEdges().stream().map(e -> new AgentBuildTypeEdge(e)).collect(Collectors.toList());
      myPageInfo = delegate.getPageInfo();
    }

    @NotNull
    @Override
    public DataFetcherResult<List<AgentBuildTypeEdge>> getEdges() {
      return DataFetcherResult.<List<AgentBuildTypeEdge>>newResult()
        .localContext(myRealAgent)
        .data(myEdges)
        .build();
    }

    @NotNull
    @Override
    public PageInfo getPageInfo() {
      return myPageInfo;
    }

    @Override
    public int getCount() {
      return myEdges.size();
    }

    @NotNull
    @Override
    public AgentRunPolicy getRunPolicy() {
      return myAgentRunPolicy;
    }
  }
}
