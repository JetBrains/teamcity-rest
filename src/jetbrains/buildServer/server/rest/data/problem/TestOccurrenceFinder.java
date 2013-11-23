package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.11.13
 */
public class TestOccurrenceFinder extends AbstractFinder<STestRun> {
  public static final String TEST_NAME_ID = "testNameId";
  public static final String BUILD = "build";
  @NotNull private final BuildFinder myBuildFinder;

  public TestOccurrenceFinder(final @NotNull BuildFinder buildFinder) {
    super(new String[]{Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, DIMENSION_ID, TEST_NAME_ID, BUILD, "status"}); //todo: specify dimensions
    myBuildFinder = buildFinder;
  }

  @Override
  @Nullable
  protected STestRun findSingleItem(final Locator locator) {
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

    // dimension-specific item search

    Long idDimension = locator.getSingleDimensionValueAsLong("id");
    if (idDimension != null) {
      STestRun item = findTestByTestRunId(idDimension);
      if (item != null) {
        return item;
      }
      throw new NotFoundException("No test run with id '" + idDimension + "' found.");
    }


    Long testNameId = locator.getSingleDimensionValueAsLong(TEST_NAME_ID);
    if (testNameId != null) {
      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        SBuild build = myBuildFinder.getBuild(null, buildDimension);
        STestRun item = findTest(testNameId, build);
        if (item == null) {
          throw new NotFoundException("No test run" + " can be found by " + TEST_NAME_ID + " '" + testNameId + "' in build with id " + build.getBuildId());
        }
        return item;
      }
    }
    return null;
  }

  @NotNull
  public List<STestRun> getAllItems() {
    throw new IllegalStateException("Sorry, listing tests is not implemented yet");
  }

  @Override
  protected List<STestRun> getPrefilteredItems(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      SBuild build = myBuildFinder.getBuild(null, buildDimension);
      return build.getFullStatistics().getAllTests();
    }

    //todo: support filtering by project and build type here
    return super.getPrefilteredItems(locator);
  }

  @Override
  protected AbstractFilter<STestRun> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<STestRun> result =
      new MultiCheckerFilter<STestRun>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);


    final String statusDimension = locator.getSingleDimensionValue("status");
    if (statusDimension != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return statusDimension.equals(item.getStatus().getText());
        }
      });
    }

    final Long testNameId = locator.getSingleDimensionValueAsLong(TEST_NAME_ID);
    if (testNameId != null) {
      result.add(new FilterConditionChecker<STestRun>() {
        public boolean isIncluded(@NotNull final STestRun item) {
          return testNameId.equals(item.getTest().getTestNameId());
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
    final List<STestRun> allTests = build.getFullStatistics().getAllTests();
    for (STestRun test : allTests) {
      if (testNameId == test.getTest().getTestNameId()) return test; //todo: does this support multiple test runs???
    }
    return null;
  }

  @Nullable
  private STestRun findTestByTestRunId(final Long testRunId) {
    throw new InvalidStateException("Searching by test run id is not yet supported"); //todo: (TeamCity) how to implement this?
  }
}
