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

package jetbrains.buildServer.server.rest.request;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.model.user.Users;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path(UserRequest.API_USERS_URL)
public class UserRequest {
  public static final String REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS = "rest.request.checkAdditionalPermissionsForUsersAndGroups";
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private UserFinder myUserFinder;
  @Context @NotNull private DataUpdater myDataUpdater;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanContext myBeanContext;

  public static final String API_USERS_URL = Constants.API_URL + "/users";

  public static String getUserHref(final jetbrains.buildServer.users.User user) {
    //todo: investigate why "DOMAIN username" does not work as query parameter
//    this.href = "/httpAuth/api/users/" + user.getUsername();
    return API_USERS_URL + "/id:" + user.getId();
  }

  public static String getRoleAssignmentHref(final SUser user, final RoleEntry roleEntry, @Nullable final String scopeParam) {
    return getUserHref(user) + "/roles/" + roleEntry.getRole().getId() + "/" + RoleAssignment.getScopeRepresentation(scopeParam);
  }

  public static String getPropertiesHref(final SUser user) {
    return getUserHref(user) + "/properties";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Users serveUsers(@QueryParam("fields") String fields) {
    if (TeamCityProperties.getBooleanOrTrue(REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)){
      myUserFinder.checkViewAllUsersPermission();
    }
    return new Users(myDataProvider.getAllUsers(),  new Fields(fields), myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  public User createUser(User userData, @QueryParam("fields") String fields) {
    final SUser user = myDataUpdater.createUser(userData.getSubmittedUsername());
    myDataUpdater.modify(user, userData, myBeanContext);
    return new User(user,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}")
  @Produces({"application/xml", "application/json"})
  public User serveUser(@PathParam("userLocator") String userLocator, @QueryParam("fields") String fields) {
    if (TeamCityProperties.getBooleanOrTrue(REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)){
      myUserFinder.checkViewUserPermission(userLocator);
    }
    return new User(myUserFinder.getUser(userLocator),  new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{userLocator}")
  public void deleteUser(@PathParam("userLocator") String userLocator) {
    final SUser user = myUserFinder.getUser(userLocator);
    myDataProvider.getServer().getSingletonService(UserModel.class).removeUserAccount(user.getId());
  }

  @PUT
  @Path("/{userLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public User updateUser(@PathParam("userLocator") String userLocator, User userData, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getUser(userLocator);
    myDataUpdater.modify(user, userData, myBeanContext);
    return new User(user,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}/{field}")
  @Produces("text/plain")
  public String serveUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName) {
    if (TeamCityProperties.getBooleanOrTrue(REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)){
      myUserFinder.checkViewUserPermission(userLocator);
    }
    return User.getFieldValue(myUserFinder.getUser(userLocator), fieldName);
  }

  @PUT
  @Path("/{userLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName, String value) {
    final SUser user = myUserFinder.getUser(userLocator);
    return User.setFieldValue(user, fieldName, value);
  }

  @GET
  @Path("/{userLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveUserProperties(@PathParam("userLocator") String userLocator, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getUser(userLocator);

    return new Properties(User.getProperties(user), null, new Fields(fields));
  }

  @GET
  @Path("/{userLocator}/properties/{name}")
  @Produces("text/plain")
  public String serveUserProperty(@PathParam("userLocator") String userLocator, @PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, User.getProperties(myUserFinder.getUser(userLocator)), true, true);
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
    return BuildTypeUtil.getParameter(name, User.getProperties(myUserFinder.getUser(userLocator)), false, true);
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
    myUserFinder.checkViewUserPermission(userLocator); //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
    SUser user = myUserFinder.getUser(userLocator);
    return new RoleAssignments(user.getRoles(), user, myBeanContext);
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
    for (RoleAssignment roleAssignment : roleAssignments.roleAssignments) {
      user.addRole(RoleAssignment.getScope(roleAssignment.scope, myBeanContext), myDataProvider.getRoleById(roleAssignment.roleId));
    }
    return new RoleAssignments(user.getRoles(), user, myBeanContext);
  }

  @POST
  @Path("/{userLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public RoleAssignment addRole(@PathParam("userLocator") String userLocator, RoleAssignment roleAssignment) {
    SUser user = myUserFinder.getUser(userLocator);
    user.addRole(RoleAssignment.getScope(roleAssignment.scope, myBeanContext), myDataProvider.getRoleById(roleAssignment.roleId));
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleAssignment.roleId, roleAssignment.scope, myBeanContext), user, myBeanContext);
  }

  @GET
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  public RoleAssignment listRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    myUserFinder.checkViewUserPermission(userLocator);  //until http://youtrack.jetbrains.net/issue/TW-20071 is fixed
    SUser user = myUserFinder.getUser(userLocator);
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleId, scopeValue, myBeanContext), user, myBeanContext);
  }

  @DELETE
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public void deleteRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                         @PathParam("scope") String scopeValue) {
    SUser user = myUserFinder.getUser(userLocator);
    user.removeRole(RoleAssignment.getScope(scopeValue, myBeanContext), myDataProvider.getRoleById(roleId));
  }


  /**
   * @deprecated Use PUT instead, preserving POST for compatibility
   */
  @Deprecated
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
    user.addRole(RoleAssignment.getScope(scopeValue, myBeanContext), myDataProvider.getRoleById(roleId));
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleId, scopeValue, myBeanContext), user, myBeanContext);
  }

  @GET
  @Path("/{userLocator}/groups")
  @Produces({"application/xml", "application/json"})
  public Groups getGroups(@PathParam("userLocator") String userLocator, @QueryParam("fields") String fields) {
    myUserFinder.checkViewUserPermission(userLocator);
    SUser user = myUserFinder.getUser(userLocator);
    return new Groups(user.getUserGroups(),  new Fields(fields), myBeanContext);
  }

  /**
   * Replaces user's roles with the submitted ones
   */
  @PUT
  @Path("/{userLocator}/groups")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Groups replaceGroups(@PathParam("userLocator") String userLocator, Groups groups, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getUser(userLocator);
    myDataUpdater.replaceUserGroups(user, groups.getFromPosted(myDataProvider.getServer()));
    return new Groups(user.getUserGroups(),  new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{userLocator}/groups")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Group addGroup(@PathParam("userLocator") String userLocator, Group group, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getUser(userLocator);
    SUserGroup userGroup = group.getFromPosted(myBeanContext.getServiceLocator());
    userGroup.addUser(user);
    return new Group(userGroup,  new Fields(fields), myBeanContext);
  }

}
