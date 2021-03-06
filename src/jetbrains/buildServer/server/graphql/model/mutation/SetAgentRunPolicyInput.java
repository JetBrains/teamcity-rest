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

import jetbrains.buildServer.server.graphql.model.AgentRunPolicy;
import org.jetbrains.annotations.NotNull;

public class SetAgentRunPolicyInput {
  @NotNull
  private final String agentId;

  @NotNull
  private final AgentRunPolicy agentRunPolicy;

  public SetAgentRunPolicyInput(@NotNull String agentId, @NotNull AgentRunPolicy agentRunPolicy) {
    this.agentId = agentId;
    this.agentRunPolicy = agentRunPolicy;
  }

  @NotNull
  public String getAgentId() {
    return agentId;
  }

  @NotNull
  public AgentRunPolicy getAgentRunPolicy() {
    return agentRunPolicy;
  }
}
