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

package jetbrains.buildServer.server.graphql.model.mutation.agentPool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssignProjectWithAgentPoolInput {
  @NotNull
  private String myProjectRawId;
  private int myAgentPoolRawId;
  @Nullable
  private Boolean myExclusively;

  public void setProjectRawId(@NotNull String projectRawId) {
    myProjectRawId = projectRawId;
  }

  public void setAgentPoolRawId(int agentPoolRawId) {
    myAgentPoolRawId = agentPoolRawId;
  }

  @NotNull
  public String getProjectRawId() {
    return myProjectRawId;
  }

  public int getAgentPoolRawId() {
    return myAgentPoolRawId;
  }

  @Nullable
  public Boolean getExclusively() {
    return myExclusively;
  }

  public void setExclusively(@NotNull Boolean exclusively) {
    myExclusively = exclusively;
  }
}