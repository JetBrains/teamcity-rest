/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.group;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.user.RoleAssignments;
import jetbrains.buildServer.server.rest.model.user.Users;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@XmlRootElement(name = "group")
public class Group extends GroupRef {
  @XmlElement(name = "parent-groups")
  public Groups parentGroups;

  @XmlElement(name = "child-groups")
  public Groups childGroups;

  @XmlElement(name = "users")
  public Users users;

  @XmlElement(name = "roles")
  public RoleAssignments roleAssignments;

  public Group() {
  }

  public Group(SUserGroup userGroup, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    super(userGroup, apiUrlBuilder);
    parentGroups = new Groups(userGroup.getParentGroups(), apiUrlBuilder);
    childGroups = new Groups(userGroup.getDirectSubgroups(), apiUrlBuilder);
    users = new Users(userGroup.getDirectUsers(), apiUrlBuilder);
    roleAssignments = new RoleAssignments(userGroup.getRoles(), userGroup, apiUrlBuilder);
  }
}
