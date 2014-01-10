package jetbrains.buildServer.server.rest.data.problem;

import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.Constants;
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
  public static final String CURRENTLY_INVESTIGATED = "currentlyInvestigated";
  public static final String MUTED = "muted";
  public static final String CURRENTLY_MUTED = "currentlyMuted";
  public static final String AFFECTED_PROJECT = "affectedProject";

  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final BuildFinder myBuildFinder;
  @NotNull private final ProblemFinder myProblemFinder;

  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final jetbrains.buildServer.ServiceLocator myServiceLocator;

  public ProblemOccurrenceFinder(final @NotNull ProjectFinder projectFinder,
                                 final @NotNull BuildFinder buildFinder,
                                 final @NotNull ProblemFinder problemFinder,
                                 final @NotNull BuildProblemManager buildProblemManager,
                                 final @NotNull ProjectManager projectManager,
                                 final @NotNull ServiceLocator serviceLocator) {
    super(new String[]{PROBLEM, IDENTITY, "type", "build", AFFECTED_PROJECT, CURRENT, MUTED, CURRENTLY_MUTED, CURRENTLY_INVESTIGATED, DIMENSION_LOOKUP_LIMIT, PagerData.START,
      PagerData.COUNT});
    myProjectFinder = projectFinder;
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
      } else{
        locator.markUnused(BUILD, PROBLEM);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<BuildProblem> getAllItems() {
    ArrayList<String> exampleLocators = new ArrayList<String>();
    exampleLocators.add(Locator.getStringLocator(DIMENSION_ID, "XXX"));
    exampleLocators.add(Locator.getStringLocator(BUILD, "XXX"));
    exampleLocators.add(Locator.getStringLocator(PROBLEM, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENT, "true", AFFECTED_PROJECT, "XXX"));
    exampleLocators.add(Locator.getStringLocator(CURRENTLY_MUTED, "true", AFFECTED_PROJECT, "XXX"));
    throw new BadRequestException("Listing all problem occurrences is not supported. Try one of locator dimensions: " + DataProvider.dumpQuoted(exampleLocators));
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
      return getCurrentProblemOccurences(getAffectedProject(locator));
    }

    String problemDimension = locator.getSingleDimensionValue(PROBLEM);
    if (problemDimension != null) {
      final PagedSearchResult<ProblemWrapper> problems = myProblemFinder.getItems(problemDimension);
      final ArrayList<BuildProblem> result = new ArrayList<BuildProblem>();
      for (ProblemWrapper problem : problems.myEntries) {
        result.addAll(getProblemOccurrences(problem));
      }
      return result;
    }

    Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null && currentlyMutedDimension) {
      final SProject affectedProject = getAffectedProject(locator);
      final List<ProblemWrapper> currentlyMutedProblems = myProblemFinder.getCurrentlyMutedProblems(affectedProject);
      final ArrayList<BuildProblem> result = new ArrayList<BuildProblem>();
      for (ProblemWrapper problem : currentlyMutedProblems) {
        result.addAll(getProblemOccurrences(Long.valueOf(problem.getId()), myServiceLocator, myBuildFinder));
      }
      return result;
    }

    return super.getPrefilteredItems(locator);
  }

  @Override
  protected AbstractFilter<BuildProblem> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<BuildProblem> result = new MultiCheckerFilter<BuildProblem>(locator.getSingleDimensionValueAsLong(PagerData.START),
                                                                                         countFromFilter != null ? countFromFilter.intValue() : null,
                                                                                         locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT));

    String problemDimension = locator.getSingleDimensionValue(PROBLEM);
    if (problemDimension != null) {
      final PagedSearchResult<ProblemWrapper> problems = myProblemFinder.getItems(problemDimension);
      final HashSet<Integer> problemIds = new HashSet<Integer>();
      for (ProblemWrapper problem : problems.myEntries) {
        problemIds.add(problem.getId().intValue());
      }
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return problemIds.contains(item.getId());
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

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      @NotNull final SProject project = myProjectFinder.getProject(affectedProjectDimension);
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return ProjectFinder.isSameOrParent(project, myProjectFinder.getProject(item.getProjectId()));
        }
      });
    }

    final Boolean currentlyInvestigatedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_INVESTIGATED);
    if (currentlyInvestigatedDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          //todo: check investigation in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyInvestigatedDimension,
                                                      !item.getAllResponsibilities().isEmpty());  //todo: TeamCity API (VM): what is the difference with   getResponsibility() ???
        }
      });
    }

    final Boolean currentlyMutedDimension = locator.getSingleDimensionValueAsBoolean(CURRENTLY_MUTED);
    if (currentlyMutedDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          //todo: check in affected Project/buildType only, if set
          return FilterUtil.isIncludedByBooleanFilter(currentlyMutedDimension, item.getCurrentMuteInfo() != null);
        }
      });
    }

    final Boolean muteDimension = locator.getSingleDimensionValueAsBoolean(MUTED);
    if (muteDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return FilterUtil.isIncludedByBooleanFilter(muteDimension, item.getMuteInBuildInfo() != null);
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
        //todo: TeamCity API, JavaDoc (VB): add into the JavaDoc that problem with a given id can only occur once in a build
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
  public static List<BuildProblem> getProblemOccurrences(@NotNull final SBuild build) {
    return ((BuildPromotionEx)build.getBuildPromotion()).getBuildProblems();
  }

  @NotNull
  private SProject getAffectedProject(@NotNull final Locator locator) {
    String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT);
    if (affectedProjectDimension != null) {
      return myProjectFinder.getProject(affectedProjectDimension);
    }else{
      return myProjectFinder.getRootProject();
    }
  }
}
