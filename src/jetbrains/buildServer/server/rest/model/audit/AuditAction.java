/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.audit;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.audit.ActionType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 02/05/2018
 */
@XmlRootElement(name = "auditAction")
@XmlType(name = "auditAction")
public class AuditAction {
  @XmlAttribute
  public String name;
  @XmlElement
  public String description;

  public AuditAction() {
  }

  public AuditAction(@NotNull final ActionType actionType, @NotNull final Fields fields, @NotNull final BeanContext context) {
    name = ValueWithDefault.decideDefault(fields.isIncluded("name"), () -> actionType.name().toLowerCase());
    description = ValueWithDefault.decideDefault(fields.isIncluded("description"), () -> actionType.getDescription());
  }
}
