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

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.build.BuildsFilter;
import jetbrains.buildServer.server.rest.data.build.BuildsFilterProcessor;
import jetbrains.buildServer.server.rest.data.build.BuildsFilterWithBuildExcludes;
import jetbrains.buildServer.server.rest.data.build.GenericBuildsFilter;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Specifies build locator.
 * @author Yegor.Yarko
 *         Date: 18.01.12
 */
public class BuildFinder {
  private static final Logger LOG = Logger.getInstance(BuildFinder.class.getName());
  public static final String DIMENSION_ID = "id";
  public static final String PROMOTION_ID = "promotionId";
  @NotNull private final DataProvider myDataProvider;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final AgentFinder myAgentFinder;

  public BuildFinder(final @NotNull DataProvider dataProvider,
                     final @NotNull ServiceLocator serviceLocator,
                     final @NotNull BuildTypeFinder buildTypeFinder,
                     final @NotNull ProjectFinder projectFinder,
                     final @NotNull UserFinder userFinder,
                     final @NotNull AgentFinder agentFinder) {
    myDataProvider = dataProvider;
    myBuildTypeFinder = buildTypeFinder;
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myAgentFinder = agentFinder;
  }

  @NotNull
  public static String getLocator(@NotNull final SBuild build) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(build.getBuildId()));
  }

  public Builds getBuildsForRequest(final SBuildType buildType,
                                    final String status,
                                    final String userLocator,
                                    final boolean includePersonal,
                                    final boolean includeCanceled,
                                    final boolean onlyPinned,
                                    final List<String> tags,
                                    final String agentName,
                                    final String sinceBuildLocator,
                                    final String sinceDate,
                                    final Long start,
                                    final Integer count,
                                    final String locatorText,
                                    final String locatorParameterName,
                                    final UriInfo uriInfo,
                                    final HttpServletRequest request,
                                    @NotNull final Fields fields,
                                    @NotNull final BeanContext beanContext) {
    BuildsFilter buildsFilter;
    if (locatorText != null) {
      Locator locator = new Locator(locatorText);
      buildsFilter = getBuildsFilter(locator, buildType);
      locator.checkLocatorFullyProcessed();
      // override start and count only if set in URL query parameters and not set in locator
      if (start != null && buildsFilter.getStart() == null) {
        buildsFilter.setStart(start);
      }
      if (count != null && buildsFilter.getCount() == null) {
        buildsFilter.setCount(count);
      }
    } else {
      // preserve 5.0 logic for personal/canceled/pinned builds
      //todo: this also changes defaults for request without locator, see http://youtrack.jetbrains.com/issue/TW-25778
      buildsFilter = new GenericBuildsFilter(buildType,
                                             null, status, null,
                                             myUserFinder.getUserIfNotNull(userLocator),
                                             includePersonal ? null : false, includeCanceled ? null : false,
                                             false, onlyPinned ? true : null, tags, new BranchMatcher(null), agentName,
                                             null, null, getRangeLimit(buildType, sinceBuildLocator, DataProvider.parseDate(sinceDate)),
                                             null,
                                             start, count, null);
    }

    final Integer c = buildsFilter.getCount();
    if (c != null) {
      buildsFilter.setCount(c != -1 ? c : null);
    } else {
      buildsFilter.setCount(jetbrains.buildServer.server.rest.request.Constants.DEFAULT_PAGE_ITEMS_COUNT_INT);
    }

    final List<SBuild> buildsList = getBuilds(buildsFilter);
    return new Builds(getBuildPromotions(buildsList),
                      new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), buildsFilter.getStart(),
                                    buildsFilter.getCount(), buildsList.size(), (locatorText != null ? locatorText : null),
                                    locatorParameterName),
                      fields, beanContext);
  }

  public static List<BuildPromotion> getBuildPromotions(final Collection<SBuild> buildsList) {
    return CollectionsUtil.convertCollection(buildsList, new Converter<BuildPromotion, SBuild>() {
      public BuildPromotion createFrom(@NotNull final SBuild source) {
        return source.getBuildPromotion();
      }
    });
  }

  /**
   * Supported build locators:
   *  213 - build with id=213
   *  213 when buildType is specified - build in the specified buildType with build number 213
   *  id:213 - build with id=213
   *  buildType:bt37 - specify Build Configuration by internal id. If specified, other locator parts should select the build
   *  number:213 when buildType is specified - build in the specified buildType with build number 213
   *  status:SUCCESS when buildType is specified - last build with the specified status in the specified buildType
   */
  @NotNull
  public SBuild getBuild(@Nullable SBuildType buildType, @Nullable final String buildLocator) {
    if (StringUtil.isEmpty(buildLocator)) {
      throw new BadRequestException("Empty build locator is not supported.");
    }

    final Locator locator = new Locator(buildLocator);

    if (locator.isSingleValue()) {
      if (buildType == null) {
        // no dimensions found and no build type, assume it's build id

        @SuppressWarnings("ConstantConditions") SBuild build = myDataProvider.getServer().findBuildInstanceById(locator.getSingleValueAsLong()); //todo: report non-number more user-friendly
        if (build == null) {
          throw new BadRequestException("Cannot find build by id '" + locator.getSingleValue() + "'.");
        }
        return build;
      }
      // no dimensions found and build type is specified, assume it's build number
      @SuppressWarnings("ConstantConditions") SBuild build = myDataProvider.getServer().findBuildInstanceByBuildNumber(buildType.getBuildTypeId(),
                                                                                                                       buildLocator);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + buildLocator + "' in build configuration " + buildType + ".");
      }
      return build;
    }

    String buildTypeLocator = locator.getSingleDimensionValue("buildType");
    buildType = myBuildTypeFinder.deriveBuildTypeFromLocator(buildType, buildTypeLocator);

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      SBuild build = myDataProvider.getServer().findBuildInstanceById(id);
      if (build == null) {
        throw new NotFoundException("No build can be found by id '" + id + "'.");
      }
      if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by id '" + locator.getSingleDimensionValue(DIMENSION_ID) + "' in build type '" + buildType + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has '" + DIMENSION_ID + "' dimension and others. Others are ignored.");
      }
      return build;
    }

    String number = locator.getSingleDimensionValue("number");
    if (number != null && buildType != null) {
      SBuild build = myDataProvider.getServer().findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + number + "' in build configuration " + buildType + ".");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'number' dimension and others. Others are ignored.");
      }
      return build;
    }

    Long promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID);
    if (promotionId != null) {
      SBuild build = getBuildByPromotionId(promotionId);
      if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by promotion id '" + promotionId + "' in build type '" + buildType + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has '" + PROMOTION_ID + "' dimension and others. Others are ignored.");
      }
      return build;
    }

    final BuildsFilter buildsFilter = getBuildsFilter(locator, buildType);
    buildsFilter.setCount(1);

    locator.checkLocatorFullyProcessed();

    final List<SBuild> filteredBuilds = getBuilds(buildsFilter);
    if (filteredBuilds.size() == 0){
      throw new NotFoundException("No build found by filter: " + buildsFilter.toString() + ".");
    }

    if (filteredBuilds.size() == 1){
      return filteredBuilds.get(0);
    }
    //todo: check for unknown dimension names in all the returns

    throw new BadRequestException("Build locator '" + buildLocator + "' is not supported (" + filteredBuilds.size() + " builds found)");
  }

  @Nullable
  public SBuild getBuildIfNotNull(@Nullable final SBuildType buildType, @Nullable final String buildLocator) {
    return buildLocator == null ? null : getBuild(buildType, buildLocator);
  }


  @NotNull
  private BuildsFilter getBuildsFilter(final Locator buildLocator, @Nullable final SBuildType buildType) {
    //todo: report unknown locator dimensions
    final SBuildType actualBuildType = myBuildTypeFinder.deriveBuildTypeFromLocator(buildType, buildLocator.getSingleDimensionValue("buildType"));
    final String projectFromLocator = buildLocator.getSingleDimensionValue("project");
    final SProject project = StringUtil.isEmpty(projectFromLocator) ? null : myProjectFinder.getProject(projectFromLocator);

    final String userLocator = buildLocator.getSingleDimensionValue("user");
    final String tagsString = buildLocator.getSingleDimensionValue("tags");
    final String singleTagString = buildLocator.getSingleDimensionValue("tag");
    if (tagsString != null && singleTagString != null){
      throw new BadRequestException("Both 'tags' and 'tag' dimensions specified. Only one can be present.");
    }
    List<String> tagsList = null;
    if (singleTagString != null) {
      tagsList = Collections.singletonList(singleTagString);
    }else if (tagsString != null) {
      tagsList = Arrays.asList(tagsString.split(","));
    }

    final Long count = buildLocator.getSingleDimensionValueAsLong(PagerData.COUNT);

    BranchMatcher branchMatcher;
    final String branchLocatorValue = buildLocator.getSingleDimensionValue("branch");
    try {
      branchMatcher = new BranchMatcher(branchLocatorValue);
    } catch (LocatorProcessException e) {
      throw new LocatorProcessException("Invalid sub-locator 'branch': " + e.getMessage());
    }

    Collection<SBuildAgent> agents = null;
    final String agentLocator = buildLocator.getSingleDimensionValue("agent");
    if (agentLocator != null){
      agents = myAgentFinder.getItems(agentLocator).myEntries;
    }
    return new GenericBuildsFilter(actualBuildType,
                                   project,
                                   buildLocator.getSingleDimensionValue("status"),
                                   buildLocator.getSingleDimensionValue("number"),
                                   myUserFinder.getUserIfNotNull(userLocator),
                                   buildLocator.getSingleDimensionValueAsBoolean("personal", false),
                                   buildLocator.getSingleDimensionValueAsBoolean("canceled", false),
                                   buildLocator.getSingleDimensionValueAsBoolean("running", false),
                                   buildLocator.getSingleDimensionValueAsBoolean("pinned"),
                                   tagsList,
                                   branchMatcher,
                                   buildLocator.getSingleDimensionValue("agentName"), //deprecated, use "agent" instead
                                   agents,
                                   ParameterCondition.create(buildLocator.getSingleDimensionValue("property")),
                                   getRangeLimit(actualBuildType, buildLocator.getSingleDimensionValue("sinceBuild"),
                                                 DataProvider.parseDate(buildLocator.getSingleDimensionValue("sinceDate"))),
                                   getRangeLimit(actualBuildType, buildLocator.getSingleDimensionValue("untilBuild"),
                                                 DataProvider.parseDate(buildLocator.getSingleDimensionValue("untilDate"))),
                                   buildLocator.getSingleDimensionValueAsLong(PagerData.START),
                                   count == null ? null : count.intValue(),
                                   buildLocator.getSingleDimensionValueAsLong("lookupLimit")
    );
  }

  @Nullable
  private RangeLimit getRangeLimit(@Nullable final SBuildType buildType,
                                         @Nullable final String buildLocator,
                                         @Nullable final Date date) {
    //todo: need buildType here?
    if (buildLocator == null && date == null) {
      return null;
    }
    if (buildLocator != null) {
      if (date != null) {
        throw new BadRequestException("Both build and date are specified for a build rage limit");
      }
      return new RangeLimit(getBuild(buildType, buildLocator));
    }
    return new RangeLimit(date);
  }

  /**
   * Finds builds by the specified criteria within specified range
   * This is slow!
   *
   * @param buildsFilter the filter for the builds to find
   * @return the builds found
   */
  private List<SBuild> getBuilds(@NotNull final BuildsFilter buildsFilter) {
    final ArrayList<SBuild> result = new ArrayList<SBuild>();
    //todo: sort and ensure there are no duplicates
    result.addAll(BuildsFilterProcessor.getMatchingRunningBuilds(buildsFilter, myDataProvider.getRunningBuildsManager()));
    final Integer originalCount = buildsFilter.getCount();
    if (originalCount == null || result.size() < originalCount) {
      final BuildsFilter patchedBuildsFilter = new BuildsFilterWithBuildExcludes(buildsFilter, result);
      if (originalCount != null){
        patchedBuildsFilter.setCount(originalCount - result.size());
      }
      result.addAll(BuildsFilterProcessor.getMatchingFinishedBuilds(patchedBuildsFilter, myDataProvider.getBuildHistory()));
    }
    return result;
  }

  @NotNull
  public static BuildPromotion getBuildPromotion(final long promotionId, @NotNull final BuildPromotionManager promotionManager) {
    final BuildPromotion buildPromotion = promotionManager.findPromotionById(promotionId);
    if (buildPromotion == null) {
      throw new NotFoundException("No build promotion can be found by id '" + promotionId + "'.");
    }
    return buildPromotion;
  }

  @NotNull
  public SBuild getBuildByPromotionId(@NotNull final Long promotionId) {
    final BuildPromotion promotion = getBuildPromotion(promotionId, myDataProvider.getPromotionManager());
    SBuild build = promotion.getAssociatedBuild();
    if (build == null) {
      throw new NotFoundException("No associated build can be found for promotion with id '" + promotionId + "'.");
    }
    return build;
  }
}
