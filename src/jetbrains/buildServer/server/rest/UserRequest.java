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

import com.sun.jersey.spi.resource.Singleton;
import com.intellij.openapi.diagnostic.Logger;

import javax.ws.rs.*;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.users.SUser;

/* todo: investigate logging issues:
    - disable initialization lines into stdout
    - too long number passed as finish for builds produses 404
*/

@Path("/httpAuth/api/users")
@Singleton
public class UserRequest {
  private final DataProvider myDataProvider;

  public UserRequest(DataProvider myDataProvider) {
    this.myDataProvider = myDataProvider;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Users serveUsers() {
    return new Users(myDataProvider.getAllUsers());
  }

  @GET
  @Path("/{userLocator}")
  @Produces({"application/xml", "application/json"})
  public User serveUser(@PathParam("userLocator") String userLocator) {
    return new User(myDataProvider.getUser(userLocator));
  }

  @PUT
  @Path("/{userLocator}")
  @Consumes({"application/xml", "application/json"})
  public void updateUser(@PathParam("userLocator") String userLocator, User userData) {
    SUser user = myDataProvider.getUser(userLocator);
    if (userData.id != null && userData.id != user.getId()) {
      throw new BadRequestException("User id is specified and does not match with user locator.");
    }

    String username = userData.username != null ? userData.username : user.getUsername();
    String name = userData.name != null ? userData.name : user.getName();
    String email = userData.email != null ? userData.email : user.getEmail();
    if (userData.username != null ||
            userData.name != null ||
            userData.email != null) {
      user.updateUserAccount(username, name, email);
    }
//    if (userData.roleAssignments != null){
//      user.removeRole
//    }
  }

  @PUT
  @Path("/{userLocator}/addRole")
  @Consumes({"application/xml", "application/json"})
  public void addRole(@PathParam("userLocator") String userLocator, RoleAssignment roleAssignment) {
    SUser user = myDataProvider.getUser(userLocator);
    user.addRole(myDataProvider.getScope(roleAssignment.scope), myDataProvider.getRoleById(roleAssignment.roleId));
  }


  //todo: rework this


  @POST
  @Path("/{userLocator}/addRole/{roleId}/{scope}")
  public void addRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId, @PathParam("scope") String scopeValue) {
    SUser user = myDataProvider.getUser(userLocator);
    user.addRole(myDataProvider.getScope(scopeValue), myDataProvider.getRoleById(roleId));
  }

  @POST
  @Path("/{userLocator}/addRole/{roleId}")
  public void addRole(@PathParam("userLocator") String userLocator, @PathParam("roleId") String roleId) {
    SUser user = myDataProvider.getUser(userLocator);
    user.addRole(RoleScope.globalScope(), myDataProvider.getRoleById(roleId));
  }
}
