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

package jetbrains.buildServer.server.graphql.model.agentPool;

import jetbrains.buildServer.server.graphql.util.ObjectIdentificationNode;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractAgentPool implements ObjectIdentificationNode {
  @NotNull
  private final jetbrains.buildServer.serverSide.agentPools.AgentPool myRealPool;

  public AbstractAgentPool(@NotNull jetbrains.buildServer.serverSide.agentPools.AgentPool realPool) {
    myRealPool = realPool;
  }

  @Override
  public String getRawId() {
    return Integer.toString(myRealPool.getAgentPoolId());
  }

  @NotNull
  public String getName() {
    return myRealPool.getName();
  }

  public int getMaxAgentsNumber() {
    return myRealPool.getMaxAgents();
  }

  @NotNull
  public jetbrains.buildServer.serverSide.agentPools.AgentPool getRealPool() {
    return myRealPool;
  }
}
