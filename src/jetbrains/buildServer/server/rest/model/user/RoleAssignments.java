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

package jetbrains.buildServer.server.rest.model.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 12.04.2009
 */
@XmlRootElement(name = "roles")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION,
    value = "Represents a list of Role entities."))
public class RoleAssignments {
  @XmlElement(name = "role")
  public List<RoleAssignment> roleAssignments;

  public RoleAssignments() {
  }

  public RoleAssignments(Collection<RoleEntry> roleEntries, SUser user, @NotNull final BeanContext context) {
    roleAssignments = new ArrayList<RoleAssignment>(roleEntries.size());
    for (RoleEntry roleEntry : roleEntries) {
      try {
        roleAssignments.add(new RoleAssignment(roleEntry, user, context));
      } catch (InvalidStateException e) {
        //ignore until http://youtrack.jetbrains.com/issue/TW-34203 is fixed
      }
    }
  }

  public RoleAssignments(Collection<RoleEntry> roleEntries, UserGroup group, @NotNull final BeanContext context) {
    roleAssignments = new ArrayList<RoleAssignment>(roleEntries.size());
    for (RoleEntry roleEntry : roleEntries) {
      try {
        roleAssignments.add(new RoleAssignment(roleEntry, group, context));
      } catch (InvalidStateException e) {
        //ignore until http://youtrack.jetbrains.com/issue/TW-34203 is fixed
      }
    }
  }
}
