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

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.audit.AuditLogAction;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "auditEvents")
@XmlType(name = "auditEvents")
public class AuditEvents {
  @XmlElement(name = "auditEvent")
  public List<AuditEvent> auditEvents;

  @XmlAttribute
  public Integer count;

  public AuditEvents() {
  }

  public AuditEvents(final @NotNull List<AuditLogAction> items, @NotNull final Fields fields, @NotNull final BeanContext context) {
    auditEvents = ValueWithDefault.decideDefault(fields.isIncluded("auditEvent", false, true),
                                                 () -> items.stream().map(i -> new AuditEvent(i, fields.getNestedField("auditEvent"), context)).collect(Collectors.toList()));

    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), items::size);
  }
}
