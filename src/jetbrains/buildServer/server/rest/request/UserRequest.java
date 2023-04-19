/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.controllers.login.RememberMe;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.impl.UserFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.UserGroupFinder;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.model.user.*;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path(UserRequest.API_USERS_URL)
@Api("User")
public class UserRequest {
  public static final String API_USERS_URL = Constants.API_URL + "/users";
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private UserFinder myUserFinder;
  @Context @NotNull private DataUpdater myDataUpdater;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanContext myBeanContext;

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
  @ApiOperation(value = "Get all users.", nickname = "getAllUsers")
  public Users serveUsers(@QueryParam("locator") String locator, @QueryParam("fields") String fields) {
    return new Users(myUserFinder.getItems(locator).getEntries(), new Fields(fields), myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Create a new user.", nickname = "addUser")
  public User createUser(User userData, @QueryParam("fields") String fields) {
    final SUser user = myDataUpdater.createUser(userData);
    // roles, groups and properties
    myDataUpdater.modify(user, userData, myBeanContext.getServiceLocator(), false);
    return new User(user, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get user matching the locator.", nickname = "getUser")
  public User serveUser(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                        @QueryParam("fields") String fields) {
    return new User(myUserFinder.getItem(userLocator, true), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{userLocator}")
  @ApiOperation(value = "Delete user matching the locator.", nickname = "deleteUser")
  public void deleteUser(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator) {
    final SUser deletee = myUserFinder.getItem(userLocator, true);
    final SUser deleter = myUserFinder.getCurrentUser();
    if (deleter == null || !deleter.hasAllPermissionsOf(deletee)) {
      throw new AccessDeniedException(deleter, "You cannot delete user that has more permissions than you");
    }
    myDataProvider.getServer().getSingletonService(UserModel.class).removeUserAccount(deletee.getId());
  }

  @PUT
  @Path("/{userLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Update user matching the locator.", nickname = "replaceUser")
  public User updateUser(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                         User userData,
                         @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
    myDataUpdater.modify(user, userData, myBeanContext.getServiceLocator(), true);
    return new User(user, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value = "Get a field of the matching user.", nickname = "getUserField")
  public String serveUserField(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                               @PathParam("field") String fieldName) {
    return User.getFieldValue(myUserFinder.getItem(userLocator, true), fieldName, myBeanContext.getServiceLocator());
  }

  @PUT
  @Path("/{userLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(value = "Update a field of the matching user.", nickname = "setUserField")
  public String setUserField(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                             @PathParam("field") String fieldName,
                             String value) {
    final SUser user = myUserFinder.getItem(userLocator, true);
    return User.setFieldValue(user, fieldName, value, myBeanContext.getServiceLocator());
  }

  @DELETE
  @Path("/{userLocator}/{field}")
  @ApiOperation(value = "Remove a property of the matching user.", nickname = "deleteUserField")
  public void deleteUserField(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                              @PathParam("field") String fieldName) {
    final SUser user = myUserFinder.getItem(userLocator, true);
    User.deleteField(user, fieldName);
  }

  //todo: use ParametersSubResource here
  @GET
  @Path("/{userLocator}/properties")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all properties of the matching user.", nickname = "getUserProperties")
  public Properties serveUserProperties(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                        @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);

    return new Properties(User.getProperties(user), null, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}/properties/{name}")
  @Produces("text/plain")
  @ApiOperation(value = "Get a property of the matching user.", nickname = "getUserProperty")
  public String serveUserProperty(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                  @PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, User.getProperties(myUserFinder.getItem(userLocator, true)), true, true, myBeanContext.getServiceLocator());
  }

  @PUT
  @Path("/{userLocator}/properties/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  @ApiOperation(value = "Update a property of the matching user.", nickname = "setUserProperty")
  public String putUserProperty(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
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
  @ApiOperation(value = "Remove a property of the matching user.", nickname = "removeUserProperty")
  public void removeUserProperty(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
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
  @ApiOperation(value = "Get all user roles of the matching user.", nickname = "getAllUserRoles")
  public RoleAssignments listRolesForUser(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator) {
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
  @ApiOperation(value = "Update user roles of the matching user.", nickname = "setUserRoles")
  public RoleAssignments replaceRoles(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                      RoleAssignments roleAssignments) {
    SUser user = myUserFinder.getItem(userLocator, true);
    for (RoleEntry roleEntry : user.getRoles()) {
      user.removeRole(roleEntry.getScope(), roleEntry.getRole());
    }
    for (RoleAssignment roleAssignment : roleAssignments.roleAssignments) {
      user.addRole(RoleAssignment.getScope(roleAssignment.scope, myBeanContext.getServiceLocator()),
                   RoleAssignment.getRoleById(roleAssignment.roleId, myBeanContext.getServiceLocator()));
    }
    return new RoleAssignments(user.getRoles(), user, myBeanContext);
  }

  @POST
  @Path("/{userLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Add a role to the matching user.", nickname = "addRoleToUser")
  public RoleAssignment addRoleToUser(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                      RoleAssignment roleAssignment) {
    SUser user = myUserFinder.getItem(userLocator, true);
    user.addRole(RoleAssignment.getScope(roleAssignment.scope, myBeanContext.getServiceLocator()),
                 RoleAssignment.getRoleById(roleAssignment.roleId, myBeanContext.getServiceLocator()));
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleAssignment.roleId, roleAssignment.scope, myBeanContext), user, myBeanContext);
  }

  @GET
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get a user role with the specific scope from the matching user.", nickname = "getUserRolesAtScope")
  public RoleAssignment listRoleForUser(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                        @PathParam("roleId") String roleId,
                                        @PathParam("scope") String scopeValue) {
    SUser user = myUserFinder.getItem(userLocator, true);
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleId, scopeValue, myBeanContext), user, myBeanContext);
  }

  @DELETE
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  @ApiOperation(value = "Remove a role with the specific scope from the matching user.", nickname = "removeUserRoleAtScope")
  public void deleteRoleFromUser(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                 @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    SUser user = myUserFinder.getItem(userLocator, true);
    user.removeRole(RoleAssignment.getScope(scopeValue, myBeanContext.getServiceLocator()), RoleAssignment.getRoleById(roleId, myBeanContext.getServiceLocator()));
  }


  /**
   * @deprecated Use PUT instead, preserving POST for compatibility
   */
  @Deprecated
  @POST
  @ApiOperation(value = "addRoleSimplePost", hidden = true)
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  public void addRoleSimplePost(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                @PathParam("roleId") String roleId,
                                @PathParam("scope") String scopeValue) {
    addRoleToUserSimple(userLocator, roleId, scopeValue);
  }

  @PUT
  @Path("/{userLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Add a role with the specific scope to the matching user.", nickname = "addRoleToUserAtScope")
  public RoleAssignment addRoleToUserSimple(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                            @PathParam("roleId") String roleId,
                                            @PathParam("scope") String scopeValue) {
    SUser user = myUserFinder.getItem(userLocator, true);
    user.addRole(RoleAssignment.getScope(scopeValue, myBeanContext.getServiceLocator()), RoleAssignment.getRoleById(roleId, myBeanContext.getServiceLocator()));
    return new RoleAssignment(DataProvider.getUserRoleEntry(user, roleId, scopeValue, myBeanContext), user, myBeanContext);
  }

  @GET
  @Path("/{userLocator}/groups")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all groups of the matching user.", nickname = "getAllUserGroups")
  public Groups getGroups(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                          @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
    return new Groups(user.getUserGroups(), new Fields(fields), myBeanContext);
  }

  /**
   * Replaces user's roles with the submitted ones
   */
  @PUT
  @Path("/{userLocator}/groups")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Update groups of the matching user.", nickname = "setUserGroups")
  public Groups replaceGroups(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                              Groups groups,
                              @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
    myDataUpdater.replaceUserGroups(user, groups.getFromPosted(myDataProvider.getServer()));
    return new Groups(user.getUserGroups(), new Fields(fields), myBeanContext);
  }

  @POST
  @Path("/{userLocator}/groups")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Add a user matching the locator to the group.", nickname = "addUserToGroup", hidden = true)
  public Group addGroupToUser(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                              Group group,
                              @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
    SUserGroup userGroup = group.getFromPosted(myBeanContext.getServiceLocator());
    userGroup.addUser(user);
    return new Group(userGroup, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{userLocator}/groups/{groupLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get a user group of the matching user.", nickname = "getUserGroup")
  public Group getGroup(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                        @PathParam("groupLocator") String groupLocator,
                        @QueryParam("fields") String fields) {
    if (TeamCityProperties.getBooleanOrTrue(UserFinder.REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)) {
      myUserFinder.checkViewAllUsersPermission();
    }
    SUser user = myUserFinder.getItem(userLocator, true);
    SUserGroup group = myBeanContext.getSingletonService(UserGroupFinder.class).getGroup(groupLocator);
    if (!user.getUserGroups().contains(group)) {
      throw new NotFoundException("User does not belong to the group");
    }
    return new Group(group, new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{userLocator}/groups/{groupLocator}")
  @ApiOperation(value = "Remove the matching user from the specific group.", nickname = "removeUserFromGroup")
  public void removeGroup(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                          @PathParam("groupLocator") String groupLocator,
                          @QueryParam("fields") String fields) {
    if (TeamCityProperties.getBooleanOrTrue(UserFinder.REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)) {
      myUserFinder.checkViewAllUsersPermission();
    }
    SUser user = myUserFinder.getItem(userLocator, true);
    SUserGroup group = myBeanContext.getSingletonService(UserGroupFinder.class).getGroup(groupLocator);
    if (!user.getUserGroups().contains(group)) {
      throw new NotFoundException("User does not belong to the group");
    }
    group.removeUser(user);
  }

  @POST
  @Path("/{userLocator}/tokens")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Create a new authentication token for the matching user.", nickname = "addUserToken")
  public Token createToken(Token token,
                           @PathParam("userLocator") String userLocator,
                           @QueryParam("fields") String fields) {
    if (token.getName() == null) {
      throw new BadRequestException("name cannot be empty");
    }
    final TokenAuthenticationModel tokenAuthenticationModel = myBeanContext.getSingletonService(TokenAuthenticationModel.class);
    final SUser user = myUserFinder.getItem(userLocator, true);
    try {
      final AuthenticationToken authenticationToken;
      if (token.getPermissionRestrictions() != null) {
        final List<PermissionRestriction> permissionRestrictions = token.getPermissionRestrictions().myPermissionRestrictions;
        if (permissionRestrictions == null) {
          throw new IllegalArgumentException("Malformed permission restrictions");
        }
        final Map<RoleScope, Permissions> restrictions = new HashMap<>();
        for (PermissionRestriction permissionRestriction : permissionRestrictions) {
          final RoleScope roleScope;
          if (BooleanUtils.isTrue(permissionRestriction.isGlobalScope)) {
            roleScope = RoleScope.globalScope();
          } else if (permissionRestriction.project != null && permissionRestriction.project.id != null) {
            final SProject project = myBeanContext.getSingletonService(ProjectManager.class).findProjectByExternalId(permissionRestriction.project.id);
            if (project == null) {
              throw new NotFoundException("Project not found for external id [" + permissionRestriction.project.id + "]");
            }
            roleScope = RoleScope.projectScope(project.getProjectId());
          } else {
            throw new IllegalArgumentException("Malformed permission restrictions, either isGlobalScope should be set to true or project should not be null");
          }
          if (permissionRestriction.permission == null || permissionRestriction.permission.id == null) {
            throw new IllegalArgumentException("Permission should not be null");
          }
          try {
            final Permission permission = Permission.valueOf(permissionRestriction.permission.id.toUpperCase());
            if (roleScope.isGlobal()) {
              if (!user.isPermissionGrantedGlobally(permission)) {
                throw new AuthorizationFailedException("User don't have " + permission + " to be restricted globally");
              }
            } else {
              if (!(user.isPermissionGrantedGlobally(permission) || user.isPermissionGrantedForProject(roleScope.getProjectId(), permission))) {
                throw new AuthorizationFailedException("User don't have permission " + permission + " to be restricted on project [" + roleScope.getProjectId() + "]");
              }
            }
            restrictions.merge(roleScope, new Permissions(permission), Permissions::mergeWith);
          } catch (IllegalArgumentException e) {
            throw new BadRequestException("Permission not found for input [" + permissionRestriction.permission.name + "]");
          }
        }
        if (permissionRestrictions.isEmpty()) {
          throw new BadRequestException("Malformed permission restrictions");
        }
        authenticationToken =
          tokenAuthenticationModel.createToken(user.getId(), token.getName(), token.getExpirationTime(), new AuthenticationToken.PermissionsRestriction(restrictions));
      } else {
        authenticationToken = tokenAuthenticationModel.createToken(user.getId(), token.getName(), token.getExpirationTime());
      }
      return new Token(authenticationToken, authenticationToken.getValue(), new Fields(fields), myBeanContext);
    } catch (AuthenticationTokenStorage.CreationException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @POST
  @Path("/{userLocator}/tokens/{name}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Create a new authentication token for the matching user.", nickname = "addUserToken", hidden = true)
  public Token createToken(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                           @PathParam("name") @NotNull final String name,
                           @QueryParam("fields") String fields) {
    final TokenAuthenticationModel tokenAuthenticationModel = myBeanContext.getSingletonService(TokenAuthenticationModel.class);
    final SUser user = myUserFinder.getItem(userLocator, true);
    try {
      final AuthenticationToken token = tokenAuthenticationModel.createToken(user.getId(), name, new Date(PermanentTokenConstants.NO_EXPIRE.getTime()));
      return new Token(token, token.getValue(), new Fields(fields), myBeanContext);
    } catch (AuthenticationTokenStorage.CreationException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @GET
  @Path("/{userLocator}/tokens")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all authentication tokens of the matching user.", nickname = "getUserTokens")
  public Tokens getTokens(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                          @QueryParam("fields") String fields) {
    final TokenAuthenticationModel tokenAuthenticationModel = myBeanContext.getSingletonService(TokenAuthenticationModel.class);
    SUser user = myUserFinder.getItem(userLocator, true);
    return new Tokens(tokenAuthenticationModel.getUserTokens(user.getId()), new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{userLocator}/tokens/{name}")
  @ApiOperation(value = "Remove an authentication token from the matching user.", nickname = "deleteUserToken")
  public void deleteToken(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                          @PathParam("name") @NotNull final String name,
                          @Context @NotNull final BeanContext beanContext) {
    final TokenAuthenticationModel tokenAuthenticationModel = myBeanContext.getSingletonService(TokenAuthenticationModel.class);
    SUser user = myUserFinder.getItem(userLocator, true);
    try {
      tokenAuthenticationModel.deleteToken(user.getId(), name);
    } catch (AuthenticationTokenStorage.DeletionException e) {
      throw new NotFoundException(e.getMessage());
    }
  }

  /**
   * Experimental use only
   */
  @GET
  @Path("/{userLocator}/debug/permissions")
  @Produces({"text/plain"})
  @ApiOperation(value = "getPermissions", hidden = true)
  public String getPermissions(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator) {
    if (!TeamCityProperties.getBoolean("rest.debug.permissionsList.enable")) {
      throw new BadRequestException("Request is not enabled. Set \"rest.debug.permissionsList.enable\" internal property to enable.");
    }
    SUser user = myUserFinder.getItem(userLocator, true);
    return DebugRequest.getRolesStringPresentation(user, myBeanContext.getSingletonService(ProjectManager.class));
  }

  /**
   * Experimental use only
   * Can be used to check whether the user has the permission(s) specified by "permissionLocator"
   * <p>
   * If project is specified, the permission is granted for this specific project, nothing can be derived about the subprojects
   * If the project is not specified, for project-level permission it means the permission is granted for all the projects on the server
   */
  @GET
  @Path("/{userLocator}/permissions")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all permissions effective for the matching user.", nickname = "getUserPermissions")
  public PermissionAssignments getPermissions(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
                                              @QueryParam("locator") String permissionLocator,
                                              @QueryParam("fields") String fields) {
    SUser user = myUserFinder.getItem(userLocator, true);
    PermissionChecker permissionChecker = myBeanContext.getSingletonService(PermissionChecker.class);
    permissionChecker.getServerActionChecker().checkCanViewUserProfile(user);
    return new PermissionAssignments(user, permissionLocator, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental use only
   */
  @DELETE
  @Path("/{userLocator}/debug/rememberMe")
  @Produces({"text/plain"})
  @ApiOperation(value = "Remove the RememberMe data of the matching user.", nickname = "removeUserRememberMe")
  public void deleteRememberMe(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator) {
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
