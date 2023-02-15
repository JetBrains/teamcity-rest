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

import java.util.Arrays;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "role")
@ModelDescription(
  value = "Represents a role.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/managing-roles-and-permissions.html",
  externalArticleName = "Managing Roles and Permissions"
)
public class Role {
  @XmlAttribute public String id;
  @XmlAttribute public String name;

  @XmlElement public Roles included;
  @XmlElement public Permissions permissions;

  @XmlAttribute public String href;

  public Role() {
  }

  public Role(jetbrains.buildServer.serverSide.auth.Role role, @NotNull final Fields fields, @NotNull final BeanContext context) {
    id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), role.getId());
    name = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("name"), role.getName());

    included = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("included", false),
      new Roles(
        Arrays.asList(role.getIncludedRoles()),
        fields.getNestedField("included", Fields.NONE, Fields.LONG),
        context
      )
    );

    permissions = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("permissions", false),
      new Permissions(
        role.getOwnPermissions().toList(),
        fields.getNestedField("permissions", Fields.NONE, Fields.LONG)
      )
    );

    href = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("href"), context.getApiUrlBuilder().getHref(role));
  }

  public Role(String id) {
    this.id = id;
  }
}
