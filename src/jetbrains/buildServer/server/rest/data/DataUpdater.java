/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.groups.UserGroupManager;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.group.GroupRef;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 12.07.2009
 */
public class DataUpdater {
  private final DataProvider myDataProvider;
  private final UserGroupManager myGroupManager;
  private final UserModel myUserModel;

  public DataUpdater(DataProvider dataProvider, UserGroupManager groupManager, UserModel userModel) {
    myDataProvider = dataProvider;
    myGroupManager = groupManager;
    myUserModel = userModel;
  }

  public SUser createUser(@Nullable final String username){
    myDataProvider.checkGlobalPermission(jetbrains.buildServer.serverSide.auth.Permission.CREATE_USER);
    if (StringUtil.isEmpty(username)){
      throw new BadRequestException("Username must not be empty when creating user.");
    }
    try {
      return myUserModel.createUserAccount(null, username); //realm is hardly ever used in the system
    } catch (DuplicateUserAccountException e) {
      throw new BadRequestException("Cannot create user as user with the same username already exists.", e);
    } catch (MaxNumberOfUserAccountsReachedException e) {
      throw new BadRequestException("Cannot create user as maximum user limit is reached.", e);
    } catch (EmptyUsernameException e) {
      throw new BadRequestException("Cannot create user with empty username.", e);
    }
  }
  public void modify(SUser user, jetbrains.buildServer.server.rest.model.user.User userData) {
    String newUsername = userData.getSubmittedUsername() != null ? userData.getSubmittedUsername() : user.getUsername();
    String newName = userData.getSubmittedName() != null ? userData.getSubmittedName() : user.getName();
    String newEmail = userData.getSubmittedEmail() != null ? userData.getSubmittedEmail() : user.getEmail();
    if (userData.getSubmittedUsername() != null ||
        userData.getSubmittedName() != null ||
        userData.getSubmittedEmail() != null) {
      user.updateUserAccount(newUsername, newName, newEmail);
    }

    if (userData.getSubmittedPassword() != null) {
      user.setPassword(userData.getSubmittedPassword());
    }

    if (userData.getSubmittedRoles() != null) {
      removeAllRoles(user);
      addRoles(user, userData.getSubmittedRoles());
    }

    if (userData.getSubmittedProperties() != null) {
      removeAllProperties(user);
      addProperties(user, userData.getSubmittedProperties());
    }

    if (userData.getSubmittedGroups() != null) {
      removeAllGroups(user);
      addGroups(user, userData.getSubmittedGroups());
    }
  }

  private void addGroups(final SUser user, final Groups groups) {
    for (GroupRef group : groups.groups) {
      final SUserGroup foundGroup = myGroupManager.findUserGroupByKey(group.key);
      if (foundGroup != null) {
        foundGroup.addUser(user);
      } else {
        throw new BadRequestException("Can't find group by key'" + group.key + "'");
      }
    }
  }

  private void removeAllGroups(final SUser user) {
    for (UserGroup group : user.getUserGroups()) {
      if (!myGroupManager.isAllUsersGroup((SUserGroup)group)) //todo (TeamCity) need to cast
      ((SUserGroup)group).removeUser(user);
    }
  }

  private void addProperties(final SUser user, final Properties properties) {
    Map<PropertyKey, String> convertedProperties = new HashMap<PropertyKey, String>(properties.properties.size());
    for (Property listItem : properties.properties) {
      convertedProperties.put(new SimplePropertyKey(listItem.name), listItem.value);
    }
    user.setUserProperties(convertedProperties);
  }

  private void removeAllProperties(final SUser user) {
    for (Map.Entry<PropertyKey, String> propertyKey : user.getProperties().entrySet()) {
      user.deleteUserProperty(propertyKey.getKey());
    }
  }

  private void removeAllRoles(final SUser user) {
    for (RoleEntry roleEntry : user.getRoles()) {
      user.removeRole(roleEntry.getScope(), roleEntry.getRole());
    }
  }

  private void addRoles(final SUser user, final RoleAssignments roles) {
    for (RoleAssignment roleAssignment : roles.roleAssignments) {
      user.addRole(DataProvider.getScope(roleAssignment.scope), myDataProvider.getRoleById(roleAssignment.roleId));
    }
  }
}
