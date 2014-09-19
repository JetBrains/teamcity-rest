/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.Arrays;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 18.09.2014
 */
public class PermissionChecker {
  @NotNull private final SecurityContext mySecurityContext;

  public PermissionChecker(@NotNull final SecurityContext securityContext) {
    mySecurityContext = securityContext;
  }

  public void checkGlobalPermission(final Permission permission) throws AuthorizationFailedException {
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (!authorityHolder.isPermissionGrantedForAnyProject(permission)) {
      throw new AuthorizationFailedException(
        "User " + authorityHolder.getAssociatedUser() + " does not have global permission " + permission);
    }
  }

  public void checkGlobalPermissionAnyOf(final Permission[] permissions) throws AuthorizationFailedException{
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    for (Permission permission : permissions) {
      if (authorityHolder.isPermissionGrantedForAnyProject(permission)) {
        return;
      }
    }

    throw new AuthorizationFailedException(
      "User " + authorityHolder.getAssociatedUser() + " does not have any of the permissions granted globally: " + Arrays.toString(permissions));
  }

  public void checkProjectPermission(@NotNull final Permission permission, @Nullable final String internalProjectId) throws AuthorizationFailedException{
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (internalProjectId == null){
      if (authorityHolder.isPermissionGrantedGlobally(permission)){
        return;
      }
      throw new AuthorizationFailedException("No permission '" + permission + " is granted globally.");
    }
    if (!authorityHolder.isPermissionGrantedForProject(internalProjectId, permission)) {
      throw new AuthorizationFailedException("User " + authorityHolder.getAssociatedUser() + " does not have permission " + permission +
                                             " in project with internal id: '" + internalProjectId + "'");
    }
  }

}
