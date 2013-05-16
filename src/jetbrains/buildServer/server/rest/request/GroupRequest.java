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
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.DataUpdater;
import jetbrains.buildServer.server.rest.data.UserGroupFinder;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
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
  @Context @NotNull private DataUpdater myDataUpdater;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;

  public static final String API_USER_GROUPS_URL = Constants.API_URL + "/userGroups";


  public static String getGroupHref(UserGroup userGroup) {
    return API_USER_GROUPS_URL + "/key:" + userGroup.getKey();
  }

  public static String getRoleAssignmentHref(final UserGroup group, final RoleEntry roleEntry, @Nullable final String scopeParam) {
    return getGroupHref(group) + "/roles/" + roleEntry.getRole().getId() + "/" + RoleAssignment.getScopeRepresentation(scopeParam);
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Groups serveGroups() {
    return new Groups(myUserGroupFinder.getAllGroups(), myApiUrlBuilder);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Group addGroup(Group description) {
    SUserGroup group = myDataUpdater.createUserGroup(description);
    return new Group(group, new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder));
  }

  @GET
  @Path("/{groupLocator}")
  @Produces({"application/xml", "application/json"})
  public Group serveGroup(@PathParam("groupLocator") String groupLocator) {
    return new Group(myUserGroupFinder.getGroup(groupLocator), new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder));
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
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    return new RoleAssignments(group.getRoles(), group, new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder));
  }

  /**
   * @deprecated Use POST instead, preserving PUT for compatibility
   */
  @PUT
  @Path("/{groupLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public RoleAssignments addRolePut(@PathParam("groupLocator") String groupLocator, RoleAssignments roleAssignments) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    for (RoleEntry roleEntry : group.getRoles()) {
      group.removeRole(roleEntry.getScope(), roleEntry.getRole());
    }
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    for (RoleAssignment roleAssignment : roleAssignments.roleAssignments) {
      group.addRole(RoleAssignment.getScope(roleAssignment.scope, context), myDataProvider.getRoleById(roleAssignment.roleId));
    }
    return new RoleAssignments(group.getRoles(), group, context);
  }

  @POST
  @Path("/{groupLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public RoleAssignment addRole(@PathParam("groupLocator") String groupLocator, RoleAssignment roleAssignment) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    group.addRole(RoleAssignment.getScope(roleAssignment.scope, context), myDataProvider.getRoleById(roleAssignment.roleId));
    return new RoleAssignment(DataProvider.getGroupRoleEntry(group, roleAssignment.roleId, roleAssignment.scope, context), group, context);
  }

  @GET
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  public RoleAssignment listRole(@PathParam("groupLocator") String groupLocator, @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    return new RoleAssignment(DataProvider.getGroupRoleEntry(group, roleId, scopeValue, context), group, context);
  }

  @DELETE
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  public void deleteRole(@PathParam("groupLocator") String groupLocator, @PathParam("roleId") String roleId,
                         @PathParam("scope") String scopeValue) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    group.removeRole(RoleAssignment.getScope(scopeValue, context), myDataProvider.getRoleById(roleId));
  }

  @POST
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  @Produces({"application/xml", "application/json"})
  public RoleAssignment addRoleSimple(@PathParam("groupLocator") String groupLocator,
                            @PathParam("roleId") String roleId,
                            @PathParam("scope") String scopeValue) {
    SUserGroup group = myUserGroupFinder.getGroup(groupLocator);
    final BeanContext context = new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder);
    group.addRole(RoleAssignment.getScope(scopeValue, context), myDataProvider.getRoleById(roleId));
    return new RoleAssignment(DataProvider.getGroupRoleEntry(group, roleId, scopeValue, context), group, context);
  }
}