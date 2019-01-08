/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.RemoteBuildType;
import jetbrains.buildServer.serverSide.userChanges.UserChangesFacade;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.graph.BFSVisitorAdapter;
import jetbrains.buildServer.util.graph.DAG;
import jetbrains.buildServer.util.graph.DAGIterator;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 12.05.13
 */
public class ChangeFinder extends AbstractFinder<SVcsModification> {
  public static final String IGNORE_CHANGES_FROM_DEPENDENCIES_OPTION = "rest.ignoreChangesFromDependenciesOption";

  public static final String PERSONAL = "personal";
  public static final String PROJECT = "project";
  public static final String BUILD_TYPE = "buildType";
  public static final String BUILD = "build";
  public static final String PROMOTION = BuildFinder.PROMOTION_ID;
  public static final String VCS_ROOT = "vcsRoot";
  public static final String VCS_ROOT_INSTANCE = "vcsRootInstance";
  public static final String USERNAME = "username";
  public static final String USER = "user";
  public static final String VERSION = "version";
  public static final String INTERNAL_VERSION = "internalVersion";
  public static final String COMMENT = "comment";
  public static final String FILE = "file";
  public static final String SINCE_CHANGE = "sinceChange";
  public static final String PENDING = "pending";
  public static final String BRANCH = "branch";
  public static final String CHILD_CHANGE = "childChange";
  public static final String PARENT_CHANGE = "parentChange";
  public static final String DAG_TRAVERSE = "dag";
  public static final String PREV_BUILD_POLICY = "policy";
  public static final String CHANGES_FROM_DEPS = "changesFromDependencies";

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

  public ChangeFinder(@NotNull final ProjectFinder projectFinder,
                      @NotNull final BuildFinder buildFinder,
                      @NotNull final BuildPromotionFinder buildPromotionFinder,
                      @NotNull final BuildTypeFinder buildTypeFinder,
                      @NotNull final VcsRootFinder vcsRootFinder,
                      @NotNull final VcsRootInstanceFinder vcsRootInstanceFinder,
                      @NotNull final UserFinder userFinder,
                      @NotNull final VcsManager vcsManager,
                      @NotNull final VcsModificationHistory vcsModificationHistory,
                      @NotNull final BranchFinder branchFinder,
                      @NotNull final ServiceLocator serviceLocator, @NotNull final PermissionChecker permissionChecker) {
    super(DIMENSION_ID, PROJECT, BUILD_TYPE, BUILD, VCS_ROOT, VCS_ROOT_INSTANCE, USERNAME, USER, VERSION, INTERNAL_VERSION, COMMENT, FILE, PENDING,
          SINCE_CHANGE, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    setHiddenDimensions(BRANCH, PERSONAL, CHILD_CHANGE, PARENT_CHANGE, DAG_TRAVERSE, PROMOTION, PREV_BUILD_POLICY, CHANGES_FROM_DEPS, //hide these for now
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
  public String getItemLocator(@NotNull final SVcsModification vcsModification) {
    return ChangeFinder.getLocator(vcsModification);
  }

  @NotNull
  public static String getLocator(@NotNull final SVcsModification vcsModification) {
    if (vcsModification.isPersonal()){
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
  public SVcsModification findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      @SuppressWarnings("ConstantConditions") SVcsModification modification = myVcsManager.findModificationById(locator.getSingleValueAsLong(), false);
      if (modification == null) {
        throw new NotFoundException("No change can be found by id '" + locator.getSingleValueAsLong() + "'.");
      }
      return modification;
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
      return modification;
    }

    return null;
  }

  @NotNull
  @Override
  public ItemFilter<SVcsModification> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SVcsModification> result = new MultiCheckerFilter<SVcsModification>();

    //myBuildType, myProject and myBranchName are handled on getting initial collection to filter

    final String vcsRootInstanceLocator = locator.getSingleDimensionValue(VCS_ROOT_INSTANCE);
    if (vcsRootInstanceLocator != null) {
      final VcsRootInstance vcsRootInstance = myVcsRootInstanceFinder.getItem(vcsRootInstanceLocator);
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return !item.isPersonal() && vcsRootInstance.getId() == item.getVcsRoot().getId(); //todo: check personal change applicability to the root
        }
      });
    }

