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
import jetbrains.buildServer.server.rest.model.user.UserData;
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
  public void modify(SUser user, UserData userData) {
    String newUsername = userData.username != null ? userData.username : user.getUsername();
    String newName = userData.name != null ? userData.name : user.getName();
    String newEmail = userData.email != null ? userData.email : user.getEmail();
    if (userData.username != null ||
        userData.name != null ||
        userData.email != null) {
      user.updateUserAccount(newUsername, newName, newEmail);
    }

    if (userData.password != null) {
      user.setPassword(userData.password);
    }

    if (userData.roles != null) {
      removeAllRoles(user);
      addRoles(user, userData.roles);
    }

    if (userData.properties != null) {
      removeAllProperties(user);
      addProperties(user, userData.properties);
    }

    if (userData.groups != null) {
      removeAllGroups(user);
      addGroups(user, userData.groups);
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
