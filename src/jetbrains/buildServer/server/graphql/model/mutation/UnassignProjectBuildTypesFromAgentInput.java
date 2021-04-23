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

import org.jetbrains.annotations.NotNull;

public class UnassignProjectBuildTypesFromAgentInput {
  @NotNull
  private final String myAgentId;

  @NotNull
  private final String myProjectId;

  public UnassignProjectBuildTypesFromAgentInput(@NotNull String agentId, @NotNull String projectId) {
    myAgentId = agentId;
    myProjectId = projectId;
  }

  @NotNull
  public String getAgentId() {
    return myAgentId;
  }

  @NotNull
  public String getProjectId() {
    return myProjectId;
  }
}
