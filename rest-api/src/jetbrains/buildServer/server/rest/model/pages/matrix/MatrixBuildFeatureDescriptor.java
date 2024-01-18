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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "matrixBuildFeatureDescriptor")
@XmlType(name = "matrixBuildFeatureDescriptor")
public class MatrixBuildFeatureDescriptor {
  private String myId;
  private Integer myCount;
  private List<MatrixParameterDescriptor> myParameters;

  public MatrixBuildFeatureDescriptor() { }

  public MatrixBuildFeatureDescriptor(@NotNull String featureId, @NotNull List<MatrixParameterDescriptor> parameters) {
    myId = featureId;
    myParameters = parameters;
    myCount = parameters.size();
  }

  @XmlAttribute(name = "id")
  public String getId() {
    return myId;
  }

  @XmlAttribute(name = "count")
  public Integer getCount() {
    return myCount;
  }

  @XmlElement(name = "parameter")
  public List<MatrixParameterDescriptor> getParameters() {
    return myParameters;
  }

  public void setParameters(List<MatrixParameterDescriptor> parameters) {
    myParameters = parameters;
  }
}
