/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.project;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "typedValueSets")
@XmlType(name = "typedValueSets")
@ModelBaseType(ObjectType.PAGINATED)
@ModelDescription(value = "List of TypedValueSets.")
public class TypedValueSets {
  private Integer myCount;
  private List<TypedValueSet> myValueSets;

  public TypedValueSets() { }

  public TypedValueSets(@NotNull Collection<jetbrains.buildServer.serverSide.parameters.TypedValueSet> data, @NotNull SProject project, @NotNull Fields fields) {
    myCount = ValueWithDefault.decideDefault(
      fields.isIncluded("count", true),
      data.size()
    );

    myValueSets = ValueWithDefault.decideDefault(
      fields.isIncluded("valueSet", true),
      () -> resolveValueSet(data, project, fields.getNestedField("valueSet"))
    );
  }

  @NotNull
  private static List<TypedValueSet> resolveValueSet(@NotNull Collection<jetbrains.buildServer.serverSide.parameters.TypedValueSet> data,
                                                     @NotNull SProject project,
                                                     @NotNull Fields fields) {
    return data.stream()
               .map(vs -> new TypedValueSet(vs, project, fields))
               .collect(Collectors.toList());
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return myCount;
  }

  @XmlElement(name = "valueSet")
  public List<TypedValueSet> getValueSet() {
    return myValueSets;
  }
}

