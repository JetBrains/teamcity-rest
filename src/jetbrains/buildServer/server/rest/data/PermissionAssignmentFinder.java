/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.util.List;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Note: this works with RESOLVED permissions, so no projects hierarchy should be considered: a permission to a project does not cause the permission granted for sub-projects
 * @author Yegor.Yarko
 * Date: 18/09/2017
 */
public class PermissionAssignmentFinder extends DelegatingFinder<PermissionAssignmentData> {
  private static final TypedFinderBuilder.Dimension<Boolean> GLOBAL = new TypedFinderBuilder.Dimension<>("global");
  private static final TypedFinderBuilder.Dimension<List<SProject>> PROJECT = new TypedFinderBuilder.Dimension<>("project");
  private static final TypedFinderBuilder.Dimension<Permission> PERMISSION = new TypedFinderBuilder.Dimension<>("permission"); //todo: support List

  public PermissionAssignmentFinder(@NotNull final AuthorityHolder authorityHolder, @NotNull final ServiceLocator serviceLocator) {
    TypedFinderBuilder<PermissionAssignmentData> builder = new TypedFinderBuilder<PermissionAssignmentData>();

    builder.dimensionBoolean(GLOBAL).description("return only globally assigned permissions").withDefault("any").
      valueForDefaultFilter(permissionAssignment -> permissionAssignment.getInternalProjectId() == null);

    //todo: support not found project with internal id
    builder.dimensionProjects(PROJECT, serviceLocator).description("project the permission is granted for, note that a permission granted for a project might not be granted to it's sub-projects").
      filter((projects, permissionA) -> {
        String internalProjectId = permissionA.getInternalProjectId();
        if (internalProjectId == null && permissionA.getPermission().isProjectAssociationSupported()) return true;
        return projects.stream().map(project -> project.getProjectId()).anyMatch(id -> id.equals(internalProjectId));
      }); //can improve performance by remembering set of projects for the entire run - might need improvement in TypedFinderBuilder
    //todo: filter projects by those user can see

    builder.dimensionEnum(PERMISSION, Permission.class).description("id of the permission to filter the results by").
      valueForDefaultFilter(p -> p.getPermission());

    builder.multipleConvertToItemHolder(TypedFinderBuilder.DimensionCondition.ALWAYS, dimensions -> getPermissions(dimensions, authorityHolder, serviceLocator));

    PermissionChecker permissionChecker = serviceLocator.getSingletonService(PermissionChecker.class);

    builder.filter(TypedFinderBuilder.DimensionCondition.ALWAYS, dimensions -> new ItemFilter<PermissionAssignmentData>() {
      @Override
      public boolean shouldStop(@NotNull final PermissionAssignmentData item) {
        return false;
      }

      @Override
      public boolean isIncluded(@NotNull final PermissionAssignmentData item) {
        return item.getInternalProjectId() == null || permissionChecker.isPermissionGranted(Permission.VIEW_PROJECT, item.getInternalProjectId());
      }
    });

    //todo: sort with global on top and projects sorted with root on top
    setDelegate(builder.build());
  }

