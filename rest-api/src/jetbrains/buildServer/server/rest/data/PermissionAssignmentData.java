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

package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 18/09/2017
 */
public class PermissionAssignmentData implements Loggable {

  @NotNull
  private final Permission myPermission;

  @Nullable
  private final String myInternalProjectId;

  /**
   * Creates global permission assignment (for project-related permission means that the permission is granted for all the projects)
   *
   * @param permission
   */
  public PermissionAssignmentData(@NotNull final Permission permission) {
    myPermission = permission;
    myInternalProjectId = null;
  }

  /**
   * Creates project-level permission assignment. It does not mean that he permission is granted for all the sub-projects
   */
  public PermissionAssignmentData(@NotNull final Permission permission, @NotNull final String internalProjectId) {
    myPermission = permission;
    myInternalProjectId = internalProjectId;
  }

  @NotNull
  public Permission getPermission() {
    return myPermission;
  }

  @Nullable
  public String getInternalProjectId() {
    return myInternalProjectId;
  }

  @NotNull
  @Override
  public String describe(final boolean verbose) {
    return myPermission.name() + (myInternalProjectId == null ? ", global" : ", projectId: " + myInternalProjectId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PermissionAssignmentData that = (PermissionAssignmentData)o;

    if (myPermission != that.myPermission) return false;
    return myInternalProjectId != null ? myInternalProjectId.equals(that.myInternalProjectId) : that.myInternalProjectId == null;
  }

  @Override
  public int hashCode() {
    int result = myPermission.hashCode();
    result = 31 * result + (myInternalProjectId != null ? myInternalProjectId.hashCode() : 0);
    return result;
  }
}