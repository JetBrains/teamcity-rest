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

package jetbrains.buildServer.server.rest.request;

import java.util.Map;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.model.user.Users;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import jetbrains.buildServer.util.StringUtil;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404 error
*/

@Path(UserRequest.API_USERS_URL)
public class UserRequest {
  @Context
  private DataProvider myDataProvider;
  @Context
  private DataUpdater myDataUpdater;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;

  public static final String API_USERS_URL = Constants.API_URL + "/users";

  public static String getUserHref(final jetbrains.buildServer.users.User user) {
    //todo: investigate why "DOMAIN username" does not work as query parameter
//    this.href = "/httpAuth/api/users/" + user.getUsername();
    return API_USERS_URL + "/id:" + user.getId();
  }

  public static String getRoleAssignmentHref(final RoleEntry roleEntry, final SUser user) {
    final RoleScope roleScope = roleEntry.getScope();
    return getUserHref(user) + "/roles/" + roleEntry.getRole().getId() + "/" + DataProvider.getScopeRepresentation(roleScope);
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Users serveUsers() {
    return new Users(myDataProvider.getAllUsers(), myApiUrlBuilder);
  }

  private void checkViewAllUsersPermission() {
    try {
      myDataProvider.checkGlobalPermission(Permission.VIEW_USER_PROFILE);
    } catch (AuthorizationFailedException e) {
      checkModifyAllUsersPermission();
    }
  }

  private void checkViewUserPermission(String userLocator) {
    SUser user;
    try {
      user = myDataProvider.getUser(userLocator);
    } catch (RuntimeException e) { // ensuring user without permisisons could not get details on existing users by error messages
      checkViewAllUsersPermission();
      return;
    }

    final jetbrains.buildServer.users.User currentUser = myDataProvider.getCurrentUser();
    if (user != null && currentUser != null && currentUser.getId() == user.getId()) {
      return;
    }
    checkViewAllUsersPermission();
  }

  private void checkModifyAllUsersPermission() {
    myDataProvider.checkGlobalPermission(jetbrains.buildServer.serverSide.auth.Permission.CHANGE_USER);
  }

  private void checkModifyUserPermission(String userLocator) {
    SUser user;
    try {
      user = myDataProvider.getUser(userLocator);
    } catch (RuntimeException e) { // ensuring user without permisisons could not get details on existing users by error messages
      checkModifyAllUsersPermission();
      return;
    }

    final jetbrains.buildServer.users.User currentUser = myDataProvider.getCurrentUser();
    if (user != null && currentUser != null && currentUser.getId() == user.getId()) {
      myDataProvider.checkGlobalPermission(jetbrains.buildServer.serverSide.auth.Permission.CHANGE_OWN_PROFILE);
      return;
    }
    checkModifyAllUsersPermission();
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  public User createUser(User userData) {
    final SUser user = myDataUpdater.createUser(userData.getSubmittedUsername());
    myDataUpdater.modify(user, userData);
    return new User(user, myApiUrlBuilder);
  }

  @GET
  @Path("/{userLocator}")
  @Produces({"application/xml", "application/json"})
  public User serveUser(@PathParam("userLocator") String userLocator) {
    return new User(myDataProvider.getUser(userLocator), myApiUrlBuilder);
  }

  @PUT
  @Path("/{userLocator}")
  @Consumes({"application/xml", "application/json"})
  public void updateUser(@PathParam("userLocator") String userLocator, User userData) {
    SUser user = myDataProvider.getUser(userLocator);
    myDataUpdater.modify(user, userData);
  }

  @GET
  @Path("/{userLocator}/{field}")
  @Produces("text/plain")
  public String serveUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName) {
    return User.getFieldValue(myDataProvider.getUser(userLocator), fieldName);
  }

  @PUT
  @Path("/{userLocator}/{field}")
  @Consumes("text/plain")
  public void setUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName, String value) {
    User.setFieldValue(myDataProvider.getUser(userLocator), fieldName, value);
  }

