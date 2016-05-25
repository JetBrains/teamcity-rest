/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.AbstractMapParametersProvider;
import jetbrains.buildServer.server.rest.data.build.TagFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.serverSide.metadata.BuildMetadataEntry;
import jetbrains.buildServer.serverSide.metadata.impl.MetadataStorageEx;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 20.08.2014
 */
public class BuildPromotionFinder extends AbstractFinder<BuildPromotion> {
  //DIMENSION_ID - id of a build or id of build promotion which will get associated build with the id
  public static final String PROMOTION_ID = BuildFinder.PROMOTION_ID;
  protected static final String PROMOTION_ID_ALIAS = "promotionId";
  protected static final String BUILD_ID = "buildId"; //this is experimental, for debug purposes only
  public static final String BUILD_TYPE = "buildType";
  public static final String PROJECT = "project"; // BuildFinder (used prior to 9.0) treats "project" as "affectedProject" and thus this behavior is different from BuildFinder
  private static final String AFFECTED_PROJECT = "affectedProject";
  public static final String AGENT = "agent";
  public static final String AGENT_NAME = "agentName";
  public static final String PERSONAL = "personal";
  public static final String USER = "user";
  protected static final String BRANCH = "branch";
  protected static final String PROPERTY = "property";
  protected static final String STATISTIC_VALUE = "statisticValue";

  public static final String STATE = "state";
  public static final String STATE_QUEUED = "queued";
  public static final String STATE_RUNNING = "running";
  public static final String STATE_FINISHED = "finished";
  protected static final String STATE_ANY = "any";

  protected static final String NUMBER = "number";
  protected static final String STATUS = "status";
  protected static final String CANCELED = "canceled";
  protected static final String FAILED_TO_START = "failedToStart";
  protected static final String PINNED = "pinned";
  protected static final String RUNNING = "running";
  protected static final String HANGING = "hanging";
  protected static final String SNAPSHOT_DEP = "snapshotDependency";
  protected static final String COMPATIBLE_AGENTS_COUNT = "compatibleAgentsCount";
  protected static final String TAGS = "tags";
  protected static final String TAG = "tag";
  protected static final String COMPATIBLE_AGENT = "compatibleAgent";
  protected static final String SINCE_BUILD = "sinceBuild"; //use startDate:(build:(<locator>),condition:after) instead
  protected static final String SINCE_DATE = "sinceDate"; //use startDate:(date:<date>,condition:after) instead
  protected static final String UNTIL_BUILD = "untilBuild"; //use startDate:(build:(<locator>),condition:before) instead
  protected static final String UNTIL_DATE = "untilDate"; //use startDate:(date:<date>,condition:before) instead

  protected static final String QUEUED_TIME = "queuedDate";
  protected static final String STARTED_TIME = "startDate";
  protected static final String FINISHED_TIME = "finishDate";

  protected static final String DEFAULT_FILTERING = "defaultFilter";
  protected static final String SINCE_BUILD_ID_LOOK_AHEAD_COUNT = "sinceBuildIdLookAheadCount";  /*experimental*/
  public static final String ORDERED = "ordered"; /*experimental*/
  public static final String STROB = "strob"; /*experimental*/  //might need a better name

  public static final String BY_PROMOTION = "byPromotion";  //used in BuildFinder
  public static final String EQUIVALENT = "equivalent"; /*experimental*/
  public static final String METADATA = "metadata"; /*experimental*/

  public static final String REVISION = "revision"; /*experimental*/
  public static final BuildPromotionComparator BUILD_PROMOTIONS_COMPARATOR = new BuildPromotionComparator();
  public static final SnapshotDepsTraverser SNAPSHOT_DEPENDENCIES_TRAVERSER = new SnapshotDepsTraverser();
  protected static final String STROB_BUILD_LOCATOR = "locator";

  private final BuildPromotionManager myBuildPromotionManager;
  private final BuildQueue myBuildQueue;
  private final BuildsManager myBuildsManager;
  private final VcsRootFinder myVcsRootFinder;
  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final UserFinder myUserFinder;
  private final AgentFinder myAgentFinder;
  private final BranchFinder myBranchFinder;
  private final MetadataStorageEx myMetadataStorage;
  private final TimeCondition myTimeCondition;

