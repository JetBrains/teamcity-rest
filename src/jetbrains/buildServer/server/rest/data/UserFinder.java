/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23.03.13
 */
public class UserFinder extends AbstractFinder<SUser>{
  public static final String USERNAME = "username";

  @NotNull private final UserModel myUserModel;
  @NotNull private final PermissionChecker myPermissionChecker;
  @NotNull private final SecurityContext mySecurityContext;

  public UserFinder(@NotNull final UserModel userModel,
                    @NotNull final PermissionChecker permissionChecker,
                    @NotNull final SecurityContext securityContext) {
    super(DIMENSION_ID, USERNAME, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    myUserModel = userModel;
    myPermissionChecker = permissionChecker;
    mySecurityContext = securityContext;
  }

  //@NotNull
  //@Override
  //public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
  //  final Locator result = super.createLocator(locatorText, locatorDefaults);
  //  result.addHiddenDimensions("password"); //experimental
  //  return result;
  //}
  //
  @NotNull
  public String getItemLocator(@NotNull final SUser user) {
    return UserFinder.getLocator(user);
  }

  @NotNull
  public static String getLocator(@NotNull final User user) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(user.getId()));
  }

  @Nullable
  @Override
  protected SUser findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's username
      @SuppressWarnings("ConstantConditions") @NotNull String singleValue = locator.getSingleValue();
      SUser user = myUserModel.findUserAccount(null, singleValue);
      if (user == null) {
        if (!"current".equals(singleValue)) {
          throw new NotFoundException("No user can be found by username '" + singleValue + "'.");
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

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      SUser user = myUserModel.findUserById(id);
      if (user == null) {
        throw new NotFoundException("No user can be found by id '" + id + "'.");
      }
      return user;
    }

    String username = locator.getSingleDimensionValue(USERNAME);
    if (username != null) {
      SUser user = myUserModel.findUserAccount(null, username);
      if (user == null) {
        throw new NotFoundException("No user can be found by username '" + username + "'.");
      }
      return user;
    }

    return null;
  }

  @NotNull
  @Override
  protected ItemFilter<SUser> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SUser> result = new MultiCheckerFilter<SUser>();

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      result.add(new FilterConditionChecker<SUser>() {
        public boolean isIncluded(@NotNull final SUser item) {
          return id.equals(item.getId());
        }
      });
    }

    String username = locator.getSingleDimensionValue(USERNAME);
    if (username != null) {
      result.add(new FilterConditionChecker<SUser>() {
         public boolean isIncluded(@NotNull final SUser item) {
           return username.equalsIgnoreCase(item.getUsername());
         }
       });
    }

    return result;
  }

  @NotNull
  @Override
  protected ItemHolder<SUser> getPrefilteredItems(@NotNull final Locator locator) {
    return getItemHolder(myUserModel.getAllUsers().getUsers());
  }

  @Nullable
  public SUser getCurrentUser() {
    //also related API: SessionUser.getUser(request)
    final User associatedUser = mySecurityContext.getAuthorityHolder().getAssociatedUser();
    if (associatedUser == null){
      return null;
    }
    if (SUser.class.isAssignableFrom(associatedUser.getClass())){
      return (SUser)associatedUser;
    }
    return myUserModel.findUserAccount(null, associatedUser.getUsername());
  }

  public void checkViewUserPermission(String userLocator) {
    SUser user;
    try {
      user = getItem(userLocator);
    } catch (RuntimeException e) { // ensuring user without permissions could not get details on existing users by error messages
      checkViewAllUsersPermission();
      return;
    }

    checkViewUserPermission(user);
  }

  public void checkViewUserPermission(final @NotNull SUser user) {
    final jetbrains.buildServer.users.User currentUser = getCurrentUser();
    if (currentUser != null && currentUser.getId() == user.getId()) {
      return;
    }
    checkViewAllUsersPermission();
  }

  public void checkViewAllUsersPermission() {
    myPermissionChecker.checkGlobalPermissionAnyOf(new Permission[]{Permission.VIEW_USER_PROFILE, Permission.CHANGE_USER});
  }
}