  @GET
  @Path("/{userLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveUserProperties(@PathParam("userLocator") String userLocator) {
    SUser user = myDataProvider.getUser(userLocator);

    return new Properties(User.getUserProperties(user));
  }

  @GET
  @Path("/{userLocator}/properties/{name}")
  @Produces("text/plain")
  public String serveUserProperties(@PathParam("userLocator") String userLocator, @PathParam("name") String parameterName) {
    SUser user = myDataProvider.getUser(userLocator);
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Property name cannot be empty.");
    }

    Map<String, String> parameters = User.getUserProperties(user);
    if (parameters.containsKey(parameterName)) {
      //TODO: process secure fields
      return parameters.get(parameterName);
    }
    throw new NotFoundException("No property with name '" + parameterName + "' is found.");
  }

  @PUT
  @Path("/{userLocator}/properties/{name}")
  @Consumes("text/plain")
  public void putUserProperty(@PathParam("userLocator") String userLocator,
                              @PathParam("name") String name,
                              String newValue) {
    SUser user = myDataProvider.getUser(userLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Property name cannot be empty.");
    }

    user.setUserProperty(new SimplePropertyKey(name), newValue);
  }

  @DELETE
  @Path("/{userLocator}/properties/{name}")
  public void removeUserProperty(@PathParam("userLocator") String userLocator,
                                 @PathParam("name") String name) {
    SUser user = myDataProvider.getUser(userLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Property name cannot be empty.");
    }

    user.deleteUserProperty(new SimplePropertyKey(name));
  }


  @GET
  @Path("/{userLocator}/roles")
  @Produces({"application/xml", "application/json"})
  public RoleAssignments listRoles(@PathParam("userLocator") String userLocator) {
    checkViewUserPermission(userLocator); //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
    SUser user = myDataProvider.getUser(userLocator);
    return new RoleAssignments(user.getRoles(), user, myApiUrlBuilder);
  }


  /**
   * @deprecated Use POST instead, preserving PUT for compatibility
   */
  @PUT
  @Path("/{userLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  public void addRolePut(@PathParam("userLocator") String userLocator, RoleAssignment roleAssignment) {
    addRole(userLocator, roleAssignment);
  }

  @POST
  @Path("/{userLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  public void addRole(@PathParam("userLocator") String userLocator, RoleAssignment roleAssignment) {
    SUser user = myDataProvider.getUser(userLocator);
    user.addRole(DataProvider.getScope(roleAssignment.scope), myDataProvider.getRoleById(roleAssignment.roleId));
  }

  @GET
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public RoleAssignment listRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    checkViewUserPermission(userLocator);  //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
    SUser user = myDataProvider.getUser(userLocator);
    return new RoleAssignment(myDataProvider.getUserRoleEntry(user, roleId, scopeValue), user, myApiUrlBuilder);
  }

  @DELETE
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public void deleteRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                         @PathParam("scope") String scopeValue) {
    SUser user = myDataProvider.getUser(userLocator);
    user.removeRole(DataProvider.getScope(scopeValue), myDataProvider.getRoleById(roleId));
  }


  /**
   * @deprecated Use PUT instead, preserving POST for compatibility
   */
  @POST
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public void addRoleSimplePost(@PathParam("userLocator") String userLocator,
                                @PathParam("roleId") String roleId,
                                @PathParam("scope") String scopeValue) {
    addRoleSimple(userLocator, roleId, scopeValue);
  }

  @PUT
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public void addRoleSimple(@PathParam("userLocator") String userLocator,
                            @PathParam("roleId") String roleId,
                            @PathParam("scope") String scopeValue) {
    SUser user = myDataProvider.getUser(userLocator);
    user.addRole(DataProvider.getScope(scopeValue), myDataProvider.getRoleById(roleId));
  }
}
