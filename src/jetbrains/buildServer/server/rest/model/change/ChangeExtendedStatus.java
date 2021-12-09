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
import jetbrains.buildServer.controllers.changes.BuildStatusText;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.ChangeStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "changeExtendedStatus")
public class ChangeExtendedStatus {
  private final Fields myFields;

  private int mySuccessfull;
  private int myNotCriticalProblem;
  private int myPending;
  private int myNewTestsFailed;
  private int myCritical;
  private int myCompilationError;
  private int myCancelled;
  private int myRunningSuccessfully;

  public ChangeExtendedStatus(@NotNull ChangeStatus changeStatus, @NotNull Fields fields, @Nullable SUser self) {
    myFields = fields;

    for(SBuild build : changeStatus.getFirstBuilds().values()) {
      if (build.isPersonal()) continue;

      String status = BuildStatusText.getBuildStatus(build, self);
      switch (status) {
        case BuildStatusText.SUCCESSFUL_RUNNING:
          myRunningSuccessfully++;
          break;
        case BuildStatusText.CRITICAL_PROBLEM:
          myCritical++;
          break;
        case BuildStatusText.COMPILATION_ERROR:
          myCompilationError++;
          break;
        case BuildStatusText.NEW_TESTS_FAILED:
          myNewTestsFailed++;
          break;
        case BuildStatusText.CANCELLED:
          myCancelled++;
          break;
        case BuildStatusText.PENDING:
          myPending++;
          break;
        case BuildStatusText.NOT_CRITICAL_PROBLEM:
          myNotCriticalProblem++;
          break;
        case BuildStatusText.SUCCESSFUL:
          mySuccessfull++;
          break;
      }
    }
  }

  @XmlAttribute(name = "critical")
  public Integer getCritical() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("critical"), myCritical);
  }

  @XmlAttribute(name = "compilationError")
  public Integer getCompilationError() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("compilationError"), myCompilationError);
  }

  @XmlAttribute(name = "newTestsFailed")
  public Integer getNewTestsFailed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("newTestsFailed"), myNewTestsFailed);
  }

  @XmlAttribute(name = "cancelled")
  public Integer getCancelled() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("cancelled"), myCancelled);
  }

  @XmlAttribute(name = "runningSuccessfully")
  public Integer getRunningSuccessfully() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("runningSuccessfully"), myRunningSuccessfully);
  }

  @XmlAttribute(name = "successfull")
  public Integer getSuccessfull() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("successfull"), mySuccessfull);
  }

  @XmlAttribute(name = "notCritical")
  public Integer getNotCriticalProblem() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("notCritical"), myNotCriticalProblem);
  }

  @XmlAttribute(name = "pending")
  public Integer getPending() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("pending"), myPending);
  }
}
