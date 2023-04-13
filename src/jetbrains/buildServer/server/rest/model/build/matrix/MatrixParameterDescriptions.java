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
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.SplitBuildsFeatureUtil;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "maxtrixParameterDescriptions")
public class MatrixParameterDescriptions {
  private List<MatrixParameterDescription> myParameters;
  private Integer myCount;

  public MatrixParameterDescriptions() { }

  public MatrixParameterDescriptions(@NotNull BuildPromotion sourcePromotion, @NotNull Fields fields) {
    myParameters = ValueWithDefault.decideDefault(
      fields.isIncluded("parameter"),
      () -> resolveMatrixParameterDescription(sourcePromotion, fields.getNestedField("parameter"))
    );

    myCount = ValueWithDefault.decideDefault(
      fields.isIncluded("count", true),
      () -> SplitBuildsFeatureUtil.getMatrixParameters(sourcePromotion).size()
    );
  }

  private List<MatrixParameterDescription> resolveMatrixParameterDescription(@NotNull BuildPromotion sourcePromotion, @NotNull Fields fields) {
    return SplitBuildsFeatureUtil.getMatrixParameters(sourcePromotion).stream()
                                 .map(param -> new MatrixParameterDescription(param.getName(), param.getValues(), fields))
                                 .collect(Collectors.toList());
  }

  @XmlElement(name = "parameter")
  public List<MatrixParameterDescription> getParameters() {
    return myParameters;
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return myCount;
  }
}
