/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import jetbrains.buildServer.ServiceLocator;
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
@XmlType(propOrder = {"id", "name", "type", "disabled", "inherited",
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

  @XmlAttribute
  @Nullable
  public Boolean inherited;

  @XmlElement
  public Properties properties;

  public PropEntity() {
  }

  public PropEntity(@NotNull ParametersDescriptor descriptor,
                    @Nullable final Boolean inherited,
                    @NotNull BuildTypeSettings buildType,
                    @NotNull final Fields fields,
                    @NotNull final ServiceLocator serviceLocator) {
    init(descriptor.getId(), null, descriptor.getType(), buildType.isEnabled(descriptor.getId()), inherited, descriptor.getParameters(), fields, serviceLocator);
  }

  public PropEntity(@NotNull final String id,
                    @Nullable final String name,
                    @NotNull final String type,
                    @Nullable final Boolean enabled,
                    @Nullable final Boolean inherited,
                    @NotNull final Map<String, String> properties,
                    @NotNull final Fields fields,
                    @NotNull final ServiceLocator serviceLocator) {
    init(id, name, type, enabled, inherited, properties, fields, serviceLocator);
  }

  protected void init(@NotNull final String id,
                      @Nullable final String name,
                      @NotNull final String type,
                      @Nullable final Boolean enabled,
                      @Nullable final Boolean inherited,
                      @NotNull final Map<String, String> properties,
                      @NotNull final Fields fields,
                      @NotNull final ServiceLocator serviceLocator) {
    this.id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), id);
    this.name = ValueWithDefault.decideDefault(fields.isIncluded("name"), name);
    this.type = ValueWithDefault.decideDefault(fields.isIncluded("type"), type);
    this.properties = ValueWithDefault.decideDefault(fields.isIncluded("properties"),
                                                     new Properties(properties, null, fields.getNestedField("properties", Fields.NONE, Fields.LONG), serviceLocator));
    this.disabled = enabled == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("disabled"), !enabled);
    this.inherited = inherited == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("inherited"), inherited);
  }

  public static String getSetting(@NotNull final BuildTypeSettings buildType, @NotNull final String id, final String name) {
    if ("disabled".equals(name)) {
      return String.valueOf(!buildType.isEnabled(id));
    }
    throw new BadRequestException("Only 'disabled' setting names is supported. '" + name + "' unknown.");
  }

  public static void setSetting(@NotNull final BuildTypeSettings buildType, @NotNull final String id, final String name, final String value) {
    if ("disabled".equals(name)) {
      buildType.setEnabled(id, !Boolean.parseBoolean(value));
    } else {
      throw new BadRequestException("Only 'disabled' setting names is supported. '" + name + "' unknown.");
    }
  }
}
