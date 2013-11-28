package jetbrains.buildServer.server.rest.data.problem;

import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.db.DBActionNoResults;
import jetbrains.buildServer.serverSide.db.DBException;
import jetbrains.buildServer.serverSide.db.DBFunctions;
import jetbrains.buildServer.serverSide.db.SQLRunnerEx;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
public class ProblemOccurrenceFinder extends AbstractFinder<BuildProblem> {
  private static final String BUILD = "build";
  private static final String IDENTITY = "identity";
  private static final String CURRENT = "current";
  private static final String PROBLEM = "problem";

  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final ProblemFinder myProblemFinder;

  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final jetbrains.buildServer.ServiceLocator myServiceLocator;

  public ProblemOccurrenceFinder(final @NotNull ProjectFinder projectFinder,
                                 final @NotNull UserFinder userFinder,
                                 final @NotNull BuildFinder buildFinder,
                                 final @NotNull ProblemFinder problemFinder,
                                 final @NotNull BuildProblemManager buildProblemManager,
                                 final @NotNull ProjectManager projectManager,
                                 final @NotNull ServiceLocator serviceLocator) {
    super(new String[]{PROBLEM, IDENTITY, "type", "build", "affectedProject", CURRENT});
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myBuildFinder = buildFinder;
    myProblemFinder = problemFinder;
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
  }

  public static String getProblemOccurrenceLocator(final @NotNull BuildProblem problem) {
    final SBuild build = problem.getBuildPromotion().getAssociatedBuild();
    if (build == null) {
      throw new InvalidStateException("Build problem with id '" + problem.getId() + "' does not have an associated build.");
    }
    return Locator.createEmptyLocator().setDimension(PROBLEM, ProblemFinder.getLocator(problem.getId())).setDimension(BUILD, BuildRequest
      .getBuildLocator(build)).getStringRepresentation();
  }

  public static String getProblemOccurrenceLocator(final @NotNull SBuild build) {
    return Locator.createEmptyLocator().setDimension(BUILD, BuildRequest.getBuildLocator(build)).getStringRepresentation();
  }

  public static String getProblemOccurrenceLocator(final @NotNull ProblemWrapper problem) {
    return Locator.createEmptyLocator().setDimension(PROBLEM, ProblemFinder.getLocator(problem)).getStringRepresentation();
  }

  @Override
  protected BuildProblem findSingleItem(@NotNull final Locator locator) {
    //todo: searching occurrence by id does not work: review!!!
    if (locator.isSingleValue()) {
      throw new NotFoundException("Single value locators are not supported: Cannot find problem occurrence without build specification.");
    }

    // dimension-specific item search

    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      @NotNull SBuild build = myBuildFinder.getBuild(null, buildDimension);

      String problemDimension = locator.getSingleDimensionValue(PROBLEM);
      if (problemDimension != null) {
        Long problemId = ProblemFinder.getProblemIdByLocator(new Locator(problemDimension));
        if(problemId == null){
          problemId = myProblemFinder.getItem(problemDimension).getId();
        }
        final BuildProblem item = findProblem(build, problemId);
        if (item != null) {
          return item;
        }
        throw new NotFoundException("No problem with id '" + problemId + "' found in build with id " + build.getBuildId());
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<BuildProblem> getAllItems() {
    throw new BadRequestException("Listing all problem occurrences is not supported. Try locator dimensions: " + Arrays.toString(getKnownDimensions()));
  }

  @Override
  protected List<BuildProblem> getPrefilteredItems(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      SBuild build = myBuildFinder.getBuild(null, buildDimension);
      return getProblemOccurrences(build);
    }

    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      final String affectedProjectDimension = locator.getSingleDimensionValue("affectedProject");
      if (affectedProjectDimension != null) {
        @NotNull final SProject project = myProjectFinder.getProject(affectedProjectDimension);
        return getCurrentProblemOccurences(project);
      }
      return getCurrentProblemOccurences(null);
    }

    String problemDimension = locator.getSingleDimensionValue(PROBLEM);
    if (problemDimension != null) {
      final ProblemWrapper problem = myProblemFinder.getItem(problemDimension);
      return getProblemOccurrences(problem);
    }

    throw new BadRequestException("Listing all problem occurrences is not supported. Try locator dimensions: " + Arrays.toString(getKnownDimensions()));
  }

