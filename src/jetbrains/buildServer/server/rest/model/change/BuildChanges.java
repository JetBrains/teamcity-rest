/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import jetbrains.buildServer.server.rest.data.change.BuildChangeData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Yegor.Yarko
 * Date: 20/03/2018
 */
@XmlRootElement(name = "buildChanges")
@XmlType(name = "buildChanges")
@ModelBaseType(ObjectType.LIST)
public class BuildChanges { //implements DefaultValueAware
  @XmlElement(name = "buildChange")
  public List<BuildChange> myBuildChanges;

  @XmlAttribute
  public Integer count;

  public BuildChanges() {
  }

  public BuildChanges(@NotNull final List<BuildChangeData> buildChangeData, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myBuildChanges = ValueWithDefault.decideDefault(fields.isIncluded("buildChange", false, true), buildChangeData.stream().map(
      data -> new BuildChange(data, fields.getNestedField("buildChange", Fields.SHORT, Fields.LONG), beanContext)).collect(Collectors.toList()));
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), buildChangeData.size());
  }
}