  @NotNull
  private FinderDataBinding.ItemHolder<PermissionAssignmentData> getPermissions(@NotNull final TypedFinderBuilder.DimensionObjects dimensions,
                                                                                @NotNull final AuthorityHolder authorityHolder,
                                                                                @NotNull final ServiceLocator serviceLocator) {
    /* The rest of the code in this method is mostly performance optimization producing the same results (with possibly changed sorting).
    if (true) {
      List<Permission> globalPermissions = authorityHolder.getGlobalPermissions().toList();
      Set<Permission> globalPermissionsSet = new HashSet<>(globalPermissions); //TeamCity API issue: this set is used to exclude global permissions from project-level ones
      return FinderDataBinding.getItemHolder(Stream.concat(
        globalPermissions.stream().map(p -> new PermissionAssignmentData(p)),
        authorityHolder.getProjectsPermissions().entrySet().stream().flatMap(
          entry -> entry.getValue().toList().stream().filter(p -> !globalPermissionsSet.contains(p)).map(p -> new PermissionAssignmentData(p, entry.getKey())))));
    }
    */

    List<Permission> permissions_raw = dimensions.get(PERMISSION);
    List<List<SProject>> projects_raw = dimensions.get(PROJECT);

    if (projects_raw != null && !projects_raw.isEmpty() && projects_raw.size() > 1) {
      throw new BadRequestException("Multiple projects dimensions are not supported");
    }
    @Nullable List<SProject> projects = projects_raw == null || projects_raw.isEmpty() ? null : projects_raw.get(0);

    if (permissions_raw != null && !permissions_raw.isEmpty() && permissions_raw.size() > 1) {
      throw new BadRequestException("Multiple permissions dimensions are not supported");
    }
    List<Permission> permissions = permissions_raw; // permissions_raw is ANDed, permissions is ORed, but so far it is not supported: todo implement

    Stream<PermissionAssignmentData> result = Stream.empty();
     List<Boolean> global_raw = dimensions.get(GLOBAL);
     if (global_raw != null && !global_raw.isEmpty() && global_raw.size() > 1) {
       throw new BadRequestException("Multiple global dimensions are not supported");
     }
     Boolean global = global_raw == null ? null : global_raw.get(0);

    if ((permissions == null || permissions.isEmpty())) {
       if (projects == null) {
        if (global == null || global) {
          result = Stream.concat(result, authorityHolder.getGlobalPermissions().toList().stream().map(p -> new PermissionAssignmentData(p)));
        }
        if (global == null || !global) {
          result = Stream.concat(result, authorityHolder.getProjectsPermissions().entrySet().stream().flatMap(entry -> entry.getValue().toList().stream().filter(p -> p.isProjectAssociationSupported()).map(p -> new PermissionAssignmentData(p, entry.getKey()))));
        }
        return FinderDataBinding.getItemHolder(result);
      }

      if (global == null || global) {
        result = Stream.concat(result, authorityHolder.getGlobalPermissions().toList().stream().filter(p -> p.isProjectAssociationSupported()).map(p -> new PermissionAssignmentData(p)));
      }
      if (global == null || !global) {
        result = Stream.concat(result, projects.stream().flatMap(project -> {
          Permissions projectPermissions = authorityHolder.getProjectsPermissions().get(project.getProjectId());
          return projectPermissions == null ? Stream.empty() : projectPermissions.toList().stream().filter(p -> p.isProjectAssociationSupported()).map(p -> new PermissionAssignmentData(p, project.getProjectId()));
        }));
      }
      return FinderDataBinding.getItemHolder(result);
     }

    if (projects == null) {
      if (global == null || global) {
        result = Stream.concat(result, permissions.stream().filter(p -> authorityHolder.isPermissionGrantedGlobally(p)).map(p -> new PermissionAssignmentData(p)));
      }
      if (global == null || !global) {
        List<SProject> allProjects = serviceLocator.getSingletonService(ProjectManager.class).getProjects();
        result = Stream.concat(result, permissions.stream().filter(p -> p.isProjectAssociationSupported()).flatMap(p -> allProjects.stream().filter(project -> {
          Permissions projectPermissions = authorityHolder.getProjectsPermissions().get(project.getProjectId());
          return projectPermissions != null && projectPermissions.contains(p);
        }).map(project -> new PermissionAssignmentData(p, project.getProjectId()))));
      }
      return FinderDataBinding.getItemHolder(result);
    }

    if (global == null || global) {
      result = Stream.concat(result, permissions.stream().filter(p -> p.isProjectAssociationSupported()).filter(p -> authorityHolder.isPermissionGrantedGlobally(p)).map(p -> new PermissionAssignmentData(p)));
    }
    if (global == null || !global) {
      result = Stream.concat(result, projects.stream().flatMap(project -> permissions.stream().filter(p -> p.isProjectAssociationSupported()).filter(p -> {
                Permissions projectPermissions = authorityHolder.getProjectsPermissions().get(project.getProjectId());
                return projectPermissions != null && projectPermissions.contains(p);
              }).map(p -> new PermissionAssignmentData(p, project.getProjectId()))));
    }
    return FinderDataBinding.getItemHolder(result);
  }

}