    final String vcsRootLocator = locator.getSingleDimensionValue(VCS_ROOT);
    if (vcsRootLocator != null) {
      final VcsRoot vcsRoot = myVcsRootFinder.getItem(vcsRootLocator);
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return !item.isPersonal() && vcsRoot.getId() == item.getVcsRoot().getParent().getId(); //todo: check personal change applicability to the root
        }
      });
    }

    final String sinceChangeLocator = locator.getSingleDimensionValue(SINCE_CHANGE); //todo: deprecate this
    if (sinceChangeLocator != null) {
      final long sinceChangeId = getChangeIdBySinceChangeLocator(sinceChangeLocator);
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return sinceChangeId < item.getId();
        }
      });
    }

    final String username = locator.getSingleDimensionValue(USERNAME);
    if (username != null) {
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return username.equalsIgnoreCase(item.getUserName()); //todo: is ignoreCase is right here?
        }
      });
    }

    if (locator.getUnusedDimensions().contains(USER)) {
      final String userLocator = locator.getSingleDimensionValue(USER);
      if (userLocator != null) {
        final SUser user = myUserFinder.getItem(userLocator);
        result.add(new FilterConditionChecker<SVcsModification>() {
          public boolean isIncluded(@NotNull final SVcsModification item) {
            return item.getCommitters().contains(user);
          }
        });
      }
    }

    //TeamCity API: exclude "fake" personal changes created by TeamCity for personal builds without personal changes
    result.add(new FilterConditionChecker<SVcsModification>() {
      public boolean isIncluded(@NotNull final SVcsModification item) {
        if (!item.isPersonal()) return true;
        return item.getChanges().size() > 0;
      }
    });

    final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL);
    if (personal != null) {
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return FilterUtil.isIncludedByBooleanFilter(personal, item.isPersonal());
        }
      });
    }

    if (personal != null && personal) {
      //initial collection can contain changes from any buildType/project
      final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeLocator != null) {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
        result.add(new FilterConditionChecker<SVcsModification>() {
          public boolean isIncluded(@NotNull final SVcsModification item) {
            return isPersonalChangeMatchesBuildType(item, buildType);
          }
        });
      }
    }

    if (locator.getUnusedDimensions().contains(BUILD)) {
      final String buildLocator = locator.getSingleDimensionValue(BUILD);
      if (buildLocator != null) {
        final Set<Long> buildChanges = getBuildChanges(myBuildFinder.getBuildPromotion(null, buildLocator), locator).map(change -> change.getId()).collect(Collectors.toSet());
        result.add(new FilterConditionChecker<SVcsModification>() {
          public boolean isIncluded(@NotNull final SVcsModification item) {
            return buildChanges.contains(item.getId());
          }
        });
      }
    }

    //pre-9.0 dimension compatibility
    if (locator.getUnusedDimensions().contains(PROMOTION)) {
      final Long promotionLocator = locator.getSingleDimensionValueAsLong(PROMOTION);
      if (promotionLocator != null) {
        @SuppressWarnings("ConstantConditions") final Set<Long> buildChanges =
          getBuildChanges(BuildFinder.getBuildPromotion(promotionLocator, myServiceLocator.findSingletonService(BuildPromotionManager.class)), null)
            .map(change -> change.getId()).collect(Collectors.toSet());
        result.add(new FilterConditionChecker<SVcsModification>() {
          public boolean isIncluded(@NotNull final SVcsModification item) {
            return buildChanges.contains(item.getId());
          }
        });
      }
    }

    if (locator.isUnused(BUILD_TYPE)) {
      final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE); //todo: support multiple buildTypes here
      if (buildTypeLocator != null) {
        SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
        result.add(item -> item.getRelatedConfigurations().contains(buildType));
      }
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    if (projectLocator != null) {
      final SProject project = myProjectFinder.getItem(projectLocator);
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return item.getRelatedProjects().contains(project);
        }
      });
    }

    final String internalVersion = locator.getSingleDimensionValue(INTERNAL_VERSION);
    if (internalVersion != null) {
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return internalVersion.equals(item.getVersion());
        }
      });
    }

    final String displayVersion = locator.getSingleDimensionValue(VERSION);
    if (displayVersion != null) {
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return displayVersion.equals(item.getDisplayVersion());
        }
      });
    }

    final String commentLocator = locator.getSingleDimensionValue(COMMENT);
    if (commentLocator != null) {
      final String containsText = new Locator(commentLocator).getSingleDimensionValue("contains"); //todo: use conditions here
      //todo: check unknown locator dimensions
      if (containsText != null) {
        result.add(new FilterConditionChecker<SVcsModification>() {
          public boolean isIncluded(@NotNull final SVcsModification item) {
            return item.getDescription().contains(containsText);
          }
        });
      }
    }

    if (locator.getUnusedDimensions().contains(PENDING)) {
      final Boolean pending = locator.getSingleDimensionValueAsBoolean(PENDING);
      if (pending != null) {
        final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE); //todo: support multiple buildTypes here
        final SBuildType buildType = buildTypeLocator == null ? null : myBuildTypeFinder.getBuildType(null, buildTypeLocator, false);
        final List<SVcsModification> pendingChanges = getPendingChanges(buildType, locator);
        result.add(new FilterConditionChecker<SVcsModification>() {
          public boolean isIncluded(@NotNull final SVcsModification item) {
            return FilterUtil.isIncludedByBooleanFilter(pending, pendingChanges.contains(item));
          }
        });
      }
    }

    final String fileLocator = locator.getSingleDimensionValue(FILE);
    if (fileLocator != null) {
      final String pathLocatorText = new Locator(fileLocator).getSingleDimensionValue("path"); //todo: use conditions here
      //todo: check unknown locator dimensions
      if (pathLocatorText != null) {
        final String containsText = new Locator(pathLocatorText).getSingleDimensionValue("contains"); //todo: use conditions here
        //todo: check unknown locator dimensions
        if (containsText != null) {
          result.add(new FilterConditionChecker<SVcsModification>() {
            public boolean isIncluded(@NotNull final SVcsModification item) {
              for (VcsFileModification vcsFileModification : item.getChanges()) {
                if (vcsFileModification.getFileName().contains(containsText)) {
                  return true;
                }
              }
              return false;
            }
          });
        }
      }
    }

    // include by build should be already handled by this time on the upper level

    if (TeamCityProperties.getBoolean("rest.request.changes.check.enforceChangeViewPermission")) {
      result.add(new FilterConditionChecker<SVcsModification>() {
        public boolean isIncluded(@NotNull final SVcsModification item) {
          return myPermissionChecker.checkCanView(item);
        }
      });
    }

    return result;
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
    return getItem(sinceChangeDimension).getId();
  }

  @NotNull
  @Override
  public ItemHolder<SVcsModification> getPrefilteredItems(@NotNull final Locator locator) {

    //todo: implement effective search by VCSRootInstance and internalVersion

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
        //support for finished builds
        //todo: use buildPromotionFinder here (ensure it also supports finished builds)
        buildFromBuildFinder = myBuildFinder.getBuildPromotion(null, buildLocator);   //THIS SHOULD NEVER HAPPEN
      }
      return FinderDataBinding.getItemHolder(getBuildChanges(buildFromBuildFinder, locator));
    }

    //pre-9.0 compatibility
    final Long promotionLocator = locator.getSingleDimensionValueAsLong(PROMOTION);
    if (promotionLocator != null) {
      //noinspection ConstantConditions
      return FinderDataBinding.getItemHolder(getBuildChanges(BuildFinder.getBuildPromotion(promotionLocator, myServiceLocator.findSingletonService(BuildPromotionManager.class)),
                                                             locator));
    }

    final String parentChangeLocator = locator.getSingleDimensionValue(CHILD_CHANGE);
    if (parentChangeLocator != null) {
      final SVcsModification parentChange = getItem(parentChangeLocator);
      return getItemHolder(getChangesWhichHasChild(parentChange, locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT))); //todo: return iterator instead of processing lookup limit here
    }

    final String childChangeLocator = locator.getSingleDimensionValue(PARENT_CHANGE);
    if (childChangeLocator != null) {
      final SVcsModification parentChange = getItem(childChangeLocator);
      return getItemHolder(getChangesWhichHasParent(parentChange, locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT)));
    }

    final String graphLocator = locator.getSingleDimensionValue(DAG_TRAVERSE);
    if (graphLocator != null) {
      final GraphFinder<SVcsModification> graphFinder = new GraphFinder<SVcsModification>(this, new GraphFinder.Traverser<SVcsModification>() {
        @NotNull
        @Override
        public GraphFinder.LinkRetriever<SVcsModification> getChildren() {
          return new GraphFinder.LinkRetriever<SVcsModification>() {
            @NotNull
            @Override
            public List<SVcsModification> getLinked(@NotNull final SVcsModification item) {
              return new ArrayList<SVcsModification>(item.getParentModifications());
            }
          };
        }

        @NotNull
        @Override
        public GraphFinder.LinkRetriever<SVcsModification> getParents() {
          return new GraphFinder.LinkRetriever<SVcsModification>() {
            @NotNull
            @Override
            public List<SVcsModification> getLinked(@NotNull final SVcsModification item) {
              final List<Long> resultIds = ((VcsRootInstanceEx)item.getVcsRoot()).getDag().getChildren(item.getId());
              return getModificationsByIds(resultIds, myVcsManager);
            }
          };
        }
      });
      graphFinder.setDefaultLookupLimit(1000L);
      return getItemHolder(graphFinder.getItems(graphLocator).myEntries);
    }

    final String userLocator = locator.getSingleDimensionValue(USER);
    if (userLocator != null) {
      final SUser user = myUserFinder.getItem(userLocator);
      return getItemHolder(myServiceLocator.getSingletonService(UserChangesFacade.class).getAllVcsModifications(user));
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
        return getItemHolder(myVcsModificationHistory.getModificationsInRange(vcsRootInstance, sinceChangeId, null)); //todo: use lookupLimit here or otherwise limit processing
      } else {
        //todo: highly inefficient!
        return getItemHolder(myVcsModificationHistory.getAllModifications(vcsRootInstance));
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
        return getItemHolder(getPendingChanges(buildType, locator));
      } else {
        locator.markUnused(PENDING);
      }
    }

    if (buildType != null) {
      return getItemHolder(getBranchChanges(buildType, SelectPrevBuildPolicy.SINCE_NULL_BUILD, locator));
    }

    if (locator.lookupSingleDimensionValue(BRANCH) != null) {
      throw new BadRequestException("Filtering changes by branch is only supported when buildType is specified.");
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    if (projectLocator != null) {
      return getItemHolder(getProjectChanges(myVcsModificationHistory, myProjectFinder.getItem(projectLocator), sinceChangeId));
    }

    if (sinceChangeId != null) {
      return getItemHolder(myVcsModificationHistory.getModificationsInRange(null, sinceChangeId, null));  //todo: use lookupLimit here or otherwise limit processing
    }

    return new ItemHolder<SVcsModification>() {
      @Override
      public void process(@NotNull final ItemProcessor<SVcsModification> processor) {
        ((VcsModificationHistoryEx)myVcsModificationHistory).processModifications(item -> processor.processItem(item));
      }
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
        //optimize if all branches are matched
        if (myBranchFinder.isAnyBranch(branchDimension)) {
          return null;
        }
        return myBranchFinder.getItems(buildType, branchDimension).myEntries;
      } catch (LocatorProcessException e) {
        throw new BadRequestException("Error in branch locator '" + branchDimension + "': " + e.getMessage(), e);
      }
    }
    return null;
  }

  private static List<SVcsModification> getModificationsByIds(final List<Long> ids, final VcsManager vcsManager) {
    return CollectionsUtil.convertAndFilterNulls(ids, new Converter<SVcsModification, Long>() {
      @Override
      public SVcsModification createFrom(@NotNull final Long source) {
        return vcsManager.findModificationById(source, false);
      }
    });
  }

  @NotNull
  private List<SVcsModification> getPendingChanges(@Nullable final SBuildType buildType, @NotNull final Locator locator) {
    if (buildType == null) {
      throw new BadRequestException("Getting pending changes is only supported when buildType is specified.");
    }
    return getBranchChanges(buildType, SelectPrevBuildPolicy.SINCE_LAST_BUILD, locator);
  }

  @NotNull
  private List<SVcsModification> getChangesWhichHasChild(@NotNull final SVcsModification change, final Long limit) {
    final ArrayList<SVcsModification> result = new ArrayList<SVcsModification>();

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
    final ArrayList<SVcsModification> result = new ArrayList<SVcsModification>();

    final DAG<Long> dag = ((VcsRootInstanceEx)change.getVcsRoot()).getDag();

    final int i = 0;
    dag.reverseBreadthFirstSearch(change.getId(), new BFSVisitorAdapter<Long>(){
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
  private List<SVcsModification> getBranchChanges(@NotNull final SBuildType buildType, @NotNull final SelectPrevBuildPolicy defaultPolicy, @NotNull final Locator locator) {
    final Boolean includeDependencyChanges = getIncludeDependencyChanges(locator);
    SelectPrevBuildPolicy policy = getBuildChangesPolicy(locator, defaultPolicy);
    List<BranchData> filterBranches = getFilterBranches(locator, buildType);
    if (filterBranches != null) {
      return filterBranches.stream().flatMap(branchData -> branchData.getChanges(policy, includeDependencyChanges).stream())
                           .map(ChangeDescriptor::getRelatedVcsChange).filter(Objects::nonNull).sorted().distinct().collect(Collectors.toList());
    } else {
      if (policy == SelectPrevBuildPolicy.SINCE_NULL_BUILD) {
        //todo: This approach has a bug: if includeDependencyChanges==true changes from all branches are returned, if includeDependencyChanges==false - only from the default branch
        if ((includeDependencyChanges != null && !includeDependencyChanges) || (includeDependencyChanges == null && !buildType.getOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES))) {
          return myVcsModificationHistory.getAllModifications(buildType); // this can be more efficient than buildType.getDetectedChanges below, but returns all branches
        }
      }
      return ((BuildTypeEx)buildType).getDetectedChanges(policy, includeDependencyChanges)
                                     .stream().map(ChangeDescriptor::getRelatedVcsChange).filter(Objects::nonNull).collect(Collectors.toList());
    }
  }

  private Stream<SVcsModification> getBuildChanges(@NotNull final BuildPromotion buildPromotion, @Nullable final Locator locator) {
    //todo: use fillDetectedChanges instead
    return ((BuildPromotionEx)buildPromotion).getDetectedChanges(getBuildChangesPolicy(locator, SelectPrevBuildPolicy.SINCE_LAST_BUILD), getIncludeDependencyChanges(locator),
                                                                 getBuildChangesProcessor(getBuildChangesLimit(locator)))
                                             .stream().map(ChangeDescriptor::getRelatedVcsChange).filter(Objects::nonNull);
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
    return ((BuildPromotionEx)buildPromotion).hasComputedChanges(getBuildChangesPolicy(locator, SelectPrevBuildPolicy.SINCE_LAST_BUILD), getBuildChangesProcessor(getBuildChangesLimit(locator)));
  }

  private static VcsModificationProcessor getBuildChangesProcessor(final @Nullable Long limit) {
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
  private Long getBuildChangesLimit(final @Nullable Locator locator) {
    if (locator == null) return null;
    Long count = null;
    if (locator.getDefinedDimensions().size() <=3) {
      Set dimensions = new HashSet<String>(locator.getDefinedDimensions());
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
