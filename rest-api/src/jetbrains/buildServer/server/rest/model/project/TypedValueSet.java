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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "typedValueSet")
@ModelDescription(
  value = "Represents a named set of predefined typed values."
)
public class TypedValueSet {
  private String myName;
  private String myDisplayName;
  private String myDescription;
  private String myShortDescription;
  private Collection<String> myKeywords;
  private List<LabeledValue> myValues;

  public TypedValueSet() { }

  public TypedValueSet(@NotNull jetbrains.buildServer.serverSide.parameters.TypedValueSet valueSet, @NotNull SProject project, @NotNull Fields fields) {
    myName = ValueWithDefault.decideDefault(
      fields.isIncluded("name", true),
      valueSet.getType()
    );
    myDisplayName = ValueWithDefault.decideDefault(
      fields.isIncluded("displayName", true),
      valueSet.getType()
    );
    myDescription = ValueWithDefault.decideDefault(
      fields.isIncluded("description"),
      valueSet.getDescription()
    );
    myShortDescription = ValueWithDefault.decideDefault(
      fields.isIncluded("shortDescription"),
      valueSet.getShortDescription()
    );
    myKeywords = ValueWithDefault.decideDefault(
      fields.isIncluded("keyword"),
      valueSet.getKeywords()
    );
    myValues = ValueWithDefault.decideDefault(
      fields.isIncluded("value"),
      () -> resolveValues(valueSet.getValues(project))
    );
  }

  @NotNull
  private List<LabeledValue> resolveValues(@NotNull Map<String, String> valueSet) {
    return valueSet.entrySet().stream()
                   .sorted(Comparator.comparing(e -> e.getValue()))
                   .map(e -> new LabeledValue(e.getKey(), e.getValue()))
                   .collect(Collectors.toList());
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return myName;
  }

  @XmlAttribute(name = "displayName")
  public String getDisplayName() {
    return myDisplayName;
  }

  @XmlAttribute(name = "description")
  public String getDescription() {
    return myDescription;
  }

  @XmlAttribute(name = "shortDescription")
  public String getShortDescription() {
    return myShortDescription;
  }

  @XmlElement(name = "keyword")
  public Collection<String> getKeywords() {
    return myKeywords;
  }

  @XmlElement(name = "value")
  public List<LabeledValue> getValues() {
    return myValues;
  }
}
