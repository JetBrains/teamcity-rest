/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 13.09.2010
 */
@XmlType(propOrder = { "progress", "elapsedTime", "estimatedDuration", "currentStageText", "outdated", "probablyHanging"})
@XmlRootElement(name = "progress-info")
public class RunningBuildInfo {
  @NotNull
  private SRunningBuild myBuild;

  public RunningBuildInfo() {
  }

  public RunningBuildInfo(@NotNull final SRunningBuild build) {
    myBuild = build;
  }

  @XmlAttribute(name = "percentageComplete")
  public Integer getProgress() {
    if (myBuild.getDurationEstimate() == -1){
      return null;
    }
    return myBuild.getCompletedPercent();
  }

  @XmlAttribute(name = "elapsedSeconds")
  public long getElapsedTime() {
    return myBuild.getElapsedTime();
  }

  @XmlAttribute(name = "estimatedTotalSeconds")
  public Long getEstimatedDuration() {
    final long durationEstimate = myBuild.getDurationEstimate();
    if (durationEstimate == -1) {
      return null;
    } else {
      return durationEstimate;
    }
  }

  @XmlAttribute
  public boolean isOutdated() {
    return myBuild.isOutdated();
  }

  @XmlAttribute
  public boolean isProbablyHanging() {
    return myBuild.isProbablyHanging();
  }

  @XmlAttribute
  public String getCurrentStageText() {
    return myBuild.getCurrentPath();
  }
}
