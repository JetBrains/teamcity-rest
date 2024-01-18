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

package jetbrains.buildServer.server.graphql.model.agentPool;

import java.util.function.BooleanSupplier;
import jetbrains.buildServer.util.impl.Lazy;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.server.rest.data.util.LazyUtil.lazy;

public class AgentPoolPermissions {
  private final Lazy<Boolean> myAuthorizeAgents;
  private final Lazy<Boolean> myManageProjects;
  private final Lazy<Boolean> myEnableAgents;
  private final Lazy<Boolean> myManageAgents;
  private final boolean myManagePool;

  public AgentPoolPermissions(@NotNull BooleanSupplier authorizeAgents,
                              @NotNull BooleanSupplier manageProjects,
                              @NotNull BooleanSupplier enableAgents,
                              @NotNull BooleanSupplier manageAgents, boolean managePool) {
    myAuthorizeAgents = lazy(() -> authorizeAgents.getAsBoolean());
    myManageProjects = lazy(() -> manageProjects.getAsBoolean());
    myEnableAgents = lazy(() -> enableAgents.getAsBoolean());
    myManageAgents = lazy(() -> manageAgents.getAsBoolean());
    myManagePool = managePool;
  }

  public boolean isAuthorizeAgents() {
    return myAuthorizeAgents.get();
  }

  public boolean isManageProjects() {
    return myManageProjects.get();
  }

  public boolean isManage() {
    return myManagePool;
  }

  public boolean isEnableAgents() {
    return myEnableAgents.get();
  }

  public boolean isManageAgents() {
    return myManageAgents.get();
  }
}