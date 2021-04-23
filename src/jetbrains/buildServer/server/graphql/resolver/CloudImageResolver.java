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

package jetbrains.buildServer.server.graphql.resolver;

import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import jetbrains.buildServer.server.graphql.model.AgentEnvironment;
import jetbrains.buildServer.server.graphql.model.AgentPool;
import jetbrains.buildServer.server.graphql.model.CloudImage;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.model.connections.agent.CloudImageInstancesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolsConnection;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class CloudImageResolver implements GraphQLResolver<CloudImage> {
  public int agentTypeId(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    return -1;
  }

  @NotNull
  public AgentEnvironment environment(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    return null;
  }

  @NotNull
  public CloudImageInstancesConnection instances(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    return null;
  }

  @NotNull
  public Project project(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    return null;
  }

  @NotNull
  public AgentPool agentPool(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    return null;
  }

  @NotNull
  public AgentPoolsConnection assignableAgentPools(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    return null;
  }
}
