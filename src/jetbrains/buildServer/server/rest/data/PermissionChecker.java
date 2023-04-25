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

package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.util.text.StringUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.serverSide.impl.projects.ProjectUtil;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * @author Yegor.Yarko
 *         Date: 18.09.2014
 */
@JerseyContextSingleton
@Component("restPermissionChecker")
public class PermissionChecker {
  @NotNull private final SecurityContextEx mySecurityContext;
  @NotNull private final ProjectManager myProjectManager;

  public PermissionChecker(@NotNull final SecurityContextEx securityContext, @NotNull final ProjectManager projectManager) {
    mySecurityContext = securityContext;
    myProjectManager = projectManager;
  }

  public void checkGlobalPermission(@NotNull final Permission permission) throws AuthorizationFailedException {
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (hasGlobalPermission(authorityHolder, permission)) return;
    throwNoPermission(authorityHolder, permission);
  }

  public boolean hasGlobalPermission(@NotNull final Permission permission) throws AuthorizationFailedException {
    return hasGlobalPermission(mySecurityContext.getAuthorityHolder(), permission);
  }

  private boolean hasGlobalPermission(@NotNull final AuthorityHolder authorityHolder, @NotNull final Permission permission) {
    if (authorityHolder.isPermissionGrantedGlobally(permission)) {
      return true;
    }
    if (permission.isProjectAssociationSupported()) {
      Set<String> allProjectsInternalIds;
      try {
        allProjectsInternalIds = mySecurityContext.runAsSystem(() -> ProjectUtil.getProjectSelfAndChildrenInternalIds(myProjectManager.getRootProject()));
      } catch (Throwable throwable) {
        return false;
      }
      if (authorityHolder.isPermissionGrantedForAnyProject(permission) &&
          authorityHolder.isPermissionGrantedForAllProjects(allProjectsInternalIds, permission)) {
        return true;
      }
    }
    return false;
  }

  public void checkGlobalPermissionAnyOf(final Permission[] permissions) throws AuthorizationFailedException{
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    for (Permission permission : permissions) {
      if (hasGlobalPermission(authorityHolder, permission)) {
        return;
      }
    }

    throwNoPermission(authorityHolder, permissions);
  }

  private void throwNoPermission(@NotNull final AuthorityHolder authorityHolder, @NotNull final Permission... permissions) throws AuthorizationFailedException {
    String permissionsPart;
    if (permissions.length > 1) {
      permissionsPart = "any of the permissions granted globally: " + Arrays.toString(permissions);
    } else {
      permissionsPart = "global permission " + permissions[0];
    }
    throw new AuthorizationFailedException("User " + describe(authorityHolder) + " does not have " + permissionsPart);
  }

  @NotNull
  public static String describe(@NotNull final AuthorityHolder authorityHolder) {
    final User user = authorityHolder.getAssociatedUser();
    if (user != null){
      return user.describe(false);
    }
    if (authorityHolder instanceof BuildAuthorityHolder){
      final long associatedBuildId = ((BuildAuthorityHolder)authorityHolder).getAssociatedBuildId();
      return "<built-in user for build with id " + associatedBuildId + ">";
    }
    if (authorityHolder.equals(SecurityContextImpl.NO_PERMISSIONS)) {
      return "<no user, anonymous>";
    }
    return "<authority holder>";
  }

  @NotNull
  public AuthorityHolder getCurrent() {
    return mySecurityContext.getAuthorityHolder();
  }

  @NotNull
  public AccessChecker getServerActionChecker() {
    return mySecurityContext.getAccessChecker();
  }

  @NotNull
  public String getCurrentUserDescription() {
    return describe(getCurrent());
  }

  public void checkProjectPermission(@NotNull final Permission permission, @Nullable final String internalProjectId) throws AuthorizationFailedException{
    checkProjectPermission(permission, internalProjectId, null);
  }

  public void checkPermission(@NotNull final Permission permission, @NotNull final BuildPromotion buildPromotion) throws AuthorizationFailedException {
    try {
      final SBuildType buildType = buildPromotion.getBuildType();
      checkProjectPermission(permission, buildType == null ? null :  buildType.getProjectId(), null);
    } catch (AccessDeniedException e) {
      throw new AuthorizationFailedException(e.getMessage());
    }
  }

  public void checkProjectPermission(@NotNull final Permission permission,
                                     @Nullable final String internalProjectId,
                                     @Nullable final String additionalMessage) throws AuthorizationFailedException{
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (internalProjectId == null){
      if (authorityHolder.isPermissionGrantedGlobally(permission)){
        return;
      }
      throw new AuthorizationFailedException("No permission '" + permission + " is granted globally.");
    }
    if (!authorityHolder.isPermissionGrantedForProject(internalProjectId, permission)) {
      throw new AuthorizationFailedException("User " + authorityHolder.getAssociatedUser() + " does not have permission " + permission +
                                             " in project with internal id: '" + internalProjectId + "'" + (!StringUtil.isEmpty(additionalMessage) ? additionalMessage : ""));
    }
  }

  // workaround for http://youtrack.jetbrains.com/issue/TW-28306
  public boolean checkCanView(@NotNull final SVcsModification change) {
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (authorityHolder.isPermissionGrantedGlobally(Permission.VIEW_PROJECT)){
      return true;
    }
    return AuthUtil.hasReadAccessTo(authorityHolder, change);
  }

// There are no separate permissions for reading groups and roles
  public void checkViewAllUsersPermission() {
    checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_USER_PROFILE, Permission.CHANGE_USER});
  }

  public void checkCanEditBuildTypeOrTemplate(@NotNull BuildTypeOrTemplate target) {
    AccessChecker accessChecker = mySecurityContext.getAccessChecker();
    if(target.isBuildType()) {
      accessChecker.checkCanEditBuildType(Objects.requireNonNull(target.getBuildType()));
    } else {
      accessChecker.checkCanEditTemplate(Objects.requireNonNull(target.getTemplate()));
    }
  }

  public boolean isPermissionGranted(@NotNull final Permission permission, @Nullable final String internalProjectId) {
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    if (internalProjectId == null){
      return hasGlobalPermission(authorityHolder, permission);
    }
    return authorityHolder.isPermissionGrantedForProject(internalProjectId, permission);
  }

  public boolean hasPermissionInAnyProject(@NotNull final Permission permission) {
    final AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    return authorityHolder.isPermissionGrantedForAnyProject(permission);
  }

  public static boolean anyOfUsersHavePermissionForAnyOfProjects(@NotNull final List<SUser> users, @NotNull final Permission permission, @Nullable final List<SProject> projects) {
    if (users.isEmpty()) return false;
    for (SUser user : users) {
      if (projects == null || !permission.isProjectAssociationSupported()) {
        if (user.isPermissionGrantedGlobally(permission)) return true;
      } else {
        for (SProject project : projects) {
          if (user.isPermissionGrantedForProject(project.getProjectId(), permission)) return true;
        }
      }
    }
    return false;
  }


}
