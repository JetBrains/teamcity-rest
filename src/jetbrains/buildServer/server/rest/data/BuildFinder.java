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
  public static final String PROMOTION_ID = "taskId";
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final BuildPromotionFinder myBuildPromotionFinder;
  @NotNull private final AgentFinder myAgentFinder;

  protected static final String LEGACY_BUILDS_FILTERING = "rest.request.builds.useLegacyBuildsFiltering";
  protected static final String SUPPORT_NON_LOCATOR_FILTERS = "rest.request.builds.supportNonLocatorFilters";
  protected static final String REST_RETURN_ONLY_STARTED_BUILDS = "rest.request.builds.returnOnlyStartedBuilds";

  public BuildFinder(final @NotNull ServiceLocator serviceLocator,
                     final @NotNull BuildTypeFinder buildTypeFinder,
                     final @NotNull ProjectFinder projectFinder,
                     final @NotNull UserFinder userFinder,
                     final @NotNull BuildPromotionFinder buildPromotionFinder,
                     final @NotNull AgentFinder agentFinder) {
    myServiceLocator = serviceLocator;
    myBuildTypeFinder = buildTypeFinder;
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myBuildPromotionFinder = buildPromotionFinder;
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

    boolean legacyFieldsPresent = status != null ||
                                  userLocator != null ||
                                  includePersonal ||
                                  includeCanceled ||
                                  onlyPinned ||
                                  (tags != null && !tags.isEmpty()) ||
                                  agentName != null ||
                                  sinceBuildLocator != null ||
                                  sinceDate != null ||
                                  start != null ||
                                  count != null; //so far buildType is not included here as it can come from context, not query
    String resultingLocatorText;
    Locator locator;

    if (locatorText != null || !legacyFieldsPresent) {
      resultingLocatorText = locatorText;
      // legacy: override start and count only if set in URL query parameters and not set in locator
      if (start != null) resultingLocatorText = Locator.setDimensionIfNotPresent(resultingLocatorText, PagerData.START, String.valueOf(start));
      if (count != null) resultingLocatorText = Locator.setDimensionIfNotPresent(resultingLocatorText, PagerData.COUNT, String.valueOf(count));
    } else {
      locator = Locator.createEmptyLocator();
      if (buildType != null) locator.setDimension("buildType", BuildTypeFinder.getLocator(buildType));
      if (status != null) locator.setDimension("status", status);
      if (userLocator != null) locator.setDimension("user", userLocator);

      // preserve 5.0 logic for personal/canceled/pinned builds; see also http://youtrack.jetbrains.com/issue/TW-25778
      if (includePersonal) locator.setDimension("personal", "any");
      if (includeCanceled) locator.setDimension("canceled", "any");

      locator.setDimension("running", "false");
      if (onlyPinned) locator.setDimension("pinned", "true");

      if (tags != null && !tags.isEmpty()) locator.setDimension("tags", StringUtil.join(",", tags)); //behavior changed comparing to 9.0: not supporting tags with comma here
      if (agentName != null) locator.setDimension("agentName", agentName);
      if (sinceBuildLocator != null) locator.setDimension("sinceBuild", sinceBuildLocator);
      if (sinceDate != null) locator.setDimension("sinceDate", sinceDate);
      if (start != null) locator.setDimension(PagerData.START, String.valueOf(start));
      if (count != null) locator.setDimension(PagerData.COUNT, String.valueOf(count));

      resultingLocatorText = locator.getStringRepresentation();
    }

    if (legacyFieldsPresent && !TeamCityProperties.getBooleanOrTrue(SUPPORT_NON_LOCATOR_FILTERS)) {
      throw new BadRequestException("Legacy query syntax supplied. Try URL parameter instead: " + locatorParameterName + "=" + resultingLocatorText + "\n" +
                                    "Set \"" + SUPPORT_NON_LOCATOR_FILTERS + "=true\" server internal property to allow such legacy queries until next TeamCity upgrade.");
    }

    final PagedSearchResult<BuildPromotion> pagedResult = getBuilds(buildType, resultingLocatorText);
    final PagerData pagerData = new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), pagedResult,
                                              locatorText == null ? null : resultingLocatorText,
                                              locatorParameterName);
    return Builds.createFromBuilds(pagedResult.myEntries, pagerData, fields, beanContext);
  }

  @NotNull
  public PagedSearchResult<BuildPromotion> getBuilds(@Nullable final SBuildType buildType, @Nullable final String locatorText) {
    Locator locator = null;
    try {
      locator = locatorText != null ? new Locator(locatorText) : Locator.createEmptyLocator();
    } catch (LocatorProcessException e) {
      //error creating locator - some special dimensions used? process by default
      return myBuildPromotionFinder.getBuildPromotions(buildType, locatorText);
    }
    if (useByPromotionFiltering(locator)) {
      return myBuildPromotionFinder.getBuildPromotions(buildType, locatorText);
    }

    BuildsFilter buildsFilter = getBuildsFilter(locator, buildType);
    locator.checkLocatorFullyProcessed();

    final Integer c = buildsFilter.getCount();
    if (c != null) {
      buildsFilter.setCount(c != -1 ? c : null);
    } else {
      buildsFilter.setCount(jetbrains.buildServer.server.rest.request.Constants.getDefaultPageItemsCount());
    }

    return new PagedSearchResult<BuildPromotion>(toBuildPromotions(getBuilds(buildsFilter)), buildsFilter.getStart(), buildsFilter.getCount());
  }

  private boolean useByPromotionFiltering(@NotNull final Locator locator) {
    final Boolean byPromotion = locator.getSingleDimensionValueAsBoolean(BuildPromotionFinder.BY_PROMOTION, !TeamCityProperties.getBoolean(LEGACY_BUILDS_FILTERING));
    return byPromotion != null && byPromotion;
  }

  @NotNull
  public BuildsFilter getBuildsFilter(@Nullable final SBuildType buildType, final String locatorText) {
    Locator locator = new Locator(locatorText);
    BuildsFilter buildsFilter = getBuildsFilter(locator, buildType);
    locator.checkLocatorFullyProcessed();

    final Integer c = buildsFilter.getCount();
    if (c != null) {
      buildsFilter.setCount(c != -1 ? c : null);
    } else {
      buildsFilter.setCount(jetbrains.buildServer.server.rest.request.Constants.getDefaultPageItemsCount());
    }
    return buildsFilter;
  }

  public static List<BuildPromotion> toBuildPromotions(final Collection<SBuild> buildsList) {
    return CollectionsUtil.convertCollection(buildsList, new Converter<BuildPromotion, SBuild>() {
      public BuildPromotion createFrom(@NotNull final SBuild source) {
        return source.getBuildPromotion();
      }
    });
  }

  @NotNull
  public static List<SBuild> toBuilds(@NotNull final Collection<BuildPromotion> buildsList) {
    final ArrayList<SBuild> result = new ArrayList<SBuild>();
    for (BuildPromotion buildPromotion : buildsList) {
      final SBuild associatedBuild = buildPromotion.getAssociatedBuild();
      if (associatedBuild != null) {
        result.add(associatedBuild);
      }
    }
    return result;
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
  public BuildPromotion getBuildPromotion(@Nullable SBuildType buildType, @Nullable final String buildLocator) {
    if (StringUtil.isEmpty(buildLocator)) {
      throw new BadRequestException("Empty single build locator is not supported.");
    }

    final Locator locator = new Locator(buildLocator);
    if (useByPromotionFiltering(locator)) {
      final BuildPromotion promotion = myBuildPromotionFinder.getBuildPromotion(buildType, buildLocator);
      if (!TeamCityProperties.getBoolean(REST_RETURN_ONLY_STARTED_BUILDS)) {
        return promotion;
      }
      final SBuild associatedBuild = promotion.getAssociatedBuild();
      if (associatedBuild != null){
        return promotion;
      } else{
        throw new NotFoundException("No associated build for found build promotion with id " + promotion.getId() + "(a queued build?)." +
                                    " Check performed as '" + REST_RETURN_ONLY_STARTED_BUILDS + "' internal property is set");
      }
    }

    if (locator.isSingleValue()) {
      if (buildType == null) {
        // no dimensions found and no build type, assume it's build id

        @SuppressWarnings("ConstantConditions") SBuild build =
          myServiceLocator.getSingletonService(BuildsManager.class).findBuildInstanceById(locator.getSingleValueAsLong()); //todo: report non-number more user-friendly
        if (build == null) {
          throw new NotFoundException("Cannot find build by id '" + locator.getSingleValue() + "'.");
        }
        return build.getBuildPromotion();
      }
      // no dimensions found and build type is specified, assume it's build number
      @SuppressWarnings("ConstantConditions") SBuild build = myServiceLocator.getSingletonService(BuildsManager.class).findBuildInstanceByBuildNumber(buildType.getBuildTypeId(),
                                                                                                                       buildLocator);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + buildLocator + "' in build configuration " + buildType + ".");
      }
      return build.getBuildPromotion();
    }

    String buildTypeLocator = locator.getSingleDimensionValue("buildType");
    buildType = myBuildTypeFinder.deriveBuildTypeFromLocator(buildType, buildTypeLocator);

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      SBuild build = myServiceLocator.getSingletonService(BuildsManager.class).findBuildInstanceById(id);
      if (build == null) {
        throw new NotFoundException("No build can be found by id '" + id + "'.");
      }
      if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by id '" + locator.getSingleDimensionValue(DIMENSION_ID) + "' in build type '" + buildType + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has '" + DIMENSION_ID + "' dimension and others. Others are ignored.");
      }
      return build.getBuildPromotion();
    }

    String number = locator.getSingleDimensionValue("number");
    if (number != null && buildType != null) {
      SBuild build = myServiceLocator.getSingletonService(BuildsManager.class).findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
      if (build == null) {
        throw new NotFoundException("No build can be found by number '" + number + "' in build configuration " + buildType + ".");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'number' dimension and others. Others are ignored.");
      }
      return build.getBuildPromotion();
    }

    Long promotionId = locator.getSingleDimensionValueAsLong(PROMOTION_ID);
    if (promotionId == null){
      promotionId = locator.getSingleDimensionValueAsLong("promotionId"); //support TeamCity 8.0 dimension
    }
    if (promotionId != null) {
      BuildPromotion build = getBuildByPromotionId(promotionId);
      if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by " + PROMOTION_ID + " '" + promotionId + "' in build type '" + buildType + "'.");
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
      return filteredBuilds.get(0).getBuildPromotion();
    }
    //todo: check for unknown dimension names in all the returns

    throw new BadRequestException("Build locator '" + buildLocator + "' is not supported (" + filteredBuilds.size() + " builds found)");
  }


  //todo: get rid of most usages of this, use getBuildPromotion instead (but there are some TeamCity API issue)
  @NotNull
  public SBuild getBuild(final @Nullable SBuildType buildType, final @Nullable String buildLocator) {
    final BuildPromotion promotion = getBuildPromotion(buildType, buildLocator);
    final SBuild associatedBuild = promotion.getAssociatedBuild();
    if (associatedBuild == null){
      throw new NotFoundException("No started build found for id " + promotion.getId());
    }
    return associatedBuild;
  }

  @NotNull
  private BuildsFilter getBuildsFilter(@NotNull final Locator buildLocator, @Nullable final SBuildType buildType) {
    //todo: report unknown locator dimensions
    final SBuildType actualBuildType = myBuildTypeFinder.deriveBuildTypeFromLocator(buildType, buildLocator.getSingleDimensionValue("buildType"));
    final String projectFromLocator = buildLocator.getSingleDimensionValue("project");
    final SProject project = StringUtil.isEmpty(projectFromLocator) ? null : myProjectFinder.getItem(projectFromLocator);

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
                                   userLocator == null ? null : myUserFinder.getItem(userLocator),
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
    result.addAll(BuildsFilterProcessor.getMatchingRunningBuilds(buildsFilter, myServiceLocator.getSingletonService(BuildsManager.class)));
    final Integer originalCount = buildsFilter.getCount();
    if (originalCount == null || result.size() < originalCount) {
      final BuildsFilter patchedBuildsFilter = new BuildsFilterWithBuildExcludes(buildsFilter, result);
      if (originalCount != null){
        patchedBuildsFilter.setCount(originalCount - result.size());
      }
      result.addAll(BuildsFilterProcessor.getMatchingFinishedBuilds(patchedBuildsFilter, myServiceLocator.getSingletonService(BuildHistory.class)));
    }
    return result;
  }

  @NotNull
  public static BuildPromotion getBuildPromotion(final long promotionId, @NotNull final BuildPromotionManager promotionManager) {
    final BuildPromotion buildPromotion = promotionManager.findPromotionOrReplacement(promotionId);
    if (buildPromotion == null) {
      throw new NotFoundException("No build promotion can be found by promotion id " + promotionId);
    }
    BuildPromotionFinder.ensureCanView(buildPromotion);
    return buildPromotion;
  }

  @NotNull
  public BuildPromotion getBuildByPromotionId(@NotNull final Long promotionId) {
    return getBuildPromotion(promotionId, myServiceLocator.getSingletonService(BuildPromotionManager.class));
  }
}
