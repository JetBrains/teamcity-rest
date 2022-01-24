/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.model.agentPool.actions;

import org.jetbrains.annotations.NotNull;

public class AgentPoolActions {
  private final AgentPoolActionStatus myMoveAgents;
  private final AgentPoolActionStatus myMoveCloudImages;
  private final AgentPoolActionStatus myAuthorizeAgents;
  private final AgentPoolActionStatus myEnableAgents;
  private final AgentPoolActionStatus myMoveProjects;
  private final AgentPoolActionStatus myUpdateProperties;

  public AgentPoolActions(@NotNull AgentPoolActionStatus moveAgents,
                          @NotNull AgentPoolActionStatus moveCloudImages,
                          @NotNull AgentPoolActionStatus authorizeAgents,
                          @NotNull AgentPoolActionStatus enableAgents,
                          @NotNull AgentPoolActionStatus moveProjects,
                          @NotNull AgentPoolActionStatus updateProperties) {
    myMoveAgents = moveAgents;
    myMoveCloudImages = moveCloudImages;
    myAuthorizeAgents = authorizeAgents;
    myEnableAgents = enableAgents;
    myMoveProjects = moveProjects;
    myUpdateProperties = updateProperties;
  }

  @NotNull
  public AgentPoolActionStatus getMoveAgents() {
    return myMoveAgents;
  }

  @NotNull
  public AgentPoolActionStatus getMoveCloudImages() {
    return myMoveCloudImages;
  }

  @NotNull
  public AgentPoolActionStatus getAuthorizeAgents() {
    return myAuthorizeAgents;
  }

  @NotNull
  public AgentPoolActionStatus getEnableAgents() {
    return myEnableAgents;
  }

  @NotNull
  public AgentPoolActionStatus getMoveProjects() {
    return myMoveProjects;
  }

  @NotNull
  public AgentPoolActionStatus getUpdateProperties() {
    return myUpdateProperties;
  }
}
