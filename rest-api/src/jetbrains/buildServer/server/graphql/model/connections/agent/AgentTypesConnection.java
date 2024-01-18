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

package jetbrains.buildServer.server.graphql.model.connections.agent;

import graphql.execution.DataFetcherResult;
import java.util.List;
import jetbrains.buildServer.server.graphql.model.AgentType;
import jetbrains.buildServer.server.graphql.model.connections.ExtensibleConnection;
import jetbrains.buildServer.server.graphql.model.connections.PaginatingConnection;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;

public class AgentTypesConnection implements ExtensibleConnection<AgentType, AgentTypeEdge> {
  @NotNull
  private final PaginatingConnection<SAgentType, AgentType, AgentTypeEdge> myDelegate;

  public AgentTypesConnection(@NotNull List<SAgentType> data, @NotNull PaginationArguments paginationArguments) {
    myDelegate = new PaginatingConnection<>(data, AgentTypeEdge::new, paginationArguments);
  }

  @NotNull
  @Override
  public DataFetcherResult<List<AgentTypeEdge>> getEdges() {
    return myDelegate.getEdges();
  }
}