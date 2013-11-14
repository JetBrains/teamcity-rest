package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.build.BuildsFilter;
import jetbrains.buildServer.server.rest.data.build.BuildsFilterProcessor;
import jetbrains.buildServer.server.rest.data.build.BuildsFilterWithBuildExcludes;
import jetbrains.buildServer.server.rest.data.build.GenericBuildsFilter;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.serverSide.*;
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
  @NotNull private final DataProvider myDataProvider;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final UserFinder myUserFinder;

  public BuildFinder(@NotNull DataProvider dataProvider,
                     final @NotNull ServiceLocator serviceLocator,
                     @NotNull BuildTypeFinder buildTypeFinder,
                     @NotNull ProjectFinder projectFinder,
                     @NotNull UserFinder userFinder) {
    myDataProvider = dataProvider;
    myServiceLocator = serviceLocator;
    myBuildTypeFinder = buildTypeFinder;
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
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
                                    final ApiUrlBuilder apiUrlBuilder) {
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
                                             null, getRangeLimit(buildType, sinceBuildLocator, DataProvider.parseDate(sinceDate)),
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
    return new Builds(buildsList, myServiceLocator,
                      new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), buildsFilter.getStart(),
                                    buildsFilter.getCount(), buildsList.size(), (locatorText != null ? locatorText : null),
                                    locatorParameterName), apiUrlBuilder);
  }

  public PagedSearchResult<SQueuedBuild> getQueuedBuilds(@Nullable final Locator locator) {
    if (locator == null) {
       return new PagedSearchResult<SQueuedBuild>(getAllQueuedBuilds(myDataProvider), null, null);
     }

     if (locator.isSingleValue()){
       locator.checkLocatorFullyProcessed();
       throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported.");
     }

     Long id = locator.getSingleDimensionValueAsLong("id");
     if (id != null) {
       final BuildPromotion buildPromotion = getBuildPromotion(id);
       final SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
       if (queuedBuild == null){
         throw new NotFoundException("No queued build can be found by id '" + buildPromotion.getId() + "' (while promotion exists).");
       }
       locator.checkLocatorFullyProcessed();
       return new PagedSearchResult<SQueuedBuild>(Collections.singletonList(queuedBuild), null, null);
     }

    AbstractFilter<SQueuedBuild> filter = getQueuedBuildsFilter(locator, myProjectFinder, myBuildTypeFinder, myDataProvider);
    locator.checkLocatorFullyProcessed();

    return new PagedSearchResult<SQueuedBuild>(getQueuedBuilds(filter), filter.getStart(), filter.getCount());
  }

  @NotNull
  private static List<SQueuedBuild> getAllQueuedBuilds(@NotNull final DataProvider dataProvider) {
    return dataProvider.getServer().getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class).getItems();
  }

  @NotNull
  public static Locator createQueuedBuildsLocator(@Nullable final String locatorText) {
    final Locator result = new Locator(locatorText, "id", "project", "buildType", "agent", "personal", PagerData.COUNT, PagerData.START, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    return result;
  }

  private List<SQueuedBuild> getQueuedBuilds(final AbstractFilter<SQueuedBuild> filter) {
    final FilterItemProcessor<SQueuedBuild> filterItemProcessor = new FilterItemProcessor<SQueuedBuild>(filter);
    AbstractFilter.processList(getAllQueuedBuilds(myDataProvider), filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  public static MultiCheckerFilter<SQueuedBuild> getQueuedBuildsFilter(@NotNull final Locator locator,
                                                                       @NotNull final ProjectFinder projectFinder,
                                                                       @NotNull final BuildTypeFinder buildTypeFinder,
                                                                       @NotNull final DataProvider dataProvider) {
    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<SQueuedBuild> result =
      new MultiCheckerFilter<SQueuedBuild>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String projectLocator = locator.getSingleDimensionValue("project");
    SProject project = null;
    if (projectLocator != null) {
      project = projectFinder.getProject(projectLocator);
      final SProject internalProject = project;
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return internalProject.equals(item.getBuildType().getProject());
        }
      });
    }

    final String buildTypeLocator = locator.getSingleDimensionValue("buildType");
    if (buildTypeLocator != null) {
      final SBuildType buildType = buildTypeFinder.getBuildType(project, buildTypeLocator);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return buildType.equals(item.getBuildType());
        }
      });
    }

    final String agentLocator = locator.getSingleDimensionValue("agent");
    if (agentLocator != null) {
      final SBuildAgent agent = dataProvider.getAgent(agentLocator);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return agent.equals(item.getBuildAgent());
        }
      });
    }

    final String compatibleAagentLocator = locator.getSingleDimensionValue("compatibleAgent"); //experimental
    if (compatibleAagentLocator != null) {
      final SBuildAgent agent = dataProvider.getAgent(compatibleAagentLocator);
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return item.getCompatibleAgents().contains(agent);
        }
      });
    }

    final Long  compatibleAgentsCount = locator.getSingleDimensionValueAsLong("compatibleAgentsCount"); //experimental
    if (compatibleAgentsCount != null) {
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return compatibleAgentsCount.equals(Integer.valueOf(item.getCompatibleAgents().size()).longValue());
        }
      });
    }

    final Boolean personal = locator.getSingleDimensionValueAsBoolean("personal");
    if (personal != null) {
      result.add(new FilterConditionChecker<SQueuedBuild>() {
        public boolean isIncluded(@NotNull final SQueuedBuild item) {
          return FilterUtil.isIncludedByBooleanFilter(personal, item.isPersonal());
        }
      });
    }

    return result;
  }

  @NotNull
  public SQueuedBuild getQueuedBuild(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty build locator is not supported.");
    }

    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty queued build locator is not supported.");
    }
    final Locator locator = createQueuedBuildsLocator(locatorText);

    if (locator.isSingleValue()) {
     // assume it's promotion id
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      locator.checkLocatorFullyProcessed();
      return getQueuedBuildByPromotionId(singleValueAsLong);
    }

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      locator.checkLocatorFullyProcessed();
      return getQueuedBuildByPromotionId(id);
    }

    locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
    final PagedSearchResult<SQueuedBuild> items = getQueuedBuilds(locator);
    if (items.myEntries.size() == 0) {
      throw new NotFoundException("No queued builds are found by locator '" + locatorText + "'.");
    }
    assert items.myEntries.size()== 1;
    return items.myEntries.get(0);
  }

  private SQueuedBuild getQueuedBuildByPromotionId(final Long singleValueAsLong) {
    final BuildPromotion buildPromotion = getBuildPromotion(singleValueAsLong);
    final SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
    if (queuedBuild == null){
      throw new NotFoundException("No queued build can be found by id '" + buildPromotion.getId() + "' (while promotion exists).");
    }
    return queuedBuild;
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

    Long id = locator.getSingleDimensionValueAsLong("id");
    if (id != null) {
      SBuild build = myDataProvider.getServer().findBuildInstanceById(id);
      if (build == null) {
        throw new NotFoundException("No build can be found by id '" + id + "'.");
      }
      if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by id '" + locator.getSingleDimensionValue("id") + "' in build type '" + buildType + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'id' dimension and others. Others are ignored.");
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

    Long promotionId = locator.getSingleDimensionValueAsLong("promotionId");
    if (promotionId != null) {
      final BuildPromotion promotion = myDataProvider.getPromotionManager().findPromotionById(promotionId);
      if (promotion == null) {
        throw new NotFoundException("No promotion can be found by promotionId '" + promotionId + "'.");
      }
      SBuild build = promotion.getAssociatedBuild();
      if (build == null) {
        throw new NotFoundException("No associated build can be found for promotion with id '" + promotionId + "'.");
      }
      if (buildType != null && !buildType.getBuildTypeId().equals(build.getBuildTypeId())) {
        throw new NotFoundException("No build can be found by promotionId '" + promotionId + "' in build type '" + buildType + "'.");
      }
      if (locator.getDimensionsCount() > 1) {
        LOG.info("Build locator '" + buildLocator + "' has 'promotionId' dimension and others. Others are ignored.");
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
                                   //todo: support agent locator here
                                   buildLocator.getSingleDimensionValue("agentName"),
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

  /**
   * Returns build promotion if found. Othervise returns null. Throws no locator exceptions
   */
  @Nullable
  public BuildPromotion getBuildPromotionByBuildQueueLocator(@Nullable final String buildQueueLocator) {
    if (StringUtil.isEmpty(buildQueueLocator)) {
      return null;
    }

    final Locator locator = new Locator(buildQueueLocator);

    if (locator.isSingleValue()) { // assume it's promotion id
      @SuppressWarnings("ConstantConditions") @NotNull final Long singleValueAsLong = locator.getSingleValueAsLong();
      return myDataProvider.getPromotionManager().findPromotionById(singleValueAsLong);
    }

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      return myDataProvider.getPromotionManager().findPromotionById(id);
    }

    return null;
  }

  @NotNull
  private BuildPromotion getBuildPromotion(final long promotionId) {
    final BuildPromotion buildPromotion = myDataProvider.getPromotionManager().findPromotionById(promotionId);
    if (buildPromotion == null) {
      throw new NotFoundException("No build promotion can be found by id '" + promotionId + "'.");
    }
    return buildPromotion;
  }


}
