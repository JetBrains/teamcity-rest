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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 20/09/2017
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "permission")
@XmlType(name = "permission")
@ModelDescription(
    value = "Represents a permission.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/role-and-permission.html",
    externalArticleName = "Roles"
)
public class Permission {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public Boolean global;

  public Permission() {
  }

  public Permission(@NotNull jetbrains.buildServer.serverSide.auth.Permission permission, @NotNull final Fields fields) {
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), () -> permission.name().toLowerCase());
    name = ValueWithDefault.decideDefault(fields.isIncluded("name"), () -> permission.getDescription());
    global = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("global"), () -> !permission.isProjectAssociationSupported());
  }
}
