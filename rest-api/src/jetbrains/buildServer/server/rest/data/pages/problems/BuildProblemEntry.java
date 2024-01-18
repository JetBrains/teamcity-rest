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

import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildProblemEntry {
  private final BuildProblem myProblem;
  private final Collection<MuteInfo> myMuteInfos;

  public BuildProblemEntry(@NotNull BuildProblem problem, @Nullable Collection<MuteInfo> muteInfos) {
    myProblem = problem;
    myMuteInfos = muteInfos;
  }

  @NotNull
  public List<BuildProblemResponsibilityEntry> getInvestigations() {
    return myProblem.getAllResponsibilities();
  }

  @NotNull
  public BuildProblem getProblem() {
    return myProblem;
  }

  @NotNull
  public BuildPromotion getBuildPromotion() {
    return myProblem.getBuildPromotion();
  }

  @Nullable
  public Collection<MuteInfo> getMuteInfos() {
    return myMuteInfos;
  }

  @Override
  public String toString() {
    return "BuildProblemEntry{" +
           "myProblem=" + myProblem +
           ", myMuteInfos=" + myMuteInfos +
           '}';
  }
}
