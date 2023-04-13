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

package jetbrains.buildServer.server.rest.model.build.matrix;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "matrixParameterDescription")
public class MatrixParameterDescription {
  private String myName;
  private List<String> myValues;

  public MatrixParameterDescription() { }

  public MatrixParameterDescription(@NotNull String name, @NotNull List<String> values, @NotNull Fields fields) {
    myName = ValueWithDefault.decideDefault(fields.isIncluded("name"), name);
    myValues = ValueWithDefault.decideDefault(fields.isIncluded("values"), values);
  }

  @Nullable
  @XmlAttribute
  public String getName() {
    return myName;
  }

  @Nullable
  @XmlElement(name="values")
  public List<String> getValues() {
    return myValues;
  }
}
