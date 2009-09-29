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
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
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
  @Nullable private SProject myProject;
  @Nullable private SBuildType myBuildType;
  @Nullable private SBuild myBuild;
  @Nullable private SVcsRoot myVcsRoot;
  @Nullable private SVcsModification mySinceChange;

  public ChangesFilter(@Nullable final SProject project,
                       @Nullable final SBuildType buildType,
                       @Nullable final SBuild build,
                       @Nullable final SVcsRoot vcsRoot,
                       @Nullable final SVcsModification sinceChange,
                       @Nullable final Long start,
                       @Nullable final Integer count) {
    super(start, count);
    myProject = project;
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

  //todo: BuiltType is ignored if VCS root is specified; sometimes we return filtered changes by checkout rules and sometimes not
  //todo: sometimes with panding sometimes not?
  public List<SVcsModification> getMatchingChanges(@NotNull final VcsModificationHistory vcsHistory) {


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
    } else if (myProject != null) {
      processList(getProjectChanges(vcsHistory, myProject, mySinceChange), filterItemProcessor);
    } else {
      //todo: highly inefficient!
      processList(vcsHistory.getAllModifications(), filterItemProcessor);
    }

    return filterItemProcessor.getResult();
  }

  static private List<SVcsModification> getProjectChanges(@NotNull final VcsModificationHistory vcsHistory,
                                                          @NotNull final SProject project,
                                                          @Nullable final SVcsModification sinceChange) {
    final List<SVcsRoot> vcsRoots = project.getVcsRoots();
    final List<SVcsModification> result = new ArrayList<SVcsModification>();
    for (SVcsRoot root : vcsRoots) {
      if (sinceChange != null) {
        result.addAll(vcsHistory.getModificationsInRange(root, sinceChange.getId(), null));
      } else {
        //todo: highly inefficient!
        result.addAll(vcsHistory.getAllModifications(root));
      }
    }
    Collections.sort(result);
    return result;
  }
}
