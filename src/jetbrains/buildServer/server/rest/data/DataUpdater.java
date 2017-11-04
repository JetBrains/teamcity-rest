/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.groups.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.PartialUpdateError;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
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
      throw new BadRequestException("Cannot create user as user with the same username already exists", e);
    } catch (MaxNumberOfUserAccountsReachedException e) {
      throw new BadRequestException("Cannot create user as maximum user limit is reached", e);
    } catch (EmptyUsernameException e) {
      throw new BadRequestException("Cannot create user with empty username", e);
    }
  }
  public void modify(SUser user, jetbrains.buildServer.server.rest.model.user.User userData, @NotNull final ServiceLocator serviceLocator) {
    updateUserCoreFields(user, userData.getSubmittedUsername(), userData.getSubmittedName(), userData.getSubmittedEmail(),
                         userData.getSubmittedPassword());

    final ArrayList<Throwable> errors = new ArrayList<Throwable>();
    try {
      if (userData.getSubmittedRoles() != null) {
        removeAllRoles(user);
        addRoles(user, userData.getSubmittedRoles(), serviceLocator);
      }
    } catch (PartialUpdateError partialUpdateError) {
      errors.add(partialUpdateError);
    }

    try {
      if (userData.getSubmittedProperties() != null) {
        removeAllProperties(user);
        addProperties(user, userData.getSubmittedProperties());
      }
    } catch (PartialUpdateError partialUpdateError) {
      errors.add(partialUpdateError);
    }

    try {
      if (userData.getSubmittedGroups() != null) {
        replaceUserGroups(user, userData.getSubmittedGroups().getFromPosted(myDataProvider.getServer()));
      }
    } catch (PartialUpdateError partialUpdateError) {
      errors.add(partialUpdateError);
    }

    if (errors.size() != 0) {
      throw new PartialUpdateError("Partial error updating user " + user.describe(false), errors);
    }
  }

  public static void updateUserCoreFields(@NotNull final SUser user,
                                    @Nullable final String submittedUsername,
                                    @Nullable final String submittedName,
                                    @Nullable final String submittedEmail,
                                    @Nullable final String submittedPassword) {
    String newUsername = submittedUsername != null ? submittedUsername : user.getUsername();
    String newName = submittedName != null ? submittedName : user.getName();
    String newEmail = submittedEmail != null ? submittedEmail : user.getEmail();
    if (submittedUsername != null ||
        submittedName != null ||
        submittedEmail != null) {
      user.updateUserAccount(newUsername, newName, newEmail);
    }

    if (submittedPassword != null) {
      user.setPassword(submittedPassword);
    }
  }

  public SUserGroup createUserGroup(@Nullable final Group groupDescription, @NotNull final ServiceLocator serviceLocator) {
    myDataProvider.checkGlobalPermission(jetbrains.buildServer.serverSide.auth.Permission.CREATE_USERGROUP);
    if (groupDescription == null) {
      throw new BadRequestException("Empty payload received while group details are expected.");
    }
    if (groupDescription.childGroups != null || groupDescription.users != null || groupDescription.roleAssignments != null) {
      //href is also ignored but not reported...
      throw new BadRequestException("Only 'key', 'name' and 'description' attributes are supported when creating user groups.");
    }
    if (StringUtil.isEmpty(groupDescription.key)) {
      throw new BadRequestException("Attribute 'key' must not be empty when creating group.");
    }
    if (StringUtil.isEmpty(groupDescription.name)) {
      throw new BadRequestException("Attribute 'name' must not be empty when creating group.");
    }
    SUserGroup resultingGroup;
    try {
      resultingGroup = myGroupManager.createUserGroup(groupDescription.key, groupDescription.name,
                                                      groupDescription.description != null ? groupDescription.description : "");
    } catch (DuplicateKeyException e) {
      throw new BadRequestException(
        "Cannot create group as group with key '" + groupDescription.key + "': group with the same key already exists");
    } catch (DuplicateNameException e) {
      throw new BadRequestException(
        "Cannot create group as group with name '" + groupDescription.name + "': group with the same name already exists");
    }

    if (groupDescription.parentGroups != null) {
      try {
        Group.setGroupParents(resultingGroup, new LinkedHashSet<>(groupDescription.parentGroups.getFromPosted(serviceLocator)), false, serviceLocator);
      } catch (Exception e) {
        myGroupManager.deleteUserGroup(resultingGroup);
        throw new BadRequestException("Cannot create group with specified parents", e);
      }
    }

    return resultingGroup;
  }

  public void deleteUserGroup(final SUserGroup group) {
    myGroupManager.deleteUserGroup(group);
  }

  public void replaceUserGroups(final SUser user, final List<SUserGroup> groupsP) {
    TreeSet<SUserGroup> groupsToProcess = new TreeSet<SUserGroup>(groupsP);
    //removing
    for (UserGroup group : user.getUserGroups()) {
      final SUserGroup userGroup = (SUserGroup)group; //TeamCity API issue: cast
      if (!groupsToProcess.contains(userGroup)) {
        if (!myGroupManager.isAllUsersGroup(userGroup)) {
          userGroup.removeUser(user);
        }
      }
      groupsToProcess.remove(userGroup);
    }
    //adding
    for (UserGroup group : groupsToProcess) {
      ((SUserGroup)group).addUser(user);
    }
  }

  private void addProperties(final SUser user, @NotNull final Properties properties) {
    if (properties.properties == null) return;
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

  private void addRoles(final SUser user, final RoleAssignments roles, @NotNull final ServiceLocator serviceLocator) throws PartialUpdateError {
    final ArrayList<Throwable> errors = new ArrayList<Throwable>();
    for (RoleAssignment roleAssignment : roles.roleAssignments) {
      try {
        user.addRole(RoleAssignment.getScope(roleAssignment.scope, serviceLocator), myDataProvider.getRoleById(roleAssignment.roleId));
      } catch (Exception e) {
        errors.add(e);
      }
    }
    if (errors.size() != 0) {
      throw new PartialUpdateError("Partial error updating roles for user " + user.describe(false), errors);
    }
  }
}
