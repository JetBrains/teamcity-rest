/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.health;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.healthStatus.ItemSeverity;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "healthItem")
@XmlType(name = "healthItem", propOrder = {"severity", "healthCategory"})
public class HealthItem {
  static final String NAME = "healthItem";
  private final ItemSeverity severity;
  private final HealthCategory healthCategory;

  @SuppressWarnings({"ConstantConditions", "unused"})
  public HealthItem() {
    this.severity = null;
    this.healthCategory = null;
  }

  public HealthItem(@NotNull final jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem healthStatusItem,
                    @NotNull final Fields fields) {
    this.severity = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("severity"), healthStatusItem::getSeverity);
    this.healthCategory = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("healthCategory"),
                                                                  () -> new HealthCategory(healthStatusItem.getCategory(),
                                                                                           fields.getNestedField("healthCategory", Fields.SHORT, Fields.SHORT)));
  }

  @XmlElement
  public ItemSeverity getSeverity() {
    return severity;
  }

  @XmlElement
  public HealthCategory getHealthCategory() {
    return healthCategory;
  }
}
