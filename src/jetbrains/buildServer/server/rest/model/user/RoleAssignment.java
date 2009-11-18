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

package jetbrains.buildServer.server.rest.model.user;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */

@XmlRootElement(name = "role")
public class RoleAssignment {
  @XmlAttribute
  public String roleId;
  @XmlAttribute
  public String scope;
  @XmlAttribute
  public String href;

  public RoleAssignment() {
  }

  public RoleAssignment(RoleEntry roleEntry) {
    roleId = roleEntry.getRole().getId();
    scope = getScopeRepresentation(roleEntry.getScope());
  }

  public RoleAssignment(RoleEntry roleEntry, SUser user, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    roleId = roleEntry.getRole().getId();
    scope = getScopeRepresentation(roleEntry.getScope());
    href = apiUrlBuilder.getHref(roleEntry, user);
  }

  public RoleAssignment(RoleEntry roleEntry, UserGroup group, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    roleId = roleEntry.getRole().getId();
    scope = getScopeRepresentation(roleEntry.getScope());
    href = apiUrlBuilder.getHref(roleEntry, group);
  }

  private static String getScopeRepresentation(@NotNull final RoleScope scope) {
    return DataProvider.getScopeRepresentation(scope);
  }
}
