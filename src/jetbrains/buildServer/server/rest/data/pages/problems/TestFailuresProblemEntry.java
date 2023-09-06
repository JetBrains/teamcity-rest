/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.pages.problems;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestFailuresProblemEntry {
  private final LongFunction<STest> myTestResolver;
  private List<InvestigationWrapper> myInvestigations;
  private List<STestRun> myRecentFailures;
  private long myTestNameId;
  private List<SingleTestMuteInfoView> myMutes;

  public TestFailuresProblemEntry(@NotNull LongFunction<STest> testResolverByTestNameId) {
    myTestResolver = testResolverByTestNameId;
  }

  public void setInvestigations(@NotNull List<InvestigationWrapper> investigations) {
    myInvestigations = investigations;
  }

  public void setRecentFailures(@Nullable List<STestRun> recentFailures) {
    myRecentFailures = recentFailures;
  }

  public void setTestNameId(long testNameId) {
    myTestNameId = testNameId;
  }

  public void setMutes(@Nullable List<SingleTestMuteInfoView> mutes) {
    myMutes = mutes;
  }

  @Nullable
  public List<STestRun> getRecentFailures() {
    return myRecentFailures;
  }

  @Nullable
  public List<InvestigationWrapper> getInvestigations() {
    return myInvestigations;
  }

  public long getTestNameId() {
    return myTestNameId;
  }

  @Nullable
  public List<SingleTestMuteInfoView> getMutes() {
    return myMutes;
  }

  @NotNull
  public STest getTest() {
    return myTestResolver.apply(myTestNameId);
  }

  /**
   * Indicates whether any of the test runs in this entry are considered to be a new failure.
   */
  public boolean isNewFailure() {
    if(myRecentFailures == null) {
      return false;
    }

    return myRecentFailures.stream().anyMatch(tr -> tr.isNewFailure());
  }

  @Nullable
  public Set<SBuildType> getFailingBuildTypes() {
    if(myRecentFailures == null) {
      return null;
    }

    return myRecentFailures.stream()
                    .map(tr -> tr.getBuild().getBuildType())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
  }
}
