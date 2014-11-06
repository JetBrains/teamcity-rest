/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.ParametersDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Author: Yegor.Yarko
 */
@XmlType(propOrder = {"id", "name", "type",
  "properties"})
//@XmlRootElement(name = "property-described-entity")
@SuppressWarnings("PublicField")
public class PropEntity {
  @XmlAttribute
  public String id;

  @XmlAttribute
  @Nullable
  public String name;

  @XmlAttribute
  public String type;

  @XmlAttribute
  @Nullable
  public Boolean disabled;

  @XmlElement
  public Properties properties;

  public PropEntity() {
  }

  public PropEntity(@NotNull ParametersDescriptor descriptor, @NotNull BuildTypeSettings buildType, @NotNull final Fields fields) {
    init(descriptor.getId(), null, descriptor.getType(), buildType.isEnabled(descriptor.getId()), descriptor.getParameters(), fields);
  }

  public PropEntity(@NotNull final String id,
                    @Nullable final String name,
                    @NotNull final String type,
                    @Nullable final Boolean enabled,
                    @NotNull final Map<String, String> properties,
                    @NotNull final Fields fields) {
    init(id, name, type, enabled, properties, fields);
  }

  protected void init(@NotNull final String id,
                    @Nullable final String name,
                    @NotNull final String type,
                    @Nullable final Boolean enabled,
                    @NotNull final Map<String, String> properties,
                    @NotNull final Fields fields) {
    this.id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), id);
    this.name = ValueWithDefault.decideDefault(fields.isIncluded("name"), name);
    this.type = ValueWithDefault.decideDefault(fields.isIncluded("type"), type);
    this.properties = ValueWithDefault.decideDefault(fields.isIncluded("properties"),
                                                     new Properties(properties, null, fields.getNestedField("properties", Fields.NONE, Fields.LONG)));
    disabled = enabled == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("disabled"), !enabled);
  }

  public static String getSetting(final BuildTypeSettings buildType, final ParametersDescriptor descriptor, final String name) {
    if ("disabled".equals(name)) {
      return String.valueOf(!buildType.isEnabled(descriptor.getId()));
    }
    throw new BadRequestException("Only 'disabled' setting names is supported. '" + name + "' unknown.");
  }

  public static void setSetting(final BuildTypeSettings buildType, final ParametersDescriptor descriptor, final String name, final String value) {
    if ("disabled".equals(name)) {
      buildType.setEnabled(descriptor.getId(), !Boolean.parseBoolean(value));
    } else {
      throw new BadRequestException("Only 'disabled' setting names is supported. '" + name + "' unknown.");
    }
  }
}
