/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 20.08.2014
 */
public class BuildPromotionFinder extends AbstractFinder<BuildPromotion> {  //todo: rework AbstractFinder to work with streamable collection of items, not serialized collections
  //DIMENSION_ID - id of a build or id of build promotion which will get associated build with the id
  public static final String PROMOTION_ID = BuildFinder.PROMOTION_ID;
  public static final String BUILD_TYPE = "buildType";
  public static final String PROJECT = "project";
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

  protected static final String NUMBER = "number";
  protected static final String STATUS = "status";
  protected static final String CANCELED = "canceled";
  protected static final String PINNED = "pinned";
  protected static final String RUNNING = "running";

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
    super(new String[]{DIMENSION_ID, PROMOTION_ID, PROJECT, AFFECTED_PROJECT, BUILD_TYPE, BRANCH, AGENT, USER, PERSONAL, STATE, PROPERTY,
      NUMBER, STATUS, CANCELED, PINNED,
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
    result.addHiddenDimensions(AGENT_NAME, RUNNING);
    return result;
  }

  @NotNull
  @Override
  public List<BuildPromotion> getAllItems() {
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
    return result;
  }

  @Nullable
  @Override
  protected BuildPromotion findSingleItem(@NotNull final Locator locator) {
    //see also getBuildId method
    if (locator.isSingleValue()) {
     // try build id first for compatibility reasons
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      final SBuild build = myBuildsManager.findBuildInstanceById(singleValueAsLong);
      if (build != null){
        return build.getBuildPromotion();
      }
      // assume it's promotion id
      return BuildFinder.getBuildPromotion(singleValueAsLong, myBuildPromotionManager);
    }

    Long promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID);
    if (promotionId == null){
      promotionId = locator.getSingleDimensionValueAsLong("promotionId"); //support TeamCity 8.0 dimension
    }
    if (promotionId != null) {
      return BuildFinder.getBuildPromotion(promotionId, myBuildPromotionManager);
    }

    final Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      final BuildPromotion buildPromotion = BuildFinder.getBuildPromotion(id, myBuildPromotionManager);
      if (!buildIdDiffersFromPromotionId(buildPromotion)){
        return buildPromotion;
      }
      final SBuild build = myBuildsManager.findBuildInstanceById(id);
      if (build != null){
        return build.getBuildPromotion();
      }
    }

