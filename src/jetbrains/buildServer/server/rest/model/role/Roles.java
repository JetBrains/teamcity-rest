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

package jetbrains.buildServer.server.rest.model.role;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "roles")
@ModelBaseType(ObjectType.LIST)
@ModelDescription(
  value = "Represents a list of roles.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/managing-roles-and-permissions.html",
  externalArticleName = "Managing Roles and Permissions"
)
public class Roles {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "role")
  public List<Role> items;

  public Roles() {
  }

  public Roles(List<jetbrains.buildServer.serverSide.auth.Role> roles, @NotNull final Fields fields, @NotNull final BeanContext context) {
    items = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("role", true),
      CollectionsUtil.convertCollection(
        roles,
        source -> new Role(source, fields.getNestedField("role"), context)
      )
    );
    count = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("count", true),
      roles.size()
    );
  }
}
