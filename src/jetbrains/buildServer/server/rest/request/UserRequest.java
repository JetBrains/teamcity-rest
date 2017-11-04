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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.controllers.login.RememberMe;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.model.user.*;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path(UserRequest.API_USERS_URL)
@Api("User")
public class UserRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private UserFinder myUserFinder;
  @Context @NotNull private DataUpdater myDataUpdater;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanContext myBeanContext;

  public static final String API_USERS_URL = Constants.API_URL + "/users";

  public static String getUserHref(@NotNull final jetbrains.buildServer.users.User user) {
    //todo: investigate why "DOMAIN username" does not work as query parameter
//    this.href = "/httpAuth/api/users/" + user.getUsername();
    return API_USERS_URL + "/" + UserFinder.getLocator(user);
  }

  public static String getRoleAssignmentHref(final SUser user, final RoleEntry roleEntry, @Nullable final String scopeParam) {
    return getUserHref(user) + "/roles/" + roleEntry.getRole().getId() + "/" + RoleAssignment.getScopeRepresentation(scopeParam);
  }

  public static String getPropertiesHref(final SUser user) {
    return getUserHref(user) + "/properties";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Users serveUsers(@QueryParam("locator") String locator, @QueryParam("fields") String fields) {
    return new Users(myUserFinder.getItems(locator).myEntries,  new Fields(fields), myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  public User createUser(User userData, @QueryParam("fields") String fields) {
    final SUser user = myDataUpdater.createUser(userData.getSubmittedUsername());
    myDataUpdater.modify(user, userData, myBeanContext.getServiceLocator());
    return new User(user,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}")
  @Produces({"application/xml", "application/json"})
  public User serveUser(@PathParam("userLocator") String userLocator, @QueryParam("fields") String fields) {
    return new User(myUserFinder.getItem(userLocator, true), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{userLocator}")
  public void deleteUser(@PathParam("userLocator") String userLocator) {
    final SUser user = myUserFinder.getItem(userLocator, true);
    myDataProvider.getServer().getSingletonService(UserModel.class).removeUserAccount(user.getId());
  }

  @PUT
  @Path("/{userLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public User updateUser(@PathParam("userLocator") String userLocator, User userData, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
    myDataUpdater.modify(user, userData, myBeanContext.getServiceLocator());
    return new User(user,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}/{field}")
  @Produces("text/plain")
  public String serveUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName) {
    return User.getFieldValue(myUserFinder.getItem(userLocator, true), fieldName);
  }

  @PUT
  @Path("/{userLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName, String value) {
    final SUser user = myUserFinder.getItem(userLocator, true);
    return User.setFieldValue(user, fieldName, value);
  }

  @DELETE
  @Path("/{userLocator}/{field}")
  public void deleteUserField(@PathParam("userLocator") String userLocator, @PathParam("field") String fieldName) {
    final SUser user = myUserFinder.getItem(userLocator, true);
    User.deleteField(user, fieldName);
  }

  //todo: use ParametersSubResource here
  @GET
  @Path("/{userLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties serveUserProperties(@PathParam("userLocator") String userLocator, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);

    return new Properties(User.getProperties(user), null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}/properties/{name}")
  @Produces("text/plain")
  public String serveUserProperty(@PathParam("userLocator") String userLocator, @PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, User.getProperties(myUserFinder.getItem(userLocator, true)), true, true, myBeanContext.getServiceLocator());
  }

  @PUT
  @Path("/{userLocator}/properties/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String putUserProperty(@PathParam("userLocator") String userLocator,
                              @PathParam("name") String name,
                              String newValue) {
    SUser user = myUserFinder.getItem(userLocator, true);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Property name cannot be empty.");
    }

    user.setUserProperty(new SimplePropertyKey(name), newValue);
    return BuildTypeUtil.getParameter(name, User.getProperties(myUserFinder.getItem(userLocator, true)), false, true, myBeanContext.getServiceLocator());
  }

  @DELETE
  @Path("/{userLocator}/properties/{name}")
  public void removeUserProperty(@PathParam("userLocator") String userLocator,
                                 @PathParam("name") String name) {
    SUser user = myUserFinder.getItem(userLocator, true);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Property name cannot be empty.");
    }

    user.deleteUserProperty(new SimplePropertyKey(name));
  }


  @GET
  @Path("/{userLocator}/roles")
  @Produces({"application/xml", "application/json"})
  public RoleAssignments listRoles(@PathParam("userLocator") String userLocator) {
    SUser user = myUserFinder.getItem(userLocator, true);
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
    SUser user = myUserFinder.getItem(userLocator, true);
    for (RoleEntry roleEntry : user.getRoles()) {
      user.removeRole(roleEntry.getScope(), roleEntry.getRole());
    }
    for (RoleAssignment roleAssignment : roleAssignments.roleAssignments) {
      user.addRole(RoleAssignment.getScope(roleAssignment.scope, myBeanContext.getServiceLocator()), myDataProvider.getRoleById(roleAssignment.roleId));
    }
    return new RoleAssignments(user.getRoles(), user, myBeanContext);
  }

  @POST
  @Path("/{userLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public RoleAssignment addRole(@PathParam("userLocator") String userLocator, RoleAssignment roleAssignment) {
    SUser user = myUserFinder.getItem(userLocator, true);
    user.addRole(RoleAssignment.getScope(roleAssignment.scope, myBeanContext.getServiceLocator()), myDataProvider.getRoleById(roleAssignment.roleId));
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleAssignment.roleId, roleAssignment.scope, myBeanContext), user, myBeanContext);
  }

  @GET
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  public RoleAssignment listRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    SUser user = myUserFinder.getItem(userLocator, true);
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleId, scopeValue, myBeanContext), user, myBeanContext);
  }

  @DELETE
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public void deleteRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId,
                         @PathParam("scope") String scopeValue) {
    SUser user = myUserFinder.getItem(userLocator, true);
    user.removeRole(RoleAssignment.getScope(scopeValue, myBeanContext.getServiceLocator()), myDataProvider.getRoleById(roleId));
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
    SUser user = myUserFinder.getItem(userLocator, true);
    user.addRole(RoleAssignment.getScope(scopeValue, myBeanContext.getServiceLocator()), myDataProvider.getRoleById(roleId));
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleId, scopeValue, myBeanContext), user, myBeanContext);
  }

  @GET
  @Path("/{userLocator}/groups")
  @Produces({"application/xml", "application/json"})
  public Groups getGroups(@PathParam("userLocator") String userLocator, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
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
    SUser user = myUserFinder.getItem(userLocator, true);
    myDataUpdater.replaceUserGroups(user, groups.getFromPosted(myDataProvider.getServer()));
    return new Groups(user.getUserGroups(),  new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{userLocator}/groups")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Group addGroup(@PathParam("userLocator") String userLocator, Group group, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
    SUserGroup userGroup = group.getFromPosted(myBeanContext.getServiceLocator());
    userGroup.addUser(user);
    return new Group(userGroup,  new Fields(fields), myBeanContext);
  }

  /**
   * Experimental use only
   */
  @GET
  @Path("/{userLocator}/debug/permissions")
  @Produces({"text/plain"})
  public String getPermissions(@PathParam("userLocator") String userLocator) {
    if (!TeamCityProperties.getBoolean("rest.debug.permissionsList.enable")) {
      throw new BadRequestException("Request is not enabled. Set \"rest.debug.permissionsList.enable\" internal property to enable.");
    }
    SUser user = myUserFinder.getItem(userLocator, true);
    return DebugRequest.getRolesStringPresentation(user, myBeanContext.getSingletonService(ProjectManager.class));
  }

  /**
   * Experimental use only
   * Can be used to check whether the user has the permission(s) specified by "permissionLocator"
   *
   * If project is specified, the permission is granted for this specific project, nothing can be derived about the subprojects
   * If the project is not specified, for project-level permission it means the permission is granted for all the projects on the server
   */
  @GET
  @Path("/{userLocator}/permissions")
  @Produces({"application/xml", "application/json"})
  public PermissionAssignments getPermissions(@PathParam("userLocator") String userLocator, @QueryParam("locator") String permissionLocator, @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
    PermissionChecker permissionChecker = myBeanContext.getSingletonService(PermissionChecker.class);
    ServerAuthUtil.checkCanViewUserProfile(permissionChecker.getCurrent(), user);
    return new PermissionAssignments(user, permissionLocator, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental use only
   */
  @DELETE
  @Path("/{userLocator}/debug/rememberMe")
  @Produces({"text/plain"})
  public void deleteRememberMe(@PathParam("userLocator") String userLocator) {
    SUser user = myUserFinder.getItem(userLocator, true);
    PermissionChecker permissionChecker = myBeanContext.getSingletonService(PermissionChecker.class);
    jetbrains.buildServer.users.User currentUser = permissionChecker.getCurrent().getAssociatedUser();
    if (currentUser == null || user.getId() != currentUser.getId()) {
      permissionChecker.checkGlobalPermission(Permission.CHANGE_USER);
    }

    myBeanContext.getSingletonService(RememberMe.class).deleteAllForUser(user.getId());
  }

  public void initForTests(@NotNull final BeanContext beanContext) {
    myBeanContext = beanContext;
    myUserFinder = beanContext.getSingletonService(UserFinder.class);
  }
}
