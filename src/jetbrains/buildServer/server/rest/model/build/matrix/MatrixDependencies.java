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
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "matrixDependencies")
public class MatrixDependencies {
  private Integer myCount;
  private List<MatrixDependency> myDeps;

  public MatrixDependencies() { }

  public MatrixDependencies(@NotNull List<BuildPromotion> deps, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    myDeps = ValueWithDefault.decideDefault(
      fields.isIncluded("dependency", false, true),
      () -> resolveBuilds(deps, fields.getNestedField("dependency"), beanContext)
    );
    myCount = ValueWithDefault.decideDefault(fields.isIncluded("count", true), deps.size());
  }

  @NotNull
  private static List<MatrixDependency> resolveBuilds(@NotNull List<BuildPromotion> builds, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    return builds.stream().map(promotion -> new MatrixDependency(promotion, fields, beanContext)).collect(Collectors.toList());
  }

  @XmlElement(name = "dependency")
  public List<MatrixDependency> getBuild() {
    return myDeps;
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return myCount;
  }
}
