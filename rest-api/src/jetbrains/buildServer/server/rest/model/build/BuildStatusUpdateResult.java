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

package jetbrains.buildServer.server.rest.model.build;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Items;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildPromotion;
import org.jetbrains.annotations.NotNull;


@XmlRootElement(name = "buildStatusUpdateResult")
@ModelDescription("Represents result of the build status upadte operation. Contains realted build and list of erros, if any.")
public class BuildStatusUpdateResult {
  private BuildPromotion myPromotion;
  private List<String> myErrors;
  private Fields myFields;
  private BeanContext myBeanContext;

  public BuildStatusUpdateResult() {
  }

  public BuildStatusUpdateResult(@NotNull BuildPromotion promotion, @NotNull List<String> errors, @NotNull Fields fields, @NotNull BeanContext ctx) {
    myPromotion = promotion;
    myErrors = errors;
    myBeanContext = ctx;
    myFields = fields;
  }

  @XmlElement(name = "build")
  public Build getBuild() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("build", false, true),
      new Build(myPromotion, myFields.getNestedField("build"), myBeanContext)
    );
  }

  @XmlElement(name = "errors")
  public Items getErrors() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded("build", true),
      new Items(myErrors)
    );
  }
}