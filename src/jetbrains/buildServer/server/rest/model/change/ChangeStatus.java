/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "changeStatus")
public class ChangeStatus {
  private final Fields myFields;
  private final int myRunning;
  private final int myPendingBuildTypes;
  private final int myFinished;
  private final int mySuccessful;
  private final int myFailed;

  public ChangeStatus(@NotNull jetbrains.buildServer.vcs.ChangeStatus mergedStatus, @NotNull Fields fields) {
    myFields = fields;
    myFinished = mergedStatus.getFinishedBuildsNumber();
    myRunning = mergedStatus.getRunningBuildsNumber();
    myPendingBuildTypes = mergedStatus.getPendingBuildsTypesNumber();
    mySuccessful = mergedStatus.getSuccessCount();
    myFailed = mergedStatus.getFailedCount();
  }

  @XmlAttribute(name = "runningBuilds")
  public Integer getRunning() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("runningBuilds", true, true), myRunning);
  }

  @XmlAttribute(name = "pendingBuildTypes")
  public Integer getPendingBuildTypes() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("pendingBuildTypes", true, true), myPendingBuildTypes);
  }

  @XmlAttribute(name = "finishedBuilds")
  public Integer getFinished() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("finishedBuilds", true, true), myFinished);
  }

  @XmlAttribute(name = "successfulBuilds")
  public Integer getSuccessful() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("successfulBuilds", true, true), mySuccessful);
  }

  @XmlAttribute(name = "failedBuilds")
  public Integer getFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("failedBuilds", true, true), myFailed);
  }
}