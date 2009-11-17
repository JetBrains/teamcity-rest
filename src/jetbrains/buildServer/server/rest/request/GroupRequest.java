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

package jetbrains.buildServer.server.rest.request;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.server.rest.model.group.Groups;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.auth.RoleScope;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produses 404
*/

@Path(GroupRequest.API_USER_GROUPS_URL)
public class GroupRequest {
  @Context
  private DataProvider myDataProvider;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;

  public static final String API_USER_GROUPS_URL = Constants.API_URL + "/userGroups";


  public static String getGroupHref(UserGroup userGroup) {
    return API_USER_GROUPS_URL + "/key:" + userGroup.getKey();
  }

  public static String getRoleAssignmentHref(final RoleEntry roleEntry, final UserGroup group) {
    final RoleScope roleScope = roleEntry.getScope();
    return getGroupHref(group) + "/roles/" + roleEntry.getRole().getId() +
           (roleScope.isGlobal() ? "/" + roleScope.getProjectId() : "");
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Groups serveGroups() {
    return new Groups(myDataProvider.getAllGroups(), myApiUrlBuilder);
  }

  @GET
  @Path("/{groupLocator}")
  @Produces({"application/xml", "application/json"})
  public Group serveGroup(@PathParam("groupLocator") String groupLocator) {
    return new Group(myDataProvider.getGroup(groupLocator), myApiUrlBuilder);
  }
}