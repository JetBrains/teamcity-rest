/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.util.function.Function;
import jetbrains.buildServer.BuildTypeDescriptor;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.change.SVcsModificationOrChangeDescriptor;
import jetbrains.buildServer.server.rest.data.util.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.impl.RemoteBuildType;
import jetbrains.buildServer.serverSide.impl.RemoteBuildTypeIdUtil;
import jetbrains.buildServer.serverSide.userChanges.UserChangesFacade;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.graph.BFSVisitorAdapter;
import jetbrains.buildServer.util.graph.DAG;
import jetbrains.buildServer.util.graph.DAGIterator;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsModificationEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jetbrains.buildServer.serverSide.auth.AuthUtil.hasReadAccessTo;

/**
 * @author Yegor.Yarko
 * @date 12.05.13
 */
@LocatorResource(value = LocatorName.CHANGE,
  extraDimensions = {AbstractFinder.DIMENSION_ID, PagerData.START, PagerData.COUNT, AbstractFinder.DIMENSION_ITEM},
  baseEntity = "Change",
  examples = {
    "`username:MyVCSUsername` — find last 100 changes made by user with `MyVCSUsername` VCS username.",
    "`pending:true,buildType:<buildTypeLocator>` — find all pending changes on build configuration found by `buildTypeLocator`."
  }
)
public class ChangeFinder extends AbstractFinder<SVcsModificationOrChangeDescriptor> {
  public static final String IGNORE_CHANGES_FROM_DEPENDENCIES_OPTION = "rest.ignoreChangesFromDependenciesOption";

