/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

public class AssignBuildTypeWithAgentInput {
  private int myAgentRawId;
  @NotNull
  private String myBuildTypeRawId;

  public void setAgentRawId(int agentRawId) {
    myAgentRawId = agentRawId;
  }

  public void setBuildTypeRawId(@NotNull String buildTypeRawId) {
    myBuildTypeRawId = buildTypeRawId;
  }

  public int getAgentRawId() {
    return myAgentRawId;
  }

  @NotNull
  public String getBuildTypeRawId() {
    return myBuildTypeRawId;
  }
}