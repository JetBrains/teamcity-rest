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

package jetbrains.buildServer.server.rest.model.changeLog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.controllers.buildType.tabs.ChangeLogVcsChangeRow;
import jetbrains.buildServer.server.rest.data.change.SVcsModificationOrChangeDescriptor;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.controllers.buildType.tabs.ChangeLogRow.BUILD_ROW;
import static jetbrains.buildServer.controllers.buildType.tabs.ChangeLogRow.VCS_CHANGE_ROW;

@XmlType(name = "changeLogRow")
public class ChangeLogRow {
  private jetbrains.buildServer.controllers.buildType.tabs.ChangeLogRow myRow;
  private Fields myFields;
  private BeanContext myBeanContext;

  public ChangeLogRow() { }

  public ChangeLogRow(@NotNull jetbrains.buildServer.controllers.buildType.tabs.ChangeLogRow row, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    myRow = row;
    myFields = fields;
    myBeanContext = beanContext;
  }

  @XmlElement(name = "build")
  public Build getBuild() {
    if (!BUILD_ROW.equals(myRow.getType()) || myRow.getBuild() == null) {
      return null;
    }

    return ValueWithDefault.decideDefault(
      myFields.isIncluded("build"),
      () -> new Build(myRow.getBuild(), myFields.getNestedField("build"), myBeanContext)
    );
  }

  @XmlElement(name = "change")
  public Change getChange() {
    if (!VCS_CHANGE_ROW.equals(myRow.getType())) {
      return null;
    }

    return ValueWithDefault.decideDefault(
      myFields.isIncluded("change"),
      () -> new Change(
        new SVcsModificationOrChangeDescriptor(((ChangeLogVcsChangeRow)myRow).getChangeDescriptor()),
        myFields.getNestedField("change", Fields.SHORT, Fields.SHORT),
        myBeanContext
      )
    );
  }
}