  public static final String PERSONAL = "personal";
  @LocatorDimension(value = "project", format = LocatorName.PROJECT, notes = "Project locator.")
  public static final String PROJECT = "project";
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  private static final String AFFECTED_PROJECT = "affectedProject";
  @LocatorDimension(value = "buildType", format = LocatorName.BUILD_TYPE, notes = "Build type locator.")
  public static final String BUILD_TYPE = "buildType";
  @LocatorDimension(value = "build", format = LocatorName.BUILD, notes = "Build locator.")
  public static final String BUILD = "build";
  public static final String PROMOTION = BuildPromotionFinder.PROMOTION_ID;
  @LocatorDimension(value = "vcsRoot", format = LocatorName.VCS_ROOT, notes = "VCS root locator.")
  public static final String VCS_ROOT = "vcsRoot";
  @LocatorDimension(value = "vcsRootInstance", format = LocatorName.VCS_ROOT_INSTANCE, notes = "VCS instance locator.")
  public static final String VCS_ROOT_INSTANCE = "vcsRootInstance";
  @LocatorDimension(value = "username", notes = "VCS side username.")
  public static final String USERNAME = "username";
  @LocatorDimension(value = "user", format = LocatorName.USER, notes = "User locator.")
  public static final String USER = "user";
  @LocatorDimension("version")
  public static final String VERSION = "version";
  @LocatorDimension("internalVersion")
  public static final String INTERNAL_VERSION = "internalVersion";
  @LocatorDimension("comment")
  public static final String COMMENT = "comment";
  @LocatorDimension("file")
  public static final String FILE = "file";
  @LocatorDimension(value = "sinceChange", notes = "Commit SHA since which the changes should be returned.")
  public static final String SINCE_CHANGE = "sinceChange";
  @LocatorDimension(value = "pending", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is pending.")
  public static final String PENDING = "pending";
  public static final String BRANCH = "branch";
  public static final String CHILD_CHANGE = "childChange";
  public static final String PARENT_CHANGE = "parentChange";
  public static final String DAG_TRAVERSE = "dag";
  public static final String PREV_BUILD_POLICY = "policy";
  public static final String CHANGES_FROM_DEPS = "changesFromDependencies"; // Before making public see TW-73600
  public static final String SETTINGS_CHANGES = "versionedSettings"; //experimental

  @NotNull private final PermissionChecker myPermissionChecker;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final BuildPromotionFinder myBuildPromotionFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final VcsRootFinder myVcsRootFinder;
  @NotNull private final VcsRootInstanceFinder myVcsRootInstanceFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final VcsManager myVcsManager;
  @NotNull private final VcsModificationHistory myVcsModificationHistory;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final BranchFinder myBranchFinder;

  public ChangeFinder(
    @NotNull final ProjectFinder projectFinder,
    @NotNull final BuildFinder buildFinder,
    @NotNull final BuildPromotionFinder buildPromotionFinder,
    @NotNull final BuildTypeFinder buildTypeFinder,
    @NotNull final VcsRootFinder vcsRootFinder,
    @NotNull final VcsRootInstanceFinder vcsRootInstanceFinder,
    @NotNull final UserFinder userFinder,
    @NotNull final VcsManager vcsManager,
    @NotNull final VcsModificationHistory vcsModificationHistory,
    @NotNull final BranchFinder branchFinder,
    @NotNull final ServiceLocator serviceLocator,
    @NotNull final PermissionChecker permissionChecker
  ) {
    super(DIMENSION_ID, PROJECT, BUILD_TYPE, BUILD, VCS_ROOT, VCS_ROOT_INSTANCE, USERNAME, USER, VERSION, INTERNAL_VERSION, COMMENT, FILE, PENDING,
          SINCE_CHANGE, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    setHiddenDimensions(BRANCH, PERSONAL, CHILD_CHANGE, PARENT_CHANGE, DAG_TRAVERSE, PROMOTION, PREV_BUILD_POLICY, CHANGES_FROM_DEPS, SETTINGS_CHANGES, //hide these for now
                        DIMENSION_LOOKUP_LIMIT //not supported in fact
    );
    myPermissionChecker = permissionChecker;
    myProjectFinder = projectFinder;
    myBuildFinder = buildFinder;
    myBuildPromotionFinder = buildPromotionFinder;
    myBuildTypeFinder = buildTypeFinder;
    myVcsRootFinder = vcsRootFinder;
    myVcsRootInstanceFinder = vcsRootInstanceFinder;
    myUserFinder = userFinder;
    myVcsManager = vcsManager;
    myVcsModificationHistory = vcsModificationHistory;
    myServiceLocator = serviceLocator;
    myBranchFinder = branchFinder;
  }

  @Nullable
  @Override
  public Long getDefaultPageItemsCount() {
    return (long)Constants.getDefaultPageItemsCount();
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final SVcsModificationOrChangeDescriptor modOrDesc) {
    return ChangeFinder.getLocator(modOrDesc.getSVcsModification());
  }

  @NotNull
  public static String getLocator(@NotNull final SVcsModification vcsModification) {
    if (vcsModification.isPersonal()) {
      return Locator.getStringLocator(DIMENSION_ID, String.valueOf(vcsModification.getId()), PERSONAL, "true");
    }
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(vcsModification.getId()));
  }

  @NotNull
  public static String getLocator(@NotNull final BuildPromotion item) {
    return Locator.getStringLocator(BUILD, BuildPromotionFinder.getLocator(item));
  }

  @Override
  @Nullable
  public SVcsModificationOrChangeDescriptor findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      @SuppressWarnings("ConstantConditions") SVcsModification modification = myVcsManager.findModificationById(locator.getSingleValueAsLong(), false);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + locator.getSingleValueAsLong() + "'.");
      }
      return new SVcsModificationOrChangeDescriptor(modification);
    }

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      Boolean isPersonal = locator.getSingleDimensionValueAsBoolean(PERSONAL, false);
      if (isPersonal == null) {
        throw new BadRequestException("When 'id' dimension is present, only true/false values are supported for '" + PERSONAL + "' dimension. Was: '" +
                                      locator.getSingleDimensionValue(PERSONAL) + "'");
      }

      SVcsModification modification = myVcsManager.findModificationById(id, isPersonal);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + locator.getSingleDimensionValue(DIMENSION_ID) + "' (searching " +
                                    (isPersonal ? "personal" : "non-personal") + " changes).");
      }
      return new SVcsModificationOrChangeDescriptor(modification);
    }

    return null;
  }

  private static List<SVcsModification> getModificationsByIds(final List<Long> ids, final VcsManager vcsManager) {
    return CollectionsUtil.convertAndFilterNulls(ids, source -> vcsManager.findModificationById(source, false));
  }

  private static boolean isPersonalChangeMatchesBuildType(@NotNull final SVcsModification change, @NotNull final SBuildType buildType) {
    final Collection<SBuildType> relatedPersonalConfigurations = change.getRelatedConfigurations();
    boolean matches = false;
    for (SBuildType personalConfiguration : relatedPersonalConfigurations) {
      if (personalConfiguration.isPersonal()) {
        if (buildType.getInternalId().equals(((RemoteBuildType)personalConfiguration).getSourceBuildType().getInternalId())) {
          matches = true;
          break;
        }
      } else {
        if (buildType.getInternalId().equals((personalConfiguration.getInternalId()))) {
          matches = true;
          break;
        }
      }
    }
    return matches;
  }

  private long getChangeIdBySinceChangeLocator(@NotNull final String sinceChangeDimension) {
    //if change id - do not find change to support cases when it does not exist
    try {
      return Long.parseLong(sinceChangeDimension);
    } catch (NumberFormatException e) {
      final Locator locator = new Locator(sinceChangeDimension);
      Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
      if (id != null && locator.getDimensionsCount() == 1) {
        return id;
      }
    }
    //locator is not id - proceed as usual
    return getItem(sinceChangeDimension).getSVcsModification().getId();
  }

  @NotNull
  @Override
  public ItemFilter<SVcsModificationOrChangeDescriptor> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SVcsModification> result = new MultiCheckerFilter<>();

    //myBuildType, myProject and myBranchName are handled on getting initial collection to filter

    final String vcsRootInstanceLocator = locator.getSingleDimensionValue(VCS_ROOT_INSTANCE);
    if (vcsRootInstanceLocator != null) {
      final VcsRootInstance vcsRootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
      result.add(item -> {
        return !item.isPersonal() && vcsRootInstance.getId() == item.getVcsRoot().getId(); //todo: check personal change applicability to the root
      });
    }

    final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT);
    if (vcsRootLocator != null) {
      final VcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
      result.add(item -> {
        return !item.isPersonal() && vcsRoot.getId() == item.getVcsRoot().getParent().getId(); //todo: check personal change applicability to the root
      });
    }

    final String sinceChangeLocator = locator.getSingleDimensionValue(SINCE_CHANGE); //todo: deprecate this
    if (sinceChangeLocator != null) {
      final long sinceChangeId = getChangeIdBySinceChangeLocator(sinceChangeLocator);
      result.add(item -> sinceChangeId < item.getId());
    }

    if (locator.isUnused(USERNAME)) {
      final String username = locator.getSingleDimensionValue(USERNAME);
      if (username != null) {
        result.add(item -> {
          return username.equalsIgnoreCase(item.getUserName()); //todo: is ignoreCase is right here?
        });
      }
    }

    if (locator.getUnusedDimensions().contains(USER)) {
      final String userLocator = locator.getSingleDimensionValue(USER);
      if (userLocator != null) {
        final SUser user = myUserFinder.getItem(userLocator);
        result.add(item -> item.getCommitters().contains(user));
      }
    }

    //TeamCity API: exclude "fake" personal changes created by TeamCity for personal builds without personal changes
    result.add(item -> {
      if (!item.isPersonal()) return true;
      return item.getChanges().size() > 0;
    });

    final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL);
    if (personal != null) {
      result.add(item -> FilterUtil.isIncludedByBooleanFilter(personal, item.isPersonal()));
    }

    if (personal != null && personal) {
      //initial collection can contain changes from any buildType/project
      final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeLocator != null) {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
        result.add(item -> isPersonalChangeMatchesBuildType(item, buildType));
      }
    }

    if (locator.getUnusedDimensions().contains(BUILD)) {
      final String buildLocator = locator.getSingleDimensionValue(BUILD);
      if (buildLocator != null) {
        final Set<Long> buildChanges = getBuildChangeDescriptors(myBuildFinder.getBuildPromotion(null, buildLocator), locator)
          .map(mord -> mord.getRelatedVcsChange().getId())
          .collect(Collectors.toSet());
        result.add(item -> buildChanges.contains(item.getId()));
      }
    }

    //pre-9.0 dimension compatibility
    if (locator.getUnusedDimensions().contains(PROMOTION)) {
      final Long promotionLocator = locator.getSingleDimensionValueAsLong(PROMOTION);
      if (promotionLocator != null) {
        @SuppressWarnings("ConstantConditions") final Set<Long> buildChanges =
          getBuildChangeDescriptors(myBuildPromotionFinder.getBuildPromotion(promotionLocator), null)
            .map(mord -> mord.getRelatedVcsChange().getId()).collect(Collectors.toSet());
        result.add(item -> buildChanges.contains(item.getId()));
      }
    }

    if (locator.isUnused(BUILD_TYPE)) {
      final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE); //todo: support multiple buildTypes here
      if (buildTypeLocator != null) {
        SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);

        //todo: this does not include "show changes from dependencies", relates to https://youtrack.jetbrains.com/issue/TW-63704
        result.add(item -> ((VcsModificationEx)item).isRelatedTo(buildType));
      }
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    if (projectLocator != null) {
      final SProject project = myProjectFinder.getItem(projectLocator);
      result.add(getInBuildTypesListChecker(project.getOwnBuildTypes().stream()));
    }

    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectLocator != null) {
      final SProject affectedProject = myProjectFinder.getItem(affectedProjectLocator);
      result.add(getInBuildTypesListChecker(affectedProject.getBuildTypes().stream()));
    }

    if (locator.isUnused(INTERNAL_VERSION)) {
      final String internalVersion = locator.getSingleDimensionValue(INTERNAL_VERSION);
      if (internalVersion != null) {
        result.add(item -> internalVersion.equals(item.getVersion()));
      }
    }

    if (locator.isUnused(VERSION)) {
      final String displayVersion = locator.getSingleDimensionValue(VERSION);
      ValueCondition condition = ParameterCondition.createValueCondition(displayVersion);
      if (displayVersion != null) {
        result.add(item -> condition.matches(item.getDisplayVersion()));
      }
    }

    final String commentLocatorText = locator.getSingleDimensionValue(COMMENT);
    if (commentLocatorText != null) {
      final String containsText = new Locator(commentLocatorText).getSingleDimensionValue("contains");
      // Preserve legacy behaviour
      if (containsText != null) {
        result.add(item -> item.getDescription().contains(containsText));
        //todo: check unprocessed dimensions
      } else {
        ValueCondition condition = ParameterCondition.createValueCondition(commentLocatorText);
        result.add(item -> condition.matches(item.getDescription()));
      }
    }

    if (locator.getUnusedDimensions().contains(PENDING)) {
      final Boolean pending = locator.getSingleDimensionValueAsBoolean(PENDING);
      if (pending != null) {
        final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE); //todo: support multiple buildTypes here
        final SBuildType buildType = buildTypeLocator == null ? null : myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
        final Set<SVcsModification> pendingChanges = getPendingChanges(buildType, locator).map(mcd -> mcd.getSVcsModification()).collect(Collectors.toSet());
        result.add(item -> FilterUtil.isIncludedByBooleanFilter(pending, pendingChanges.contains(item)));
      }
    }

    final String fileLocator = locator.getSingleDimensionValue(FILE);
    if (fileLocator != null) {
      final String pathLocatorText = new Locator(fileLocator).getSingleDimensionValue("path"); //todo: use conditions here
      //todo: check unknown locator dimensions
      if (pathLocatorText != null) {
        final String containsText = new Locator(pathLocatorText).getSingleDimensionValue("contains");
        if (containsText != null) {
          result.add(item -> {
            for (VcsFileModification vcsFileModification : item.getChanges()) {
              if (vcsFileModification.getFileName().contains(containsText)) {
                return true;
              }
            }
            return false;
          });
          //todo: check unknown locator dimensions
        } else {
          ValueCondition condition = ParameterCondition.createValueCondition(pathLocatorText);
          result.add(item -> item.getChanges().stream().map(m -> m.getFileName()).anyMatch(condition::matches));
        }
      }
    }

    // See FinderImp.getItemsByLocator(..) and this.createDuplicateChecker(..) for implementation details.
    boolean deduplicate = locator.getSingleDimensionValueAsStrictBoolean(DIMENSION_UNIQUE, locator.isAnyPresent(DIMENSION_ITEM));
    if (deduplicate) {
      final KeyDuplicateChecker<SVcsModification, String> checker = new KeyDuplicateChecker<>(m -> m.getVersion());
      result.add(item -> !checker.checkDuplicateAndRemember(item));
    }

    // include by build should be already handled by this time on the upper level

    if (TeamCityProperties.getBoolean("rest.request.changes.check.enforceChangeViewPermission")) {
      result.add(myPermissionChecker::checkCanView);
    }

    return new UnwrappingFilter<>(result, SVcsModificationOrChangeDescriptor::getSVcsModification);
  }

  @NotNull
  private FilterConditionChecker<SVcsModification> getInBuildTypesListChecker(@NotNull Stream<SBuildType> buildTypes) {
    Set<String> btIds = buildTypes.map(BuildTypeDescriptor::getBuildTypeId).collect(Collectors.toSet());
    HashMap<String, Boolean> remoteBtIsInTheList = new HashMap<>();

    return modification -> {
      List<String> modBtIds = ((VcsModificationEx)modification).getRelatedConfigurationIds(false);

      for (String modBtId : modBtIds) {
        if (remoteBtIsInTheList.containsKey(modBtId)) {
          // that is a remote configuration we've seen, and we already made a check
          if (remoteBtIsInTheList.get(modBtId)) {
            return true;
          }

          continue;
        }

        boolean isNewRemoteBt = RemoteBuildTypeIdUtil.isValidRemoteBuildTypeId(modBtId);
        if (isNewRemoteBt) {
          // that is a remote configuration we didn't see yet, let's make a check and remember
          String parentBtId = RemoteBuildTypeIdUtil.getParentBuildTypeId(modBtId);
          boolean checkBtIsInTheList = btIds.contains(parentBtId);
          remoteBtIsInTheList.put(modBtId, checkBtIsInTheList);

          if (checkBtIsInTheList) {
            return true;
          }

          continue;
        }

        // that is a regular configuration, let's just check
        if (btIds.contains(modBtId)) {
          return true;
        }
      }

      return false;
    };
  }

  @Nullable
  private List<BranchData> getFilterBranches(@NotNull final Locator locator, @Nullable final SBuildType buildType) {
    String branchDimension = locator.getSingleDimensionValue(BRANCH);
    if (branchDimension != null) {
      if (buildType == null) {
        throw new BadRequestException("Filtering changes by branch is only supported when buildType is specified.");
      }
      try {
        //return branches even if myBranchFinder.isAnyBranch(branchDimension)) as the only proper way for now to get changes is to iterate the branches
        return myBranchFinder.getItems(buildType, branchDimension).myEntries;
      } catch (LocatorProcessException e) {
        throw new BadRequestException("Error in branch locator '" + branchDimension + "': " + e.getMessage(), e);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public ItemHolder<SVcsModificationOrChangeDescriptor> getPrefilteredItems(@NotNull final Locator locator) {
    final String internalVersion = locator.getSingleDimensionValue(INTERNAL_VERSION);
    if (internalVersion != null) {
      return wrapModifications(((VcsModificationHistoryEx)myVcsModificationHistory).findModificationsByVersion(internalVersion));
    }

    ValueCondition displayVersionCondition = ParameterCondition.createValueCondition(locator.lookupSingleDimensionValue(VERSION));
    final String displayVersion = displayVersionCondition != null ? displayVersionCondition.getConstantValueIfSimpleEqualsCondition() : null;
    if (displayVersion != null) {
      locator.markUsed(VERSION);
      return wrapModifications(((VcsModificationHistoryEx)myVcsModificationHistory).findModificationsByDisplayVersion(displayVersion));
    }

    Boolean personal = locator.lookupSingleDimensionValueAsBoolean(PERSONAL);
    if (personal != null && personal) {

      if (locator.lookupSingleDimensionValue(BUILD) == null && locator.lookupSingleDimensionValue(USER) == null) {
        throw new BadRequestException("Filtering personal changes is supported only when '" + DIMENSION_ID + "', '" + USER + "' or '" + BUILD + "' dimensions are specified");
      }
    }

    final String buildLocator = locator.getSingleDimensionValue(BUILD);
    if (buildLocator != null) {
      BuildPromotion buildFromBuildFinder;
      try {
        buildFromBuildFinder = myBuildPromotionFinder.getItem(buildLocator);
      } catch (Exception e) {
        if (!TeamCityProperties.getBoolean("rest.request.changes.legacySingleBuildSearch")) {
          throw e;
        }
        //support for finished builds
        //todo: use buildPromotionFinder here (ensure it also supports finished builds)
        buildFromBuildFinder = myBuildFinder.getBuildPromotion(null, buildLocator);   //THIS SHOULD NEVER HAPPEN
      }

      return wrapDescriptors(getBuildChangeDescriptors(buildFromBuildFinder, locator));
    }

    //pre-9.0 compatibility
    final Long promotionLocator = locator.getSingleDimensionValueAsLong(PROMOTION);
    if (promotionLocator != null) {
      return wrapDescriptors(getBuildChangeDescriptors(myBuildPromotionFinder.getBuildPromotion(promotionLocator), locator));
    }

    final String parentChangeLocator = locator.getSingleDimensionValue(CHILD_CHANGE);
    if (parentChangeLocator != null) {
      final SVcsModification parentChange = getItem(parentChangeLocator).getSVcsModification();

      return wrapModifications(getChangesWhichHasChild(parentChange, locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT)));
      //todo: return iterator instead of processing lookup limit here
    }

    final String childChangeLocator = locator.getSingleDimensionValue(PARENT_CHANGE);
    if (childChangeLocator != null) {
      final SVcsModification parentChange = getItem(childChangeLocator).getSVcsModification();
      return wrapModifications(getChangesWhichHasParent(parentChange, locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT)));
    }

    final String graphLocator = locator.getSingleDimensionValue(DAG_TRAVERSE);
    if (graphLocator != null) {
      final GraphFinder<SVcsModification> graphFinder = new GraphFinder<SVcsModification>(
        locatorText -> getItems(locatorText).myEntries.stream().map(SVcsModificationOrChangeDescriptor::getSVcsModification).collect(Collectors.toList()),
        new ChangesGraphTraverser()
      );

      graphFinder.setDefaultLookupLimit(1000L);
      return wrapModifications(graphFinder.getItems(graphLocator).myEntries);
    }

    final String userLocator = locator.getSingleDimensionValue(USER);
    if (userLocator != null) {
      final SUser user = myUserFinder.getItem(userLocator);
      Stream<SVcsModification> modifications = myServiceLocator.getSingletonService(UserChangesFacade.class)
                                                               .getAllVcsModifications(user).stream()
                                                               .flatMap(this::modificationWithDuplicates)
                                                               .filter(myPermissionChecker::checkCanView);
      return wrapModifications(modifications);
    }

    final String username = locator.getSingleDimensionValue(USERNAME);
    if (username != null) {
      Stream<SVcsModification> modifications = myServiceLocator.getSingletonService(VcsModificationsStorage.class)
                                                               .findModificationsByUsername(username).stream()
                                                               .flatMap(this::modificationWithDuplicates)
                                                               .filter(myPermissionChecker::checkCanView);
      return wrapModifications(modifications);
    }

    Long sinceChangeId = null;
    final String sinceChangeLocator = locator.getSingleDimensionValue(SINCE_CHANGE);
    if (sinceChangeLocator != null) {
      sinceChangeId = getChangeIdBySinceChangeLocator(sinceChangeLocator);
    }

    final String vcsRootInstanceLocator = locator.getSingleDimensionValue(VCS_ROOT_INSTANCE);
    if (vcsRootInstanceLocator != null) {
      final VcsRootInstance vcsRootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
      if (sinceChangeId != null) {
        //todo: use lookupLimit here or otherwise limit processing
        return wrapModifications(myVcsModificationHistory.getModificationsInRange(vcsRootInstance, sinceChangeId, null));
      } else {
        //todo: highly inefficient!
        return wrapModifications(myVcsModificationHistory.getAllModifications(vcsRootInstance));
      }
    }

    SBuildType buildType = null;
    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE); //todo: support multiple buildTypes here
    if (buildTypeLocator != null) {
      buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
    }

    Boolean pending = locator.getSingleDimensionValueAsBoolean(PENDING);
    if (pending != null) {
      if (pending) {
        Stream<SVcsModificationOrChangeDescriptor> changes = getPendingChanges(buildType, locator);
        return processor -> changes.allMatch(processor::processItem);
      } else {
        locator.markUnused(PENDING);
      }
    }

    if (buildType != null) {
      Stream<SVcsModificationOrChangeDescriptor> changes = getBranchChanges(buildType, SelectPrevBuildPolicy.SINCE_NULL_BUILD, locator);
      return processor -> changes.allMatch(processor::processItem);
    }

    if (locator.lookupSingleDimensionValue(BRANCH) != null) {
      throw new BadRequestException("Filtering changes by branch is only supported when buildType is specified.");
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    if (projectLocator != null) {
      return wrapModifications(getProjectChanges(myProjectFinder.getItem(projectLocator), sinceChangeId, false));
    }

    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectLocator != null) {
      return wrapModifications(getProjectChanges(myProjectFinder.getItem(affectedProjectLocator), sinceChangeId, true));
    }

    if (sinceChangeId != null) {
      return wrapModifications(myVcsModificationHistory.getModificationsInRange(null, sinceChangeId, null));  //todo: use lookupLimit here or otherwise limit processing
    }

    return wrapModifications(((VcsModificationHistoryEx)myVcsModificationHistory)::processModifications);  // ItemHolder
  }

  @NotNull
  private Stream<SVcsModification> modificationWithDuplicates(@NotNull SVcsModification m) {
    Collection<SVcsModification> dupliactes = m.getDuplicates();
    if (dupliactes.size() > 0) {
      return Stream.concat(Stream.of(m), m.getDuplicates().stream());
    }

    return Stream.of(m);
  }

  @Nullable
  @Override
  public DuplicateChecker<SVcsModificationOrChangeDescriptor> createDuplicateChecker() {
    // TLDR: Deduplication must be done last at the filtering stage, can't be done here.
    //
    // We must report that duplicate checks are not supported via duplicateChecker as it is applied *before* filtering, which leads to some
    // modificaionts disapperaing form result set.
    // I.e. we have two modifications tied to project A and project B. Request is recieved for modifications in project A,
    // and modifications come to duplicate checker in order B, A then following will happen:
    // 1. mod B is passed further to filters and filtered out as it's in the wrong project.
    // 2. mod A is not passed further as we've already seen mod with the same version.
    // 3. empty result is returned even though there is a mod which satisfies all the filters.
    return null;
  }

  @NotNull
  private Stream<SVcsModificationOrChangeDescriptor> getPendingChanges(@Nullable final SBuildType buildType, @NotNull final Locator locator) {
    if (buildType == null) {
      throw new BadRequestException("Getting pending changes is only supported when buildType is specified.");
    }
    return getBranchChanges(buildType, SelectPrevBuildPolicy.SINCE_LAST_BUILD, locator);
  }

  @NotNull
  private List<SVcsModification> getChangesWhichHasChild(@NotNull final SVcsModification change, final Long limit) {
    final ArrayList<SVcsModification> result = new ArrayList<>();

    final DAG<Long> dag = ((VcsRootInstanceEx)change.getVcsRoot()).getDag();

    int i = 0;
    final DAGIterator<Long> iterator = dag.iterator(change.getId());
    while (iterator.hasNext() && (limit == null || i < limit)) {
      final SVcsModification modificationById = myVcsManager.findModificationById(iterator.next(), false);
      if (modificationById != null) result.add(modificationById);
    }

    return result;
  }

  @NotNull
  private List<SVcsModification> getChangesWhichHasParent(@NotNull final SVcsModification change, final Long limit) {
    final ArrayList<SVcsModification> result = new ArrayList<>();

    final DAG<Long> dag = ((VcsRootInstanceEx)change.getVcsRoot()).getDag();

    final int i = 0;
    dag.reverseBreadthFirstSearch(change.getId(), new BFSVisitorAdapter<Long>() {
      @Override
      public boolean discover(@NotNull final Long node) {
        final SVcsModification modificationById = myVcsManager.findModificationById(node, false);
        if (modificationById != null) result.add(modificationById);
        return limit == null || i < limit;
      }
    });

    return result;
  }


  @NotNull
  private Stream<SVcsModificationOrChangeDescriptor> getBranchChanges(@NotNull final SBuildType buildType,
                                                                      @NotNull final SelectPrevBuildPolicy defaultPolicy,
                                                                      @NotNull final Locator locator) {
    final Boolean includeDependencyChanges = getIncludeDependencyChanges(locator);
    SelectPrevBuildPolicy policy = getBuildChangesPolicy(locator, defaultPolicy);
    List<BranchData> filterBranches = getFilterBranches(locator, buildType);

    //legacy behavior emulation
    if (TeamCityProperties.getBooleanOrTrue("rest.request.changes.legacyChangesInAllBranches")) {
      boolean anyBranch = filterBranches == null || myBranchFinder.isAnyBranch(locator.lookupSingleDimensionValue(BRANCH));
      if (anyBranch && policy == SelectPrevBuildPolicy.SINCE_NULL_BUILD && locator.lookupSingleDimensionValueAsBoolean(SETTINGS_CHANGES) == null) {
        locator.markUsed(SETTINGS_CHANGES); //in case it was set to "any"
        //todo: This approach has a bug: if includeDependencyChanges==true changes from all branches are returned, if includeDependencyChanges==false - only from the default branch
        if ((includeDependencyChanges != null && !includeDependencyChanges) || (includeDependencyChanges == null && !buildType.getOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES))) {
          return myVcsModificationHistory.getAllModifications(buildType).stream().map(SVcsModificationOrChangeDescriptor::new);
          // this can be more efficient than buildType.getDetectedChanges below, but returns all branches
        }
      }
    }

    //getting this before filtering is important: othrwise it can never be called and dimension reported as ignored
    Predicate<ChangeDescriptor> changeDescriptorFilter = getChangeDescriptorFilter(locator);

    if (filterBranches != null) {
      Function<BranchData, Stream<SVcsModificationOrChangeDescriptor>> flattenBranchData = branchData ->
        branchData.getChanges(policy, includeDependencyChanges).stream()
                  .filter(changeDescriptorFilter)
                  .filter(cd -> cd.getRelatedVcsChange() != null)
                  .map(SVcsModificationOrChangeDescriptor::new);

      return filterBranches.stream()
                           .flatMap(flattenBranchData)
                           .sorted(Comparator.comparing(mcd -> mcd.getSVcsModification()))
                           .collect(Collectors.toMap(mcd -> mcd.getSVcsModification().getId(), mcd -> mcd, this::chooseChangeNotFromDependency, LinkedHashMap::new))
                           .values().stream();
    } else {
      return ((BuildTypeEx)buildType).getDetectedChanges(policy, includeDependencyChanges)
                                     .stream()
                                     .filter(changeDescriptorFilter)
                                     .filter(cd -> cd.getRelatedVcsChange() != null)
                                     .map(SVcsModificationOrChangeDescriptor::new);
    }
  }

  @NotNull
  private SVcsModificationOrChangeDescriptor chooseChangeNotFromDependency(@NotNull SVcsModificationOrChangeDescriptor mcd1,
                                                                           @NotNull SVcsModificationOrChangeDescriptor mcd2) {
    // We assume that mcd1.getSVcsModification().getId() == mcd2.getSVcsModification().getId(), so it's the same change in VCS.
    // E.g. in a case when the same change is in both buildType and snapshot dependency, let's select the former one as it seems more important.

    if (mcd1.getChangeDescriptor() != null && mcd2.getChangeDescriptor() != null) {
      // Let's select the one coming NOT from dependency if possible.
      return ChangeDescriptorConstants.VCS_CHANGE.equals(mcd2.getChangeDescriptor().getType()) ? mcd2 : mcd1;
    } else {
      // Let's select one at least potentially having a descriptor.
      return mcd2.getChangeDescriptor() != null ? mcd2 : mcd1;
    }
  }

  @NotNull
  private Predicate<ChangeDescriptor> getChangeDescriptorFilter(@Nullable final Locator locator) {
    Boolean changesFromSettings = locator != null ? locator.getSingleDimensionValueAsBoolean(SETTINGS_CHANGES) : null;
    if (changesFromSettings == null) return cd -> true;
    return cd -> FilterUtil.isIncludedByBooleanFilter(changesFromSettings, "true".equals(cd.getAssociatedData().get(ChangeDescriptorConstants.SETTINGS_ROOT_CHANGE)));
  }

  private Stream<ChangeDescriptor> getBuildChangeDescriptors(@NotNull final BuildPromotion buildPromotion, @Nullable final Locator locator) {
    // getting this before filtering is important: othrwise it can never be called and dimension reported as ignored
    // todo: use fillDetectedChanges instead
    Predicate<ChangeDescriptor> changeDescriptorFilter = getChangeDescriptorFilter(locator);
    return ((BuildPromotionEx)buildPromotion).getDetectedChanges(getBuildChangesPolicy(locator, SelectPrevBuildPolicy.SINCE_LAST_BUILD), getIncludeDependencyChanges(locator),
                                                                 getBuildChangesProcessor(getBuildChangesLimit(locator)))
                                             .stream().filter(changeDescriptorFilter).filter(cd -> cd.getRelatedVcsChange() != null);
  }

  public boolean isCheap(@NotNull final BuildPromotion buildPromotion, @Nullable final String locatorText) {
    Locator locator = null;
    if (!StringUtil.isEmpty(locatorText)) {
      try {
        locator = new Locator(locatorText);
      } catch (LocatorProcessException e) {
        return false;
      }
    }
    return ((BuildPromotionEx)buildPromotion).hasComputedChanges(
      getBuildChangesPolicy(locator, SelectPrevBuildPolicy.SINCE_LAST_BUILD),
      getBuildChangesProcessor(getBuildChangesLimit(locator))
    );
  }

  private static VcsModificationProcessor getBuildChangesProcessor(@Nullable final Long limit) {
    return limit == null ? VcsModificationProcessor.ACCEPT_ALL : new LimitingVcsModificationProcessor(limit.intValue());
  }

  @Nullable
  private Boolean getIncludeDependencyChanges(@Nullable final Locator locator) {
    if (locator != null && locator.getSingleDimensionValue(CHANGES_FROM_DEPS) != null) {
      return locator.getSingleDimensionValueAsStrictBoolean(CHANGES_FROM_DEPS, false); //default value is guaranteed to be ignored
    }

    if (TeamCityProperties.getBoolean(IGNORE_CHANGES_FROM_DEPENDENCIES_OPTION)) {
      return false;
    }

    return null;
  }

  @NotNull
  public static SelectPrevBuildPolicy getBuildChangesPolicy() {
    return SelectPrevBuildPolicy.SINCE_LAST_BUILD;
  }

  @NotNull
  public static SelectPrevBuildPolicy getBuildChangesPolicy(@Nullable Locator locator, @NotNull final SelectPrevBuildPolicy defaultValue) {
    if (locator != null) {
      String prevBuildPolicy = locator.getSingleDimensionValue(PREV_BUILD_POLICY);
      if (prevBuildPolicy != null) {
        return TypedFinderBuilder.getEnumValue(prevBuildPolicy, SelectPrevBuildPolicy.class);
      }
    }
    return defaultValue;
  }

  @Nullable
  private Long getBuildChangesLimit(@Nullable final Locator locator) {
    if (locator == null) return null;
    Long count = null;
    if (locator.getDefinedDimensions().size() <= 3) {
      Set<String> dimensions = new HashSet<>(locator.getDefinedDimensions());
      dimensions.remove(BUILD);
      dimensions.remove(PagerData.COUNT);
      dimensions.remove(DIMENSION_LOOKUP_LIMIT);
      if (dimensions.isEmpty()) {
        //no filtering dimensions other than "build"
        count = getCountNotMarkingAsUsed(locator);
      }
    }

    Long lookupLimit = getLookupLimit(locator);
    if (count == null) {
      return lookupLimit;
    } else {
      return lookupLimit == null ? count : Math.min(lookupLimit, count);
    }
  }

  @NotNull
  private List<SVcsModification> getProjectChanges(@NotNull final SProject project, @Nullable final Long sinceChangeId, boolean includeChangesFromChildren) {
    final List<SVcsModification> result = new ArrayList<>();

    Stream<VcsRootInstance> vcsRoots;
    if (includeChangesFromChildren) {
      vcsRoots = project.getVcsRootInstances().stream();
    } else {
      vcsRoots = project.getOwnBuildTypes().stream()
                        .flatMap(bt -> bt.getVcsRootInstances().stream());
    }

    Set<Long> interestingRootIds = vcsRoots.map(VcsRoot::getId).collect(Collectors.toSet());

    VcsModificationsStorage vcsModificationsStorage = myServiceLocator.getSingletonService(VcsModificationsStorage.class);
    SecurityContext securityContext = myServiceLocator.getSingletonService(SecurityContext.class);
    final AuthorityHolder authorityHolder = securityContext.getAuthorityHolder();

    vcsModificationsStorage.processModifications(m -> {
      if (sinceChangeId != null && m.getId() < sinceChangeId) return false;

      if (interestingRootIds.contains(m.getVcsRoot().getId()) && AuthUtil.hasReadAccessTo(authorityHolder, m)) {
        result.add(m);
      }

      return true;
    });

    return result;
  }

  private ItemHolder<SVcsModificationOrChangeDescriptor> wrapModifications(@NotNull ItemHolder<SVcsModification> toWrap) {
    return new WrappingItemHolder<>(toWrap, SVcsModificationOrChangeDescriptor::new);
  }

  private ItemHolder<SVcsModificationOrChangeDescriptor> wrapModifications(@NotNull Stream<SVcsModification> toWrap) {
    return new WrappingItemHolder<>(toWrap, SVcsModificationOrChangeDescriptor::new);
  }

  private ItemHolder<SVcsModificationOrChangeDescriptor> wrapModifications(@NotNull List<SVcsModification> toWrap) {
    return new WrappingItemHolder<>(toWrap, SVcsModificationOrChangeDescriptor::new);
  }

  private ItemHolder<SVcsModificationOrChangeDescriptor> wrapDescriptors(@NotNull Stream<ChangeDescriptor> toWrap) {
    return new WrappingItemHolder<>(toWrap, SVcsModificationOrChangeDescriptor::new);
  }

  private class ChangesGraphTraverser implements GraphFinder.Traverser<SVcsModification> {
    @NotNull
    @Override
    public GraphFinder.LinkRetriever<SVcsModification> getChildren() {
      return item -> new ArrayList<>(item.getParentModifications());
    }

    @NotNull
    @Override
    public GraphFinder.LinkRetriever<SVcsModification> getParents() {
      return item -> getModificationsByIds(((VcsRootInstanceEx)item.getVcsRoot()).getDag().getChildren(item.getId()), myVcsManager);
    }
  }
}
