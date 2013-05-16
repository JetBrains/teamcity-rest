/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.model.user.Users;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404 error
*/

@Path(UserRequest.API_USERS_URL)
public class UserRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private UserFinder myUserFinder;
  @Context @NotNull private DataUpdater myDataUpdater;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;

  public static final String API_USERS_URL = Constants.API_URL + "/users";

  public static String getUserHref(final jetbrains.buildServer.users.User user) {
    //todo: investigate why "DOMAIN username" does not work as query parameter
//    this.href = "/httpAuth/api/users/" + user.getUsername();
    return API_USERS_URL + "/id:" + user.getId();
  }

  public static String getRoleAssignmentHref(final SUser user, final RoleEntry roleEntry, @Nullable final String scopeParam) {
    return getUserHref(user) + "/roles/" + roleEntry.getRole().getId() + "/" + RoleAssignment.getScopeRepresentation(scopeParam);
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
      user = myUserFinder.getUser(userLocator);
    } catch (RuntimeException e) { // ensuring user without permissions could not get details on existing users by error messages
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
      user = myUserFinder.getUser(userLocator);
    } catch (RuntimeException e) { // ensuring user without permissions could not get details on existing users by error messages
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
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    myDataUpdater.modify(user, userData, context);
    return new User(user, context);
  }

  @GET
  @Path("/{userLocator}")
  @Produces({"application/xml", "application/json"})
  public User serveUser(@PathParam("userLocator") String userLocator) {
    return new User(myUserFinder.getUser(userLocator), new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder));
  }

  @PUT
  @Path("/{userLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public User updateUser(@PathParam("userLocator") String userLocator, User userData) {
    SUser user = myUserFinder.getUser(userLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    myDataUpdater.modify(user, userData, context);
    return new User(user, context);
  }

  @GET
  @Path("/{userLocator}/{field}")
  @Produces("text/plain")
  public String serveUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName) {
    return User.getFieldValue(myUserFinder.getUser(userLocator), fieldName);
  }

  @PUT
  @Path("/{userLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName, String value) {
    final SUser user = myUserFinder.getUser(userLocator);
    User.setFieldValue(user, fieldName, value);
    return User.getFieldValue(user, fieldName);
  }

  @GET
  @Path("/{userLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveUserProperties(@PathParam("userLocator") String userLocator) {
    SUser user = myUserFinder.getUser(userLocator);

    return new Properties(User.getUserProperties(user));
  }

  @GET
  @Path("/{userLocator}/properties/{name}")
  @Produces("text/plain")
  public String serveUserProperties(@PathParam("userLocator") String userLocator, @PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, User.getUserProperties(myUserFinder.getUser(userLocator)), true, true);
  }

  @PUT
  @Path("/{userLocator}/properties/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String putUserProperty(@PathParam("userLocator") String userLocator,
                              @PathParam("name") String name,
                              String newValue) {
    SUser user = myUserFinder.getUser(userLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Property name cannot be empty.");
    }

    user.setUserProperty(new SimplePropertyKey(name), newValue);
    return BuildTypeUtil.getParameter(name, User.getUserProperties(myUserFinder.getUser(userLocator)), false, true);
  }

  @DELETE
  @Path("/{userLocator}/properties/{name}")
  public void removeUserProperty(@PathParam("userLocator") String userLocator,
                                 @PathParam("name") String name) {
    SUser user = myUserFinder.getUser(userLocator);
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
    SUser user = myUserFinder.getUser(userLocator);
    return new RoleAssignments(user.getRoles(), user, new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder));
  }


  /**
   * Replaces user's roles with the submitted ones
   */
  @PUT
  @Path("/{userLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public RoleAssignments replaceRoles(@PathParam("userLocator") String userLocator, RoleAssignments roleAssignments) {
    SUser user = myUserFinder.getUser(userLocator);
    for (RoleEntry roleEntry : user.getRoles()) {
      user.removeRole(roleEntry.getScope(), roleEntry.getRole());
    }
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    for (RoleAssignment roleAssignment : roleAssignments.roleAssignments) {
      user.addRole(RoleAssignment.getScope(roleAssignment.scope, context), myDataProvider.getRoleById(roleAssignment.roleId));
    }
    return new RoleAssignments(user.getRoles(), user, context);
  }

  @POST
  @Path("/{userLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public RoleAssignment addRole(@PathParam("userLocator") String userLocator, RoleAssignment roleAssignment) {
    SUser user = myUserFinder.getUser(userLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    user.addRole(RoleAssignment.getScope(roleAssignment.scope, context), myDataProvider.getRoleById(roleAssignment.roleId));
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleAssignment.roleId, roleAssignment.scope, context), user, context);
  }

  @GET
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  public RoleAssignment listRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    checkViewUserPermission(userLocator);  //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
    SUser user = myUserFinder.getUser(userLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleId, scopeValue, context), user, context);
  }

  @DELETE
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public void deleteRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                         @PathParam("scope") String scopeValue) {
    SUser user = myUserFinder.getUser(userLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    user.removeRole(RoleAssignment.getScope(scopeValue, context), myDataProvider.getRoleById(roleId));
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
  @Produces({"application/xml", "application/json"})
  public RoleAssignment addRoleSimple(@PathParam("userLocator") String userLocator,
                            @PathParam("roleId") String roleId,
                            @PathParam("scope") String scopeValue) {
    SUser user = myUserFinder.getUser(userLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    user.addRole(RoleAssignment.getScope(scopeValue, context), myDataProvider.getRoleById(roleId));
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleId, scopeValue, context), user, context);
  }
}
