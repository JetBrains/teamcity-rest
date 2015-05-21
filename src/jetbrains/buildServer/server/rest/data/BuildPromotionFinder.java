/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jetbrains.buildServer.server.rest.data.build.TagFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.BuildDependency;
import jetbrains.buildServer.serverSide.impl.BuildPromotionManagerEx;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 20.08.2014
 */
public class BuildPromotionFinder extends AbstractFinder<BuildPromotion> {
  //DIMENSION_ID - id of a build or id of build promotion which will get associated build with the id
  public static final String PROMOTION_ID = BuildFinder.PROMOTION_ID;
  public static final String BUILD_TYPE = "buildType";
  public static final String PROJECT = "project"; //todo: BuildFinder treats "project" as "affectedProject" thus this behavior is differet from BuildFinder
  private static final String AFFECTED_PROJECT = "affectedProject";
  public static final String AGENT = "agent";
  public static final String AGENT_NAME = "agentName";
  public static final String PERSONAL = "personal";
  public static final String USER = "user";
  protected static final String BRANCH = "branch";
  protected static final String PROPERTY = "property";

  public static final String STATE = "state";
  public static final String STATE_QUEUED = "queued";
  public static final String STATE_RUNNING = "running";
  public static final String STATE_FINISHED = "finished";
  protected static final String STATE_ANY = "any";

  protected static final String NUMBER = "number";
  protected static final String STATUS = "status";
  protected static final String CANCELED = "canceled";
  protected static final String PINNED = "pinned";
  protected static final String RUNNING = "running";
  protected static final String SNAPSHOT_DEP = "snapshotDependency";
  protected static final String COMPATIBLE_AGENTS_COUNT = "compatibleAgentsCount";
  protected static final String TAGS = "tags";
  protected static final String TAG = "tag";
  protected static final String COMPATIBLE_AGENT = "compatibleAgent";
  protected static final String SINCE_BUILD = "sinceBuild";
  protected static final String SINCE_DATE = "sinceDate";
  protected static final String UNTIL_BUILD = "untilBuild";
  protected static final String UNTIL_DATE = "untilDate";
  public static final String BY_PROMOTION = "byPromotion";  //used in BuildFinder
  public static final String EQUIVALENT = "equivalent"; /*experimental*/
  public static final BuildPromotionComparator BUILD_PROMOTIONS_COMPARATOR = new BuildPromotionComparator();

  private final BuildPromotionManager myBuildPromotionManager;
  private final BuildQueue myBuildQueue;
  private final BuildsManager myBuildsManager;
  private final BuildHistory myBuildHistory;
  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final UserFinder myUserFinder;
  private final AgentFinder myAgentFinder;

