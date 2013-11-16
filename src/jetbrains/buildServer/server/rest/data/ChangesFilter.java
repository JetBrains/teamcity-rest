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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.RemoteBuildType;
import jetbrains.buildServer.serverSide.userChanges.UserChangesFacade;
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
  @Nullable private final String myBranchName;
  @Nullable private final SBuild myBuild;
  @Nullable private final VcsRootInstance myVcsRootInstance;
  @Nullable private final SVcsRoot myVcsRoot;
  @Nullable private final Long mySinceChangeId;
  @Nullable private final String myVcsUsername;
  @Nullable private final SUser myUser;
  @Nullable private final Boolean myPersonal;
  @Nullable private final String myDisplayVersion;
  @Nullable private final String myInternalVersion;
  @Nullable private final Locator myCommentLocator;
  @Nullable private final Locator myFileLocator;

  private final boolean myEnforceChangeViewPermissson;
  @NotNull private final DataProvider myDataProvider;

  public ChangesFilter(@Nullable final SProject project,
                       @Nullable final SBuildType buildType,
                       @Nullable final String branchName,
                       @Nullable final SBuild build,
                       @Nullable final VcsRootInstance vcsRootInstance,
                       @Nullable final SVcsRoot vcsRoot,
                       @Nullable final Long sinceChangeId,
                       @Nullable final String vcsUsername,
                       @Nullable final SUser user,
                       @Nullable final Boolean personal,
                       @Nullable final String displayVersion,
                       @Nullable final String internalVersion,
                       @Nullable final String commentLocator,
                       @Nullable final String fileLocator,
                       @Nullable final Long start,
                       @Nullable final Integer count,
                       @Nullable final Long lookupLimit,
                       @NotNull final DataProvider dataProvider) {
    super(start, count, lookupLimit);
    myProject = project;
    myBuildType = buildType;
    myBranchName = branchName;
    myBuild = build;
    myVcsRootInstance = vcsRootInstance;
    myVcsRoot = vcsRoot;
    mySinceChangeId = sinceChangeId;
    myVcsUsername = vcsUsername;
    myUser = user;
    myPersonal = personal;
    myDisplayVersion = displayVersion;
    myInternalVersion = internalVersion;
    myDataProvider = dataProvider;
    myCommentLocator = commentLocator != null ? new Locator(commentLocator) : null;
    myFileLocator = fileLocator != null ? new Locator(fileLocator) : null;

    if (myVcsRoot != null && myPersonal != null && myPersonal){
      throw new BadRequestException("filtering personal changes by VCS root is not supported.");
    }
    if (myVcsRootInstance != null && myPersonal != null && myPersonal){
      throw new BadRequestException("filtering personal changes by VCS root instance is not supported.");
    }

    myEnforceChangeViewPermissson = TeamCityProperties.getBoolean("rest.request.changes.check.enforceChangeViewPermissson");
  }

  @Override
  protected boolean isIncluded(@NotNull final SVcsModification change) {
    //myBuildType, myProject and myBranchName are handled on getting initial collection to filter

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

    if (mySinceChangeId != null && mySinceChangeId >= change.getId()) {
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

    if (myPersonal != null && myPersonal) {
      //initial collection can contain changes from any buildType/project
      if (myBuildType != null) {
        if (!isPersonalChangeMatchesBuildType(change, myBuildType)){
          return false;
        }
      }
      if (myProject != null && !change.getRelatedProjects().contains(myProject)){
        return false;
      }
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

    return !myEnforceChangeViewPermissson || myDataProvider.checkCanView(change);
  }

  private static boolean isPersonalChangeMatchesBuildType(@NotNull final SVcsModification change, @NotNull final SBuildType buildType) {
    final Collection<SBuildType> relatedPersonalConfigurations = change.getRelatedConfigurations();
    boolean matches = false;
    for (SBuildType personalConfiguration : relatedPersonalConfigurations) {
      if (personalConfiguration.isPersonal()){
         if (buildType.getInternalId().equals(((RemoteBuildType)personalConfiguration).getSourceBuildType().getInternalId())){
          matches =  true;
          break;
         }
      } else{
        if (buildType.getInternalId().equals((personalConfiguration.getInternalId()))){
         matches =  true;
         break;
        }
      }
    }
    return matches;
  }

  @Override
  public boolean shouldStop(final SVcsModification item) {
    if (mySinceChangeId != null && mySinceChangeId >= item.getId()) {
      return true;
    }
    return super.shouldStop(item);
  }

  public List<SVcsModification> getMatchingChanges(@NotNull final ServiceLocator serviceLocator) {
    final FilterItemProcessor<SVcsModification> filterItemProcessor = new FilterItemProcessor<SVcsModification>(this);
    processList(getInitialChangesCollection(serviceLocator), filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  //todo: BuiltType is ignored if VCS root is specified; sometimes we return filtered changes by checkout rules and sometimes not
  //todo: sometimes with pending sometimes not?
  @NotNull
  private List<SVcsModification> getInitialChangesCollection(@NotNull final ServiceLocator serviceLocator) {
    final VcsModificationHistory vcsHistory = serviceLocator.getSingletonService(VcsManager.class).getVcsHistory();
    if (myBranchName != null){
      if (myBuildType == null){
        throw new BadRequestException("Filtering changes by branch is only supported when buildType is specified.");
      }
      return getBranchChanges(myBuildType, myBranchName);
    } if (myPersonal != null && myPersonal){
      if (myUser == null){
        throw new BadRequestException("Serving personal changes is only supported when user is specified.");
      }
      return serviceLocator.getSingletonService(UserChangesFacade.class).getAllVcsModifications(myUser);
    } else if (myBuild != null) {
      return getBuildChanges(myBuild);
    } else if (myBuildType != null) {
      return getBuildTypeChanges(vcsHistory, myBuildType);
    } else if (myVcsRootInstance != null) {
      if (mySinceChangeId != null) {
        return vcsHistory.getModificationsInRange(myVcsRootInstance, mySinceChangeId, null);
      } else {
        //todo: highly inefficient!
        return vcsHistory.getAllModifications(myVcsRootInstance);
      }
    } else if (myProject != null) {
      return getProjectChanges(vcsHistory, myProject, mySinceChangeId);
    } else {
      //todo: highly inefficient!
      return vcsHistory.getAllModifications();
    }
  }

  private List<SVcsModification> getBuildTypeChanges(final VcsModificationHistory vcsHistory,
                                                     final SBuildType buildType) {
    if (TeamCityProperties.getBoolean(IGNORE_CHANGES_FROM_DEPENDENCIES_OPTION) || !buildType.getOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES)){
      return vcsHistory.getAllModifications(buildType);
    }
    final List<ChangeDescriptor> changes = ((BuildTypeEx)buildType).getDetectedChanges(SelectPrevBuildPolicy.SINCE_FIRST_BUILD);

    return convertChanges(changes);
  }

  private ArrayList<SVcsModification> convertChanges(final List<ChangeDescriptor> changes) {
    final ArrayList<SVcsModification> result = new ArrayList<SVcsModification>();
    for (ChangeDescriptor change : changes) {
      SVcsModification mod = change.getRelatedVcsChange();
      if (mod != null) result.add(mod);
    }
    return result;
  }

  private List<SVcsModification> getBranchChanges(@NotNull final SBuildType buildType, @NotNull final String branchName) {
    final boolean includeDependencyChanges = TeamCityProperties.getBoolean(IGNORE_CHANGES_FROM_DEPENDENCIES_OPTION) || !buildType.getOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES);
    final List<ChangeDescriptor> changes =  ((BuildTypeEx)buildType).getBranchByDisplayName(branchName).getDetectedChanges(SelectPrevBuildPolicy.SINCE_FIRST_BUILD, includeDependencyChanges);
    return convertChanges(changes);
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
                                                          @Nullable final Long sinceChangeId) {
    final List<VcsRootInstance> vcsRoots = project.getVcsRootInstances();
    final List<SVcsModification> result = new ArrayList<SVcsModification>();
    for (VcsRootInstance root : vcsRoots) {
      if (sinceChangeId != null) {
        result.addAll(vcsHistory.getModificationsInRange(root, sinceChangeId, null));
      } else {
        //todo: highly inefficient!
        result.addAll(vcsHistory.getAllModifications(root));
      }
    }
    Collections.sort(result);
    return result;
  }
}
