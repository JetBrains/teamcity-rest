/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.user.Permission;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;

public class Permissions {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "permission")
  public List<Permission> items;

  public Permissions() {
  }

  public Permissions(List<jetbrains.buildServer.serverSide.auth.Permission> permissions, @NotNull Fields fields) {
    items = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("permission", true),
      CollectionsUtil.convertCollection(
        permissions,
        (source) -> new Permission(source, fields.getNestedField("permission"))
      )
    );
    count = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("count", true),
      permissions.size()
    );
  }
}