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
import java.util.Collection;
import jetbrains.buildServer.server.graphql.model.AgentPool;
import jetbrains.buildServer.server.graphql.model.AgentPoolPermissions;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolAgentsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolCloudImagesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolProjectsConnection;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class AgentPoolResolver implements GraphQLResolver<AgentPool> {
  @NotNull
  private final ProjectFinder myProjectFinder;

  public AgentPoolResolver(@NotNull ProjectFinder projectFinder) {
    myProjectFinder = projectFinder;
  }

  @NotNull
  public AgentPoolAgentsConnection agents(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    // TODO: implement
    jetbrains.buildServer.serverSide.agentPools.AgentPool realPool = env.getLocalContext();

    return null;
  }

  @NotNull
  public AgentPoolProjectsConnection projects(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    // TODO: implement
    jetbrains.buildServer.serverSide.agentPools.AgentPool realPool = env.getLocalContext();

    Collection<String> projectids = realPool.getProjectIds();

    return null;
  }

  @NotNull
  public AgentPoolPermissions permissions(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    // TODO: implement permissions retireval

    return new AgentPoolPermissions(false, false);
  }

  @NotNull
  public AgentPoolCloudImagesConnection cloudImages(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    // TODO: implement

    return null;
  }
}
