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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class UserFinder {
  private static final Logger LOG = Logger.getInstance(UserFinder.class.getName());
  public static final String ID = "id";
  public static final String USERNAME = "username";
  @NotNull private final ServiceLocator myServiceLocator;

  public UserFinder(final @NotNull ServiceLocator serviceLocator) {
    myServiceLocator = serviceLocator;
  }

  @Nullable
  public SUser getUserIfNotNull(@Nullable final String userLocator) {
    return userLocator == null ? null : getUser(userLocator);
  }

  @NotNull
  public SUser getUser(String userLocator) {
    if (StringUtil.isEmpty(userLocator)) {
      throw new BadRequestException("Empty user locator is not supported.");
    }

    final UserModel userModel = myServiceLocator.getSingletonService(UserModel.class);
    final Locator locator = new Locator(userLocator);
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's username
      SUser user = userModel.findUserAccount(null, userLocator);
      if (user == null) {
        if (!"current".equals(userLocator)) {
          throw new NotFoundException("No user can be found by username '" + userLocator + "'.");
        }
        // support for predefined "current" keyword to get current user
        final SUser currentUser = getCurrentUser();
        if (currentUser == null) {
          throw new NotFoundException("No current user.");
        } else {
          return currentUser;
        }
      }
      return user;
    }

    Long id = locator.getSingleDimensionValueAsLong(ID);
    if (id != null) {
      SUser user = userModel.findUserById(id);
      if (user == null) {
        throw new NotFoundException("No user can be found by id '" + id + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("User locator '" + userLocator + "' has '" + ID + "' dimension and others. Others are ignored.");
      }
      return user;
    }

    String username = locator.getSingleDimensionValue(USERNAME);
    if (username != null) {
      SUser user = userModel.findUserAccount(null, username);
      if (user == null) {
        throw new NotFoundException("No user can be found by username '" + username + "'.");
      }
      return user;
    }
    throw new NotFoundException("User locator '" + userLocator + "' is not supported.");
  }

  @Nullable
  public SUser getCurrentUser() {
    return DataProvider.getCurrentUser(myServiceLocator);
  }

  public void checkViewUserPermission(String userLocator) {
    SUser user;
    try {
      user = getUser(userLocator);
    } catch (RuntimeException e) { // ensuring user without permissions could not get details on existing users by error messages
      checkViewAllUsersPermission();
      return;
    }

    checkViewUserPermission(user);
  }

  public void checkViewUserPermission(final @NotNull SUser user) {
    final jetbrains.buildServer.users.User currentUser = myServiceLocator.getSingletonService(DataProvider.class).getCurrentUser();
    if (currentUser != null && currentUser.getId() == user.getId()) {
      return;
    }
    checkViewAllUsersPermission();
  }

  public void checkViewAllUsersPermission() {
    myServiceLocator.getSingletonService(DataProvider.class).checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_USER_PROFILE, Permission.CHANGE_USER});
  }
}
