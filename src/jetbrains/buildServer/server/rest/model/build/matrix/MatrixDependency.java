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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Entries;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.SplitBuildsFeatureUtil;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "matrixDependency")
public class MatrixDependency {
  private BeanContext myBeanContext;
  private BuildPromotion myPromotion;
  private Build myBuild;
  private Entries myParameters;

  public MatrixDependency() { }

  public MatrixDependency(@NotNull BuildPromotion promotion, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    myPromotion = promotion;
    myBeanContext = beanContext;
    myParameters = ValueWithDefault.decideDefault(
      fields.isIncluded("parameters", true),
      () -> new Entries(SplitBuildsFeatureUtil.getMatrixParameterResolvedValues(myPromotion), fields.getNestedField("parameters"))
    );
    myBuild = ValueWithDefault.decideDefault(fields.isIncluded("build", false, true), () -> new Build(myPromotion, fields.getNestedField("build"), myBeanContext));
  }

  @XmlElement(name = "parameters")
  public Entries getParameters() {
    return myParameters;
  }

  @Nullable
  @XmlElement(name = "build")
  public Build getBuild() {
    return myBuild;
  }
}
