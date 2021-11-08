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

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BulkAuthorizeAgentsInput {
  private List<Integer> myAgentIds;
  private String myReason;
  private Integer myTargetAgentPoolId;

  @NotNull
  public List<Integer> getAgentIds() {
    return myAgentIds;
  }

  public void setAgentIds(@NotNull List<Integer> agentIds) {
    myAgentIds = agentIds;
  }

  @Nullable
  public String getReason() {
    return myReason;
  }

  public void setReason(@Nullable String reason) {
    myReason = reason;
  }

  @Nullable
  public Integer getTargetAgentPoolId() {
    return myTargetAgentPoolId;
  }

  public void setTargetAgentPoolId(@Nullable Integer targetAgentPoolId) {
    myTargetAgentPoolId = targetAgentPoolId;
  }
}
