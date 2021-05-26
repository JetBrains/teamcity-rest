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

import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.graphql.util.ParentsFetcher;
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BuildTypeResolver implements GraphQLResolver<BuildType> {
  @Autowired
  @NotNull
  private BuildTypeFinder myBuildTypeFinder;

  @NotNull
  public ProjectsConnection ancestorProjects(@NotNull BuildType source, @NotNull DataFetchingEnvironment env) throws Exception {
    SBuildType bt = myBuildTypeFinder.getItem("id:" + source.getId()).getBuildType();

    if(bt == null) {
      return ProjectsConnection.empty();
    }

    return new ProjectsConnection(ParentsFetcher.getAncestors(bt));
  }
}