  @Override
  protected AbstractFilter<BuildProblem> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<BuildProblem> result =
      new MultiCheckerFilter<BuildProblem>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);


    String problemDimension = locator.getSingleDimensionValue(PROBLEM);
    if (problemDimension != null) {
      final ProblemWrapper problem = myProblemFinder.getItem(problemDimension);
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return problem.getId() == item.getId();
        }
      });
    }

    final String identityDimension = locator.getSingleDimensionValue(IDENTITY);
    if (identityDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return identityDimension.equals(item.getBuildProblemData().getIdentity());
        }
      });
    }

    final String typeDimension = locator.getSingleDimensionValue("type");
    if (typeDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return typeDimension.equals(item.getBuildProblemData().getType());
        }
      });
    }

    final String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      final SBuild build = myBuildFinder.getBuild(null, buildDimension);
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return build.getBuildPromotion().equals(item.getBuildPromotion());
        }
      });
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue("affectedProject");
    if (affectedProjectDimension != null) {
      @NotNull final SProject project = myProjectFinder.getProject(affectedProjectDimension);
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return project.getProjects().contains(myProjectFinder.getProject(item.getProjectId())); //todo: inneffective! is there an API call for this?
        }
      });
    }

    final String currentDimension = locator.getSingleDimensionValue(CURRENT);
    if (currentDimension != null) {
      @NotNull final Set<Integer> currentBuildProblemsList = new TreeSet<Integer>();
      for (BuildProblem buildProblem : getCurrentProblemOccurences(null)) {
        currentBuildProblemsList.add(buildProblem.getId());
      }
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return currentBuildProblemsList.contains(item.getId());
        }
      });
    }

    return result;
  }

  @NotNull
  private List<BuildProblem> getCurrentProblemOccurences(@Nullable SProject project) {
    if (project == null) {
      project = myProjectManager.getRootProject();
    }
    return myBuildProblemManager.getCurrentBuildProblemsList(project);
    /*
    final List<BuildProblem> currentBuildProblemsList = myBuildProblemManager.getCurrentBuildProblemsList(project);

    @NotNull final Set<BuildProblem> resultSet = new TreeSet<BuildProblem>();
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      resultSet.add(buildProblem);
    }

    return new ArrayList<BuildProblem>(resultSet);
    */
  }

  @Nullable
  private static BuildProblem findProblem(@NotNull final SBuild build, @NotNull final Long problemId) {
    final List<BuildProblem> buildProblems = getProblemOccurrences(build);
    for (BuildProblem buildProblem : buildProblems) {
      if (buildProblem.getId() == problemId.intValue()) {
        //todo: TeamCity API (VB): is this right that problem with a given id can only occur once in a build?
        return buildProblem;
      }
    }
    return null;
  }

  private List<BuildProblem> getProblemOccurrences(final ProblemWrapper problem) {
    return getProblemOccurrences(problem.getId(), myServiceLocator, myBuildFinder);
  }

  @NotNull
  static List<BuildProblem> getProblemOccurrences(@NotNull final Long problemId, @NotNull final ServiceLocator serviceLocator, @NotNull final BuildFinder buildFinder) {
    //todo: TeamCity API (VB): how to do this?
    final ArrayList<BuildProblem> result = new ArrayList<BuildProblem>();
    try {
      //final SQLRunner sqlRunner = myServiceLocator.getSingletonService(SQLRunner.class);
      //workaround for http://youtrack.jetbrains.com/issue/TW-25260
      final SQLRunnerEx sqlRunner = serviceLocator.getSingletonService(BuildServerEx.class).getSQLRunner();
      sqlRunner.withDB(new DBActionNoResults() {
        public void run(final DBFunctions dbf) throws DBException {
          dbf.queryForTuples(new Object() {
            public void getBuildProblem(String build_state_id) throws IOException {
              result.add(findProblem(buildFinder.getBuildByPromotionId(Long.valueOf(build_state_id)), problemId));
            }
          },
                             "getBuildProblem",
                             "select build_state_id from build_problem where problem_id = " + problemId);
        }
      });
    } catch (Exception e) {
      throw new OperationException("Error performing database query: " + e.toString(), e);
    }

    return result;
  }

  @NotNull
  private static List<BuildProblem> getProblemOccurrences(@NotNull final SBuild build) {
    return ((BuildPromotionEx)build.getBuildPromotion()).getBuildProblems();
  }
}
