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

package jetbrains.buildServer.server.rest.model.user;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.AuthenticationToken;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "permissionRestrictions")
@XmlType(name = "permissionRestrictions")
@ModelBaseType(ObjectType.LIST)
public class PermissionRestrictions {
  @Nullable
  @XmlElement(name = "permissionRestriction")
  public List<PermissionRestriction> myPermissionRestrictions;
  @Nullable
  @XmlAttribute
  public Integer count;

  public PermissionRestrictions() {
  }

  public PermissionRestrictions(
    @NotNull final AuthenticationToken.PermissionsRestriction permissionsRestriction,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext) {
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count", true, true),
                                                    () -> permissionsRestriction.getPermissions().values().stream().mapToInt(permissions -> permissions.toList().size()).sum());
    myPermissionRestrictions = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("permissionRestriction", false, true), () -> {
      final ProjectManager projectManager = beanContext.getSingletonService(ProjectManager.class);
      return permissionsRestriction.getPermissions().entrySet().stream().flatMap(roleScopePermissionsEntry -> {
        final RoleScope roleScope = roleScopePermissionsEntry.getKey();
        final SProject project;
        if (roleScope.isGlobal()) {
          project = null;
        } else {
          project = projectManager.findProjectById(roleScope.getProjectId());
        }

        Fields permissionRestrictionFields = fields.getNestedField("permissionRestriction");
        return roleScopePermissionsEntry.getValue()
                                        .toList()
                                        .stream()
                                        .map(permission -> new PermissionRestriction(project, permission, permissionRestrictionFields, beanContext));
      }).collect(Collectors.toList());
    });
  }
}
