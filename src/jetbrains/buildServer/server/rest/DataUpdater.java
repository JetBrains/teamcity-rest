/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import jetbrains.buildServer.server.rest.data.RoleAssignment;
import jetbrains.buildServer.server.rest.data.RoleAssignments;
import jetbrains.buildServer.server.rest.data.UserData;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.SUser;

/**
 * @author Yegor.Yarko
 *         Date: 12.07.2009
 */
public class DataUpdater {
  private DataProvider myDataProvider;

  public DataUpdater(DataProvider dataProvider) {
    myDataProvider = dataProvider;
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
