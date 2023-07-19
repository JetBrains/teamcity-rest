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

package jetbrains.buildServer.server.rest.model.deployment;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentHistory;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentStateEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "deploymentStateEntries")
@XmlType(name = "deploymentStateEntries")
@ModelBaseType(ObjectType.LIST)
public class StateEntries {
  @XmlAttribute public Integer count = 0;

  @XmlElement(name = "deploymentStateEntry")
  public List<StateEntry> items = new ArrayList<>();

  public StateEntries() { }

  public StateEntries(
    @Nullable final List<DeploymentStateEntry> entries,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    if (entries != null) {
      items = ValueWithDefault.decideDefault(
        fields.isIncluded("deploymentStateEntry"),
        resolveStateEntries(entries, fields, beanContext)
      );

      count = ValueWithDefault.decideIncludeByDefault(
        fields.isIncluded("count"),
        entries.size()
      );
    }
  }

  @NotNull
  private static ArrayList<StateEntry> resolveStateEntries(@NotNull List<DeploymentStateEntry> entries, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    ArrayList<StateEntry> list = new ArrayList<>(entries.size());
    Fields entryFields = fields.getNestedField("deploymentStateEntry");

    for (DeploymentStateEntry entry : entries) {
      list.add(
        new StateEntry(entry, entryFields, beanContext)
      );
    }

    return list;
  }

  @NotNull
  public DeploymentHistory getHistoryFromPosted() {
    ArrayList<DeploymentStateEntry> entries = new ArrayList<>();
    for (StateEntry item : items) {
        entries.add(item.getEntryFromPosted());
    }
    return new DeploymentHistory(entries);
  }
}
