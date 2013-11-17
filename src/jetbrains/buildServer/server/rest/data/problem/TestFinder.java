package jetbrains.buildServer.server.rest.data.problem;

import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class TestFinder extends AbstractFinder<STest> {
  @NotNull private final TestBridge myTestBridge;
  @NotNull private final ProjectFinder myProjectFinder;

  public TestFinder(final @NotNull TestBridge testBridge,
                    final @NotNull ProjectFinder projectFinder) {
    super(testBridge, new String[]{Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, DIMENSION_ID, "name", "project"}); //todo: specify dimensions
    myTestBridge = testBridge;
    myProjectFinder = projectFinder;
  }

  public TestBridge getTestBridge() {
    return myTestBridge;
  }

  @Override
  @Nullable
  protected STest findSingleItem(final Locator locator) {
    if (locator.isSingleValue()) {
      return null;
      /*
      // no dimensions found, assume it's id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting id, found empty value.");
      }
      STest item = myTestBridge.findTest(parsedId, null);
      if (item == null) {
        throw new NotFoundException("No test can be found by id '" + parsedId + "'.");
      }
      locator.checkLocatorFullyProcessed();
      return item;
      */
    }

    // dimension-specific item search

    String projectDimension = locator.getSingleDimensionValue("project");
    if (projectDimension != null) {
      SProject project = myProjectFinder.getProject(projectDimension);
      Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
      if (id != null) {
        STest item = myTestBridge.findTest(id, project.getProjectId());
        if (item == null) {
          throw new NotFoundException("No test" + " can be found by " + DIMENSION_ID + " '" + id + "' in project " + project.describe(false));
        }
        return item;
      }

      /*
      String nameDimension = locator.getSingleDimensionValue("name");
      if (nameDimension != null) {
        STest item = myTestBridge.findTestByName(nameDimension);
        if (item == null) {
          throw new NotFoundException("No test" + " can be found by name '" + nameDimension + "'.");
        }
        return item;
      }
      */
    }
    return null;
  }

  @Override
  protected AbstractFilter<STest> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<STest> result =
      new MultiCheckerFilter<STest>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String nameDimension = locator.getSingleDimensionValue("name");
    if (nameDimension != null) {
      result.add(new FilterConditionChecker<STest>() {
        public boolean isIncluded(@NotNull final STest item) {
          return nameDimension.equals(item.getName().getAsString());
        }
      });
    }
    return result;
  }
}
