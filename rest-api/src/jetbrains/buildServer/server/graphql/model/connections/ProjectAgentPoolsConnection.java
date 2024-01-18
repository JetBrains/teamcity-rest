/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import graphql.relay.PageInfo;
import java.util.List;
import java.util.function.Function;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectAgentPoolsConnection implements ExtensibleConnection<AbstractAgentPool, ProjectAgentPoolsConnection.ProjectAgentPoolEdge> {
  private final PaginatingConnection<jetbrains.buildServer.serverSide.agentPools.AgentPool, AbstractAgentPool, ProjectAgentPoolEdge> myDelegate;
  private final Function<jetbrains.buildServer.serverSide.agentPools.AgentPool, AbstractAgentPool> myPoolFactory;

  public ProjectAgentPoolsConnection(@NotNull List<jetbrains.buildServer.serverSide.agentPools.AgentPool> data,
                                     @NotNull Function<jetbrains.buildServer.serverSide.agentPools.AgentPool, AbstractAgentPool> poolFactory) {
    myPoolFactory = poolFactory;
    myDelegate = new PaginatingConnection<>(data, ProjectAgentPoolEdge::new, PaginationArguments.everything());
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

  public class ProjectAgentPoolEdge extends LazyEdge<jetbrains.buildServer.serverSide.agentPools.AgentPool, AbstractAgentPool> {

    public ProjectAgentPoolEdge(@NotNull jetbrains.buildServer.serverSide.agentPools.AgentPool data) {
      super(data, myPoolFactory::apply);
    }
  }
}