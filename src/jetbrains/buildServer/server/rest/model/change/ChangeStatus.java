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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.controllers.changes.BuildStatusText;
import jetbrains.buildServer.controllers.changes.FailedTestsBean;
import jetbrains.buildServer.messages.ErrorData;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.ItemsProviders;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.StandardProperties;
import jetbrains.buildServer.users.impl.UserEx;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.BuildTypeChangeStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlType(name = "changeStatus")
@ModelDescription(
  value = "Aggregated statuses of the first builds with this change. Fairly expensive to compute."
)
public class ChangeStatus {
  private static final String CRITICAL_BUILDS_FIELD          = "criticalBuilds";
  private static final String COMPILATION_ERROR_BUILDS_FIELD = "compilationErrorBuilds";
  private static final String NEW_TESTS_FAILED_BUILDS_FIELD  = "newTestsFailedBuilds";
  private static final String NON_CRITICAL_BUILDS_FIELD      = "notCriticalBuilds";

  private final Fields myFields;
  private final jetbrains.buildServer.vcs.ChangeStatus myChangeStatus;
  private final BeanContext myBeanContext;

  private int myCancelledCount;
  private int myQueuedBuildsCount;
  private int myPendingCount;

  private int myFinishedBuildsCount;
  private int myFailedBuildsCount;
  private int mySuccessfulBuildsCount;

  private int myRunningBuildsCount;
  private int myRunningSuccessfullyCount;

  private int myNewFailedTests;
  private int myOtherFailedTests;
  private int myTotalProblemCount;

  private BuildsCollector myCriticalCollector;
  private BuildsCollector myCompilationErrorCollector;
  private BuildsCollector myNewTestsFailedCollector;
  private BuildsCollector myNotCriticalCollector;

  public ChangeStatus(@NotNull jetbrains.buildServer.vcs.ChangeStatus mergedStatus, @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myFields = fields;
    myChangeStatus = mergedStatus;
    myBeanContext = beanContext;

    initCounters();
    initTests();
  }

  @XmlAttribute(name = "runningBuilds")
  public Integer getRunning() {
    return myRunningBuildsCount;
  }

  @XmlAttribute(name = "runningSuccessfullyBuilds")
  public Integer getRunningSuccessfuly() {
    return myRunningSuccessfullyCount;
  }

  @XmlAttribute(name = "pendingBuildTypes")
  public Integer getPendingBuildTypes() {
    return myPendingCount;
  }

  @XmlAttribute(name = "finishedBuilds")
  public Integer getFinished() {
    return myFinishedBuildsCount;
  }

  @XmlAttribute(name = "successfulBuilds")
  public Integer getSuccessful() {
    return mySuccessfulBuildsCount;
  }

  @XmlAttribute(name = "failedBuilds")
  public Integer getFailed() {
    return myFailedBuildsCount;
  }

  @XmlAttribute(name = "cancelledBuilds")
  public Integer getCancelled() {
    return myCancelledCount;
  }

  @XmlAttribute(name = "totalProblems")
  public Integer getTotalProblemCount() {
    return myTotalProblemCount;
  }

  @XmlAttribute(name = "newFailedTests")
  public Integer getNewFailedTests() {
    return myNewFailedTests;
  }

  @XmlAttribute(name = "otherFailedTests")
  public Integer getOtherFailedTests() {
    return myOtherFailedTests;
  }

  @XmlAttribute(name = "queuedBuildsCount")
  public Integer getQueuedBuildsCount() {
    return myQueuedBuildsCount;
  }

