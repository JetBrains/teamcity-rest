package jetbrains.buildServer.server.rest.data.problem;

import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.11.13
 */
public class TestOccurrenceFinder extends AbstractFinder<STestRun> {
  private static final String BUILD = "build";
  private static final String TEST = "test";
  private static final String BUILD_TYPE = "buildType";
  private static final String PROJECT = "project";
  private static final String STATUS = "status";
  private static final String BRANCH = "branch";
  private static final String IGNORED = "ignored";

  @NotNull private final TestFinder myTestFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectFinder myProjectFinder;

  @NotNull private final BuildHistoryEx myBuildHistory;

  public TestOccurrenceFinder(final @NotNull TestFinder testFinder,
                              final @NotNull BuildFinder buildFinder,
                              final @NotNull BuildTypeFinder buildTypeFinder,
                              final @NotNull ProjectFinder projectFinder, final @NotNull BuildHistoryEx buildHistory) {
    super(new String[]{DIMENSION_ID, TEST, BUILD_TYPE, BUILD, PROJECT, STATUS, BRANCH, IGNORED});
    myTestFinder = testFinder;
    myBuildFinder = buildFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectFinder = projectFinder;
    myBuildHistory = buildHistory;
  }

  public static String getTestRunLocator(final @NotNull STestRun testRun) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(testRun.getTestRunId())).
      setDimension(BUILD, BuildRequest.getBuildLocator(testRun.getBuild())).getStringRepresentation();
  }

  public static String getTestRunLocator(final @NotNull STest test) {
    return Locator.createEmptyLocator().setDimension(TEST, TestFinder.getTestLocator(test)).getStringRepresentation();
  }

  public static String getTestRunLocator(final @NotNull SBuild build) {
    return Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(build)).getStringRepresentation();
  }

  @Nullable
  @Override
  public Locator getLocatorOrNull(@Nullable final String locatorText) {
    final Locator locator = super.getLocatorOrNull(locatorText);
    if (locator != null){
      locator.setDimensionIfNotPresent(PagerData.COUNT, String.valueOf(Constants.DEFAULT_PAGE_ITEMS_COUNT));
      locator.addIgnoreUnusedDimensions(PagerData.COUNT);
    }
    return locator;
  }

  @Override
  @Nullable
  protected STestRun findSingleItem(@NotNull final Locator locator) {
    /*
    if (locator.isSingleValue()) {
      Long idDimension = locator.getSingleValueAsLong();
      if (idDimension != null) {
        STestRun item = findTestByTestRunId(idDimension);
        if (item != null) {
          return item;
        }
        throw new NotFoundException("No test run with id '" + idDimension + "' found.");
      }
    }
    */

    // dimension-specific item search

    Long idDimension = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (idDimension != null) {

      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        SBuild build = myBuildFinder.getBuild(null, buildDimension);
        STestRun item = findTestByTestRunId(idDimension, build);
        if (item != null) {
          return item;
        }
        throw new NotFoundException("No test run with id '" + idDimension + "' found in build with id " + build.getBuildId());
      }
      throw new BadRequestException("Cannot find test by " + DIMENSION_ID + " only, make sure to specify " + BUILD + " locator.");
    }

    String testDimension = locator.getSingleDimensionValue(TEST);
    if (testDimension != null) {
      STest test = myTestFinder.getItem(testDimension);

      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        SBuild build = myBuildFinder.getBuild(null, buildDimension);
        STestRun item = findTest(test.getTestNameId(), build);
        if (item == null) {
          throw new NotFoundException("No run for test " + test.getName() + ", id: " + test.getTestNameId() + " can be found in build with id " + build.getBuildId());
        }
        return item;
      }
    }
    return null;
  }

  @NotNull
  public List<STestRun> getAllItems() {
    throw new BadRequestException("Listing all test occurrences is not supported. Try locator dimensions: " + Arrays.toString(getKnownDimensions()));
  }

  @Override
  protected List<STestRun> getPrefilteredItems(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      SBuild build = myBuildFinder.getBuild(null, buildDimension);
      return build.getFullStatistics().getAllTests();
    }

    String testDimension = locator.getSingleDimensionValue(TEST);
    if (testDimension != null) {
      STest test = myTestFinder.getItem(testDimension);

      String branchDimension = locator.getSingleDimensionValue(BRANCH);

      String buildTypeDimension = locator.getSingleDimensionValue(BUILD_TYPE);
      if (buildTypeDimension != null) {
        final SBuildType buildType = myBuildTypeFinder.getBuildType(null, buildTypeDimension);
        return myBuildHistory.getTestHistory(test.getTestNameId(), buildType.getBuildTypeId(), 0, branchDimension); //no personal builds
      }

      String projectDimension = locator.getSingleDimensionValue(PROJECT);
      if (projectDimension != null) {
        final SProject project = myProjectFinder.getProject(projectDimension);
        return myBuildHistory.getTestHistory(test.getTestNameId(), project, -1, null); //no personal builds, no branches filtering
      }
      return myBuildHistory.getTestHistory(test.getTestNameId(), myProjectFinder.getRootProject(), 0, branchDimension); //no personal builds
    }

    throw new BadRequestException("Listing all test occurrences is not supported. Try locator dimensions: " + Arrays.toString(getKnownDimensions()));
  }

  @Override
  protected AbstractFilter<STestRun> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<STestRun> result =
      new MultiCheckerFilter<STestRun>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);


    final String statusDimension = locator.getSingleDimensionValue(STATUS);
    if (statusDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return statusDimension.equals(item.getStatus().getText());
        }
      });
    }

    final Boolean ignoredDimension = locator.getSingleDimensionValueAsBoolean(IGNORED);
    if (ignoredDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return FilterUtil.isIncludedByBooleanFilter(ignoredDimension, item.isIgnored());
        }
      });
    }

    String testDimension = locator.getSingleDimensionValue(TEST);
    if (testDimension != null) {
      final long testNameId = myTestFinder.getItem(testDimension).getTestNameId();
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return testNameId == item.getTest().getTestNameId();
        }
      });
    }

    final String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      final SBuild build = myBuildFinder.getBuild(null, buildDimension);
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return build.getBuildId() == item.getBuild().getBuildId();
        }
      });
    }

    return result;
  }


  @Nullable
  private STestRun findTest(final @NotNull Long testNameId, final @NotNull SBuild build) {
//    final List<STestRun> allTests = build.getFullStatistics().getAllTests();
    final List<STestRun> allTests = build.getBuildStatistics(new BuildStatisticsOptions(BuildStatisticsOptions.IGNORED_TESTS | BuildStatisticsOptions.PASSED_TESTS, 0)).getAllTests();
    //todo: TeamCity API: if stacktraces are not loaded,should I then load them somehow to get them for the returned STestRun (see TestOccurrence)
    for (STestRun test : allTests) {
      if (testNameId == test.getTest().getTestNameId()) return test; //todo: TeamCity API: does this support multiple test runs???
    }
    return null;
  }

  @Nullable
  private STestRun findTestByTestRunId(@NotNull final Long testRunId, @NotNull final SBuild build) {
    //todo: TeamCity API (MP) how to implement this without build?
//    final List<STestRun> allTests = build.getFullStatistics().getAllTests();
    final List<STestRun> allTests = build.getBuildStatistics(new BuildStatisticsOptions(BuildStatisticsOptions.IGNORED_TESTS | BuildStatisticsOptions.PASSED_TESTS, 0)).getAllTests();
    //todo: TeamCity API: if stacktraces are not loaded,should I then load them somehow to get them for the returned STestRun (see TestOccurrence)
    for (STestRun test : allTests) {
      if (testRunId.equals(Long.valueOf(test.getTestRunId()))) {
        return test;
      }
    }
    return null;
  }
}
