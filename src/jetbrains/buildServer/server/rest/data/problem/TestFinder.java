package jetbrains.buildServer.server.rest.data.problem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.tests.TestName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class TestFinder extends AbstractFinder<STest> {
  private static final String NAME = "name";
  public static final String AFFECTED_PROJECT = "affectedProject";
  private static final String CURRENT = "current";
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String CURRENTLY_MUTED = "currentlyMuted";

  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final STestManager myTestManager;
  @NotNull private final TestName2IndexImpl myTestName2Index; //TeamCIty open API issue
  @NotNull private final CurrentProblemsManager myCurrentProblemsManager;

  public TestFinder(final @NotNull ProjectFinder projectFinder,
                    final @NotNull STestManager testManager,
                    final @NotNull TestName2IndexImpl testName2Index,
                    final @NotNull CurrentProblemsManager currentProblemsManager) {
    super(new String[]{DIMENSION_ID, NAME, AFFECTED_PROJECT, CURRENT, CURRENTLY_INVESTIGATED, CURRENTLY_MUTED, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME}); //todo: specify dimensions
    myTestManager = testManager;
    myProjectFinder = projectFinder;
    myTestName2Index = testName2Index;
    myCurrentProblemsManager = currentProblemsManager;
  }

  public static String getTestLocator(final @NotNull STest test) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(test.getTestNameId())).getStringRepresentation();
  }

  @Override
  @Nullable
  protected STest findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting id, found empty value.");
      }
      STest item = findTest(parsedId);
      if (item == null) {
        throw new NotFoundException("No test can be found by id '" + parsedId + "' on the entire server.");
      }
      locator.checkLocatorFullyProcessed();
      return item;
    }

    // dimension-specific item search

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      STest item = findTest(id);
      if (item == null) {
        throw new NotFoundException("No test" + " can be found by " + DIMENSION_ID + " '" + id + "' on the entire server.");
      }
      return item;
    }

    String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null) {
      final Long testNameId = myTestName2Index.findTestNameId(new TestName(nameDimension));
      if (testNameId == null) {
        throw new NotFoundException("No test can be found by " + NAME + " '" + nameDimension + "' on the entire server.");
      }
      return findTest(testNameId);
    }

    return null;
  }

  @Override
  @NotNull
  public List<STest> getAllItems() {
    //todo: TeamCity API: find a way to do this
    throw new BadRequestException("Listing all tests is not supported. Try locator dimensions: " + CURRENT + ":true");
  }

  @Override
  protected List<STest> getPrefilteredItems(@NotNull final Locator locator) {
    final SProject affectedProject;
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      affectedProject = myProjectFinder.getProject(affectedProjectDimension);
    }else{
      affectedProject = myProjectFinder.getRootProject();
    }

    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      return getCurrentlyFailingTest(affectedProject);
    }

    throw new BadRequestException("Listing all tests is not supported. Try locator dimensions: " + CURRENT + ":true");
  }

  private List<STest> getCurrentlyFailingTest(@NotNull final SProject affectedProject) {
    final List<STestRun> failingTestOccurrences = TestOccurrenceFinder.getCurrentOccurences(affectedProject, myCurrentProblemsManager);
    final HashSet<STest> result = new HashSet<STest>(failingTestOccurrences.size());
    for (STestRun testOccurrence : failingTestOccurrences) {
      result.add(testOccurrence.getTest());
    }
    return new ArrayList<STest>(result);
  }

  @Override
  protected AbstractFilter<STest> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<STest> result =
      new MultiCheckerFilter<STest>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null) {
      result.add(new FilterConditionChecker<STest>() {
        public boolean isIncluded(@NotNull final STest item) {
          return nameDimension.equals(item.getName().getAsString());
        }
      });
    }

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(new FilterConditionChecker<STest>() {
        public boolean isIncluded(@NotNull final STest item) {
          //todo: check investigation in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension, !item.getAllResponsibilities().isEmpty());
        }
      });
    }

    final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null) {
      result.add(new FilterConditionChecker<STest>() {
        public boolean isIncluded(@NotNull final STest item) {
          //todo: check mute in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, item.getCurrentMuteInfo() != null);
        }
      });
    }

    return result;
  }

  @Nullable
  public STest findTest(final @NotNull Long testNameId) {
    return myTestManager.findTest(testNameId, myProjectFinder.getRootProject().getProjectId()); //STest in root project should have all the data across entire server
  }
}
