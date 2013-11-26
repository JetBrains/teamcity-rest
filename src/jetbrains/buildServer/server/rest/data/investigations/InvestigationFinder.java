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
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class InvestigationFinder extends AbstractFinder<InvestigationWrapper> {
  public static final String PROBLEM_DIMENSION = "problem";
  public static final String TEST_DIMENSION = "test";
  public static final String ASSIGNMENT_PROJECT = "assignmentProject";
  public static final String AFFECTED_PROJECT = "affectedProject";
  public static final String ASSIGNEE = "assignee";
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
    super(new String[]{ASSIGNEE, "reporter", "type", "state", ASSIGNMENT_PROJECT, AFFECTED_PROJECT, PROBLEM_DIMENSION});
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

  @Override
  @NotNull
  public List<InvestigationWrapper> getAllItems() {
    return getInvestigationWrappersForProject(null, null);
  }

  @Override
  protected AbstractFilter<InvestigationWrapper> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<InvestigationWrapper> result =
      new MultiCheckerFilter<InvestigationWrapper>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String investigatorDimension = locator.getSingleDimensionValue(ASSIGNEE);
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

//todo: add affectedBuildType
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

    @Nullable User user = null;
    final String investigatorDimension = locator.getSingleDimensionValue(ASSIGNEE);
    if (investigatorDimension != null) {
      user = myUserFinder.getUser(investigatorDimension);
    }

    final String assignmentProjectDimension = locator.getSingleDimensionValue(ASSIGNMENT_PROJECT);
    if (assignmentProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getProject(assignmentProjectDimension);

      return getInvestigationWrappersForProject(project, user);
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null){
      @NotNull final SProject project = myProjectFinder.getProject(affectedProjectDimension);
      return getInvestigationWrappersForProjectWithSubprojects(project, user);
    }

    return super.getPrefilteredItems(locator);
  }

  @NotNull
  private List<InvestigationWrapper> getInvestigationWrappersForProjectWithSubprojects(@NotNull final SProject project, @Nullable User user) {
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>();

    result.addAll(getInvestigationWrappersForProject(project, user));

    //todo: TeamCity API: is there a dedicated wahy to do this?
    final List<SProject> subProjects = project.getProjects();
    for (SProject subProject : subProjects) {
      result.addAll(getInvestigationWrappersForProject(subProject, user));
    }
    return result;
  }

  private List<InvestigationWrapper> getInvestigationWrappersForProject(@Nullable final SProject project, @Nullable User user) {
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>();

    final String projectId = project == null ? null : project.getProjectId();

    final List<BuildTypeResponsibilityEntry> buildTypeResponsibilities = myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(user, projectId);
    result.addAll(CollectionsUtil.convertCollection(buildTypeResponsibilities, new Converter<InvestigationWrapper, BuildTypeResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final BuildTypeResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    final List<TestNameResponsibilityEntry> testResponsibilities = myTestNameResponsibilityFacade.getUserTestNameResponsibilities(user, projectId);
    result.addAll(CollectionsUtil.convertCollection(testResponsibilities, new Converter<InvestigationWrapper, TestNameResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final TestNameResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    final List<BuildProblemResponsibilityEntry> problemResponsibilities = myBuildProblemResponsibilityFacade.getUserBuildProblemResponsibilities(user, projectId);
    result.addAll(CollectionsUtil.convertCollection(problemResponsibilities, new Converter<InvestigationWrapper, BuildProblemResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final BuildProblemResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    //todo: sort!
    return result;
  }

  @NotNull
  private List<InvestigationWrapper> getInvestigationWrappers(@NotNull final STest item) {
    final List<TestNameResponsibilityEntry> responsibilities = item.getAllResponsibilities();
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>(responsibilities.size());
    for (TestNameResponsibilityEntry responsibility : responsibilities) {
      result.add(new InvestigationWrapper(responsibility));
    }
    return result;
  }


  private boolean isInvestigationRelatedToProblem(@NotNull final InvestigationWrapper item, @NotNull final ProblemWrapper problem) {
    if (!item.isProblem()){
      return false;
    }
    @SuppressWarnings("ConstantConditions") @NotNull final BuildProblemResponsibilityEntry problemRE = item.getProblemRE();
    return problemRE.getBuildProblemInfo().getId() == problem.getId();
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
