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

package jetbrains.buildServer.server.graphql.model.connections;

import graphql.execution.DataFetcherResult;
import graphql.relay.PageInfo;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectsConnection implements ExtensibleConnection<Project, ProjectsConnection.ProjectsConnectionEdge> {
  public static ProjectsConnection empty() {
    return new ProjectsConnection(Collections.emptyList());
  }

  @NotNull
  private final NonPaginatingLazyConnection<SProject, Project, ProjectsConnectionEdge> myDelegate;

  public ProjectsConnection(@NotNull List<SProject> data) {
    myDelegate = new NonPaginatingLazyConnection<>(data, ProjectsConnectionEdge::new);
  }

  public int getCount() {
    return myDelegate.getData().size();
  }

  @NotNull
  @Override
  public DataFetcherResult<List<ProjectsConnectionEdge>> getEdges() {
    return myDelegate.getEdges();
  }

  @Nullable
  @Override
  public PageInfo getPageInfo() {
    return myDelegate.getPageInfo();
  }


  public class ProjectsConnectionEdge extends LazyEdge<SProject, Project> {
    public ProjectsConnectionEdge(@NotNull SProject data) {
      super(data, Project::new);
    }
  }
}
