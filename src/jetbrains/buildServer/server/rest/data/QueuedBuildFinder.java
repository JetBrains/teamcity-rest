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

import com.google.common.collect.ComparisonChain;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.TreeSet;

/**
 * @author Yegor.Yarko
 *         Date: 21.12.13
 */
@LocatorResource(value = LocatorName.BUILD_QUEUE,
    extraDimensions = {FinderImpl.DIMENSION_ID, PagerData.START, PagerData.COUNT, AbstractFinder.DIMENSION_ITEM},
    baseEntity = "Build",
    examples = {
        "`buildType:<buildTypeLocator>` — find queued builds under build configuration found by buildTypeLocator.",
        "`user:<userLocator>` — find queued builds started by user found by userLocator."
    }
)
public class QueuedBuildFinder extends AbstractFinder<SQueuedBuild> {
  @LocatorDimension(BuildFinder.PROMOTION_ID) public static final String PROMOTION_ID = BuildFinder.PROMOTION_ID;
  @LocatorDimension(value = "buildType", format = LocatorName.BUILD_TYPE, notes = "Build type locator.")
  public static final String BUILD_TYPE = "buildType";
  @LocatorDimension(value = "project", format = LocatorName.PROJECT, notes = "Project locator.")
  public static final String PROJECT = "project";
  @LocatorDimension(value = "pool", format = LocatorName.AGENT_POOL, notes = "Agent pool locator.")
  public static final String POOL = "pool";
  @LocatorDimension(value = "agent", format = LocatorName.AGENT, notes = "Agent locator.")
  public static final String AGENT = "agent";
  @LocatorDimension(value = "personal", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is personal.")
  public static final String PERSONAL = "personal";
  @LocatorDimension(value = "user", format = LocatorName.USER, notes = "User locator.")
  public static final String USER = "user";

  private final BuildQueue myBuildQueue;
  private final ProjectFinder myProjectFinder;
  private final BuildTypeFinder myBuildTypeFinder;
  private final UserFinder myUserFinder;
  private final AgentFinder myAgentFinder;
  private final AgentPoolFinder myAgentPoolFinder;
  private final BuildPromotionManager myBuildPromotionManager;
  private final BuildsManager myBuildsManager;

  public QueuedBuildFinder(final BuildQueue buildQueue,
                           final ProjectFinder projectFinder,
                           final BuildTypeFinder buildTypeFinder,
                           final UserFinder userFinder,
                           final AgentFinder agentFinder,
                           AgentPoolFinder agentPoolFinder, final BuildPromotionManager buildPromotionManager,
                           final BuildsManager buildsManager) {
    super(DIMENSION_ID, PROMOTION_ID, PROJECT, POOL, BUILD_TYPE, AGENT, USER, PERSONAL, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    setHiddenDimensions(DIMENSION_LOOKUP_LIMIT, "compatibleAgent", "compatibleAgentsCount");
    myBuildQueue = buildQueue;
    myProjectFinder = projectFinder;
    myBuildTypeFinder = buildTypeFinder;
    myUserFinder = userFinder;
    myAgentPoolFinder = agentPoolFinder;
    myAgentFinder = agentFinder;
    myBuildPromotionManager = buildPromotionManager;
    myBuildsManager = buildsManager;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final SQueuedBuild sQueuedBuild) {
    return QueuedBuildFinder.getLocator(sQueuedBuild);
  }

  @NotNull
  public static String getLocator(@NotNull final SQueuedBuild build) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(build.getBuildPromotion().getId()));
  }

  @NotNull
  public TreeSet<SQueuedBuild> createContainerSet() {
    return new TreeSet<>(new Comparator<SQueuedBuild>() {
      @Override
      public int compare(final SQueuedBuild o1, final SQueuedBuild o2) {
        return ComparisonChain.start()
                              .compare(o1.getItemId(), o2.getItemId())
                              .result();
      }
    });
  }

