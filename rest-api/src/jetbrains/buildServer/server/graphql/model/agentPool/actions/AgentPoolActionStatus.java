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

package jetbrains.buildServer.server.graphql.model.agentPool.actions;

import org.jetbrains.annotations.Nullable;

public class AgentPoolActionStatus {
  public static AgentPoolActionStatus available() {
    return new AgentPoolActionStatus(true, null);
  }

  public static AgentPoolActionStatus unavailable(@Nullable AgentPoolActionUnavailabilityReason reason) {
    return new AgentPoolActionStatus(false, reason);
  }

  private final boolean myAvailable;
  private final AgentPoolActionUnavailabilityReason myReason;
  private AgentPoolActionStatus(boolean isAvailable, @Nullable AgentPoolActionUnavailabilityReason reason) {
    myAvailable = isAvailable;
    myReason = reason;
  }

  public boolean isAvailable() {
    return myAvailable;
  }

  @Nullable
  public AgentPoolActionUnavailabilityReason getUnavailabilityReason() {
    return myReason;
  }
}