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
import jetbrains.buildServer.server.rest.data.pages.matrix.MatrixBuildFeatureService;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.pages.matrix.MatrixBuildFeatureDescriptor;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.DefaultValueAware;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "matrixConfiguration")
public class MatrixConfiguration implements DefaultValueAware {
  private boolean myEnabled;
  private MatrixBuildFeatureDescriptor myParameters;
  private MatrixDependencies myDependencies;

  public MatrixConfiguration() { }

  public MatrixConfiguration(@NotNull BuildPromotion matrixPromotion, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    myEnabled = MatrixBuildFeatureService.isMatrixBuild(matrixPromotion);
    if(!myEnabled) {
      myParameters = null;
      myDependencies = null;
    } else {
      myParameters = ValueWithDefault.decideDefault(
        fields.isIncluded("parameters", false, true),
        () -> MatrixBuildFeatureService.resolveParameters(matrixPromotion)
      );
      myDependencies = ValueWithDefault.decideDefault(
        fields.isIncluded("dependencies", false, true),
        () -> resolveDependencies(matrixPromotion, fields.getNestedField("dependencies"), beanContext)
      );
    }
  }

  @Nullable
  private MatrixDependencies resolveDependencies(@NotNull BuildPromotion matrixPromotion, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    List<BuildPromotion> deps = MatrixBuildFeatureService.getDependencies(matrixPromotion);
    if(deps == null) {
      return null;
    }

    return new MatrixDependencies(deps, fields, beanContext);
  }

  @Nullable
  @XmlElement(name = "parameters")
  public MatrixBuildFeatureDescriptor getParameters() {
    return myParameters;
  }

  @Nullable
  @XmlElement(name = "dependencies")
  public MatrixDependencies getDependencies() {
    return myDependencies;
  }

  @Nullable
  @XmlAttribute(name = "enabled")
  public Boolean getEnabled() {
    return myEnabled;
  }

  @Override
  public boolean isDefault() {
    return !myEnabled;
  }
}
