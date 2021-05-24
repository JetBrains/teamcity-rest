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

package jetbrains.buildServer.server.graphql.model.connections;

import graphql.execution.DataFetcherResult;
import graphql.relay.ConnectionCursor;
import graphql.relay.Edge;
import graphql.relay.PageInfo;
import java.util.List;
import java.util.function.Function;
import jetbrains.buildServer.server.graphql.model.AgentPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectAgentPoolsConnection implements ExtensibleConnection<AgentPool, ProjectAgentPoolsConnection.ProjectAgentPoolEdge> {
  @NotNull
  private final NonPaginatingLazyConnection<jetbrains.buildServer.serverSide.agentPools.AgentPool, AgentPool, ProjectAgentPoolsConnection.ProjectAgentPoolEdge> myDelegate;

  public ProjectAgentPoolsConnection(List<jetbrains.buildServer.serverSide.agentPools.AgentPool> data) {
    myDelegate = new NonPaginatingLazyConnection<>(data, ProjectAgentPoolEdge::new);
  }

  @NotNull
  @Override
  public DataFetcherResult<List<ProjectAgentPoolEdge>> getEdges() {
    return myDelegate.getEdges();
  }

  @Nullable
  @Override
  public PageInfo getPageInfo() {
    return myDelegate.getPageInfo();
  }

  public class ProjectAgentPoolEdge extends LazyEdge<jetbrains.buildServer.serverSide.agentPools.AgentPool, AgentPool> {

    public ProjectAgentPoolEdge(@NotNull jetbrains.buildServer.serverSide.agentPools.AgentPool data) {
      super(data, AgentPool::new);
    }
  }
}