  @NotNull
  @Override
  public ItemHolder<SQueuedBuild> getPrefilteredItems(@NotNull final Locator locator) {
    final String poolLocator = locator.getSingleDimensionValue(POOL);
    if (poolLocator != null) {
      AgentPool pool = myAgentPoolFinder.getItem(poolLocator);
      return getItemHolder(((BuildQueueEx)myBuildQueue).getItemsByPool(pool.getAgentPoolId()));
    }

    return getItemHolder(myBuildQueue.getItems());
  }

  @Override
  public SQueuedBuild findSingleItem(@NotNull final Locator locator) {

    if (locator.isSingleValue()) {
     // assume it's promotion id
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      return getQueuedBuildByPromotionId(singleValueAsLong);
    }

    Long promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID); //handling pre-9.0 URLs
    if (promotionId == null) {
      promotionId = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    }
    if (promotionId != null) {
      return getQueuedBuildByPromotionId(promotionId);
    }

   return null;
  }

  private SQueuedBuild getQueuedBuildByPromotionId(final Long id) {
    final BuildPromotion buildPromotion = BuildFinder.getBuildPromotion(id, myBuildPromotionManager);
    final SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild == null){
      throw new NotFoundException("No queued build with id '" + buildPromotion.getId() + "' can be found (build already started or finished?).");
    }
    return queuedBuild;
  }

  @NotNull
  @Override
  public ItemFilter<SQueuedBuild> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SQueuedBuild> result = new MultiCheckerFilter<SQueuedBuild>();

    final String projectLocator = locator.getSingleDimensionValue(PROJECT);
    SProject project = null;
    if (projectLocator != null) {
      project = myProjectFinder.getItem(projectLocator);
      final SProject internalProject = project;
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return internalProject.equals(item.getBuildType().getProject());
        }
      });
    }


    final String buildTypeLocator = locator.getSingleDimensionValue(BUILD_TYPE);
    if (buildTypeLocator != null) {
      final SBuildType buildType = myBuildTypeFinder.getBuildType(project, buildTypeLocator, false);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return buildType.equals(item.getBuildPromotion().getParentBuildType());
        }
      });
    }

    final String agentLocator = locator.getSingleDimensionValue(AGENT);
    if (agentLocator != null) {
      final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return agent.equals(item.getBuildAgent());
        }
      });
    }

    final String compatibleAagentLocator = locator.getSingleDimensionValue("compatibleAgent"); //experimental
    if (compatibleAagentLocator != null) {
      final SBuildAgent agent = myAgentFinder.getItem(compatibleAagentLocator);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return item.getCanRunOnAgents().contains(agent);
        }
      });
    }

    final Long compatibleAgentsCount = locator.getSingleDimensionValueAsLong("compatibleAgentsCount"); //experimental
    if (compatibleAgentsCount != null) {
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return compatibleAgentsCount.equals(Integer.valueOf(item.getCanRunOnAgents().size()).longValue());
        }
      });
    }

    final Boolean personal = locator.getSingleDimensionValueAsBoolean(PERSONAL);
    if (personal != null) {
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(personal, item.isPersonal());
        }
      });
    }

    final String userDimension = locator.getSingleDimensionValue(USER);
    if (userDimension != null) {
      final SUser user = myUserFinder.getItem(userDimension);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          final SUser actualUser = item.getTriggeredBy().getUser();
          return actualUser != null && user.getId() == actualUser.getId();
        }
      });
    }

    return result;
  }

  /**
   * Returns build promotion of the queued or already started build, if found.
   */
  @NotNull
  public BuildPromotion getBuildPromotionByBuildQueueLocator(@Nullable final String buildQueueLocator) {
    if (StringUtil.isEmpty(buildQueueLocator)) {
      throw new BadRequestException("Empty locator is not supported.");
    }

    final Locator locator = new Locator(buildQueueLocator);

    if (locator.isSingleValue()) { // assume it's promotion id
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      return BuildFinder.getBuildPromotion(singleValueAsLong, myBuildPromotionManager);
    }

    Long promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID);
    if (promotionId != null) {
      return BuildFinder.getBuildPromotion(promotionId, myBuildPromotionManager);
    }

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      return BuildPromotionFinder.getBuildPromotionById(id, myBuildPromotionManager, myBuildsManager);
    }

    return getItem(buildQueueLocator).getBuildPromotion();
  }
}
