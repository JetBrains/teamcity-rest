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

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.serverSide.BuildHistory;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildsFilterSettings {
  @Nullable private final String myBuildTypeId;
  @Nullable private final String myStatus;
  @Nullable private final String myUsername;
  private final boolean myIncludePersonal;
  private final boolean myIncludeCanceled;
  private final boolean myOnlyPinned;
  @Nullable private final String myAgentName;
  @Nullable private final Long myStart;
  @Nullable private final Integer myCount;

  /**
   * @param buildTypeId     id of the build type to return builds from, can be null to return all builds
   * @param status          status of the builds to include
   * @param username        limit builds to those triggered by user, can be null to return all builds
   * @param includePersonal limit builds to non-personal
   * @param includeCanceled limit builds to non-canceled
   * @param onlyPinned      limit builds to pinned
   * @param agentName       limit builds to those ran on specified agent, can be null to return all builds
   * @param start           the index of the first build to return (begins with 0), 0 by default
   * @param count           the number of builds to return, all by default
   */
  public BuildsFilterSettings(@Nullable final String buildTypeId,
                              @Nullable final String status,
                              @Nullable final String username,
                              final boolean includePersonal,
                              final boolean includeCanceled,
                              final boolean onlyPinned,
                              @Nullable final String agentName,
                              @Nullable final Long start,
                              @Nullable final Integer count) {
    myBuildTypeId = buildTypeId;
    myStatus = status;
    myUsername = username;
    myIncludePersonal = includePersonal;
    myIncludeCanceled = includeCanceled;
    myOnlyPinned = onlyPinned;
    myAgentName = agentName;
    myStart = start;
    myCount = count;
  }

  private boolean isIncluded(final SFinishedBuild build) {
    if (myAgentName != null && !myAgentName.equals(build.getAgentName())) {
      return false;
    }
    if (myBuildTypeId != null && !myBuildTypeId.equals(build.getBuildTypeId())) {
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
    if (myUsername != null) {
      final SUser triggeredByUser = build.getTriggeredBy().getUser();
      if (!build.getTriggeredBy().isTriggeredByUser() || (triggeredByUser != null && !myUsername.equals(triggeredByUser.getUsername()))) {
        return false;
      }
    }
    return true;
  }

  private boolean isIncludedByRange(final long index) {
    final long actualStart = myStart == null ? 0 : myStart;
    return (index >= actualStart) && (myCount == null || index < actualStart + myCount);
  }

  private boolean isBelowUpperRangeLimit(final long index) {
    final long actualStart = myStart == null ? 0 : myStart;
    return myCount == null || index < actualStart + myCount;
  }

  public List<SFinishedBuild> getMatchingBuilds(@NotNull final BuildHistory buildHistory, @NotNull final UserModel userModel) {
    final BuildsFilterItemProcessor buildsFilterItemProcessor = new BuildsFilterItemProcessor(this);
    if (myBuildTypeId != null) {
      User user = null;
      if (myIncludePersonal && myUsername != null) {
        user = userModel.findUserAccount(null, myUsername);
      }
      buildHistory.processEntries(myBuildTypeId, user, myIncludePersonal, myIncludeCanceled, false, buildsFilterItemProcessor);
    } else {
      buildHistory.processEntries(buildsFilterItemProcessor);
    }
    return buildsFilterItemProcessor.getResult();
  }

  private static class BuildsFilterItemProcessor implements ItemProcessor<SFinishedBuild> {
    long myCurrentIndex = 0;
    private final BuildsFilterSettings myBuildsFilterSettings;
    private final ArrayList<SFinishedBuild> myList = new ArrayList<SFinishedBuild>();

    public BuildsFilterItemProcessor(final BuildsFilterSettings buildsFilterSettings) {
      myBuildsFilterSettings = buildsFilterSettings;
    }

    public boolean processItem(final SFinishedBuild item) {
      if (!myBuildsFilterSettings.isIncluded(item)) {
        return true;
      }
      if (myBuildsFilterSettings.isIncludedByRange(myCurrentIndex)) {
        myList.add(item);
      }
      ++myCurrentIndex;
      return myBuildsFilterSettings.isBelowUpperRangeLimit(myCurrentIndex);
    }

    public ArrayList<SFinishedBuild> getResult() {
      return myList;
    }
  }
}
