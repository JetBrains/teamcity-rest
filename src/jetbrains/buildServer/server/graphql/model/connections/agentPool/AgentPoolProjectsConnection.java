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

package jetbrains.buildServer.server.graphql.model.connections.agentPool;

import graphql.execution.DataFetcherResult;
import graphql.relay.PageInfo;
import java.util.List;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.model.connections.ExtensibleConnection;
import jetbrains.buildServer.server.graphql.model.connections.LazyEdge;
import jetbrains.buildServer.server.graphql.model.connections.NonPaginatingLazyConnection;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgentPoolProjectsConnection implements ExtensibleConnection<Project, AgentPoolProjectsConnection.AgentPoolProjectsConnectionEdge> {
  @NotNull
  private final NonPaginatingLazyConnection<SProject, Project, AgentPoolProjectsConnectionEdge> myDelegate;

  public AgentPoolProjectsConnection(@NotNull List<SProject> data) {
    myDelegate = new NonPaginatingLazyConnection<>(data, AgentPoolProjectsConnectionEdge::new);
  }

  int getCount() {
    return myDelegate.getData().size();
  }

  @NotNull
  @Override
  public DataFetcherResult<List<AgentPoolProjectsConnectionEdge>> getEdges() {
    return myDelegate.getEdges();
  }

  @Nullable
  @Override
  public PageInfo getPageInfo() {
    return myDelegate.getPageInfo();
  }

  public class AgentPoolProjectsConnectionEdge extends LazyEdge<SProject, Project> {
    public AgentPoolProjectsConnectionEdge(@NotNull SProject data) {
      super(data, Project::new);
    }
  }
}
