/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import java.util.List;
import jetbrains.buildServer.serverSide.BuildHistory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildsFilter extends AbstractFilter<SFinishedBuild> {
  @Nullable private final String myStatus;
  private final boolean myIncludePersonal;
  private final boolean myIncludeCanceled;
  private final boolean myOnlyPinned;
  @Nullable private final String myAgentName;
  @Nullable private final RangeLimit mySince;
  @Nullable private final SUser myUser;
  @Nullable private final SBuildType myBuildType;

  /**
   * @param buildType       build type to return builds from, can be null to return all builds
   * @param status          status of the builds to include
   * @param user            limit builds to those triggered by user, can be null to return all builds
   * @param includePersonal limit builds to non-personal
   * @param includeCanceled limit builds to non-canceled
   * @param onlyPinned      limit builds to pinned
   * @param agentName       limit builds to those ran on specified agent, can be null to return all builds
   * @param since           the RangeLimit to return only the builds since the limit. If contains build, it is not included, if contains the date, the builds that were started at and later then the date are included
   * @param start           the index of the first build to return (begins with 0), 0 by default
   * @param count           the number of builds to return, all by default
   */
  public BuildsFilter(@Nullable final SBuildType buildType,
                      @Nullable final String status,
                      @Nullable final SUser user,
                      final boolean includePersonal,
                      final boolean includeCanceled,
                      final boolean onlyPinned,
                      @Nullable final String agentName,
                      @Nullable final RangeLimit since,
                      @Nullable final Long start,
                      @Nullable final Integer count) {
    super(start, count);
    myBuildType = buildType;
    myStatus = status;
    myUser = user;
    myIncludePersonal = includePersonal;
    myIncludeCanceled = includeCanceled;
    myOnlyPinned = onlyPinned;
    myAgentName = agentName;
    mySince = since;
  }

  protected boolean isIncluded(@NotNull final SFinishedBuild build) {
    if (myAgentName != null && !myAgentName.equals(build.getAgentName())) {
      return false;
    }
    if (myBuildType != null && !myBuildType.getBuildTypeId().equals(build.getBuildTypeId())) {
      return false;
    }
    if (myStatus != null && !myStatus.equalsIgnoreCase(build.getStatusDescriptor().getStatus().getText())) {
      return false;
    }
    if (!myIncludePersonal && build.isPersonal()) {
      return false;
    }
    if (!myIncludeCanceled && (build.getCanceledInfo() != null)) {
      return false;
    }
    if (myOnlyPinned && !build.isPinned()) {
      return false;
    }
    if (myUser != null) {
      final SUser userWhoTriggered = build.getTriggeredBy().getUser();
      if (!build.getTriggeredBy().isTriggeredByUser() || (userWhoTriggered != null && myUser.getId() != userWhoTriggered.getId())) {
        return false;
      }
    }
    if (mySince != null) {
      if (mySince.getDate().after(build.getStartDate())) {
        return false;
      } else {
        //filter out the build itself (see BuildHistory.getEntriesSince )
        final SBuild sinceBuild = mySince.getBuild();
        if (sinceBuild != null && sinceBuild.getBuildId() == build.getBuildId()) {
          return false;
        }
      }
    }
    return true;
  }

  public List<SFinishedBuild> getMatchingBuilds(@NotNull final BuildHistory buildHistory) {
    final FilterItemProcessor<SFinishedBuild> buildsFilterItemProcessor = new FilterItemProcessor<SFinishedBuild>(this);
    if (myBuildType != null) {
      SBuild sinceBuild;
      if (mySince != null && (sinceBuild = mySince.getBuild()) != null) {
        processList(buildHistory.getEntriesSince(sinceBuild, myBuildType), buildsFilterItemProcessor);
      } else {
        buildHistory.processEntries(myBuildType.getBuildTypeId(), getUserForProcessEntries(), myIncludePersonal, myIncludeCanceled, false,
                                    buildsFilterItemProcessor);
      }
    } else {
      buildHistory.processEntries(buildsFilterItemProcessor);
    }
    return buildsFilterItemProcessor.getResult();
  }

  private User getUserForProcessEntries() {
    if (myIncludePersonal && myUser != null) {
      return myUser;
    }
    return null;
  }
}
