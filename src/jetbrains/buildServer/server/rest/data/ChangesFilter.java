/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2009
 */
public class ChangesFilter extends AbstractFilter<SVcsModification> {
  public static final String IGNORE_CHANGES_FROM_DEPENDENCIES_OPTION = "rest.ignoreChangesFromDependenciesOption";
  @Nullable private final SProject myProject;
  @Nullable private final SBuildType myBuildType;
  @Nullable private final SBuild myBuild;
  @Nullable private final VcsRootInstance myVcsRootInstance;
  @Nullable private final SVcsRoot myVcsRoot;
  @Nullable private final SVcsModification mySinceChange;
  @Nullable private final String myVcsUsername;
  @Nullable private final SUser myUser;
  @Nullable private final Boolean myPersonal;
  @Nullable private final String myDisplayVersion;
  @Nullable private final String myInternalVersion;
  @Nullable private final Locator myCommentLocator;
  @Nullable private final Locator myFileLocator;
  @Nullable private final Long myLookupLimit;

  public ChangesFilter(@Nullable final SProject project,
                       @Nullable final SBuildType buildType,
                       @Nullable final SBuild build,
                       @Nullable final VcsRootInstance vcsRootInstance,
                       @Nullable final SVcsRoot vcsRoot,
                       @Nullable final SVcsModification sinceChange,
                       @Nullable final String vcsUsername,
                       @Nullable final SUser user,
                       @Nullable final Boolean personal,
                       @Nullable final String displayVersion,
                       @Nullable final String internalVersion,
                       @Nullable final String commentLocator,
                       @Nullable final String fileLocator,
                       @Nullable final Long start,
                       @Nullable final Integer count,
                       @Nullable final Long lookupLimit) {
    super(start, count, lookupLimit);
    myProject = project;
    myBuildType = buildType;
    myBuild = build;
    myVcsRootInstance = vcsRootInstance;
    myVcsRoot = vcsRoot;
    mySinceChange = sinceChange;
    myVcsUsername = vcsUsername;
    myUser = user;
    myPersonal = personal;
    myDisplayVersion = displayVersion;
    myInternalVersion = internalVersion;
    myCommentLocator = commentLocator != null ? new Locator(commentLocator) : null;
    myFileLocator = fileLocator != null ? new Locator(fileLocator) : null;
    myLookupLimit = lookupLimit;

    if (myVcsRoot != null && myPersonal != null && myPersonal){
      throw new BadRequestException("filtering personal changes by VCS root is not supported.");
    }
    if (myVcsRootInstance != null && myPersonal != null && myPersonal){
      throw new BadRequestException("filtering personal changes by VCS root instance is not supported.");
    }
  }

  @Override
  protected boolean isIncluded(@NotNull final SVcsModification change) {
    if (myVcsRootInstance != null) {
      if (change.isPersonal()) {
        return false;
      } else {
        if (myVcsRootInstance.getId() != change.getVcsRoot().getId()) {
          return false;
        }
      }
    }

    if (myVcsRoot != null) {
      if (change.isPersonal()) {
        return false;
      } else {
        if (myVcsRoot.getId() != change.getVcsRoot().getParentId()) {
          return false;
        }
      }
    }

    if (mySinceChange != null && mySinceChange.getId() >= change.getId()) {
      return false;
    }

    if (myVcsUsername != null && !myVcsUsername.equalsIgnoreCase(change.getUserName())){ //todo: is ignoreCase is right here?
      return false;
    }

    if (myUser != null){
      if (!change.getCommitters().contains(myUser)) return false;
    }

    if (!FilterUtil.isIncludedByBooleanFilter(myPersonal, change.isPersonal())){
      return false;
    }

    if (myInternalVersion != null && !myInternalVersion.equals(change.getVersion())){
      return false;
    }

    if (myDisplayVersion != null && !myDisplayVersion.equals(change.getDisplayVersion())){
      return false;
    }

    if (myCommentLocator != null){
      final String containsText = myCommentLocator.getSingleDimensionValue("contains"); //todo: check uncnown locator dimensions
      if (containsText != null && !change.getDescription().contains(containsText)) {
        return false;
      }
    }

    if (myFileLocator != null){
      final String pathLocatorText = myFileLocator.getSingleDimensionValue("path"); //todo: check uncnown locator dimensions
      if (pathLocatorText != null){
        final Locator pathLocator = new Locator(pathLocatorText);
        final String containsText = pathLocator.getSingleDimensionValue("contains");
        if (containsText != null) {
          boolean oneOfFileMatches = false;
          for (VcsFileModification vcsFileModification : change.getChanges()) {
            if (vcsFileModification.getFileName().contains(containsText)){
              oneOfFileMatches = true;
              break;
            }
          }
          if (!oneOfFileMatches) return false;
        }
      }
    }

    // include by myBuild should be already handled by this time on the upper level

    return true;
  }

