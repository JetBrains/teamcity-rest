package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
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
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriInfo;
import java.util.*;

/**
 * Specifies build locator.
 * @author Yegor.Yarko
 *         Date: 18.01.12
 */
public class BuildFinder {
  private static final Logger LOG = Logger.getInstance(BuildFinder.class.getName());
  @NotNull private final DataProvider myDataProvider;
  @NotNull private BuildTypeFinder myBuildTypeFinder;
  @NotNull private ProjectFinder myProjectFinder;
  @NotNull private UserFinder myUserFinder;

  public BuildFinder(@NotNull DataProvider dataProvider, @NotNull BuildTypeFinder buildTypeFinder, @NotNull ProjectFinder projectFinder, @NotNull UserFinder userFinder) {
    myDataProvider = dataProvider;
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
                                             start, count, null, myDataProvider.getServer());
    }

    final Integer c = buildsFilter.getCount();
    if (c != null) {
      buildsFilter.setCount(c != -1 ? c : null);
    } else {
      buildsFilter.setCount(jetbrains.buildServer.server.rest.request.Constants.DEFAULT_PAGE_ITEMS_COUNT_INT);
    }

    final List<SBuild> buildsList = getBuilds(buildsFilter);
    return new Builds(buildsList, myDataProvider,
                      new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), buildsFilter.getStart(),
                                    buildsFilter.getCount(), buildsList.size(), (locatorText != null ? locatorText : null),
                                    locatorParameterName), apiUrlBuilder);
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
    if (number != null) {
      if (buildType != null) {
        SBuild build = myDataProvider.getServer().findBuildInstanceByBuildNumber(buildType.getBuildTypeId(), number);
        if (build == null) {
          throw new NotFoundException("No build can be found by number '" + number + "' in build configuration " + buildType + ".");
        }
        if (locator.getDimensionsCount() > 1) {
          LOG.info("Build locator '" + buildLocator + "' has 'number' dimension and others. Others are ignored.");
        }
        return build;
      }else{
        throw new NotFoundException("Build number is specified without build configuration. Cannot find build by build number only.");
      }
    }
    {
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
}
