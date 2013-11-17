package jetbrains.buildServer.server.rest.data.investigations;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.users.User;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class InvestigationFinder extends AbstractFinder<InvestigationWrapper> {
  public static final String PROBLEM_DIMENSION = "problem";
  public static final String TEST_DIMENSION = "test";
  private final ResponsibilityEntryBridge myResponsibilityEntryBridge;
  private final ProjectFinder myProjectFinder;
  private final ProblemFinder myProblemFinder;
  private final TestFinder myTestFinder;
  private final UserFinder myUserFinder;

  public InvestigationFinder(final ResponsibilityEntryBridge responsibilityEntryBridge,
                             final ProjectFinder projectFinder,
                             final ProblemFinder problemFinder,
                             final TestFinder testFinder,
                             final UserFinder userFinder) {
    super(responsibilityEntryBridge, new String[]{"assignee", "reporter", "type", "state", "assignmentProject", PROBLEM_DIMENSION});
    myResponsibilityEntryBridge = responsibilityEntryBridge;
    myProjectFinder = projectFinder;
    myProblemFinder = problemFinder;
    myTestFinder = testFinder;
    myUserFinder = userFinder;
  }

  public ResponsibilityEntryBridge getResponsibilityEntryBridge() {
    return myResponsibilityEntryBridge;
  }

  @Override
  protected InvestigationWrapper findSingleItem(final Locator locator) {
    return null;

    /*
    // dimension-specific item search
    String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (id != null) {
      InvestigationWrapper item = findItemById(id);
      if (item == null) {
        throw new NotFoundException("No investigation" + " can be found by " + DIMENSION_ID + " '" + id + "'.");
      }
      return item;
    }
    */
  }

  @Override
  protected AbstractFilter<InvestigationWrapper> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<InvestigationWrapper> result =
      new MultiCheckerFilter<InvestigationWrapper>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String investigatorDimension = locator.getSingleDimensionValue("assignee");
    if (investigatorDimension != null) {
      @NotNull final User user = myUserFinder.getUser(investigatorDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return user.equals(item.getResponsibleUser());
        }
      });
    }

    final String reporterDimension = locator.getSingleDimensionValue("reporter");
    if (reporterDimension != null) {
      @NotNull final User user = myUserFinder.getUser(reporterDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return user.equals(item.getReporterUser());
        }
      });
    }

    final String typeDimension = locator.getSingleDimensionValue("type");
    if (typeDimension != null) {
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return typeDimension.equals(item.getType());
        }
      });
    }

    final String stateDimension = locator.getSingleDimensionValue("state");
    if (stateDimension != null) {
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return stateDimension.equals(item.getState().name());
        }
      });
    }

    final String projectDimension = locator.getSingleDimensionValue("assignmentProject");
    if (projectDimension != null) {
      @NotNull final SProject project = myProjectFinder.getProject(projectDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return myResponsibilityEntryBridge.isInvestigationRelatedToProject(item, project);
        }
      });
    }

//todo: add affectedProject, affectedBuildType
    return result;
  }

  @Override
  protected List<InvestigationWrapper> getPrefilteredItems(@NotNull final Locator locator) {
    final String problemDimension = locator.getSingleDimensionValue(PROBLEM_DIMENSION);
    if (problemDimension != null){
      final BuildProblem problem = myProblemFinder.getItem(problemDimension);
      return getInvestigationWrappers(problem);
    }

    final String testDimension = locator.getSingleDimensionValue(TEST_DIMENSION);
    if (testDimension != null){
      final STest test = myTestFinder.getItem(testDimension);
      return getInvestigationWrappers(test);
    }
    return super.getPrefilteredItems(locator);
  }

  private List<InvestigationWrapper> getInvestigationWrappers(final BuildProblem problem) {
    final List<BuildProblemResponsibilityEntry> responsibilities = problem.getAllResponsibilities();
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>(responsibilities.size());
    for (BuildProblemResponsibilityEntry responsibility : responsibilities) {
      result.add(new InvestigationWrapper(responsibility));
    }
    return result;
  }

  private List<InvestigationWrapper> getInvestigationWrappers(final STest item) {
    final List<TestNameResponsibilityEntry> responsibilities = item.getAllResponsibilities();
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>(responsibilities.size());
    for (TestNameResponsibilityEntry responsibility : responsibilities) {
      result.add(new InvestigationWrapper(responsibility));
    }
    return result;
  }

  /*
  @NotNull
  public InvestigationWrapper getItem(@Nullable final String locatorText) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty VCS root intance locator is not supported.");
    }
    final Locator locator = createVcsRootInstanceLocator(locatorText);

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's root instance id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting VCS root instance id, found empty value.");
      }
      VcsRootInstance root = myVcsManager.findRootInstanceById(parsedId);
      if (root == null) {
        throw new NotFoundException("No VCS root instance can be found by id '" + parsedId + "'.");
      }
      locator.checkLocatorFullyProcessed();
      return root;
    }

    locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
    final PagedSearchResult<VcsRootInstance> vcsRoots = getVcsRootInstances(locator);
    if (vcsRoots.myEntries.size() == 0) {
      throw new NotFoundException("No VCS root instances are found by locator '" + locatorText + "'.");
    }
    assert vcsRoots.myEntries.size()== 1;
    return vcsRoots.myEntries.get(0);
  }
  */
}
