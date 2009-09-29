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
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsModificationHistory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2009
 */
public class ChangesFilter extends AbstractFilter<SVcsModification> {
  @Nullable private SBuildType myBuildType;
  @Nullable private SBuild myBuild;
  @Nullable private SVcsRoot myVcsRoot;
  @Nullable private SVcsModification mySinceChange;

  public ChangesFilter(@Nullable final SBuildType buildType,
                       @Nullable final SBuild build,
                       @Nullable final SVcsRoot vcsRoot,
                       @Nullable final SVcsModification sinceChange,
                       @Nullable final Long start,
                       @Nullable final Integer count) {
    super(start, count);
    myBuildType = buildType;
    myBuild = build;
    myVcsRoot = vcsRoot;
    mySinceChange = sinceChange;
  }

  @Override
  protected boolean isIncluded(@NotNull final SVcsModification change) {
    if (myVcsRoot != null) {
      if (change.isPersonal()) {
        return false;
      } else {
        assert myVcsRoot != null;
        if (myVcsRoot.getId() != change.getVcsRoot().getId()) {
          return false;
        }
      }
    }

    if (mySinceChange != null && mySinceChange.getId() >= change.getId()) {
      return false;
    }

    // include by myBuild should be already handled by this time on the upper level

    return true;
  }

  public List<SVcsModification> getMatchingChanges(final VcsModificationHistory vcsHistory) {


    final FilterItemProcessor<SVcsModification> filterItemProcessor = new FilterItemProcessor<SVcsModification>(this);
    if (myBuild != null) {
      processList(myBuild.getContainingChanges(), filterItemProcessor);
    } else if (myBuildType != null) {
      processList(vcsHistory.getAllModifications(myBuildType), filterItemProcessor);
    } else if (myVcsRoot != null) {
      if (mySinceChange != null) {
        processList(vcsHistory.getModificationsInRange(myVcsRoot, mySinceChange.getId(), null), filterItemProcessor);
      } else {
        //todo: highly inefficient!
        processList(vcsHistory.getAllModifications(myVcsRoot), filterItemProcessor);
      }
    } else {
      //todo: highly inefficient!
      processList(vcsHistory.getAllModifications(), filterItemProcessor);
    }

    return filterItemProcessor.getResult();
  }
}
