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

package jetbrains.buildServer.server.rest.request;

import com.intellij.openapi.util.text.StringUtil;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.data.UserGroupFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypeUtil;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.SimplePropertyKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(GroupRequest.API_USER_GROUPS_URL)
public class GroupRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private UserGroupFinder myUserGroupFinder;
  @Context @NotNull private UserFinder myUserFinder;
  @Context @NotNull private DataUpdater myDataUpdater;
  @Context @NotNull private BeanContext myBeanContext;

  public static final String API_USER_GROUPS_URL = Constants.API_URL + "/userGroups";


  public static String getGroupHref(UserGroup userGroup) {
    return API_USER_GROUPS_URL + "/key:" + userGroup.getKey();
  }

  public static String getRoleAssignmentHref(final UserGroup group, final RoleEntry roleEntry, @Nullable final String scopeParam) {
    return getGroupHref(group) + "/roles/" + roleEntry.getRole().getId() + "/" + RoleAssignment.getScopeRepresentation(scopeParam);
  }

  public static String getPropertiesHref(final UserGroup group) {
    return getGroupHref(group) + "/properties";
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Groups serveGroups(@QueryParam("fields") String fields) {
    if (TeamCityProperties.getBooleanOrTrue(UserRequest.REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)){
      myUserFinder.checkViewAllUsersPermission();
    }
    return new Groups(myUserGroupFinder.getAllGroups(), new Fields(fields), myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Group addGroup(Group description, @QueryParam("fields") String fields) {
    SUserGroup group = myDataUpdater.createUserGroup(description);
    return new Group(group,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{groupLocator}")
  @Produces({"application/xml", "application/json"})
  public Group serveGroup(@PathParam("groupLocator") String groupLocator, @QueryParam("fields") String fields) {
    if (TeamCityProperties.getBooleanOrTrue(UserRequest.REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)){
      myUserFinder.checkViewAllUsersPermission();
    }
    return new Group(myUserGroupFinder.getGroup(groupLocator),  new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{groupLocator}")
  public void deleteGroup(@PathParam("groupLocator") String groupLocator) {
    myDataUpdater.deleteUserGroup(myUserGroupFinder.getGroup(groupLocator));
  }

  @GET
  @Path("/{groupLocator}/roles")
  @Produces({"application/xml", "application/json"})
  public RoleAssignments listRoles(@PathParam("groupLocator") String groupLocator) {
    if (TeamCityProperties.getBooleanOrTrue(UserRequest.REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)){
      myUserFinder.checkViewAllUsersPermission();
    }
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    return new RoleAssignments(group.getRoles(), group, myBeanContext);
  }

  /**
   * @deprecated Use POST instead, preserving PUT for compatibility
   */
  @Deprecated
  @PUT
  @Path("/{groupLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public RoleAssignments addRolePut(@PathParam("groupLocator") String groupLocator, RoleAssignments roleAssignments) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    for (RoleEntry roleEntry : group.getRoles()) {
      group.removeRole(roleEntry.getScope(), roleEntry.getRole());
    }
    for (RoleAssignment roleAssignment : roleAssignments.roleAssignments) {
      group.addRole(RoleAssignment.getScope(roleAssignment.scope, myBeanContext), myDataProvider.getRoleById(roleAssignment.roleId));
    }
    return new RoleAssignments(group.getRoles(), group, myBeanContext);
  }

  @POST
  @Path("/{groupLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public RoleAssignment addRole(@PathParam("groupLocator") String groupLocator, RoleAssignment roleAssignment) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    group.addRole(RoleAssignment.getScope(roleAssignment.scope, myBeanContext), myDataProvider.getRoleById(roleAssignment.roleId));
    return new RoleAssignment(DataProvider.getGroupRoleEntry(group, roleAssignment.roleId, roleAssignment.scope, myBeanContext), group, myBeanContext);
  }

  @GET
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  public RoleAssignment listRole(@PathParam("groupLocator") String groupLocator, @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    if (TeamCityProperties.getBooleanOrTrue(UserRequest.REST_CHECK_ADDITIONAL_PERMISSIONS_ON_USERS_AND_GROUPS)){
      myUserFinder.checkViewAllUsersPermission();
    }
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    return new RoleAssignment(DataProvider.getGroupRoleEntry(group, roleId, scopeValue, myBeanContext), group, myBeanContext);
  }

  @DELETE
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  public void deleteRole(@PathParam("groupLocator") String groupLocator, @PathParam("roleId") String roleId,
                         @PathParam("scope") String scopeValue) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    group.removeRole(RoleAssignment.getScope(scopeValue, myBeanContext), myDataProvider.getRoleById(roleId));
  }

  @POST
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  public RoleAssignment addRoleSimple(@PathParam("groupLocator") String groupLocator,
                            @PathParam("roleId") String roleId,
                            @PathParam("scope") String scopeValue) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    group.addRole(RoleAssignment.getScope(scopeValue, myBeanContext), myDataProvider.getRoleById(roleId));
    return new RoleAssignment(DataProvider.getGroupRoleEntry(group, roleId, scopeValue, myBeanContext), group, myBeanContext);
  }
  
  @GET
  @Path("/{groupLocator}/properties")
  @Produces({"application/xml", "application/json"})
  public Properties getProperties(@PathParam("groupLocator") String groupLocator, @QueryParam("fields") String fields) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    return new Properties(User.getProperties(group), null, new Fields(fields));
  }

  @GET
  @Path("/{groupLocator}/properties/{name}")
  @Produces("text/plain")
  public String serveUserProperties(@PathParam("groupLocator") String groupLocator, @PathParam("name") String parameterName) {
    return BuildTypeUtil.getParameter(parameterName, User.getProperties( myUserGroupFinder.getGroup(groupLocator)), true, true);
  }

  @PUT
  @Path("/{groupLocator}/properties/{name}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String putUserProperty(@PathParam("groupLocator") String groupLocator,
                              @PathParam("name") String name,
                              String newValue) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Property name cannot be empty.");
    }

    group.setGroupProperty(new SimplePropertyKey(name), newValue);
    return BuildTypeUtil.getParameter(name, User.getProperties(group), false, true);
  }

  @DELETE
  @Path("/{groupLocator}/properties/{name}")
  public void removeUserProperty(@PathParam("groupLocator") String groupLocator,
                                 @PathParam("name") String name) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    if (StringUtil.isEmpty(name)) {
      throw new BadRequestException("Property name cannot be empty.");
    }

    group.deleteGroupProperty(new SimplePropertyKey(name));
  }
  /**
   * Experimental use only
   */
  @GET
  @Path("/{groupLocator}/debug/permissions")
  @Produces({"text/plain"})
  public String getPermissions(@PathParam("groupLocator") String groupLocator) {
    if (!TeamCityProperties.getBoolean("rest.debug.permissionsList.enable")) {
      throw new BadRequestException("Request is not enabled. Set \"rest.debug.permissionsList.enable\" internal property to enable.");
    }
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    return DebugRequest.getRolesStringPresentation(group, myBeanContext.getSingletonService(ProjectManager.class));
  }
}