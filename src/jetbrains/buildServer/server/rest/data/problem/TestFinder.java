package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestManager;
import jetbrains.buildServer.serverSide.TestName2IndexImpl;
import jetbrains.buildServer.tests.TestName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class TestFinder extends AbstractFinder<STest> {
  public static final String NAME = "name";
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final STestManager myTestManager;
  @NotNull private final TestName2IndexImpl myTestName2Index; //TeamCIty open API issue

  public TestFinder(final @NotNull ProjectFinder projectFinder,
                    final @NotNull STestManager testManager,
                    final @NotNull TestName2IndexImpl testName2Index) {
    super(new String[]{Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, DIMENSION_ID, NAME}); //todo: specify dimensions
    myTestManager = testManager;
    myProjectFinder = projectFinder;
    myTestName2Index = testName2Index;
  }

  public static String getTestLocator(final @NotNull STest test) {
    return Locator.createEmptyLocator().setDimension("id", String.valueOf(test.getTestNameId())).getStringRepresentation();
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
    throw new IllegalStateException("Sorry, listing tests is not implemented yet");
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
    return result;
  }

  @Nullable
  public STest findTest(final @NotNull Long testNameId) {
    return myTestManager.findTest(testNameId, myProjectFinder.getRootProject().getProjectId()); //STest in root project should have all the data across entire server
  }
}