  @NotNull
  public static String getLocator(@NotNull final BuildPromotion buildPromotion) {
    final Long associatedBuildId = buildPromotion.getAssociatedBuildId();
    if (associatedBuildId == null) {
      return Locator.getStringLocator(DIMENSION_ID, String.valueOf(buildPromotion.getId())); //assume this is a queued build, so buildId==promotionId
    }
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(associatedBuildId));
  }

  public BuildPromotionFinder(final BuildPromotionManager buildPromotionManager,
                              final BuildQueue buildQueue,
                              final BuildsManager buildsManager,
                              final BuildHistory buildHistory,
                              final ProjectFinder projectFinder,
                              final BuildTypeFinder buildTypeFinder,
                              final UserFinder userFinder,
                              final AgentFinder agentFinder) {
    super(new String[]{DIMENSION_ID, PROMOTION_ID, PROJECT, AFFECTED_PROJECT, BUILD_TYPE, BRANCH, AGENT, USER, PERSONAL, STATE, TAG, PROPERTY, COMPATIBLE_AGENT,
      NUMBER, STATUS, CANCELED, PINNED, DIMENSION_LOOKUP_LIMIT,
      Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, PagerData.START, PagerData.COUNT});
    myBuildPromotionManager = buildPromotionManager;
    myBuildQueue = buildQueue;
    myBuildsManager = buildsManager;
    myBuildHistory = buildHistory;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myUserFinder = userFinder;
    myAgentFinder = agentFinder;
  }

  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = super.createLocator(locatorText, locatorDefaults);
    result.addHiddenDimensions(AGENT_NAME, RUNNING, COMPATIBLE_AGENTS_COUNT, SNAPSHOT_DEP, TAGS, SINCE_BUILD, SINCE_DATE, UNTIL_BUILD, UNTIL_DATE,
                               STATE_RUNNING //compatibility with pre-9.1
    );
    result.addIgnoreUnusedDimensions(BY_PROMOTION);
    result.addIgnoreUnusedDimensions(EQUIVALENT);
    return result;
  }

  @Nullable
  @Override
  public ItemHolder<BuildPromotion> getAllItems() {
    return null;
    /*
    final ArrayList<BuildPromotion> result = new ArrayList<BuildPromotion>();
    result.addAll(CollectionsUtil.convertCollection(myBuildQueue.getItems(), new Converter<BuildPromotion, SQueuedBuild>() {
      public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
        return source.getBuildPromotion();
      }
    }));
    result.addAll(CollectionsUtil.convertCollection(myBuildsManager.getRunningBuilds(), new Converter<BuildPromotion, SRunningBuild>() {
      public BuildPromotion createFrom(@NotNull final SRunningBuild source) {
        return source.getBuildPromotion();
      }
    }));
    result.addAll(CollectionsUtil.convertCollection(myBuildHistory.getEntries(true), new Converter<BuildPromotion, SFinishedBuild>() {
      public BuildPromotion createFrom(@NotNull final SFinishedBuild source) {
        return source.getBuildPromotion();
      }
    }));
    return getItemHolder(result);
    */
  }

  @Nullable
  @Override
  protected BuildPromotion findSingleItem(@NotNull final Locator locator) {
    //see also getBuildId method
    if (locator.isSingleValue()) {
      try {
        // try build id first for compatibility reasons
        @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
        final SBuild build = myBuildsManager.findBuildInstanceById(singleValueAsLong);
        if (build != null){
          return build.getBuildPromotion();
        }
        // assume it's promotion id
        try {
          return BuildFinder.getBuildPromotion(singleValueAsLong, myBuildPromotionManager);
        } catch (NotFoundException e) {
          //promotion not found. Assume it's a build number
          return null;
        }
      } catch (LocatorProcessException e) {
      // got exception, probbaly not a parsable number, it can be a build number then. Cannot find a build by build number only, so delegate to scanning
        return null;
      }
    }

    Long promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID);
    if (promotionId == null){
      promotionId = locator.getSingleDimensionValueAsLong("promotionId"); //support TeamCity 8.0 dimension
    }
    if (promotionId != null) {
      return checkBuildType(BuildFinder.getBuildPromotion(promotionId, myBuildPromotionManager), locator);
    }

    final Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      final BuildPromotion buildPromotion = BuildFinder.getBuildPromotion(id, myBuildPromotionManager);
      if (!buildIdDiffersFromPromotionId(buildPromotion)){
        return checkBuildType(buildPromotion, locator);
      }
      final SBuild build = myBuildsManager.findBuildInstanceById(id);
      if (build != null){
        return checkBuildType(build.getBuildPromotion(), locator);
      }
    }

    final String number = locator.getSingleDimensionValue(NUMBER);
    if (number != null) {
      final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeLocator != null) {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);
        SBuild build = myBuildsManager.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
        if (build == null) {
          throw new NotFoundException("No build can be found by number '" + number + "' in build configuration with id '" + buildType.getExternalId() + "'.");
        }
        return build.getBuildPromotion();
      }
      //search by scanning // throw new BadRequestException("Cannot search build by number without build type specified");
    }

    return null;
  }

  /**
   * Utility method to get id from the locator even if there is no such build
   * Should match findSingleItem method logic
   */
  @Nullable
  private Long getBuildId(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      return locator.getSingleValueAsLong();
    }
    return locator.getSingleDimensionValueAsLong(DIMENSION_ID);
  }

  @NotNull
  @Override
  protected AbstractFilter<BuildPromotion> getFilter(final Locator locator) {

    Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    if (countFromFilter == null) {
      //limiting to 100 builds by default
      countFromFilter = (long)Constants.getDefaultPageItemsCount();
    }
    final MultiCheckerFilter<BuildPromotion> result =
      new MultiCheckerFilter<BuildPromotion>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter.intValue(),
                                             locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT));

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

    final String branchLocatorValue = locator.getSingleDimensionValue(BRANCH);
    // should filter by branch even if not specified in the locator
    final BranchMatcher branchMatcher;
    try {
      branchMatcher = new BranchMatcher(branchLocatorValue);
    } catch (LocatorProcessException e) {
      throw new LocatorProcessException("Invalid sub-locator '" + BRANCH + "': " + e.getMessage());
    }
    if (!branchMatcher.matchesAnyBranch()) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return branchMatcher.matches(item);
        }
      });
    }

    if (locator.isSingleValue()) {
      //can only be a build number
      final String buildNumber = locator.getSingleValue();
      if (buildNumber != null) {
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            final SBuild associatedBuild = item.getAssociatedBuild();
            return associatedBuild!= null && buildNumber.equals(associatedBuild.getBuildNumber());
          }
        });
        return result;
      }
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    SProject project = null;
    if (projectLocator != null) {
      project = myProjectFinder.getProject(projectLocator);
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
      affectedProject = myProjectFinder.getProject(affectedProjectLocator);
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
      final SBuildType buildType = myBuildTypeFinder.getBuildType(affectedProject, buildTypeLocator);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return buildType.equals(item.getParentBuildType());
        }
      });
    }

    final String agentLocator = locator.getSingleDimensionValue(AGENT);
    if (agentLocator != null) {
      final List<SBuildAgent> agents = myAgentFinder.getItems(agentLocator).myEntries;
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          if (build != null) {
            return agents.contains(build.getAgent());
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

    final String tag = locator.getSingleDimensionValue(TAG);
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

    final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL, false);
    if (personal != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return FilterUtil.isIncludedByBooleanFilter(personal, item.isPersonal());
        }
      });
    }

    final String userDimension = locator.getSingleDimensionValue(USER);
    if (userDimension != null) {
      final SUser user = myUserFinder.getUser(userDimension);
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

    final String property = locator.getSingleDimensionValue(PROPERTY);
    if (property != null) {
      final ParameterCondition parameterCondition = ParameterCondition.create(property);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return parameterCondition.matches(((BuildPromotionEx)item).getParametersProvider()); //TeamCity open API issue
        }
      });
    }

    if (locator.getUnusedDimensions().contains(SNAPSHOT_DEP)) { //performance optimization: do not filter if already processed
      final String snapshotDepDimension = locator.getSingleDimensionValue(SNAPSHOT_DEP);
      if (snapshotDepDimension != null) {
        final List<BuildPromotion> snapshotRelatedBuilds = getSnapshotRelatedBuilds(snapshotDepDimension);
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return snapshotRelatedBuilds.contains(item);
          }
        });
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

    final Boolean canceled = locator.getSingleDimensionValueAsBoolean(CANCELED, false);
    if (canceled != null) {
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          return build == null || FilterUtil.isIncludedByBooleanFilter(canceled, build.getCanceledInfo() != null);
        }
      });
    }

    return result;
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

    return createStateLocator(STATE_FINISHED); // default to only finished builds
  }

  @NotNull
  private MultiCheckerFilter<SBuild> getBuildFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SBuild> result = new MultiCheckerFilter<SBuild>(null, null, null);

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

    //compatibility, use "agent" locator instead
    final String agentName = locator.getSingleDimensionValue(AGENT_NAME);
    if (agentName != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return agentName.equals(item.getAgent().getName());
        }
      });
    }

    //todo: filter on gettings builds; more options (all times); also for buildPromotion, use "since:(build:(),start:(build:(),date:()),queued:(build:(),date:()),finish:(build:(),date:()))"
    final String sinceBuild = locator.getSingleDimensionValue(SINCE_BUILD);
    if (sinceBuild != null) {
      final Long buildId = getBuildId(sinceBuild);
      if (buildId != null) {
        result.add(new FilterConditionChecker<SBuild>() {
          public boolean isIncluded(@NotNull final SBuild item) {
            return buildId < item.getBuildId();
          }
        });
      }
    }

    final Date sinceDate = DataProvider.parseDate(locator.getSingleDimensionValue(SINCE_DATE));
    if (sinceDate != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return sinceDate.before(item.getStartDate());
        }
      });
    }

    final String untilBuild = locator.getSingleDimensionValue(UNTIL_BUILD);
    if (untilBuild != null) {
      final Long buildId = getBuildId(untilBuild);
      if (buildId != null) {
        result.add(new FilterConditionChecker<SBuild>() {
          public boolean isIncluded(@NotNull final SBuild item) {
            return !(buildId < item.getBuildId());
          }
        });
      }
    }

    final Date untilDate = DataProvider.parseDate(locator.getSingleDimensionValue(UNTIL_DATE));
    if (untilDate != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return !(untilDate.before(item.getStartDate()));
        }
      });
    }

    return result;
  }

  @Nullable
  private Long getBuildId(@Nullable final String buildLocator) {
    if (buildLocator == null) {
      return null;
    }
    final Long buildId = getBuildId(new Locator(buildLocator));
    if (buildId != null) {
      return buildId;
    }
    return getItem(buildLocator).getId();
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


  @Override
  protected ItemHolder<BuildPromotion> getPrefilteredItems(@NotNull Locator locator) {
    final String equivalent = locator.getSingleDimensionValue(EQUIVALENT);
    if (equivalent != null) {
      final BuildPromotion build = getItem(equivalent);
      final List<BuildPromotionEx> result = ((BuildPromotionManagerEx)myBuildPromotionManager).getStartedEquivalentPromotions(build);
      final List<BuildPromotion> convertedResult = new ArrayList<BuildPromotion>(result.size());
      for (BuildPromotionEx item : result) {
        convertedResult.add(item);
      }
      Collections.sort(convertedResult, BUILD_PROMOTIONS_COMPARATOR);
      return getItemHolder(convertedResult);
    }

    final String snapshotDepDimension = locator.getSingleDimensionValue(SNAPSHOT_DEP);
    if (snapshotDepDimension != null) {
      return getItemHolder(getSnapshotRelatedBuilds(snapshotDepDimension));
    }

    final ArrayList<BuildPromotion> result = new ArrayList<BuildPromotion>();

    Locator stateLocator = getStateLocator(locator);

    if (isStateIncluded(stateLocator, STATE_QUEUED)) {
      result.addAll(CollectionsUtil.convertCollection(myBuildQueue.getItems(), new Converter<BuildPromotion, SQueuedBuild>() {
        public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
          return source.getBuildPromotion();
        }
      }));
    }

    if (isStateIncluded(stateLocator, STATE_RUNNING)) {
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
          affectedProject = myProjectFinder.getProject(affectedProjectLocator);
        }
        buildType = myBuildTypeFinder.getBuildType(affectedProject, buildTypeLocator);
      }

      if (buildType != null) {
        final BuildQueryOptions options = new BuildQueryOptions();
        options.setBuildTypeId(buildType.getBuildTypeId());//todo add javadoc which id is this

        final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL);
        if ((personal == null && locator.getSingleDimensionValue(PERSONAL) != null) ||
            (personal != null && personal)) {

          final String userDimension = locator.getSingleDimensionValue(USER);
          options.setIncludePersonal(true, userDimension == null ? null : myUserFinder.getUser(userDimension));
        } else {
          options.setIncludePersonal(false, null);
        }

        final Boolean canceled = locator.getSingleDimensionValueAsBoolean(CANCELED);
        if ((canceled == null && locator.getSingleDimensionValue(CANCELED) != null) ||
            (canceled != null && canceled)) {
          options.setIncludeCanceled(true);
        } else {
          options.setIncludeCanceled(false);
        }

        final String branchLocatorValue = locator.getSingleDimensionValue(BRANCH);
        final BranchMatcher branchMatcher;
        try {
          branchMatcher = new BranchMatcher(branchLocatorValue);
        } catch (LocatorProcessException e) {
          throw new LocatorProcessException("Invalid sub-locator '" + BRANCH + "': " + e.getMessage());
        }

        if (branchMatcher.matchesAnyBranch()) {
          options.setMatchAllBranches(true);
        } else {
          final String singleBranch = branchMatcher.getSingleBranchIfNotDefault();
          if (singleBranch != null) {
            options.setBranch(singleBranch);
          } else {
            locator.markUnused(BRANCH);
          }
        }

        options.setIncludeRunning(false); //running builds are retrieved separately and appear before finished ones
        //options.setOrderByChanges(true); //todo: add test, check with 9.0

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
      } else {
        //TeamCity API: allow myBuildsManager.processBuilds work without build type, add more options
        finishedBuilds = new ItemHolder<BuildPromotion>() {
          public boolean process(@NotNull final ItemProcessor<BuildPromotion> processor) {
            myBuildHistory.processEntries(new ItemProcessor<SFinishedBuild>() {
              public boolean processItem(final SFinishedBuild item) {
                return processor.processItem(item.getBuildPromotion());
              }
            });
            return false;
          }
        };
      }
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
      final String buildTypeDimension = locator.getSingleDimensionValue(BuildPromotionFinder.BUILD_TYPE);
      if (buildTypeDimension != null) {
        if (!buildType.getInternalId().equals(myBuildTypeFinder.getItem(buildTypeDimension).getInternalId())) {
          throw new BadRequestException("Context build type is not the same as build type in '" + BuildPromotionFinder.BUILD_TYPE + "' dimention");
        }
      } else {
        return locator.setDimensionIfNotPresent(BuildPromotionFinder.BUILD_TYPE, BuildTypeFinder.getLocator(buildType)).getStringRepresentation();
      }
    }
    return locator.getStringRepresentation();
  }

  @NotNull
  private BuildPromotion checkBuildType(@NotNull final BuildPromotion buildPromotion, @NotNull final Locator locator) {
    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator == null) return buildPromotion;

    final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeLocator);

    if (buildType.equals(buildPromotion.getParentBuildType())) return buildPromotion;

    throw new NotFoundException("Found build with id " + buildPromotion.getId() + " does not belong to the build type with id '" + buildType.getExternalId() +"'");
  }

  @NotNull
  private List<BuildPromotion> getSnapshotRelatedBuilds(@NotNull final String snapshotDepDimension) {
    Locator snapshotDepLocator = new Locator(snapshotDepDimension, "from", "to", "recursive", "includeInitial");
    Boolean recursive = snapshotDepLocator.getSingleDimensionValueAsBoolean("recursive", true);
    if (recursive == null) recursive = true;

    Boolean includeOriginal = snapshotDepLocator.getSingleDimensionValueAsBoolean("includeInitial", false);
    if (includeOriginal == null) includeOriginal = false;

    ArrayList<BuildPromotion> resultTo = new ArrayList<BuildPromotion>();
    final String toBuildDimension = snapshotDepLocator.getSingleDimensionValue("to");
    if (toBuildDimension != null) {
      final List<BuildPromotion> toBuilds = getItems(toBuildDimension).myEntries;
      if (includeOriginal) {
        resultTo.addAll(toBuilds);
      }
      if (recursive) {
        for (BuildPromotion toBuild : toBuilds) {
          resultTo.addAll(toBuild.getAllDependencies());
        }
      } else {
        final Set<BuildPromotion> alldependencyBuilds = new TreeSet<BuildPromotion>();
        for (BuildPromotion toBuild : toBuilds) {
          alldependencyBuilds.addAll(CollectionsUtil.convertCollection(toBuild.getDependencies(), new Converter<BuildPromotion, BuildDependency>() {
            public BuildPromotion createFrom(@NotNull final BuildDependency source) {
              return source.getDependOn();
            }
          }));
        }
        resultTo.addAll(alldependencyBuilds);
      }
    }

    ArrayList<BuildPromotion> resultFrom = new ArrayList<BuildPromotion>();
    final String fromBuildDimension = snapshotDepLocator.getSingleDimensionValue("from");
    if (fromBuildDimension != null) {
      final List<BuildPromotion> fromBuilds = getItems(fromBuildDimension).myEntries;
      if (includeOriginal) {
        resultFrom.addAll(fromBuilds);
      }
      final Collection<BuildPromotion> allDependingOn = getAllDependOn(fromBuilds, recursive);
      resultFrom.addAll(allDependingOn);
    }

    snapshotDepLocator.checkLocatorFullyProcessed();

    ArrayList<BuildPromotion> result = resultTo;
    if (!result.isEmpty() && !resultFrom.isEmpty()) {
      result = new ArrayList<BuildPromotion>(CollectionsUtil.intersect(result, resultFrom));
    } else {
      result = !result.isEmpty() ? result : resultFrom;
    }
    Collections.sort(result, BUILD_PROMOTIONS_COMPARATOR);
    return result; //todo: patch branch locator, personal, etc.???
  }

  private Collection<BuildPromotion> getAllDependOn(final List<BuildPromotion> items, boolean recursive) {
    final Set<BuildPromotion> processed = new TreeSet<BuildPromotion>();
    final List<BuildPromotion> toProcess = new ArrayList<BuildPromotion>();
    for (BuildPromotion item : items) {
      toProcess.addAll(getDependingPromotions(item));
    }
    while (!toProcess.isEmpty()) {
      final List<BuildPromotion> currentBatch = new ArrayList<BuildPromotion>(toProcess);
      toProcess.clear();
      for (BuildPromotion item : currentBatch) {
        if (!processed.contains(item)) {
          processed.add(item);
          if (recursive) {
            toProcess.addAll(getDependingPromotions(item));
          }
        }
      }
    }
    return processed;
  }

  @NotNull
  private List<BuildPromotion> getDependingPromotions(@NotNull final BuildPromotion fromBuild) {
    return CollectionsUtil.convertCollection(fromBuild.getDependedOnMe(), new Converter<BuildPromotion, BuildDependency>() {
      public BuildPromotion createFrom(@NotNull final BuildDependency source) {
        return source.getDependent();
      }
    });
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
        throw new BadRequestException("Unsupported value of '" + STATE + "' dimension: '" + stateDimension + "'. Should be one of the build states or '" + STATE_ANY + "'");
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
              return -Long.valueOf(b1.getBuildId()).compareTo(b2.getBuildId());
            }
            return 1;
          }
          if (b2.isFinished()) {
            return -1;
          }
          return -Long.valueOf(b1.getBuildId()).compareTo(b2.getBuildId());
        }
        return 1;
      }
      return -1;
    }
  }
}