  @XmlElement(name = CRITICAL_BUILDS_FIELD)
  public Builds getCriticalBuilds() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded(CRITICAL_BUILDS_FIELD),
      new Builds(myCriticalCollector, myFields.getNestedField(CRITICAL_BUILDS_FIELD), myBeanContext)
    );
  }

  @XmlElement(name = NON_CRITICAL_BUILDS_FIELD)
  public Builds getNonCriticalBuilds() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded(NON_CRITICAL_BUILDS_FIELD),
      new Builds(myNotCriticalCollector, myFields.getNestedField(NON_CRITICAL_BUILDS_FIELD), myBeanContext)
    );
  }

  @XmlElement(name = NEW_TESTS_FAILED_BUILDS_FIELD)
  public Builds getNewTestsFailedBuilds() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded(NEW_TESTS_FAILED_BUILDS_FIELD),
      new Builds(myNewTestsFailedCollector, myFields.getNestedField(NEW_TESTS_FAILED_BUILDS_FIELD), myBeanContext)
    );
  }

  @XmlElement(name = COMPILATION_ERROR_BUILDS_FIELD)
  public Builds getCompilationErrorBuilds() {
    return ValueWithDefault.decideDefault(
      myFields.isIncluded(COMPILATION_ERROR_BUILDS_FIELD),
      new Builds(myCompilationErrorCollector, myFields.getNestedField(COMPILATION_ERROR_BUILDS_FIELD), myBeanContext)
    );
  }

  private void initTests() {
    SecurityContext context = myBeanContext.getSingletonService(SecurityContext.class);

    Comparator<SBuildType> userOrder;
    if(context.getAuthorityHolder().getAssociatedUser() != null) {
      UserEx user = (UserEx) context.getAuthorityHolder().getAssociatedUser();
      userOrder = user.getProjectVisibilityHolder().getUserBuildTypeOrder();
    } else {
      userOrder = new BuildTypeComparator();
    }

    FailedTestsBean testsBean = new FailedTestsBean(userOrder);
    testsBean.fillFromModification(myChangeStatus);
    myNewFailedTests   = testsBean.getNewTestsNumber();
    myOtherFailedTests = testsBean.getOtherTestsNumber();
  }

  private void initCounters() {
    // This is heavily inspired by ChangeDetailsCalculator, but with some simplifications, so not reusing it here.
    SecurityContext context = myBeanContext.getSingletonService(SecurityContext.class);
    SUser self = (SUser) context.getAuthorityHolder().getAssociatedUser();

    myCriticalCollector = new BuildsCollector(myFields.getNestedField("criticalBuilds"));
    myCompilationErrorCollector = new BuildsCollector(myFields.getNestedField("compilationErrorBuilds"));
    myNewTestsFailedCollector = new BuildsCollector(myFields.getNestedField("newTestsFailedBuilds"));
    myNotCriticalCollector = new BuildsCollector(myFields.getNestedField("notCriticalBuilds"));

    final boolean includePersonalBuilds = self != null && StringUtil.isTrue(self.getPropertyValue(StandardProperties.SHOW_ALL_PERSONAL_BUILDS));

    for (BuildTypeChangeStatus status : myChangeStatus.getBuildTypesStatus().values()) {
      if(status.getBuildType().getProject().isVirtual()) {
        continue;
      }

      final SBuild firstBuild = status.getFirstBuild();
      if (firstBuild == null) {
        SQueuedBuild queued = status.getQueuedBuild();
        if(queued != null && (includePersonalBuilds || !queued.isPersonal())) {
          myQueuedBuildsCount++;
        }

        if (status.getQueuedBuild() == null) {
          myPendingCount++;
        }
        continue;
      }

      if(firstBuild.isPersonal() && !includePersonalBuilds) {
        continue;
      }

      if(firstBuild.getCanceledInfo() != null) {
        myCancelledCount++;
        continue;
      }

      if (firstBuild.isFinished()) {
        myFinishedBuildsCount++;

        if (status.isSuccessful()) {
          mySuccessfulBuildsCount++;
          continue; // no need to count problems, as our build is green
        }
      } else {
        myRunningBuildsCount++;

        Status runningBuildStatus = firstBuild.getBuildStatus();
        if(runningBuildStatus.isSuccessful()) {
          myRunningSuccessfullyCount++;
          continue; // no need to count problems, as our build is green
        }
      }

      myFailedBuildsCount++;

      String statusText = BuildStatusText.getBuildStatus(firstBuild, self);
      final BuildPromotionEx buildPromo = (BuildPromotionEx) firstBuild.getBuildPromotion();
      switch (statusText) {
        case BuildStatusText.NEW_TESTS_FAILED:
          myNewTestsFailedCollector.put(buildPromo);
          break;

        case BuildStatusText.CRITICAL_PROBLEM:
          myCriticalCollector.put(buildPromo);
          break;

        default:
          myNotCriticalCollector.put(buildPromo);
      }

      boolean compilationErrorCounted = false;
      for (BuildProblem problem : buildPromo.getBuildProblems()) {
        if (problem.isMutedInBuild()) continue;
        final String problemType = problem.getBuildProblemData().getType();
        if (BuildProblemData.TC_FAILED_TESTS_TYPE.equals(problemType) || ErrorData.isSnapshotDependencyError(problemType)) continue;

        if(BuildProblemData.TC_COMPILATION_ERROR_TYPE.equals(problemType) && !compilationErrorCounted) {
          myCompilationErrorCollector.put(buildPromo);
          compilationErrorCounted = true;
        }

        myTotalProblemCount++;
      }
    }
  }

  /**
   * Simple utility class to avoid storing builds promotions if not necessary.
   */
  private class BuildsCollector implements ItemsProviders.ItemsRetriever<BuildPromotion> {
    private int myCount = 0;
    private List<BuildPromotion> myPromotions = null;

    BuildsCollector(@NotNull Fields fields) {
      if(fields.isIncluded("build", false, true)) {
        myPromotions = new ArrayList<>();
      }
    }

    public void put(@NotNull BuildPromotion promotion) {
      myCount++;
      if(myPromotions != null) {
        myPromotions.add(promotion);
      }
    }

    @Nullable
    @Override
    public List<BuildPromotion> getItems() {
      return myPromotions;
    }

    @Override
    public Integer getCount() {
      return myCount;
    }

    @Override
    public boolean isCountCheap() {
      return true;
    }

    @Nullable
    @Override
    public PagerData getPagerData() {
      return null;
    }
  }
}
