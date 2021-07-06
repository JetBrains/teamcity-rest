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

package jetbrains.buildServer.server.graphql.model.mutation;

import jetbrains.buildServer.server.graphql.model.CloudImage;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import org.jetbrains.annotations.NotNull;

public class MoveCloudImageToAgentPoolPayload {
  @NotNull
  private final CloudImage myCloudImage;

  @NotNull
  private final AgentPool mySourceAgentPool;

  @NotNull
  private final AgentPool myTargetAgentPool;

  public MoveCloudImageToAgentPoolPayload(@NotNull CloudImage cloudImage, @NotNull AgentPool sourceAgentPool, @NotNull AgentPool targetAgentPool) {
    myCloudImage = cloudImage;

    mySourceAgentPool = sourceAgentPool;

    myTargetAgentPool = targetAgentPool;
  }

  @NotNull
  public CloudImage getCloudImage() {
    return myCloudImage;
  }

  @NotNull
  public AgentPool getSourceAgentPool() {
    return mySourceAgentPool;
  }

  @NotNull
  public AgentPool getTargetAgentPool() {
    return myTargetAgentPool;
  }
}
