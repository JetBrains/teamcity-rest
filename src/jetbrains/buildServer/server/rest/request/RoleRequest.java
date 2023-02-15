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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.role.Role;
import jetbrains.buildServer.server.rest.model.role.Roles;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.RolesManager;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

@Path(RoleRequest.API_SUB_URL)
@Api("Role")
public class RoleRequest {
  @NotNull
  public static final String API_SUB_URL = Constants.API_URL + "/roles";
  @Context private ServiceLocator myServiceLocator;
  @Context @NotNull public PermissionChecker myPermissionChecker;
  @Context @NotNull private BeanContext myBeanContext;

  public static String getRoleHref(jetbrains.buildServer.serverSide.auth.Role role) {
    return API_SUB_URL + "/id:" + role.getId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all roles.", nickname = "getRoles")
  public Roles getRoles(@QueryParam("fields") String fields) {
    myPermissionChecker.checkViewAllUsersPermission();
    RolesManager rolesManager = myServiceLocator.getSingletonService(RolesManager.class);
    return new Roles(rolesManager.getAvailableRoles(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/id:{id}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get a role with specified id.", nickname = "getRole")
  public Role getRole(@PathParam("id") final String id, @QueryParam("fields") String fields) {
    myPermissionChecker.checkViewAllUsersPermission();
    RolesManager rolesManager = myServiceLocator.getSingletonService(RolesManager.class);

    jetbrains.buildServer.serverSide.auth.Role role = rolesManager.findRoleById(id);
    if (role == null) {
      throw new NotFoundException("Role '" + id + "' is not found");
    }
    return new Role(role, new Fields(fields), myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Create a new role.", nickname = "createRole")
  public Role createRole(final Role role, @QueryParam("fields") String fields) {
    myPermissionChecker.checkGlobalPermission(Permission.MANAGE_ROLES);
    RolesManager rolesManager = myServiceLocator.getSingletonService(RolesManager.class);

    if (StringUtil.isEmpty(role.name)) {
      throw new BadRequestException("name must be provided");
    }
    if (StringUtil.isNotEmpty(role.id)) {
      throw new BadRequestException("id is generated automatically");
    }

    List<Permission> permissions = null;
    List<jetbrains.buildServer.serverSide.auth.Role> included = null;
    if (role.permissions != null) {
      permissions = CollectionsUtil.convertCollection(
        role.permissions.items,
        source -> lookupPermission(source.id)
      );
    }
    if (role.included != null) {
      included = CollectionsUtil.convertCollection(
        role.included.items,
        source -> Optional.ofNullable(rolesManager.findRoleById(source.id))
                          .orElseThrow(() -> new BadRequestException("Role '" + source.id + "' does not exist"))
      );
    }

    jetbrains.buildServer.serverSide.auth.Role newRole = rolesManager.createNewRole(role.name);
    if (permissions != null) {
      newRole.addPermissions(permissions.toArray(new Permission[0]));
    }
    if (included != null) {
      for (jetbrains.buildServer.serverSide.auth.Role i : included) {
        // The core allows adding the same role twice
        if (Arrays.stream(newRole.getIncludedRoles()).noneMatch(r -> r.getId().equals(i.getId()))) {
          newRole.addIncludedRole(i);
        }
      }
    }

    return new Role(newRole, new Fields(fields, Fields.LONG), myBeanContext);
  }

  @DELETE
  @Path("/id:{id}")
  @ApiOperation(value = "Delete a role matching the id.", nickname = "deleteRole")
  public void deleteRole(@PathParam("id") final String id) {
    myPermissionChecker.checkGlobalPermission(Permission.MANAGE_ROLES);
    RolesManager rolesManager = myServiceLocator.getSingletonService(RolesManager.class);

    jetbrains.buildServer.serverSide.auth.Role role = rolesManager.findRoleById(id);
    if (role == null) {
      throw new NotFoundException("Role '" + id + "' not found");
    }
    rolesManager.deleteRole(role);
  }

  @PUT
  @Path("/id:{roleId}/permissions/{permissionId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Add a permission to a role.", nickname = "addPermission")
  public Role addPermission(
    @PathParam("roleId") final String roleId,
    @PathParam("permissionId") final String permissionId,
    @QueryParam("fields") String fields) {
    myPermissionChecker.checkGlobalPermission(Permission.MANAGE_ROLES);
    RolesManager rolesManager = myServiceLocator.getSingletonService(RolesManager.class);

    jetbrains.buildServer.serverSide.auth.Role role = rolesManager.findRoleById(roleId);
    if (role == null) {
      throw new NotFoundException("Role '" + roleId + "' not found");
    }

    role.addPermissions(lookupPermission(permissionId));
    return new Role(role, new Fields(fields, Fields.LONG), myBeanContext);
  }

  @DELETE
  @Path("/id:{roleId}/permissions/{permissionId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Remove a permission from a role.", nickname = "removePermission")
  public Role removePermission(
    @PathParam("roleId") final String roleId,
    @PathParam("permissionId") final String permissionId,
    @QueryParam("fields") String fields) {
    myPermissionChecker.checkGlobalPermission(Permission.MANAGE_ROLES);
    RolesManager rolesManager = myServiceLocator.getSingletonService(RolesManager.class);

    jetbrains.buildServer.serverSide.auth.Role role = rolesManager.findRoleById(roleId);
    if (role == null) {
      throw new NotFoundException("Role '" + roleId + "' not found");
    }

    role.removePermission(lookupPermission(permissionId));
    return new Role(role, new Fields(fields, Fields.LONG), myBeanContext);
  }

  @PUT
  @Path("/id:{roleId}/included/{includedId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Add an included role.", nickname = "addIncludedRole")
  public Role addIncludedRole(
    @PathParam("roleId") final String roleId,
    @PathParam("includedId") final String includedId,
    @QueryParam("fields") String fields) {
    myPermissionChecker.checkGlobalPermission(Permission.MANAGE_ROLES);
    RolesManager rolesManager = myServiceLocator.getSingletonService(RolesManager.class);

    jetbrains.buildServer.serverSide.auth.Role role = rolesManager.findRoleById(roleId);
    if (role == null) {
      throw new NotFoundException("Role '" + roleId + "' not found");
    }
    jetbrains.buildServer.serverSide.auth.Role includedRole = rolesManager.findRoleById(includedId);
    if (includedRole == null) {
      throw new BadRequestException("Role '" + includedId + "' not found");
    }

    if (Arrays.stream(role.getIncludedRoles()).noneMatch(r -> r.getId().equals(includedRole.getId()))) {
      role.addIncludedRole(includedRole);
    }
    return new Role(role, new Fields(fields, Fields.LONG), myBeanContext);
  }

  @DELETE
  @Path("/id:{roleId}/included/{includedId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Remove an included role.", nickname = "removeIncludedRole")
  public Role removeIncludedRole(
    @PathParam("roleId") final String roleId,
    @PathParam("includedId") final String includedId,
    @QueryParam("fields") String fields) {
    myPermissionChecker.checkGlobalPermission(Permission.MANAGE_ROLES);
    RolesManager rolesManager = myServiceLocator.getSingletonService(RolesManager.class);

    jetbrains.buildServer.serverSide.auth.Role role = rolesManager.findRoleById(roleId);
    if (role == null) {
      throw new NotFoundException("Role '" + roleId + "' not found");
    }
    jetbrains.buildServer.serverSide.auth.Role includedRole = rolesManager.findRoleById(includedId);
    if (includedRole == null) {
      throw new BadRequestException("Role '" + includedId + "' not found");
    }

    role.removeIncludedRole(includedRole);
    return new Role(role, new Fields(fields, Fields.LONG), myBeanContext);
  }

  @NotNull
  private Permission lookupPermission(final String id) {
    final Permission permission = Permission.lookupPermission(id.toUpperCase());
    if (permission == null) {
      throw new BadRequestException("Permission '" + id + "' does not exist");
    }
    return permission;
  }

  public void initForTests(
    @NotNull BeanContext context,
    @NotNull PermissionChecker permissionChecker) {
    myBeanContext = context;
    myServiceLocator = context.getServiceLocator();
    myPermissionChecker = permissionChecker;
  }
}
