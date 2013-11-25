package jetbrains.buildServer.server.rest.data.investigations;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.responsibility.*;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class InvestigationFinder extends AbstractFinder<InvestigationWrapper> {
  public static final String PROBLEM_DIMENSION = "problem";
  public static final String TEST_DIMENSION = "test";
  private final ProjectFinder myProjectFinder;
  private final ProblemFinder myProblemFinder;
  private final TestFinder myTestFinder;
  private final UserFinder myUserFinder;

  private final BuildTypeResponsibilityFacade myBuildTypeResponsibilityFacade;
  private final TestNameResponsibilityFacade myTestNameResponsibilityFacade;
  private final BuildProblemResponsibilityFacade myBuildProblemResponsibilityFacade;

  public InvestigationFinder(final ProjectFinder projectFinder,
                             final ProblemFinder problemFinder,
                             final TestFinder testFinder,
                             final UserFinder userFinder,
                             final BuildTypeResponsibilityFacade buildTypeResponsibilityFacade,
                             final TestNameResponsibilityFacade testNameResponsibilityFacade,
                             final BuildProblemResponsibilityFacade buildProblemResponsibilityFacade) {
    super(new String[]{"assignee", "reporter", "type", "state", "assignmentProject", PROBLEM_DIMENSION});
    myProjectFinder = projectFinder;
    myProblemFinder = problemFinder;
    myTestFinder = testFinder;
    myUserFinder = userFinder;
    myBuildTypeResponsibilityFacade = buildTypeResponsibilityFacade;
    myTestNameResponsibilityFacade = testNameResponsibilityFacade;
    myBuildProblemResponsibilityFacade = buildProblemResponsibilityFacade;
  }

  @Override
  protected InvestigationWrapper findSingleItem(@NotNull final Locator locator) {
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

  @NotNull
  public List<InvestigationWrapper> getAllItems() {
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>();

    final List<BuildTypeResponsibilityEntry> buildTypeResponsibilities = myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(null, null);
    result.addAll(CollectionsUtil.convertCollection(buildTypeResponsibilities, new Converter<InvestigationWrapper, BuildTypeResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final BuildTypeResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    final List<TestNameResponsibilityEntry> testResponsibilities = myTestNameResponsibilityFacade.getUserTestNameResponsibilities(null, null);
    result.addAll(CollectionsUtil.convertCollection(testResponsibilities, new Converter<InvestigationWrapper, TestNameResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final TestNameResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    final List<BuildProblemResponsibilityEntry> problemResponsibilities = myBuildProblemResponsibilityFacade.getUserBuildProblemResponsibilities(null, null);
    result.addAll(CollectionsUtil.convertCollection(problemResponsibilities, new Converter<InvestigationWrapper, BuildProblemResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final BuildProblemResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    //todo: sort!
    return result;
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
          return isInvestigationRelatedToProject(item, project);
        }
      });
    }

    final String problemDimension = locator.getSingleDimensionValue(PROBLEM_DIMENSION);
    if (problemDimension != null) {
      @NotNull final ProblemWrapper problem = myProblemFinder.getSingleItem(problemDimension);
      result.add(new FilterConditionChecker<InvestigationWrapper>() {
        public boolean isIncluded(@NotNull final InvestigationWrapper item) {
          return isInvestigationRelatedToProblem(item, problem);
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
      final ProblemWrapper problem = myProblemFinder.getItem(problemDimension);
      return problem.getInvestigations();
    }

    final String testDimension = locator.getSingleDimensionValue(TEST_DIMENSION);
    if (testDimension != null){
      final STest test = myTestFinder.getItem(testDimension);
      return getInvestigationWrappers(test);
    }
    return super.getPrefilteredItems(locator);
  }

  private List<InvestigationWrapper> getInvestigationWrappers(final STest item) {
    final List<TestNameResponsibilityEntry> responsibilities = item.getAllResponsibilities();
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>(responsibilities.size());
    for (TestNameResponsibilityEntry responsibility : responsibilities) {
      result.add(new InvestigationWrapper(responsibility));
    }
    return result;
  }


  @SuppressWarnings("RedundantIfStatement")
  private boolean isInvestigationRelatedToProject(final InvestigationWrapper item, final SProject project) {
    if (myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(null, project.getProjectId()).contains(item.getBuildTypeRE())){
      return true;
    }
    if (myTestNameResponsibilityFacade.getUserTestNameResponsibilities(null, project.getProjectId()).contains(item.getTestRE())){
      return true;
    }
    if (myBuildProblemResponsibilityFacade.getUserBuildProblemResponsibilities(null, project.getProjectId()).contains(item.getProblemRE())){
      return true;
    }
    return false;
  }

  private boolean isInvestigationRelatedToProblem(final InvestigationWrapper item, final ProblemWrapper problem) {
    if (!item.isProblem()){
      return false;
    }
    @SuppressWarnings("ConstantConditions") @NotNull final BuildProblemResponsibilityEntry problemRE = item.getProblemRE();
    return problemRE.getBuildProblemInfo().getId() == problem.getId() && problemRE.getBuildProblemInfo().getProjectId() == problem.getProject().getProjectId();
  }

  public BuildTypeResponsibilityEntry getBuildTypeRE(@NotNull final SBuildType buildType) {
    final List<BuildTypeResponsibilityEntry> userBuildTypeResponsibilities = myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(null, null);
    for (BuildTypeResponsibilityEntry responsibility : userBuildTypeResponsibilities) {
      if (responsibility.getBuildType().equals(buildType)){
        return responsibility;
      }
    }
    throw new NotFoundException("Build type with id '" + buildType.getExternalId() + "' does not have associated investigation.");
  }
}
