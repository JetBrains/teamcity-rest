package jetbrains.buildServer.server.rest.data.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.tests.TestName;
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
  public static final String AFFECTED_PROJECT = "affectedProject";
  private static final String CURRENT = "current";
  private static final String STATUS = "status";
  private static final String BRANCH = "branch";
  private static final String IGNORED = "ignored";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String MUTED = "muted";
  public static final String CURRENTLY_MUTED = "currentlyMuted";

  @NotNull private final TestFinder myTestFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final BuildTypeFinder myBuildTypeFinder;
  @NotNull private final ProjectFinder myProjectFinder;

  @NotNull private final BuildHistoryEx myBuildHistory;
  @NotNull private final CurrentProblemsManager myCurrentProblemsManager;

  public TestOccurrenceFinder(final @NotNull TestFinder testFinder,
                              final @NotNull BuildFinder buildFinder,
                              final @NotNull BuildTypeFinder buildTypeFinder,
                              final @NotNull ProjectFinder projectFinder,
                              final @NotNull BuildHistoryEx buildHistory,
                              final @NotNull CurrentProblemsManager currentProblemsManager) {
    super(new String[]{DIMENSION_ID, TEST, BUILD_TYPE, BUILD, AFFECTED_PROJECT, CURRENT, STATUS, BRANCH, IGNORED, MUTED, CURRENTLY_MUTED, CURRENTLY_INVESTIGATED});
    myTestFinder = testFinder;
    myBuildFinder = buildFinder;
    myBuildTypeFinder = buildTypeFinder;
    myProjectFinder = projectFinder;
    myBuildHistory = buildHistory;
    myCurrentProblemsManager = currentProblemsManager;
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
    if (locator != null && !locator.isSingleValue()){
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

  @Override
  @NotNull
  public List<STestRun> getAllItems() {
    throw new BadRequestException("Listing all test occurrences is not supported. Try locator dimensions: " + BUILD + ", " + TEST + ", " + CURRENT + ":true");
  }

  @Override
  protected List<STestRun> getPrefilteredItems(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      SBuild build = myBuildFinder.getBuild(null, buildDimension);
      return build.getFullStatistics().getAllTests();
    }

    final SProject affectedProject;
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      affectedProject = myProjectFinder.getProject(affectedProjectDimension);
    }else{
      affectedProject = myProjectFinder.getRootProject();
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

      return myBuildHistory.getTestHistory(test.getTestNameId(), affectedProject, 0, branchDimension); //no personal builds
    }

    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      return getCurrentOccurences(affectedProject, myCurrentProblemsManager);
    }

    throw new BadRequestException("Listing all test occurrences is not supported. Try locator dimensions: " + BUILD + ", " + TEST + ", " + CURRENT + ":true");
  }

  @NotNull
  public static List<STestRun> getCurrentOccurences(@NotNull final SProject affectedProject, @NotNull final CurrentProblemsManager currentProblemsManager) {
    final CurrentProblems currentProblems = currentProblemsManager.getProblemsForProject(affectedProject);
    final Map<TestName, List<STestRun>> failingTests = currentProblems.getFailingTests();
    final Map<TestName, List<STestRun>> mutedTestFailures = currentProblems.getMutedTestFailures();
    final Set<STestRun> result = new java.util.HashSet<STestRun>(failingTests.size() + mutedTestFailures.size());
    //todo: check whether STestRun is OK to put into the set
    for (List<STestRun> testRuns : failingTests.values()) {
      result.addAll(testRuns);
    }
    for (List<STestRun> testRuns : mutedTestFailures.values()) {
      result.addAll(testRuns);
    }
    return new ArrayList<STestRun>(result);
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

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension, isCurrentlyInvestigated(item));
        }
      });
    }

    final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) { //todo: TeamCity API (MP): is there an API way to figure out there is a mute for a STestRun ?
          return FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, isCurrentlyMuted(item));
        }
      });
    }

    final Boolean muteDimension = locator.getSingleDimensionValueAsBoolean(MUTED);
    if (muteDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return FilterUtil.isIncludedByBooleanFilter(muteDimension, item.isMuted());
        }
      });
    }

    final String currentDimension = locator.getSingleDimensionValue(CURRENT);
    if (currentDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return !item.isFixed(); //todo: is this the same as the test occurring in current problems???
        }
      });
    }

    return result;
  }

  public boolean isCurrentlyMuted(@NotNull final STestRun item) {  //todo: TeamCity API (MP): is there an API way to figure out there is an investigation for a STestRun ?
    final CurrentMuteInfo currentMuteInfo = item.getTest().getCurrentMuteInfo();
    if (currentMuteInfo == null){
      return false;
    }
    final SBuildType buildType = item.getBuild().getBuildType();
    if (buildType == null){
      return false; //might need to log this
    }

    if (currentMuteInfo.getBuildTypeMuteInfo().keySet().contains(buildType)) return true;

    final Set<SProject> projects = currentMuteInfo.getProjectsMuteInfo().keySet();
    for (SProject project : projects) {
      if (ProjectFinder.isSameOrParent(project, buildType.getProject())) return true;
    }
    return false;
  }

  public boolean isCurrentlyInvestigated(@NotNull final STestRun item) {  //todo: TeamCity API (MP): is there an API way to figure out there is an investigation for a STestRun ?
    final List<TestNameResponsibilityEntry> testResponsibilities =
      item.getTest().getAllResponsibilities();//todo: TeamCity API (MP): what is the difference with getResponsibility() ?
    for (TestNameResponsibilityEntry testResponsibility : testResponsibilities) {
      final SBuildType buildType = item.getBuild().getBuildType();
      if (buildType != null) {  //might need to log this
        if (ProjectFinder.isSameOrParent(testResponsibility.getProject(), buildType.getProject())) {
          return true;
        }
      }
    }
    return false;
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
