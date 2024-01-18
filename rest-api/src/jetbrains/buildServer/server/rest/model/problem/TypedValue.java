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

package jetbrains.buildServer.server.rest.model.problem;

import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("PublicField")
@XmlType(name = "typedValue")
@XmlRootElement(name = "typedValue")
@ModelDescription("Represents a name-value-type relation.")
public class TypedValue {
  @XmlAttribute public String name;
  @XmlAttribute public String type;
  @XmlAttribute public String value;

  public TypedValue() {
  }

  public TypedValue(final @NotNull String name, @NotNull final String type, final @NotNull String value, @NotNull final Fields fields) {
    this.name = ValueWithDefault.decideDefault(fields.isIncluded("name", true, true), name);
    this.type = ValueWithDefault.decideDefault(fields.isIncluded("type", true, true), type);
    this.value = ValueWithDefault.decideDefault(fields.isIncluded("value", true, true), value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final TypedValue that = (TypedValue)o;
    return Objects.equals(name, that.name) &&
           Objects.equals(type, that.type) &&
           Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, value);
  }

  @Override
  public String toString() {
    return "TypedValue{" +
           "name='" + name + '\'' +
           ", type='" + type + '\'' +
           ", value='" + value + '\'' +
           '}';
  }
}