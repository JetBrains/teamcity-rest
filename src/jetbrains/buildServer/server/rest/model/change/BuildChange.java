/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.change;

/**
 * @author Yegor.Yarko
 * Date: 20/03/2018
 */

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.server.rest.data.change.BuildChangeData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

/**
 * Experimental use only
 */
@XmlRootElement(name = "buildChange")
@XmlType(name = "buildChange", propOrder = {"nextBuild", "prevBuild"})
@ModelDescription("Represents links to the next or previous build.")
public class BuildChange {
  @XmlElement Build prevBuild;
  @XmlElement Build nextBuild;

  public BuildChange() {
  }

  public BuildChange(BuildChangeData buildChange, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    nextBuild = ValueWithDefault.decideDefault(fields.isIncluded("nextBuild"), () -> Util.resolveNull(buildChange.getNextBuild(), (b) -> new Build(b, fields.getNestedField("nextBuild"), beanContext)));
    prevBuild = ValueWithDefault.decideDefault(fields.isIncluded("prevBuild"), () -> Util.resolveNull(buildChange.getPreviousBuild(), (b) -> new Build(b, fields.getNestedField("prevBuild"), beanContext)));
  }
}
