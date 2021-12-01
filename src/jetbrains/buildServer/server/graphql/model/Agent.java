/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.model;

import jetbrains.buildServer.server.graphql.util.ObjectIdentificationNode;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;

public class Agent implements ObjectIdentificationNode {
  private final String myId;
  @NotNull
  private final String myName;
  private final boolean myAuthorized;

  public Agent(@NotNull SBuildAgent realAgent) {
    myId = Integer.toString(realAgent.getId());
    myName = realAgent.getName();
    myAuthorized = realAgent.isAuthorized();
  }

  public String getRawId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isAuthorized() {
    return myAuthorized;
  }
}