    final String number = locator.getSingleDimensionValue(NUMBER);
    if (number != null) {
      final SBuildType buildType = myBuildTypeFinder.getBuildType(null, locator.getSingleDimensionValue(BUILD_TYPE));

      SBuild build = myBuildsManager.findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + number + "' in build configuration with id '" + buildType.getExternalId() + "'.");
      }
      return build.getBuildPromotion();
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
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<BuildPromotion> result =
      new MultiCheckerFilter<BuildPromotion>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

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

    final String branchLocatorValue = locator.getSingleDimensionValue(BRANCH);
    if (branchLocatorValue != null) {
      final BranchMatcher branchMatcher;
      try {
        branchMatcher = new BranchMatcher(branchLocatorValue);
      } catch (LocatorProcessException e) {
        throw new LocatorProcessException("Invalid sub-locator '" + BRANCH + "': " + e.getMessage());
      }
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return branchMatcher.matches(item);
        }
      });
    }

    //compatibility support
    final String tags = locator.getSingleDimensionValue("tags");
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

    final String tag = locator.getSingleDimensionValue("tag");
    if (tag != null) {
      if (!tag.startsWith("format:extended")) {
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            return item.getTags().contains(tag);
          }
        });
      } else {
        //unofficial experimental support for "tag:(format:regexp,value:.*)" tag specification
        //todo: locator parsing logic should be moved to build locator parsing
        result.add(new FilterConditionChecker<BuildPromotion>() {
          public boolean isIncluded(@NotNull final BuildPromotion item) {
            try {
              final Locator tagsLocator = new Locator(tag);

              if (!isTagsMatchLocator(item.getTags(), tagsLocator)) {
                return false;
              }
              final Set<String> unusedDimensions = tagsLocator.getUnusedDimensions();
              if (unusedDimensions.size() > 0) {
                throw new BadRequestException("Unknown dimensions in locator 'tag': " + unusedDimensions);
              }
            } catch (LocatorProcessException e) {
              throw new BadRequestException("Invalid locator 'tag': " + e.getMessage(), e);
            }
            return true;
          }
        });
      }
    }

    final String compatibleAagentLocator = locator.getSingleDimensionValue("compatibleAgent"); //experimental, only for queued builds
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

    final Long compatibleAgentsCount = locator.getSingleDimensionValueAsLong("compatibleAgentsCount"); //experimental, only for queued builds
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

    return result;
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

    final Boolean canceled = locator.getSingleDimensionValueAsBoolean(CANCELED);
    if (canceled != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(canceled, item.getCanceledInfo() != null);
        }
      });
    }

    //for compatibility, use "state:running" instead
    final Boolean running = locator.getSingleDimensionValueAsBoolean(RUNNING);
    ;
    if (running != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(running, !item.isFinished());
        }
      });
    }

    final Boolean pinned = locator.getSingleDimensionValueAsBoolean(PINNED);
    if (pinned != null) {
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(pinned, !item.isPinned());
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

    //todo: filter on gettings builds; more options (all times); also for buildPromotion
    final String sinceBuild = locator.getSingleDimensionValue("sinceBuild");
    final Date sinceDate = DataProvider.parseDate(locator.getSingleDimensionValue("sinceDate"));
    if (sinceBuild != null || sinceDate != null) {
      final RangeLimit rangeLimit = new RangeLimit(getBuildId(sinceBuild), sinceDate);
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return rangeLimit.before(item);
        }
      });
    }

    final String untilBuild = locator.getSingleDimensionValue("untilBuild");
    final Date untilDate = DataProvider.parseDate(locator.getSingleDimensionValue("untilDate"));
    if (untilBuild != null || untilDate != null) {
      final RangeLimit rangeLimit = new RangeLimit(getBuildId(untilBuild), untilDate);
      result.add(new FilterConditionChecker<SBuild>() {
        public boolean isIncluded(@NotNull final SBuild item) {
          return !rangeLimit.before(item);
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

  public class RangeLimit {
    @Nullable private final Long myBuildId;
    @Nullable private final Date myStartDate;

    public RangeLimit(@Nullable final Long buildId, @Nullable final Date startDate) {
      myBuildId = buildId;
      myStartDate = startDate;
    }

    public boolean before(@NotNull SBuild build) {
      if (myBuildId != null) {
        return myBuildId < build.getBuildId();
      }
      if (myStartDate != null) {
        return myStartDate.before(build.getStartDate());
      }
      return false;
    }
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
  protected List<BuildPromotion> getPrefilteredItems(@NotNull Locator locator) { //todo: highly ineffective when finished builds are included
    final ArrayList<BuildPromotion> result = new ArrayList<BuildPromotion>();

    final String stateDimension = locator.getSingleDimensionValue(STATE);
    if (stateDimension == null) {
      throw new BadRequestException("Only single item locators or locators with '" + STATE + "' dimension are supported for build promotions");
    }

    final Locator stateLocator = new Locator(stateDimension, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, STATE_QUEUED, STATE_RUNNING, STATE_FINISHED);

    if ("any".equals(stateLocator.getSingleValue()) || STATE_QUEUED.equals(stateDimension) ||
        (!stateLocator.isSingleValue() && FilterUtil.isIncludedByBooleanFilter(stateLocator.getSingleDimensionValueAsBoolean(STATE_QUEUED), true))) {
      result.addAll(CollectionsUtil.convertCollection(myBuildQueue.getItems(), new Converter<BuildPromotion, SQueuedBuild>() {
        public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
          return source.getBuildPromotion();
        }
      }));
    }

    if ("any".equals(stateLocator.getSingleValue()) || STATE_RUNNING.equals(stateDimension) ||
        (!stateLocator.isSingleValue() && FilterUtil.isIncludedByBooleanFilter(stateLocator.getSingleDimensionValueAsBoolean(STATE_RUNNING), true))) {
      result.addAll(CollectionsUtil.convertCollection(myBuildsManager.getRunningBuilds(), new Converter<BuildPromotion, SRunningBuild>() {
        public BuildPromotion createFrom(@NotNull final SRunningBuild source) {
          return source.getBuildPromotion();
        }
      }));
    }

    if ("any".equals(stateLocator.getSingleValue()) || STATE_FINISHED.equals(stateDimension) ||
        (!stateLocator.isSingleValue() && FilterUtil.isIncludedByBooleanFilter(stateLocator.getSingleDimensionValueAsBoolean(STATE_FINISHED), true))) {
      throw new BadRequestException("Getting finished builds is not supported for build promotions locator");
      /*
      result.addAll(CollectionsUtil.convertCollection(myBuildHistory.getEntries(true), new Converter<BuildPromotion, SFinishedBuild>() {
        public BuildPromotion createFrom(@NotNull final SFinishedBuild source) {
          return source.getBuildPromotion();
        }
      }));
      */
    }
    stateLocator.checkLocatorFullyProcessed();

    return result;
  }

  public static boolean buildIdDiffersFromPromotionId(@NotNull final BuildPromotion buildPromotion) {
    return buildPromotion.getAssociatedBuildId() != null && buildPromotion.getId() != buildPromotion.getAssociatedBuildId();
  }
}
