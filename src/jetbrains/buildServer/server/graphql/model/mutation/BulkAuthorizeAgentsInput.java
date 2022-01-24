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

package jetbrains.buildServer.server.graphql.model.mutation;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BulkAuthorizeAgentsInput {
  private List<Integer> myAgentRawIds;
  private String myReason;
  private Integer myTargetAgentPoolRawId;

  @NotNull
  public List<Integer> getAgentRawIds() {
    return myAgentRawIds;
  }

  public void setAgentRawIds(@NotNull List<Integer> agentRawIds) {
    myAgentRawIds = agentRawIds;
  }

  @Nullable
  public String getReason() {
    return myReason;
  }

  public void setReason(@Nullable String reason) {
    myReason = reason;
  }

  @Nullable
  public Integer getTargetAgentPoolRawId() {
    return myTargetAgentPoolRawId;
  }

  public void setTargetAgentPoolRawId(@Nullable Integer targetAgentPoolRawId) {
    myTargetAgentPoolRawId = targetAgentPoolRawId;
  }
}
