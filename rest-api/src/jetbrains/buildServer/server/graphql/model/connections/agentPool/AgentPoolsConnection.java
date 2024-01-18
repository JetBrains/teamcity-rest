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

package jetbrains.buildServer.server.graphql.model.connections.agentPool;

import graphql.execution.DataFetcherResult;
import graphql.relay.PageInfo;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.connections.ExtensibleConnection;
import jetbrains.buildServer.server.graphql.model.connections.LazyEdge;
import jetbrains.buildServer.server.graphql.model.connections.PaginatingConnection;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class AgentPoolsConnection implements ExtensibleConnection<AbstractAgentPool, AgentPoolsConnection.AgentPoolsConnectionEdge> {
  public static AgentPoolsConnection empty() {
    return new AgentPoolsConnection(Collections.emptyList(), pool -> new AgentPool(pool), PaginationArguments.everything());
  }

  private final Function<jetbrains.buildServer.serverSide.agentPools.AgentPool, AbstractAgentPool> myPoolFactory;
  private final PaginatingConnection<jetbrains.buildServer.serverSide.agentPools.AgentPool, AbstractAgentPool, AgentPoolsConnectionEdge> myDelegate;

  public AgentPoolsConnection(@NotNull Collection<jetbrains.buildServer.serverSide.agentPools.AgentPool> data,
                              @NotNull Function<jetbrains.buildServer.serverSide.agentPools.AgentPool, AbstractAgentPool> poolFactory,
                              @NotNull PaginationArguments paginationArguments) {
    myPoolFactory = poolFactory;
    myDelegate = new PaginatingConnection<>(data, AgentPoolsConnectionEdge::new, paginationArguments);
  }

  public int getCount() {
    return myDelegate.getData().size();
  }

  @NotNull
  @Override
  public DataFetcherResult<List<AgentPoolsConnectionEdge>> getEdges() {
    return myDelegate.getEdges();
  }

  @Nullable
  @Override
  public PageInfo getPageInfo() {
    return myDelegate.getPageInfo();
  }

  public class AgentPoolsConnectionEdge extends LazyEdge<jetbrains.buildServer.serverSide.agentPools.AgentPool, AbstractAgentPool> {
    public AgentPoolsConnectionEdge(@NotNull jetbrains.buildServer.serverSide.agentPools.AgentPool data) {
      super(data, myPoolFactory::apply);
    }

    @Override
    public String getCursor() {
      return myData.getName();
    }
  }
}