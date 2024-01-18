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

package jetbrains.buildServer.server.rest.model.health;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "healthCategory")
@XmlType(name = "healthCategory")
public class HealthCategory {
  private final String id;
  private final String name;
  private final String description;
  private final String helpUrl;

  @SuppressWarnings("unused")
  public HealthCategory() {
    this.id = null;
    this.name = null;
    this.description = null;
    this.helpUrl = null;
  }

  public HealthCategory(@NotNull final ItemCategory category, @NotNull final Fields fields) {
    this.id = ValueWithDefault.decideDefault(fields.isIncluded("id"), category.getId());
    this.name = ValueWithDefault.decideDefault(fields.isIncluded("name"), category.getName());
    this.description = ValueWithDefault.decideDefault(fields.isIncluded("description"), category.getDescription());
    this.helpUrl = ValueWithDefault.decideDefault(fields.isIncluded("helpUrl"), category.getHelpUrl());
  }

  @XmlElement
  public String getId() {
    return id;
  }

  @XmlElement
  public String getName() {
    return name;
  }

  @XmlElement
  public String getDescription() {
    return description;
  }

  @XmlElement
  public String getHelpUrl() {
    return helpUrl;
  }
}