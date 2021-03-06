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

package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.ShortStatistics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestCountersData {
  @NotNull
  private Integer myCount = 0;
  @Nullable
  private Integer myMuted = 0;
  @Nullable
  private Integer myPassed = 0;
  @Nullable
  private Integer myFailed = 0;
  @Nullable
  private Integer myIgnored = 0;
  @Nullable
  private Integer myNewFailed = 0;
  @Nullable
  private Long myDuration = 0l;

  public TestCountersData() { }

  public TestCountersData(@NotNull final ShortStatistics statistics) {
    myCount     = statistics.getAllTestCount();
    myIgnored   = statistics.getIgnoredTestCount();
    myPassed    = statistics.getPassedTestCount();
    myFailed    = statistics.getFailedTestCount();
    myNewFailed = statistics.getNewFailedCount();
    myMuted     = statistics.getMutedTestsCount();
    myDuration  = statistics.getTotalDuration();
  }

  public TestCountersData(@NotNull final List<STestRun> testRuns) {
    this(testRuns, true, true, true, true, true, true);
  }

  public TestCountersData(@NotNull final List<STestRun> testRuns,
                          boolean calcSuccess,
                          boolean calcFailed,
                          boolean calcMuted,
                          boolean calcIgnored,
                          boolean calcNewFailure,
                          boolean calcDuration) {
    if (calcFailed) myFailed = 0;
    if (calcMuted) myMuted = 0;
    if (calcSuccess) myPassed = 0;
    if (calcIgnored) myIgnored = 0;
    if (calcNewFailure) myNewFailed = 0;
    if (calcDuration) myDuration = 0l;

    for(STestRun testRun : testRuns) {
      if (calcMuted && testRun.isMuted()) {
        myMuted++;
      }
      if (calcIgnored && testRun.isIgnored()) {
        myIgnored++;
      }
      final Status status = testRun.getStatus();
      if (calcSuccess && status.isSuccessful()) {
        myPassed++;
      }
      if (calcFailed && status.isFailed() && !testRun.isMuted()) {
        myFailed++;
      }
      if (calcNewFailure && testRun.isNewFailure() && !testRun.isMuted()) {
        myNewFailed++;
      }
      if(calcDuration) {
        myDuration += testRun.getDuration();
      }
    }
    myCount = testRuns.size();
  }

  public TestCountersData(@NotNull Integer count,
                          @Nullable Integer passed,
                          @Nullable Integer failed,
                          @Nullable Integer muted,
                          @Nullable Integer ignored,
                          @Nullable Integer newFailed,
                          @Nullable Long duration) {
    myCount = count;
    myPassed = passed;
    myFailed = failed;
    myMuted = muted;
    myIgnored = ignored;
    myNewFailed = newFailed;
    myDuration = duration;
  }

  @NotNull
  public Integer getCount() {
    return myCount;
  }

  @Nullable
  public Integer getMuted() {
    return myMuted;
  }

  @Nullable
  public Integer getPassed() {
    return myPassed;
  }

  @Nullable
  public Integer getFailed() {
    return myFailed;
  }

  @Nullable
  public Integer getIgnored() {
    return myIgnored;
  }

  @Nullable
  public Integer getNewFailed() {
    return myNewFailed;
  }

  @Nullable
  public Long getDuration() {
    return myDuration;
  }
}
