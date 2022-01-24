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

package jetbrains.buildServer.server.graphql.model.agentPool.actions;

import java.util.List;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import org.jetbrains.annotations.NotNull;

public class MissingGlobalOrPerProjectPermission implements AgentPoolActionUnavailabilityReason{
  private final List<String> myGlobalPermissionNames;
  private final String myProjectPermissionName;
  private final ProjectsConnection myProjectsWithoutPermission;
  private final int myHiddenProjectWithoutPermissionCount;

  public MissingGlobalOrPerProjectPermission(@NotNull List<String> globalPermissionNames,
                                             @NotNull String projectPermissionName,
                                             @NotNull ProjectsConnection projectsWithoutPermission,
                                             int hiddenProjectWithoutPermissionCount) {
    myGlobalPermissionNames = globalPermissionNames;
    myProjectPermissionName = projectPermissionName;
    myProjectsWithoutPermission = projectsWithoutPermission;
    myHiddenProjectWithoutPermissionCount = hiddenProjectWithoutPermissionCount;
  }

  @NotNull
  public List<String> getGlobalPermissionNames() {
    return myGlobalPermissionNames;
  }

  @NotNull
  public String getProjectPermissionName() {
    return myProjectPermissionName;
  }

  @NotNull
  public ProjectsConnection getProjectsWithoutPermission() {
    return myProjectsWithoutPermission;
  }

  public int getHiddenProjectWithoutPermissionCount() {
    return myHiddenProjectWithoutPermissionCount;
  }
}
