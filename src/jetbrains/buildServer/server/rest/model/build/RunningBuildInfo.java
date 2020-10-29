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

package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ExtensionProperty;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 13.09.2010
 */
@XmlType(propOrder = {"percentageComplete", "elapsedSeconds", "estimatedTotalSeconds", "leftSeconds", "currentStageText", "outdated", "probablyHanging", "lastActivityTime",
                      "outdatedReasonBuild"})
@XmlRootElement(name = "progress-info")
@Extension(properties = @ExtensionProperty(name = ExtensionType.X_DESCRIPTION, value = "Represents a progress estimate of this build."))
public class RunningBuildInfo {
  @NotNull
  private SRunningBuild myBuild;
  @NotNull private Fields myFields;
  @NotNull private BeanContext myBeanContext;

  public RunningBuildInfo() {
  }

  public RunningBuildInfo(@NotNull final SRunningBuild build, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myBuild = build;
    myFields = fields;
    myBeanContext = beanContext;
  }

  @XmlAttribute(name = "percentageComplete")
  public Integer getPercentageComplete() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("percentageComplete", true), () -> {
      if (myBuild.getDurationEstimate() == -1) {
        return null;
      }
      return myBuild.getCompletedPercent();
    });
  }

  @XmlAttribute(name = "elapsedSeconds")
  public Long getElapsedSeconds() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("elapsedSeconds", true), () -> myBuild.getElapsedTime());
  }

  /**
   * @return estimate for the remaining time of the build, considering the current build stage and progress
   */
  @XmlAttribute(name = "leftSeconds")
  public Long getLeftSeconds() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("leftSeconds", false, false), () -> getIfAvailable(myBuild.getEstimationForTimeLeft()));
  }

  /**
   * @return Estimate for the total build duration based on the build history, not considering the current build stage and progress
   */
  @XmlAttribute(name = "estimatedTotalSeconds")
  public Long getEstimatedTotalSeconds() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("estimatedTotalSeconds", true), () -> getIfAvailable(myBuild.getDurationEstimate()));
  }

  @XmlAttribute
  public Boolean isOutdated() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("outdated", true), myBuild.isOutdated());
  }

  @XmlAttribute
  public Boolean isProbablyHanging() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("probablyHanging", true), myBuild.isProbablyHanging());
  }

  @XmlAttribute
  public String getCurrentStageText() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("currentStageText", true), myBuild.getCurrentPath());
  }

  /**
   * Experimental
   */
  @XmlAttribute
  public String getLastActivityTime() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("lastActivityTime", false, false), Util.formatTime(myBuild.getLastBuildActivityTimestamp()));
  }

  /**
   * Experimental
   */
  @XmlElement
  public Build getOutdatedReasonBuild() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("outdatedReasonBuild", false, false),
                                          () -> {
                                            SFinishedBuild recentlyFinishedBuild = myBuild.getRecentlyFinishedBuild();
                                            return recentlyFinishedBuild == null
                                                   ? null
                                                   : new Build(recentlyFinishedBuild.getBuildPromotion(), myFields.getNestedField("outdatedReasonBuild"), myBeanContext);
                                          });
  }

  @Nullable
  private Long getIfAvailable(final long value) {
    return value == -1 ? null : value;
  }
}
