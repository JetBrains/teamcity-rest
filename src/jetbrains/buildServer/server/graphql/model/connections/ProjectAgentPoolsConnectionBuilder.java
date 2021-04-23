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

import graphql.relay.Connection;
import graphql.relay.Edge;
import graphql.relay.PageInfo;
import graphql.relay.SimpleListConnection;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.graphql.model.AgentPool;
import org.jetbrains.annotations.NotNull;

public class ProjectAgentPoolsConnectionBuilder {
  @NotNull
  private final SimpleListConnection<AgentPool> myDelegate;
  private final int myCount;

  public ProjectAgentPoolsConnectionBuilder(List<AgentPool> data) {
    myDelegate = new SimpleListConnection<AgentPool>(data);
    myCount = data.size();
  }

  public ProjectAgentPoolsConnection get(DataFetchingEnvironment environment) {
    return new ProjectAgentPoolsConnectionBuilder.ConnectionImpl(myDelegate.get(environment), myCount);
  }

  private class ConnectionImpl implements ProjectAgentPoolsConnection {
    @NotNull
    private final Connection<AgentPool> myDelegate;
    private final int myCount;

    ConnectionImpl(@NotNull Connection<AgentPool> delegate, int count) {
      myDelegate = delegate;
      myCount = count;
    }

    @Override
    public List<ProjectAgentPoolEdge> getEdges() {
      return myDelegate.getEdges().stream().map(ProjectAgentPoolEdge::new).collect(Collectors.toList());
    }

    @Override
    public PageInfo getPageInfo() {
      return myDelegate.getPageInfo();
    }

    public int getCount() {
      return myCount;
    }
  }
}
