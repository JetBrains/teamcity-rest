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

package jetbrains.buildServer.server.graphql.model.mutation.agentPool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UpdateAgentPoolInput {
  @NotNull
  private int myRawId;
  @Nullable
  private String myName;
  @Nullable
  private Integer myMaxAgentsNumber;

  public UpdateAgentPoolInput() { }

  public void setRawId(int rawId) {
    myRawId = rawId;
  }

  public void setName(@Nullable String name) {
    myName = name;
  }

  public void setMaxAgentsNumber(@Nullable Integer maxAgentsNumber) {
    myMaxAgentsNumber = maxAgentsNumber;
  }

  public int getRawId() {
    return myRawId;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public Integer getMaxAgentsNumber() {
    return myMaxAgentsNumber;
  }
}
