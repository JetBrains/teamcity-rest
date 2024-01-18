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

package jetbrains.buildServer.server.rest.model.pages.matrix;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.project.LabeledValue;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "matrixParameterDescriptor")
public class MatrixParameterDescriptor {
  private String myName;
  private Integer myValueCount;
  private List<LabeledValue> myValues;

  public MatrixParameterDescriptor() { }

  public MatrixParameterDescriptor(@NotNull String name, @NotNull List<LabeledValue> values) {
    myName = name;
    myValueCount = values.size();
    myValues = values;
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @XmlAttribute(name = "valueCount")
  public Integer getValueCount() {
    return myValueCount;
  }

  @XmlElement(name = "value")
  public List<LabeledValue> getValues() {
    return myValues;
  }

  public void setValues(List<LabeledValue> values) {
    myValues = values;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MatrixParameterDescriptor that = (MatrixParameterDescriptor)o;
    return Objects.equals(myName, that.myName) && Objects.equals(myValues, that.myValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myValues);
  }

  @Override
  public String toString() {
    return "Parameter{name=" + myName +
           ", values=[" + myValues.stream().map(LabeledValue::toString).collect(Collectors.joining(", ")) +
           "]}";
  }
}