  //todo: BuiltType is ignored if VCS root is specified; sometimes we return filtered changes by checkout rules and sometimes not
  //todo: sometimes with pending sometimes not?
  public List<SVcsModification> getMatchingChanges(@NotNull final VcsModificationHistory vcsHistory) {


    final FilterItemProcessor<SVcsModification> filterItemProcessor = new FilterItemProcessor<SVcsModification>(this);
    if (myBuild != null) {
      processList(getBuildChanges(myBuild), filterItemProcessor);
    } else if (myBuildType != null) {
      processList(getBuildTypeChanges(vcsHistory, myBuildType), filterItemProcessor);
    } else if (myVcsRootInstance != null) {
      if (mySinceChange != null) {
        processList(vcsHistory.getModificationsInRange(myVcsRootInstance, mySinceChange.getId(), null), filterItemProcessor);
      } else {
        //todo: highly inefficient!
        processList(vcsHistory.getAllModifications(myVcsRootInstance), filterItemProcessor);
      }
    } else if (myProject != null) {
      processList(getProjectChanges(vcsHistory, myProject, mySinceChange), filterItemProcessor);
    } else {
      //todo: highly inefficient!
      processList(vcsHistory.getAllModifications(), filterItemProcessor);
    }

    return filterItemProcessor.getResult();
  }

  private List<SVcsModification> getBuildTypeChanges(final VcsModificationHistory vcsHistory,
                                                     final SBuildType buildType) {
    if (TeamCityProperties.getBoolean(IGNORE_CHANGES_FROM_DEPENDENCIES_OPTION) || !buildType.getOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES)){
      return vcsHistory.getAllModifications(buildType);
    }
    final List<ChangeDescriptor> changes = ((BuildTypeEx)buildType).getDetectedChanges(SelectPrevBuildPolicy.SINCE_NULL_BUILD);

    final ArrayList<SVcsModification> result = new ArrayList<SVcsModification>();
    for (ChangeDescriptor change : changes) {
      SVcsModification mod = change.getRelatedVcsChange();
      if (mod != null) result.add(mod);
    }
    return result;
  }

  private static List<SVcsModification> getBuildChanges(final SBuild build) {
    if (TeamCityProperties.getBoolean(IGNORE_CHANGES_FROM_DEPENDENCIES_OPTION)) {
      return build.getContainingChanges();
    }

    List<SVcsModification> res = new ArrayList<SVcsModification>();
    for (ChangeDescriptor ch: ((BuildPromotionEx)build.getBuildPromotion()).getDetectedChanges(SelectPrevBuildPolicy.SINCE_LAST_BUILD)) {
      final SVcsModification mod = ch.getRelatedVcsChange();
      if (mod != null) {
        res.add(mod);
      }
    }

    return res;
  }

  static private List<SVcsModification> getProjectChanges(@NotNull final VcsModificationHistory vcsHistory,
                                                          @NotNull final SProject project,
                                                          @Nullable final SVcsModification sinceChange) {
    final List<VcsRootInstance> vcsRoots = project.getVcsRootInstances();
    final List<SVcsModification> result = new ArrayList<SVcsModification>();
    for (VcsRootInstance root : vcsRoots) {
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
