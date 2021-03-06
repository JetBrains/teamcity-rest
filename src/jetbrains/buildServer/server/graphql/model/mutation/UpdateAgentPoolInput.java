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
import org.jetbrains.annotations.Nullable;

public class UpdateAgentPoolInput {
  @NotNull
  private final String myId;
  @Nullable
  private final String myName;
  @Nullable
  private final Integer myMaxAgents;

  public UpdateAgentPoolInput(@NotNull String id, @Nullable String name, @Nullable Integer maxAgents) {
    myId = id;
    myName = name;
    myMaxAgents = maxAgents;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public Integer getMaxAgents() {
    return myMaxAgents;
  }
}
