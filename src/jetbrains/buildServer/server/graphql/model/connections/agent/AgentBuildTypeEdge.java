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

package jetbrains.buildServer.server.graphql.model.connections.agent;

import graphql.relay.ConnectionCursor;
import graphql.relay.Edge;
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import org.jetbrains.annotations.NotNull;

public class AgentBuildTypeEdge implements Edge<BuildType> {
  @NotNull
  private final Edge<BuildType> myDelegate;

  public AgentBuildTypeEdge(@NotNull Edge<BuildType> delegate) {
      myDelegate = delegate;
  }

  @Override
  public BuildType getNode() {
    return myDelegate.getNode();
  }

  @Override
  public ConnectionCursor getCursor() {
    return myDelegate.getCursor();
  }

  @NotNull
  public BuildType getAgentBuildType() {
    return myDelegate.getNode();
  }
}
