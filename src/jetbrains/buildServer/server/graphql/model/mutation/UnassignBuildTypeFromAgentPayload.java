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

import jetbrains.buildServer.server.graphql.model.Agent;
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import org.jetbrains.annotations.NotNull;

public class UnassignBuildTypeFromAgentPayload {
  @NotNull
  private final Agent myAgent;
  @NotNull
  private final BuildType myBuildType;

  public UnassignBuildTypeFromAgentPayload(@NotNull Agent agent, @NotNull BuildType buildType) {
    myAgent = agent;
    myBuildType = buildType;
  }

  @NotNull
  public Agent getAgent() {
    return myAgent;
  }

  @NotNull
  public BuildType getBuildType() {
    return myBuildType;
  }
}