  @NotNull
  public static String getLocator(@NotNull final BuildPromotion buildPromotion) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(getBuildId(buildPromotion)));
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final BuildPromotion buildPromotion) {
    return BuildPromotionFinder.getLocator(buildPromotion);
  }

  public BuildPromotionFinder(final BuildPromotionManager buildPromotionManager,
                              final BuildQueue buildQueue,
                              final BuildsManager buildsManager,
                              final VcsRootFinder vcsRootFinder,
                              final ProjectFinder projectFinder,
                              final BuildTypeFinder buildTypeFinder,
                              final UserFinder userFinder,
                              final AgentFinder agentFinder,
                              final BranchFinder branchFinder,
                              final TimeCondition timeCondition,
                              final MetadataStorageEx metadataStorage) {
    super(DIMENSION_ID, PROMOTION_ID, PROJECT, AFFECTED_PROJECT, BUILD_TYPE, BRANCH, AGENT, USER, PERSONAL, STATE, TAG, PROPERTY, COMPATIBLE_AGENT,
          NUMBER, STATUS, CANCELED, PINNED, QUEUED_TIME, STARTED_TIME, FINISHED_TIME, SINCE_BUILD, SINCE_DATE, UNTIL_BUILD, UNTIL_DATE, FAILED_TO_START, SNAPSHOT_DEP, HANGING,
          DEFAULT_FILTERING, SINCE_BUILD_ID_LOOK_AHEAD_COUNT,
          Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    myBuildPromotionManager = buildPromotionManager;
    myBuildQueue = buildQueue;
    myBuildsManager = buildsManager;
    myVcsRootFinder = vcsRootFinder;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myUserFinder = userFinder;
    myAgentFinder = agentFinder;
    myBranchFinder = branchFinder;
    myMetadataStorage = metadataStorage;
    myTimeCondition = timeCondition;
  }

  @Override
  public Long getDefaultPageItemsCount() {
    return (long)Constants.getDefaultPageItemsCount();
  }

  @Override
  public Long getDefaultLookupLimit() {
    final long defaultLookupLimit = TeamCityProperties.getLong("rest.request.builds.defaultLookupLimit", 5000);
    if (defaultLookupLimit != 0) {
      return defaultLookupLimit;
    }
    return null;
  }

  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = super.createLocator(locatorText, locatorDefaults);
    result.addHiddenDimensions(AGENT_NAME, TAGS, RUNNING); //compatibility
    result.addHiddenDimensions(BY_PROMOTION); //switch for legacy behavior
    result.addHiddenDimensions(COMPATIBLE_AGENTS_COUNT); //experimental for queued builds only
    result.addHiddenDimensions(EQUIVALENT, REVISION, PROMOTION_ID_ALIAS, BUILD_ID, METADATA);
    result.addHiddenDimensions(STATISTIC_VALUE); //experimental
    result.addHiddenDimensions(SINCE_BUILD_ID_LOOK_AHEAD_COUNT); //experimental
    result.addHiddenDimensions(ORDERED); //experimental
    result.addHiddenDimensions(STROB); //experimental
    return result;
  }

  @Nullable
  @Override
  protected BuildPromotion findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      @NotNull final Long singleValueAsLong;
      try {
        //noinspection ConstantConditions
        singleValueAsLong = locator.getSingleValueAsLong();
      } catch (LocatorProcessException e) {
        throw new BadRequestException("Invalid single value: '" + locator.getSingleValue() + "'. Should be a numeric build id", e);
      }
      // difference from 9.0 behavior where we never searched by promotion id in case of single value locators
      return getBuildPromotionById(singleValueAsLong, myBuildPromotionManager, myBuildsManager);
    }

    Long promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID);
    if (promotionId == null){
      promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID_ALIAS); //support TeamCity 8.0 dimension
    }
    if (promotionId != null) {
      return BuildFinder.getBuildPromotion(promotionId, myBuildPromotionManager);
    }

    Long buildId = locator.getSingleDimensionValueAsLong(BUILD_ID);
    if (buildId != null) {
      final SBuild build = myBuildsManager.findBuildInstanceById(buildId);
      if (build != null) {
        return build.getBuildPromotion();
      }
      throw new NotFoundException("No build found by build id '" + buildId + "'.");
    }

    final Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      return getBuildPromotionById(id, myBuildPromotionManager, myBuildsManager);
    }

    return null;
  }

  @NotNull
  @Override
  protected ItemFilter<BuildPromotion> getFilter(@NotNull final Locator locator) {

    final MultiCheckerFilter<BuildPromotion> result = new MultiCheckerFilter<BuildPromotion>();

    Locator stateLocator = getStateLocator(locator);

    if (!isStateIncluded(stateLocator, STATE_QUEUED)) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return item.getQueuedBuild() == null;
        }
      });
    }

    if (!isStateIncluded(stateLocator, STATE_RUNNING)) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild associatedBuild = item.getAssociatedBuild();
          return associatedBuild == null || associatedBuild.isFinished();
        }
      });
    }

    if (!isStateIncluded(stateLocator, STATE_FINISHED)) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild associatedBuild = item.getAssociatedBuild();
          return associatedBuild == null || !associatedBuild.isFinished();
        }
      });
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    SProject project = null;
    if (projectLocator != null) {
      project = myProjectFinder.getItem(projectLocator); //todo: support multiple projects here
      final SProject internalProject = project;
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuildType buildType = item.getBuildType();
          return buildType != null && internalProject.equals(buildType.getProject());
        }
      });
    }

    final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    SProject affectedProject = null;
    if (affectedProjectLocator != null) {
      affectedProject = myProjectFinder.getItem(affectedProjectLocator);
      final SProject internalProject = affectedProject;
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuildType buildType = item.getBuildType();
          return buildType != null && ProjectFinder.isSameOrParent(internalProject, buildType.getProject());
        }
      });
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      final Set<SBuildType> buildTypes = new HashSet<>(myBuildTypeFinder.getBuildTypes(affectedProject, buildTypeLocator));
      if (buildTypes.isEmpty()){
        throw new NotFoundException("No build types found for locator '" + buildTypeLocator + "'");
      }
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return buildTypes.contains(item.getParentBuildType());
        }
      });
    }

    final String branchLocatorValue = locator.getSingleDimensionValue(BRANCH);
    if (branchLocatorValue != null) {
      final PagedSearchResult<Branch> branches = myBranchFinder.getItemsIfValidBranchListLocator(buildTypeLocator, branchLocatorValue);
      if (branches != null) {
        //branches found - use them
        Set<String> branchNames = getBranchNamesSet(branches.myEntries);
        Set<String> branchDisplayNames = getBranchDisplayNamesSet(branches.myEntries);
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            final Branch buildBranch = BranchFinder.getBuildBranch(item);
            return branchNames.contains(buildBranch.getName()) || branchDisplayNames.contains(buildBranch.getDisplayName());
          }
        });
      } else {
        //branches not found by locator - try to use filter
        BranchFinder.BranchFilterDetails branchFilterDetails;
        try {
          branchFilterDetails = myBranchFinder.getBranchFilterDetails(branchLocatorValue);
        } catch (LocatorProcessException locatorException) {
          throw new BadRequestException("Invalid sub-locator '" + BRANCH + "': " + locatorException.getMessage(), locatorException);
        }
        if (!branchFilterDetails.isAnyBranch()) {
          result.add(new FilterConditionChecker<BuildPromotion>() {
            public boolean isIncluded(@NotNull final BuildPromotion item) {
              return branchFilterDetails.isIncluded(item);
            }
          });
        }
      }
    }

    final String agentLocator = locator.getSingleDimensionValue(AGENT);
    if (agentLocator != null) {
      final List<SBuildAgent> agents = myAgentFinder.getItems(agentLocator).myEntries;
      if (agents.isEmpty()){
        throw new NotFoundException("No agents are found by locator '" + agentLocator +"'");
      }
      result.add(new FilterConditionChecker<BuildPromotion>() {
        //todo: consider improving performance, see jetbrains/buildServer/server/rest/data/build/GenericBuildsFilter.java:120
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          if (build != null) {
            final SBuildAgent buildAgent = build.getAgent();
            final int buildAgentId = buildAgent.getId();
            final String buildAgentName = buildAgent.getName();
            return CollectionsUtil.contains(agents, new Filter<SBuildAgent>() {
              public boolean accept(@NotNull final SBuildAgent agent) {
                if (agent.getId() > 0){
                  return buildAgentId == agent.getId();
                } else {
                  return buildAgentName.equals(agent.getName());
                }
              }
            });
          }

          final SQueuedBuild queuedBuild = item.getQueuedBuild(); //for queued build using compatible agents
          if (queuedBuild != null) {
            return !CollectionsUtil.intersect(queuedBuild.getCompatibleAgents(), agents).isEmpty();
          }
          return false;
        }
      });
    }

    //compatibility support
    final String tags = locator.getSingleDimensionValue(TAGS);
    if (tags != null) {
      final List<String> tagsList = Arrays.asList(tags.split(","));
      if (tagsList.size() > 0) {
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return item.getTags().containsAll(tagsList);
          }
        });
      }
    }

    final String tag = locator.getSingleDimensionValue(TAG); //support multiple dimensions here, https://youtrack.jetbrains.com/issue/TW-44203
    if (tag != null) {
      if (tag.startsWith("format:extended")) { //pre-9.1 compatibility
        //todo: log this?
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            try {
              return isTagsMatchLocator(item.getTags(), new Locator(tag));
            } catch (LocatorProcessException e) {
              throw new BadRequestException("Invalid locator 'tag' (legacy format is used): " + e.getMessage(), e);
            }
          }
        });
      } else {
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return new TagFinder(myUserFinder, item).getItems(tag, TagFinder.getDefaultLocator()).myEntries.size() > 0;
          }
        });
      }
    }

    final String compatibleAagentLocator = locator.getSingleDimensionValue(COMPATIBLE_AGENT); //experimental, only for queued builds
    if (compatibleAagentLocator != null) {
      final SBuildAgent agent = myAgentFinder.getItem(compatibleAagentLocator);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SQueuedBuild queuedBuild = item.getQueuedBuild();
          if (queuedBuild != null) {
            return queuedBuild.getCompatibleAgents().contains(agent);
          }
          return false;
        }
      });
    }

    final Long compatibleAgentsCount = locator.getSingleDimensionValueAsLong(COMPATIBLE_AGENTS_COUNT); //experimental, only for queued builds
    if (compatibleAgentsCount != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SQueuedBuild queuedBuild = item.getQueuedBuild();
          if (queuedBuild != null) {
            return compatibleAgentsCount.equals(Integer.valueOf(queuedBuild.getCompatibleAgents().size()).longValue());
          }
          return false;
        }
      });
    }

    final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL);
    if (personal != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return FilterUtil.isIncludedByBooleanFilter(personal, item.isPersonal());
        }
      });
    }

    final String userDimension = locator.getSingleDimensionValue(USER);
    if (userDimension != null) {
      final SUser user = myUserFinder.getItem(userDimension);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          SUser actualUser = null;
          final SBuild build = item.getAssociatedBuild();
          if (build != null) {
            actualUser = build.getTriggeredBy().getUser();
          }
          final SQueuedBuild queuedBuild = item.getQueuedBuild();
          if (queuedBuild != null) {
            actualUser = queuedBuild.getTriggeredBy().getUser();
          }
          return actualUser != null && user.getId() == actualUser.getId();
        }
      });
    }

    final List<String> properties = locator.getDimensionValue(PROPERTY);
    if (!properties.isEmpty()) {
      final Matcher<ParametersProvider> parameterCondition = ParameterCondition.create(properties);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          //does not correspond to Build.getProperties() which includes less parameters
          return parameterCondition.matches(((BuildPromotionEx)item).getParametersProvider()); //TeamCity open API issue
        }
      });
    }

    if (locator.getUnusedDimensions().contains(SNAPSHOT_DEP)) { //performance optimization: do not filter if already processed
      final String snapshotDepDimension = locator.getSingleDimensionValue(SNAPSHOT_DEP);
      if (snapshotDepDimension != null) {
        final Set<BuildPromotion> snapshotRelatedBuilds = new HashSet<>(getSnapshotRelatedBuilds(snapshotDepDimension));
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return snapshotRelatedBuilds.contains(item);
          }
        });
      }
    }

    if (locator.getUnusedDimensions().contains(EQUIVALENT)) { //performance optimization: do not filter if already processed
      final String equivalent = locator.getSingleDimensionValue(EQUIVALENT);
      if (equivalent != null) {
        final Set<BuildPromotion> filter = new HashSet<BuildPromotion>(((BuildPromotionEx)getItem(equivalent)).getStartedEquivalentPromotions());
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return filter.contains(item);
          }
        });
      }
    }

    if (locator.getUnusedDimensions().contains(METADATA)) { //performance optimization: do not filter if already processed
      final String metadata = locator.getSingleDimensionValue(METADATA);
      if (metadata != null) {
        final Iterator<BuildMetadataEntry> metadataEntries = getBuildMetadataEntryIterator(metadata);
        final Set<Long> buildIds = new HashSet<Long>();
        while (metadataEntries.hasNext()) {
          BuildMetadataEntry metadataEntry = metadataEntries.next();
          buildIds.add(metadataEntry.getBuildId());
        }
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return buildIds.contains(item.getAssociatedBuildId());
          }
        });
      }
    }

    if (locator.getUnusedDimensions().contains(ORDERED)) { //performance optimization: do not filter if already processed
      final String graphLocator = locator.getSingleDimensionValue(ORDERED);
      if (graphLocator != null) {
        final GraphFinder<BuildPromotion> graphFinder = new BuildPromotionOrderedFinder(BuildPromotionFinder.this);
        final Set<BuildPromotion> filter = new HashSet<BuildPromotion>(graphFinder.getItems(graphLocator).myEntries);
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return filter.contains(item);
          }
        });
      }
    }

    final String sinceBuildDimension = locator.getSingleDimensionValue(SINCE_BUILD);
    BuildPromotion sinceBuildPromotion = null;
    Long sinceBuildId = null;
    if (sinceBuildDimension != null) {
      try {
        sinceBuildPromotion = getItem(sinceBuildDimension);
        final SQueuedBuild queuedBuild = sinceBuildPromotion.getQueuedBuild();
        if (queuedBuild != null) {
          //compare queued builds by id (triggering sequence)
          final long buildPromotionId = getBuildId(sinceBuildPromotion);
          result.add(new FilterConditionChecker<BuildPromotion>() {
            public boolean isIncluded(@NotNull final BuildPromotion item) {
              return buildPromotionId < getBuildId(item);
            }
          });
        } else {
          // for started build, compare by start time
          final SBuild limitingBuild = sinceBuildPromotion.getAssociatedBuild();
          if (limitingBuild != null) {
            final Date startDate = limitingBuild.getStartDate();
            result.add(new FilterConditionChecker<BuildPromotion>() {
              public boolean isIncluded(@NotNull final BuildPromotion item) {
                final SBuild build = item.getAssociatedBuild();
                if (build == null) return true;
                if (startDate.equals(build.getStartDate()) && limitingBuild.getBuildId() != build.getBuildId()) return true;
                return startDate.before(build.getStartDate());
              }
            });
          }
        }
      } catch (NotFoundException e) {
        //build not found by sinceBuild locator, extract id ad filter using it
        sinceBuildId = getBuildId(sinceBuildDimension);
        final long sinceBuildIdFinal = sinceBuildId;
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return sinceBuildIdFinal < getBuildId(item);
          }
        });
      }
    }

    final String untilBuild = locator.getSingleDimensionValue(UNTIL_BUILD);
    if (untilBuild != null) {
      try {
        final BuildPromotion untilBuildPromotion = getItem(untilBuild);
        final SQueuedBuild queuedBuild = untilBuildPromotion.getQueuedBuild();
        if (queuedBuild != null) {
          //compare queued builds by id (triggering sequence)
          final long buildPromotionId = getBuildId(untilBuildPromotion);
          result.add(new FilterConditionChecker<BuildPromotion>() {
            public boolean isIncluded(@NotNull final BuildPromotion item) {
              return !(buildPromotionId < getBuildId(item));
            }
          });
        } else {
          // for started build, compare by start time
          final SBuild limitingBuild = untilBuildPromotion.getAssociatedBuild();
          if (limitingBuild != null) {
            final Date startDate = limitingBuild.getStartDate();
            result.add(new FilterConditionChecker<BuildPromotion>() {
              public boolean isIncluded(@NotNull final BuildPromotion item) {
                final SBuild build = item.getAssociatedBuild();
                return build == null || !startDate.before(build.getStartDate());
              }
            });
          }
        }
      } catch (NotFoundException e) {
        //build not found by sinceBuild locator, extract id ad filter using it
        final long untilBuildId = getBuildId(untilBuild);
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return !(untilBuildId < getBuildId(item));
          }
        });
      }
    }

    TimeCondition.FilterAndLimitingDate<BuildPromotion> queuedFiltering =
      myTimeCondition.processTimeConditions(QUEUED_TIME, locator, TimeCondition.QUEUED_BUILD_TIME, TimeCondition.QUEUED_BUILD_TIME);
    if (queuedFiltering != null) result.add(queuedFiltering.getFilter());

    TimeCondition.FilterAndLimitingDate<BuildPromotion> startedFiltering =
      myTimeCondition.processTimeConditions(STARTED_TIME, locator, TimeCondition.STARTED_BUILD_TIME, TimeCondition.STARTED_BUILD_TIME);
    @Nullable Date sinceStartDate = null;
    if (startedFiltering != null) {
      result.add(startedFiltering.getFilter());
      sinceStartDate = startedFiltering.getLimitingDate();
    }

    //todo: add processing cut of based on assumption of max build time (say, a week); for other times as well
    TimeCondition.FilterAndLimitingDate<BuildPromotion> finishFiltering =
      myTimeCondition.processTimeConditions(FINISHED_TIME, locator, TimeCondition.FINISHED_BUILD_TIME, TimeCondition.FINISHED_BUILD_TIME);
    if (finishFiltering != null) result.add(finishFiltering.getFilter());

    final String revisionLocatorText = locator.getSingleDimensionValue(REVISION);
    if (revisionLocatorText != null) {
      final Locator revisionLocator = new Locator(revisionLocatorText, "version", "internalVersion", "vcsRoot", Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
      final String revision = revisionLocator.getSingleValue();
      if (revision != null) {
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            final List<BuildRevision> buildRevisions = item.getRevisions();
            for (BuildRevision rev : buildRevisions) {
              if (revision.equals(rev.getRevisionDisplayName())) {
                return true;
              }
            }
            return false;
          }
        });
      } else {
        final String vcsRootLocator = revisionLocator.getSingleDimensionValue("vcsRoot");
        final SVcsRoot vcsRoot = vcsRootLocator == null ? null : myVcsRootFinder.getItem(vcsRootLocator);
        final String version = revisionLocator.getSingleDimensionValue("version");
        final String internalVersion = revisionLocator.getSingleDimensionValue("internalVersion");
        revisionLocator.checkLocatorFullyProcessed();
        if (vcsRoot != null || !StringUtil.isEmpty(version) || !StringUtil.isEmpty(internalVersion)) {
          result.add(new FilterConditionChecker<BuildPromotion>() {
            public boolean isIncluded(@NotNull final BuildPromotion item) {
              final List<BuildRevision> revisions = item.getRevisions();
              for (BuildRevision rev : revisions) {
                if ((vcsRoot == null || vcsRoot.getId() == rev.getRoot().getParent().getId()) &&
                    (version == null || version.equals(rev.getRevisionDisplayName())) &&
                    (internalVersion == null || internalVersion.equals(rev.getRevision()))) {
                  return true;
                }
              }
              return false;
            }
          });
        }
      }
    }

    final MultiCheckerFilter<SBuild> buildFilter = getBuildFilter(locator);
    if (buildFilter.getSubFiltersCount() > 0) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          if (build == null) {
            return false;
          }
          return buildFilter.isIncluded(build);
        }
      });
    }

    sinceStartDate = TimeCondition.maxDate(sinceStartDate, DataProvider.parseDate(locator.getSingleDimensionValue(SINCE_DATE))); //see also filtering in getBuildFilter

    final Boolean canceled = locator.getSingleDimensionValueAsBoolean(CANCELED);
    if (canceled != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          return build == null || FilterUtil.isIncludedByBooleanFilter(canceled, build.getCanceledInfo() != null);
        }
      });
    }

    final Boolean failedToStart = locator.getSingleDimensionValueAsBoolean(FAILED_TO_START);
    if (failedToStart != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          return build == null || FilterUtil.isIncludedByBooleanFilter(failedToStart, build.isInternalError());
        }
      });
    }

    return getFilterWithProcessingCutOff(result, locator.getSingleDimensionValueAsLong(SINCE_BUILD_ID_LOOK_AHEAD_COUNT), sinceBuildPromotion, sinceBuildId, sinceStartDate);
  }

  private Set<String> getBranchNamesSet(final List<Branch> branches) {
    final HashSet<String> result = new HashSet<>(branches.size());
    for (Branch branch : branches) {
      result.add(branch.getName());
    }
    return result;
  }

  private Set<String> getBranchDisplayNamesSet(final List<Branch> branches) {
    final HashSet<String> result = new HashSet<>(branches.size());
    for (Branch branch : branches) {
      result.add(branch.getDisplayName());
    }
    return result;
  }

  private ItemFilter<BuildPromotion> getFilterWithProcessingCutOff(@NotNull final MultiCheckerFilter<BuildPromotion> result,
                                                                       @Nullable final Long lookupLimit,
                                                                       @Nullable final BuildPromotion sinceBuildPromotion,
                                                                       @Nullable Long sinceBuildId,
                                                                       @Nullable Date sinceStartDate) {
    if (sinceBuildPromotion == null && sinceBuildId == null && sinceStartDate == null) {
      return result;
    }

    if (sinceBuildPromotion != null) {
      sinceBuildId = sinceBuildId != null ? Math.max(sinceBuildId, getBuildId(sinceBuildPromotion)) : getBuildId(sinceBuildPromotion);
      final SBuild sinceBuild = sinceBuildPromotion.getAssociatedBuild();
      if (sinceBuild != null) {
        sinceStartDate = TimeCondition.maxDate(sinceStartDate, sinceBuild.getStartDate());
      }
    }

    //cut off builds traversing
    final long lookAheadCount = lookupLimit != null ? lookupLimit : TeamCityProperties.getLong("rest.request.builds.sinceBuildIdLookAheadCount", 50);
    final Long sinceBuildIdFinal = sinceBuildId;
    final Date sinceStartDateFinal = sinceStartDate;
    return new ItemFilter<BuildPromotion>() {
      private long currentLookAheadCount = 0;

      public boolean isIncluded(@NotNull final BuildPromotion item) {
        return result.isIncluded(item);
      }

      public boolean shouldStop(@NotNull final BuildPromotion item) {
        if (result.shouldStop(item)) return true;
        final SBuild build = item.getAssociatedBuild();
        if (build == null || !build.isFinished()) return false; //do not stop while processing queued and running builds
        if (sinceStartDateFinal != null && sinceStartDateFinal.after(build.getStartDate())) return true;
        if (sinceBuildIdFinal != null) {
          if (sinceBuildIdFinal.equals(getBuildId(item))) return true; //found exactly the limiting build - stop here
          if (sinceBuildIdFinal > getBuildId(item)) {
            currentLookAheadCount++;
          } else {
            currentLookAheadCount = 0; //reset the counter
          }
          return currentLookAheadCount > lookAheadCount; // stop only after finding more than lookAheadCount builds with lesser id (try to take into account builds reordering)
        }
        return false;
      }
    };
  }

  @NotNull
  private Locator getStateLocator(@NotNull final Locator locator) {
    final String stateDimension = locator.getSingleDimensionValue(STATE);
    if (stateDimension != null) {
      return createStateLocator(stateDimension);
    }

    final String stateRunningDimension = locator.getSingleDimensionValue(RUNNING); //compatibility with pre-9.1
    if (stateRunningDimension != null) {
      final Boolean legacyRunning = locator.getSingleDimensionValueAsBoolean(RUNNING);
      if (legacyRunning == null) {
        return createStateLocator(Locator.getStringLocator(STATE_FINISHED, "true", STATE_RUNNING, "true", STATE_QUEUED, "false"));
      }
      if (legacyRunning) {
        return createStateLocator(STATE_RUNNING);
      } else {
        return createStateLocator(STATE_FINISHED);
      }
    }

    return createStateLocator(STATE_ANY); // default to all the builds
  }

  private boolean isStateLocatorPresent(@NotNull final Locator locator) {
    final Set<String> usedDimensions = locator.getUsedDimensions();
    if (locator.getSingleDimensionValue(STATE) != null) {
      if (!usedDimensions.contains(STATE)) locator.markUnused(STATE);
      return true;
    }
    if (locator.getSingleDimensionValue(RUNNING) != null) {
      if (!usedDimensions.contains(RUNNING)) locator.markUnused(RUNNING);
      return true;
    }
    return false;
  }

  @NotNull
  private MultiCheckerFilter<SBuild> getBuildFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SBuild> result = new MultiCheckerFilter<SBuild>();

    final String buildNumber = locator.getSingleDimensionValue(NUMBER);
    if (buildNumber != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return buildNumber.equals(item.getBuildNumber());
        }
      });
    }

    final String status = locator.getSingleDimensionValue(STATUS);
    if (status != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return status.equalsIgnoreCase(item.getStatusDescriptor().getStatus().getText());
        }
      });
    }

    final Boolean pinned = locator.getSingleDimensionValueAsBoolean(PINNED);
    if (pinned != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(pinned, item.isPinned());
        }
      });
    }

    final Boolean hanging = locator.getSingleDimensionValueAsBoolean(HANGING);
    if (hanging != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          if (item.isFinished()) return !hanging;
          return FilterUtil.isIncludedByBooleanFilter(hanging, ((SRunningBuild)item).isProbablyHanging());
        }
      });
    }

    //compatibility, use "agent" locator instead
    final String agentName = locator.getSingleDimensionValue(AGENT_NAME);
    if (agentName != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return agentName.equals(item.getAgent().getName());
        }
      });
    }

    final Date sinceDate = DataProvider.parseDate(locator.getSingleDimensionValue(SINCE_DATE)); //see also settings cut off date in main filter
    if (sinceDate != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return sinceDate.before(item.getStartDate());
        }
      });
    }

    final Date untilDate = DataProvider.parseDate(locator.getSingleDimensionValue(UNTIL_DATE));
    if (untilDate != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return !(untilDate.before(item.getStartDate()));
        }
      });
    }

    final List<String> statisticValues = locator.getDimensionValue(STATISTIC_VALUE);
    if (!statisticValues.isEmpty()) {
      final Matcher<ParametersProvider> parameterCondition = ParameterCondition.create(statisticValues);
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return parameterCondition.matches(new AbstractMapParametersProvider(Build.getBuildStatisticsValues(item)));
        }
      });
    }

    return result;
  }

  @NotNull
  public static BuildPromotion getBuildPromotionById(@NotNull final Long id,
                                                     @NotNull final BuildPromotionManager buildPromotionManager,
                                                     @NotNull final BuildsManager buildsManager) {
    //the logic should match that of getBuildId(String)
    final BuildPromotion buildPromotion = buildPromotionManager.findPromotionOrReplacement(id);
    if (buildPromotion != null && (getBuildId(buildPromotion) == buildPromotion.getId())) {
      return buildPromotion;
    }
    final SBuild build = buildsManager.findBuildInstanceById(id);
    if (build != null) {
      return build.getBuildPromotion();
    }
    throw new NotFoundException("No build found by id '" + id + "'.");
  }

  @NotNull
  private Long getBuildId(@Nullable final String buildLocator) {
    //the logic should match that of findSingleItem
    if (buildLocator == null) {
      throw new BadRequestException("Cannot find build or build id for empty locator. Try specifying '" + DIMENSION_ID + "' locator dimension");
    }

    final Locator locator = new Locator(buildLocator);
    final Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null && locator.getUnusedDimensions().isEmpty()) {
      return id;
    }

    throw new BadRequestException("Cannot find build or build id for locator '" + buildLocator + "'. Try specifying '" + DIMENSION_ID + "' locator dimension");
  }

  @NotNull
  public static Long getBuildId(@NotNull final BuildPromotion buildPromotion) {
    final Long buildId = buildPromotion.getAssociatedBuildId(); //it is important to get id from the build as that might be different from promotion id
    return buildId != null ? buildId : buildPromotion.getId(); // there should be no queued builds with old ids (TW-38777)
  }

  private boolean isTagsMatchLocator(final List<String> buildTags, final Locator tagsLocator) {
    if (!"extended".equals(tagsLocator.getSingleDimensionValue("format"))) {
      throw new BadRequestException("Only 'extended' value is supported for 'format' dimension of 'tag' dimension");
    }
    final Boolean present = tagsLocator.getSingleDimensionValueAsBoolean("present", true);
    final String patternString = tagsLocator.getSingleDimensionValue("regexp");
    if (present == null) {
      return true;
    }
    Boolean tagsMatchPattern = null;
    if (patternString != null) {
      if (StringUtil.isEmpty(patternString)) {
        throw new BadRequestException("'regexp' sub-dimension should not be empty for 'tag' dimension");
      }
      try {
        tagsMatchPattern = tagsMatchPattern(buildTags, patternString);
      } catch (PatternSyntaxException e) {
        throw new BadRequestException(
          "Bad syntax for Java regular expression in 'regexp' sub-dimension of 'tag' dimension: " + e.getMessage(), e);
      }
    }
    if (tagsMatchPattern == null) {
      if ((present && buildTags.size() != 0) || (!present && (buildTags.size() == 0))) {
        return true;
      }
    } else {
      if (present && tagsMatchPattern) {
        return true;
      } else if (!present && !tagsMatchPattern) {
        return true;
      }
    }
    return false;
  }

  private Boolean tagsMatchPattern(@NotNull final List<String> tags, @NotNull final String patternString) throws PatternSyntaxException {
    final Pattern pattern = Pattern.compile(patternString);
    boolean atLestOneMatches = false;
    for (String tag : tags) {
      atLestOneMatches = atLestOneMatches || pattern.matcher(tag).matches();
    }
    return atLestOneMatches;
  }


  @NotNull
  @Override
  protected ItemHolder<BuildPromotion> getPrefilteredItems(@NotNull Locator locator) {
    final Boolean byPromotion = locator.getSingleDimensionValueAsBoolean(BY_PROMOTION);
    if (byPromotion != null && !byPromotion) {
      throw new BadRequestException("Found '" + BY_PROMOTION + "' locator set to 'false' which is not supported");
    }

    final String strob = locator.getSingleDimensionValue(STROB);
    if (strob != null) {
      final Locator strobLocator = new Locator(strob, BUILD_TYPE, BRANCH, STROB_BUILD_LOCATOR);

      List<Locator> partialLocators = Collections.singletonList(Locator.createEmptyLocator());
      partialLocators = getPartialLocatorsForStrobDimension(partialLocators, strobLocator, BUILD_TYPE, myBuildTypeFinder);
      partialLocators = getPartialLocatorsForStrobDimension(partialLocators, strobLocator, BRANCH, myBranchFinder);

      final String strobBuildLocator = strobLocator.getSingleDimensionValue(STROB_BUILD_LOCATOR);

      final AggregatingItemHolder<BuildPromotion> strobResult = new AggregatingItemHolder<>();
      for (Locator partialLocator : partialLocators) {
        partialLocator.setDimensionIfNotPresent(PagerData.COUNT, "1");  //limit to single item per strob item by default
        final String finalBuildLocator = Locator.createLocator(strobBuildLocator, partialLocator, new String[]{}).getStringRepresentation();
        strobResult.add(getItemHolder(getItems(finalBuildLocator).myEntries));
      }
      strobLocator.checkLocatorFullyProcessed();
      return strobResult;
    }

    setLocatorDefaults(locator);

    final String equivalent = locator.getSingleDimensionValue(EQUIVALENT);
    if (equivalent != null) {
      final BuildPromotionEx build = (BuildPromotionEx)getItem(equivalent);
      final List<BuildPromotionEx> result = build.getStartedEquivalentPromotions();
      final Set<BuildPromotion> convertedResult = new TreeSet<BuildPromotion>(BUILD_PROMOTIONS_COMPARATOR);
      for (BuildPromotionEx item : result) {
        convertedResult.add(item);
      }
      return getItemHolder(convertedResult);
    }

    final String metadata = locator.getSingleDimensionValue(METADATA);
    if (metadata != null) {
      final Iterator<BuildMetadataEntry> metadataEntries = getBuildMetadataEntryIterator(metadata);
      return new ItemHolder<BuildPromotion>() {
        @Override
        public boolean process(@NotNull final ItemProcessor<BuildPromotion> processor) {
          while (metadataEntries.hasNext()) {
            BuildMetadataEntry metadataEntry = metadataEntries.next();
            SBuild build = myBuildsManager.findBuildInstanceById(metadataEntry.getBuildId());
            if (build != null) {
              if (!processor.processItem(build.getBuildPromotion())) {
                return false;
              }
            }
          }
          return true;
        }
      };
    }

    final String graphLocator = locator.getSingleDimensionValue(ORDERED);
    if (graphLocator != null) {
      final GraphFinder<BuildPromotion> graphFinder = new BuildPromotionOrderedFinder(this);
      //consider performance optimization by converting id to build only on actual retrieve (use GraphFinder<Int/buildId>)
      return getItemHolder(graphFinder.getItems(graphLocator).myEntries);
    }

    final String snapshotDepDimension = locator.getSingleDimensionValue(SNAPSHOT_DEP);
    if (snapshotDepDimension != null) {
      return getItemHolder(getSnapshotRelatedBuilds(snapshotDepDimension));
    }

    final String number = locator.getSingleDimensionValue(NUMBER);
    if (number != null) {
      final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeLocator != null) {
        final List<SBuildType> buildTypes = myBuildTypeFinder.getBuildTypes(null, buildTypeLocator);
        final Set<BuildPromotion> builds = new TreeSet<BuildPromotion>(BUILD_PROMOTIONS_COMPARATOR);
        for (SBuildType buildType : buildTypes) {
          builds.addAll(BuildFinder.toBuildPromotions(myBuildsManager.findBuildInstancesByBuildNumber(buildType.getBuildTypeId(), number)));
        }
        return getItemHolder(builds);
      } else{
        // if build type is not specified, search by scanning (performance impact)
        locator.markUnused(NUMBER);
      }
    }

    final ArrayList<BuildPromotion> result = new ArrayList<BuildPromotion>();

    Locator stateLocator = getStateLocator(locator);

    if (isStateIncluded(stateLocator, STATE_QUEUED)) {
      //todo: should sort backwards as currently the order does not seem right...
      result.addAll(CollectionsUtil.convertCollection(myBuildQueue.getItems(), new Converter<BuildPromotion, SQueuedBuild>() {
        public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
          return source.getBuildPromotion();
        }
      }));
    }

    if (isStateIncluded(stateLocator, STATE_RUNNING)) {  //todo: address an issue when a build can appear twice in the output
      result.addAll(CollectionsUtil.convertCollection(myBuildsManager.getRunningBuilds(), new Converter<BuildPromotion, SRunningBuild>() {
        public BuildPromotion createFrom(@NotNull final SRunningBuild source) {
          return source.getBuildPromotion();
        }
      }));
    }

    ItemHolder<BuildPromotion> finishedBuilds = null;
    if (isStateIncluded(stateLocator, STATE_FINISHED)) {
      @Nullable SBuildType buildType = null;
      final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeLocator != null) {
        final String affectedProjectLocator = locator.getSingleDimensionValue(AFFECTED_PROJECT);
        SProject affectedProject = null;
        if (affectedProjectLocator != null) {
          affectedProject = myProjectFinder.getItem(affectedProjectLocator);
        }
        List<SBuildType> buildTypes = myBuildTypeFinder.getBuildTypes(affectedProject, buildTypeLocator);
        if (buildTypes.size() == 1) {
          buildType = buildTypes.get(0);
        } else {
          locator.markUnused(BUILD_TYPE, AFFECTED_PROJECT);
        }
      }

      final BuildQueryOptions options = new BuildQueryOptions();
      if (buildType != null) {
        options.setBuildTypeId(buildType.getBuildTypeId());
      }

      final Boolean personal = locator.lookupSingleDimensionValueAsBoolean(PERSONAL);
      if (personal == null || personal) {
        final String userDimension = locator.getSingleDimensionValue(USER);
        options.setIncludePersonal(true, userDimension == null ? null : myUserFinder.getItem(userDimension));
      } else {
        options.setIncludePersonal(false, null);
      }

      final Boolean failedToStart = locator.lookupSingleDimensionValueAsBoolean(FAILED_TO_START);
      final Boolean canceled = locator.lookupSingleDimensionValueAsBoolean(CANCELED);
      if (canceled == null || canceled || failedToStart == null || failedToStart) {
        options.setIncludeCanceled(true); //also includes failed to start builds, TW-32060
      } else {
        options.setIncludeCanceled(false);
      }

      final String branchLocatorValue = locator.lookupSingleDimensionValue(BRANCH); // do not mark dimension as used as not all can be used from it
      if (branchLocatorValue != null) {
        BranchFinder.BranchFilterDetails branchFilterDetails;
        try {
          branchFilterDetails = myBranchFinder.getBranchFilterDetailsWithoutLocatorCheck(branchLocatorValue);
          // parsed OK, setting options
          if (branchFilterDetails.isAnyBranch()) {
            options.setMatchAllBranches(true);
          } else {
            if (branchFilterDetails.isDefaultBranchOrNotBranched()) {
              options.setMatchAllBranches(false);
              options.setBranch(Branch.DEFAULT_BRANCH_NAME);
            }
            if (branchFilterDetails.getBranchName() != null) {
              //ineffective, but otherwise cannot find a build by display name branch (need support in BuildQueryOptions to get default + named branch)
              options.setMatchAllBranches(true);
              //options.setMatchAllBranches(false);
              //options.setBranch(branchFilterDetails.getBranchName());
            }
          }
        } catch (LocatorProcessException e) {
          // not parsed, cannot extract name or default status
          options.setMatchAllBranches(true);
        }
      } else {
        options.setMatchAllBranches(true);
      }

      options.setIncludeRunning(false); //running builds are retrieved separately and appear before finished ones
      options.setOrderByChanges(false);

      finishedBuilds = new ItemHolder<BuildPromotion>() {
        public boolean process(@NotNull final ItemProcessor<BuildPromotion> processor) {
          myBuildsManager.processBuilds(options, new ItemProcessor<SBuild>() {
            public boolean processItem(SBuild item) {
              return processor.processItem(item.getBuildPromotion());
            }
          });
          return false;
        }
      };
    }

    stateLocator.checkLocatorFullyProcessed();

    final ItemHolder<BuildPromotion> finishedBuildsFinal = finishedBuilds;
    return new ItemHolder<BuildPromotion>() {
      public boolean process(@NotNull final ItemProcessor<BuildPromotion> processor) {
        if (new CollectionItemHolder<BuildPromotion>(result).process(processor)) {
          if (finishedBuildsFinal != null) {
            return finishedBuildsFinal.process(processor);
          }
          return true;
        }
        return false;
      }
    };
  }

  @NotNull
  private Iterator<BuildMetadataEntry> getBuildMetadataEntryIterator(@NotNull final String metadataLocatorText) {
    Locator metadataLocator = new Locator(metadataLocatorText, "providerId", "key");
    String providerId = metadataLocator.getSingleDimensionValue("providerId");
    if (providerId == null) {
      throw new BadRequestException("Metadata locator '" + metadataLocatorText + "' should contain '" + "providerId" + "' dimension.");
    }
    String key = metadataLocator.getSingleDimensionValue("key");
    metadataLocator.checkLocatorFullyProcessed();

    return key == null ? myMetadataStorage.getAllEntries(providerId) : myMetadataStorage.getEntriesByKey(providerId, key);
  }

  @NotNull
  private <ITEM> List<Locator> getPartialLocatorsForStrobDimension(@NotNull final List<Locator> resultStrobBuildLocators, @NotNull final Locator strobLocator, @NotNull final String strobDimension,
                                                                   @NotNull final AbstractFinder<ITEM> strobTypeFinder) {
    final String strobDimensionValue = strobLocator.getSingleDimensionValue(strobDimension);
    if (strobDimensionValue == null) {
      return resultStrobBuildLocators;
    }
    List<Locator> result = new ArrayList<>();
    for (Locator strobBuildLocator : resultStrobBuildLocators) {

      String patchedStrobDimensionValue = strobDimensionValue;
      // preprocess locator before use
      if (BUILD_TYPE.equals(strobDimension)) {
        patchedStrobDimensionValue = Locator.setDimensionIfNotPresent(strobDimensionValue, BuildTypeFinder.TEMPLATE_FLAG_DIMENSION_NAME, "false");
      } else {
        final String buildTypeLocator = strobBuildLocator.getSingleDimensionValue(BUILD_TYPE);
        if (BRANCH.equals(strobDimension) && buildTypeLocator != null) {
          patchedStrobDimensionValue = Locator.setDimensionIfNotPresent(strobDimensionValue, BranchFinder.BUILD_TYPE, buildTypeLocator);
        }
      }

      final PagedSearchResult<ITEM> items = strobTypeFinder.getItems(patchedStrobDimensionValue);
      for (ITEM item : items.myEntries) {
        result.add(new Locator(strobBuildLocator).setDimensionIfNotPresent(strobDimension, strobTypeFinder.getItemLocator(item)));
      }
    }
    return result;
  }

  private void setLocatorDefaults(@NotNull final Locator locator) {
    final Boolean defaultFiltering = locator.getSingleDimensionValueAsBoolean(DEFAULT_FILTERING, true);
    if (!locator.isSingleValue() && (defaultFiltering == null || defaultFiltering)) {
      locator.setDimensionIfNotPresent(PERSONAL, "false");
      locator.setDimensionIfNotPresent(CANCELED, "false");
      if (!isStateLocatorPresent(locator)) {
        locator.setDimension(STATE, STATE_FINISHED);
      }
      locator.setDimensionIfNotPresent(FAILED_TO_START, "false");
      if (locator.lookupSingleDimensionValue(SNAPSHOT_DEP) == null &&
          locator.lookupSingleDimensionValue(EQUIVALENT) == null &&
          locator.lookupSingleDimensionValue(ORDERED) == null) {
        //do not force branch to default for some locators
        locator.setDimensionIfNotPresent(BRANCH, myBranchFinder.getDefaultBranchLocator());
      }
    }
  }

  @NotNull
  public BuildPromotion getBuildPromotion(final @Nullable SBuildType buildType, @Nullable final String locatorText) {
    if (buildType == null) {
      return getItem(locatorText);
    }

    final Locator locator = locatorText != null ? new Locator(locatorText) : Locator.createEmptyLocator();
    if (locator.isEmpty() || !locator.isSingleValue()) {
      return getItem(patchLocatorWithBuildType(buildType, locator));
    }
    //single value locator
    //use logic like BuildFinder: if there is build type and single value, assume it's build number
    final String buildNumber = locator.getSingleValue();
    assert buildNumber != null;
    SBuild build = myBuildsManager.findBuildInstanceByBuildNumber(buildType.getInternalId(), buildNumber);
    if (build != null) return build.getBuildPromotion();

    throw new NotFoundException("No build can be found by number '" + buildNumber + "' in the build type with id '" + buildType.getExternalId() + "'");

    /*
    final BuildPromotion singleItem = findSingleItem(locator);
    if (singleItem != null) { //will find it the regular way, go for it with all due checks
      return getItem(locator.getStringRepresentation());
    }
    */
  }

  @NotNull
  public PagedSearchResult<BuildPromotion> getBuildPromotions(final @Nullable SBuildType buildType, final @Nullable String locatorText) {
    if (buildType == null) {
      return getItems(locatorText);
    }

    final Locator locator = locatorText != null ? new Locator(locatorText) : Locator.createEmptyLocator();
    if (locator.isEmpty() || !locator.isSingleValue()) {
      return getItems(patchLocatorWithBuildType(buildType, locator));  //todo: test empty locator with not empty build type
    }

    //single value
    return new PagedSearchResult<BuildPromotion>(Collections.singletonList(getBuildPromotion(buildType, locatorText)), null, null);
  }

  @NotNull
  private String patchLocatorWithBuildType(@Nullable final SBuildType buildType, @NotNull final Locator locator) {
    if (buildType != null) {
        return locator.setDimensionIfNotPresent(BuildPromotionFinder.BUILD_TYPE, BuildTypeFinder.getLocator(buildType)).getStringRepresentation();
    }
    return locator.getStringRepresentation();
  }

  @NotNull
  private List<BuildPromotion> getSnapshotRelatedBuilds(@NotNull final String snapshotDepDimension) {
    final GraphFinder<BuildPromotion> graphFinder = new GraphFinder<BuildPromotion>(this, SNAPSHOT_DEPENDENCIES_TRAVERSER);
    final List<BuildPromotion> result = graphFinder.getItems(snapshotDepDimension).myEntries;
    Collections.sort(result, BUILD_PROMOTIONS_COMPARATOR);
    return result; //todo: patch branch locator, personal, etc.???
  }

  @NotNull
  private Locator createStateLocator(@NotNull final String stateDimension) {
    final Locator locator = new Locator(stateDimension, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, STATE_QUEUED, STATE_RUNNING, STATE_FINISHED);
    if (locator.isSingleValue()) {
      //check single value validity
      if (!stateDimension.equals(STATE_QUEUED) &&
          !stateDimension.equals(STATE_RUNNING) &&
          !stateDimension.equals(STATE_FINISHED) &&
          !stateDimension.equals(STATE_ANY)) {
        throw new BadRequestException("Unsupported value of '" + STATE + "' dimension: '" + stateDimension +
                                      "'. Should be one of the build states('" + STATE_QUEUED + "', '" + STATE_RUNNING + "', '" + STATE_FINISHED + "') or '" + STATE_ANY + "'");
      }
    }

    return locator;
  }

  private boolean isStateIncluded(@NotNull final Locator stateLocator, @NotNull final String state) {
    final String singleValue = stateLocator.getSingleValue();
    if (singleValue != null && (STATE_ANY.equals(singleValue) || state.equals(singleValue))) {
      return true;
    }
    //noinspection RedundantIfStatement
    if (!stateLocator.isSingleValue() && FilterUtil.isIncludedByBooleanFilter(stateLocator.getSingleDimensionValueAsBoolean(state, false), true)) {
      return true;
    }
    return false;
  }

  public static boolean buildIdDiffersFromPromotionId(@NotNull final BuildPromotion buildPromotion) {
    return buildPromotion.getAssociatedBuildId() != null && buildPromotion.getId() != buildPromotion.getAssociatedBuildId();
  }

  private static class BuildPromotionComparator implements Comparator<BuildPromotion> {
    public int compare(final BuildPromotion o1, final BuildPromotion o2) {
      final SQueuedBuild qb1 = o1.getQueuedBuild();
      final SQueuedBuild qb2 = o2.getQueuedBuild();
      if (qb1 != null) {
        if (qb2 != null) {
          return -qb1.getItemId().compareTo(qb2.getItemId());
        }
        return -1;
      }
      if (qb2 != null) {
        return 1;
      }

      final SBuild b1 = o1.getAssociatedBuild();
      final SBuild b2 = o2.getAssociatedBuild();
      if (b1 != null) {
        if (b2 != null) {
          if (b1.isFinished()) {
            if (b2.isFinished()) {
              final int resultByStartDate = b1.getStartDate().compareTo(b2.getStartDate());
              if (resultByStartDate == 0) return -Long.valueOf(b1.getBuildId()).compareTo(b2.getBuildId());
              return -resultByStartDate;
            }
            return 1;
          }
          if (b2.isFinished()) {
            return -1;
          }
          final int resultByStartDate = b1.getStartDate().compareTo(b2.getStartDate());
          if (resultByStartDate == 0) return -Long.valueOf(b1.getBuildId()).compareTo(b2.getBuildId());
          return -resultByStartDate;
        }
        return 1;
      }
      return -1;
    }
  }

  private static class SnapshotDepsTraverser implements GraphFinder.Traverser<BuildPromotion> {
    @NotNull
    public GraphFinder.LinkRetriever<BuildPromotion> getChildren() {
      return new GraphFinder.LinkRetriever<BuildPromotion>() {
        @NotNull
        public List<BuildPromotion> getLinked(@NotNull final BuildPromotion item) {
          return CollectionsUtil.convertCollection(item.getDependencies(), new Converter<BuildPromotion, BuildDependency>() {
                      public BuildPromotion createFrom(@NotNull final BuildDependency source) {
                        return source.getDependOn();
                      }
                    });
        }
      };
    }

    @NotNull
    public GraphFinder.LinkRetriever<BuildPromotion> getParents() {
      return new GraphFinder.LinkRetriever<BuildPromotion>() {
        @NotNull
        public List<BuildPromotion> getLinked(@NotNull final BuildPromotion item) {
          return CollectionsUtil.convertCollection(item.getDependedOnMe(), new Converter<BuildPromotion, BuildDependency>() {
                public BuildPromotion createFrom(@NotNull final BuildDependency source) {
                  return source.getDependent();
                }
              });
        }
      };
    }
  }

  private class BuildPromotionOrderedFinder extends GraphFinder<BuildPromotion> {
    public BuildPromotionOrderedFinder(@NotNull final BuildPromotionFinder finder) {
      super(finder, new BuildPromotionOrderSupportTraverser());
    }

    @Override
    protected void collectLinked(@NotNull final Set<BuildPromotion> result,
                                 @NotNull final Collection<BuildPromotion> toProcess,
                                 @NotNull final Collection<BuildPromotion> stopItems,
                                 final Long lookupLimit,
                                 @NotNull final LinkRetriever<BuildPromotion> linkRetriever,
                                 final boolean recursive) {
      if (!recursive) {
        throw new BadRequestException("Builds traversal is only supported in 'recursive:true' mode");
      }

      for (BuildPromotion item : toProcess) {
        if (stopItems.contains(item)) {
          result.add(item);
        } else {
          final List<BuildPromotion> linked = linkRetriever.getLinked(item);
          for (BuildPromotion promotion : linked) {
            result.add(promotion);
            if (stopItems.contains(item)) {
              break;
            }
          }
        }
      }
    }
  }

  private class BuildPromotionOrderSupportTraverser implements GraphFinder.Traverser<BuildPromotion> {
    @NotNull
    @Override
    public GraphFinder.LinkRetriever<BuildPromotion> getChildren() {
      return new GraphFinder.LinkRetriever<BuildPromotion>() {
        @NotNull
        @Override
        public List<BuildPromotion> getLinked(@NotNull final BuildPromotion item) {
          final SBuildType buildType = item.getBuildType();
          if (buildType == null) {
            return Collections.emptyList();
          }
          final SBuild associatedBuild = item.getAssociatedBuild();
          if (associatedBuild != null) {
            final List<OrderedBuild> buildsBefore = ((BuildTypeEx)buildType).getBuildTypeOrderedBuilds().getBuildsBefore(associatedBuild);
            //consider performance optimization by converting id to build only on actual retrieve
            return CollectionsUtil.convertAndFilterNulls(buildsBefore, new Converter<BuildPromotion, OrderedBuild>() {
              @Override
              public BuildPromotion createFrom(@NotNull final OrderedBuild source) {
                return myBuildPromotionManager.findPromotionById(source.getPromotionId());
              }
            });
          } else {
            final Branch branch = item.getBranch();
            if (branch == null) {
              return Collections.emptyList();
            }
            final List<OrderedBuild> buildsBefore = ((BuildTypeEx)buildType).getBuildTypeOrderedBuilds().getBuildsBefore(item, new Filter<String>() {
              @Override
              public boolean accept(@NotNull final String data) {
                return true;
              }
            });
            return CollectionsUtil.convertAndFilterNulls(buildsBefore, new Converter<BuildPromotion, OrderedBuild>() {
              @Override
              public BuildPromotion createFrom(@NotNull final OrderedBuild source) {
                return myBuildPromotionManager.findPromotionById(source.getPromotionId());
              }
            });
          }
        }
      };
    }

    @NotNull
    @Override
    public GraphFinder.LinkRetriever<BuildPromotion> getParents() {
      return new GraphFinder.LinkRetriever<BuildPromotion>() {
        @NotNull
        @Override
        public List<BuildPromotion> getLinked(@NotNull final BuildPromotion item) {
          final SBuildType buildType = item.getBuildType();
          if (buildType == null) {
            return Collections.emptyList();
          }
          final SBuild associatedBuild = item.getAssociatedBuild();
          if (associatedBuild != null) {
            final List<OrderedBuild> buildsBefore = ((BuildTypeEx)buildType).getBuildTypeOrderedBuilds().getBuildsAfter(associatedBuild);
            return CollectionsUtil.convertAndFilterNulls(buildsBefore, new Converter<BuildPromotion, OrderedBuild>() {
              @Override
              public BuildPromotion createFrom(@NotNull final OrderedBuild source) {
                return myBuildPromotionManager.findPromotionById(source.getPromotionId());
              }
            });
          } else {
            return Collections.emptyList();
          }
        }
      };
    }
  }
}
