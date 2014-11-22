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

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
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
  public static final String PROMOTION_ID = BuildFinder.PROMOTION_ID;
  public static final String BUILD_TYPE = "buildType";
  public static final String PROJECT = "project";
  public static final String AGENT = "agent";
  public static final String PERSONAL = "personal";
  public static final String USER = "user";

  public static final String STATE = "state";
  public static final String STATE_QUEUED = "queued";
  public static final String STATE_RUNNING = "running";
  public static final String STATE_FINISHED = "finished";

  private final BuildPromotionManager myBuildPromotionManager;
  private final BuildQueue myBuildQueue;
  private final BuildsManager myBuildsManager;
  private final BuildHistory myBuildHistory;
  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final UserFinder myUserFinder;
  private final AgentFinder myAgentFinder;
  private final DataProvider myDataProvider;

  public BuildPromotionFinder(final BuildPromotionManager buildPromotionManager,
                              final BuildQueue buildQueue,
                              final BuildsManager buildsManager,
                              final BuildHistory buildHistory,
                              final ProjectFinder projectFinder,
                              final BuildTypeFinder buildTypeFinder,
                              final UserFinder userFinder,
                              final AgentFinder agentFinder,
                              final DataProvider dataProvider) {
    super(new String[]{PROMOTION_ID, PROJECT, BUILD_TYPE, AGENT, USER, PERSONAL, STATE, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, PagerData.START, PagerData.COUNT});
    myBuildPromotionManager = buildPromotionManager;
    myBuildQueue = buildQueue;
    myBuildsManager = buildsManager;
    myBuildHistory = buildHistory;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myUserFinder = userFinder;
    myAgentFinder = agentFinder;
    myDataProvider = dataProvider;
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
    if (locator.isSingleValue()) {
      // assume it's promotion id
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      locator.checkLocatorFullyProcessed();
      return BuildFinder.getBuildPromotion(singleValueAsLong, myBuildPromotionManager);
    }

    Long id = locator.getSingleDimensionValueAsLong(PROMOTION_ID);
    if (id != null) {
      locator.checkLocatorFullyProcessed();
      return BuildFinder.getBuildPromotion(id, myBuildPromotionManager);
    }

    return null;
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
          return internalProject.equals(item.getBuildType().getProject());
        }
      });
    }

    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      final SBuildType buildType = myBuildTypeFinder.getBuildType(project, buildTypeLocator);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          return buildType.equals(item.getParentBuildType());
        }
      });
    }

    final String agentLocator = locator.getSingleDimensionValue(AGENT);
    if (agentLocator != null) {
      final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
      result.add(new FilterConditionChecker<BuildPromotion>() {
        public boolean isIncluded(@NotNull final BuildPromotion item) {
          final SBuild build = item.getAssociatedBuild();
          if (build != null) {
            return agent.equals(build.getAgent());
          }

          final SQueuedBuild queuedBuild = item.getQueuedBuild(); //for queued build using compatible agents
          if (queuedBuild != null) {
            return queuedBuild.getCompatibleAgents().contains(agent);
          }
          return false;
        }
      });
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

    return result;
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
