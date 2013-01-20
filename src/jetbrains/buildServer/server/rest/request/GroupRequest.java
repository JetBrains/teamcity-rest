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
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.server.rest.model.user.RoleAssignment;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.auth.RoleScope;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produces 404
*/

@Path(GroupRequest.API_USER_GROUPS_URL)
public class GroupRequest {
  @Context
  private DataProvider myDataProvider;
  @Context
  private DataUpdater myDataUpdater;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;

  public static final String API_USER_GROUPS_URL = Constants.API_URL + "/userGroups";


  public static String getGroupHref(UserGroup userGroup) {
    return API_USER_GROUPS_URL + "/key:" + userGroup.getKey();
  }

  public static String getRoleAssignmentHref(final RoleEntry roleEntry, final UserGroup group) {
    final RoleScope roleScope = roleEntry.getScope();
    return getGroupHref(group) + "/roles/" + roleEntry.getRole().getId() + "/" + DataProvider.getScopeRepresentation(roleScope);
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Groups serveGroups() {
    return new Groups(myDataProvider.getAllGroups(), myApiUrlBuilder);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Group addGroup(Group description) {
    SUserGroup group = myDataUpdater.createUserGroup(description);
    return new Group(group, myApiUrlBuilder);
  }

  @GET
  @Path("/{groupLocator}")
  @Produces({"application/xml", "application/json"})
  public Group serveGroup(@PathParam("groupLocator") String groupLocator) {
    return new Group(myDataProvider.getGroup(groupLocator), myApiUrlBuilder);
  }

  @DELETE
  @Path("/{groupLocator}")
  public void deleteGroup(@PathParam("groupLocator") String groupLocator) {
    myDataUpdater.deleteUserGroup(myDataProvider.getGroup(groupLocator));
  }

  @GET
  @Path("/{groupLocator}/roles")
  @Produces({"application/xml", "application/json"})
  public RoleAssignments listRoles(@PathParam("groupLocator") String groupLocator) {
    SUserGroup group = myDataProvider.getGroup(groupLocator);
    return new RoleAssignments(group.getRoles(), group, myApiUrlBuilder);
  }

  /**
   * @deprecated Use POST instead, preserving PUT for compatibility
   */
  @PUT
  @Path("/{groupLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  public void addRolePut(@PathParam("groupLocator") String groupLocator, RoleAssignment roleAssignment) {
    addRole(groupLocator, roleAssignment);
  }

  @POST
  @Path("/{groupLocator}/roles")
  @Consumes({"application/xml", "application/json"})
  public void addRole(@PathParam("groupLocator") String groupLocator, RoleAssignment roleAssignment) {
    SUserGroup group = myDataProvider.getGroup(groupLocator);
    group.addRole(DataProvider.getScope(roleAssignment.scope), myDataProvider.getRoleById(roleAssignment.roleId));
  }

  @GET
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  public RoleAssignment listRole(@PathParam("groupLocator") String groupLocator, @PathParam("roleId") String roleId,
                                 @PathParam("scope") String scopeValue) {
    SUserGroup group = myDataProvider.getGroup(groupLocator);
    return new RoleAssignment(myDataProvider.getGroupRoleEntry(group, roleId, scopeValue), group, myApiUrlBuilder);
  }

  @DELETE
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  public void deleteRole(@PathParam("groupLocator") String groupLocator, @PathParam("roleId") String roleId,
                         @PathParam("scope") String scopeValue) {
    SUserGroup group = myDataProvider.getGroup(groupLocator);
    group.removeRole(DataProvider.getScope(scopeValue), myDataProvider.getRoleById(roleId));
  }

  @POST
  @Path("/{groupLocator}/roles/{roleId}/{scope}")
  public void addRoleSimple(@PathParam("groupLocator") String groupLocator,
                            @PathParam("roleId") String roleId,
                            @PathParam("scope") String scopeValue) {
    SUserGroup group = myDataProvider.getGroup(groupLocator);
    group.addRole(DataProvider.getScope(scopeValue), myDataProvider.getRoleById(roleId));
  }
}