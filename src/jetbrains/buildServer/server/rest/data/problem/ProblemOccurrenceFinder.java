package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
public class ProblemOccurrenceFinder extends AbstractFinder<BuildProblem> {
  public static final String BUILD = "build";
  public static final String IDENTITY = "identity";
  public static final String ID = "problemId";
  public static final String CURRENT = "current";
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final BuildFinder myBuildFinder;

  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull final jetbrains.buildServer.ServiceLocator myServiceLocator;

  public ProblemOccurrenceFinder(final @NotNull ProjectFinder projectFinder,
                                 final @NotNull UserFinder userFinder,
                                 final @NotNull BuildFinder buildFinder,
                                 final @NotNull BuildProblemManager buildProblemManager,
                                 final @NotNull ProjectManager projectManager,
                                 final @NotNull ServiceLocator serviceLocator) {
    super(new String[]{ID, "identity", "type", "build", "affectedProject", CURRENT});
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myBuildFinder = buildFinder;
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
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

      Long idDimension = locator.getSingleDimensionValueAsLong(ID);
      if (idDimension != null) {
        final BuildProblem item = findProblem(build, idDimension);
        if (item != null) {
          return item;
        }
        throw new NotFoundException("No problem with id '" + idDimension + "' found in build with id " + build.getBuildId());
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<BuildProblem> getAllItems() {
    throw new BadRequestException("Listing all problem occurrences is not supported.");
  }

  @Override
  protected List<BuildProblem> getPrefilteredItems(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      SBuild build = myBuildFinder.getBuild(null, buildDimension);
      return getBuildProblems(build);
    }

    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension != null && currentDimension) {
      final String affectedProjectDimension = locator.getSingleDimensionValue("affectedProject");
      if (affectedProjectDimension != null) {
        @NotNull final SProject project = myProjectFinder.getProject(affectedProjectDimension);
        return getCurrentProblemOccurencesList(project);
      }
      return getCurrentProblemOccurencesList(null);
    }

    throw new BadRequestException("Listing all problem occurrences is not supported.");
  }

  @Override
  protected AbstractFilter<BuildProblem> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<BuildProblem> result =
      new MultiCheckerFilter<BuildProblem>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);


    final Long idDimension = locator.getSingleDimensionValueAsLong(ID);
    if (idDimension != null) {
      result.add(new FilterConditionChecker<BuildProblem>() {
        public boolean isIncluded(@NotNull final BuildProblem item) {
          return idDimension.intValue() == item.getId();
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
      for (BuildProblem buildProblem : getCurrentProblemOccurencesList(null)) {
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

  public static String getProblemOccurrenceLocator(final @NotNull BuildProblem problem) {
    final SBuild build = problem.getBuildPromotion().getAssociatedBuild();
    if (build == null) {
      throw new InvalidStateException("Build problem with id '" + problem.getId() + "' does not have an associated build.");
    }
    return ID + ":" + problem.getId() + "," + getProblemOccurrenceLocator(build);//todo: use locator rendering here
  }

  public static String getProblemOccurrenceLocator(final @NotNull SBuild build) {
    return ProblemOccurrenceFinder.BUILD + ":(" + BuildRequest.getBuildLocator(build) + ")"; //todo: use location rendering here
  }

  @NotNull
  private List<BuildProblem> getCurrentProblemOccurencesList(@Nullable SProject project) {
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
  private BuildProblem findProblem(@NotNull final SBuild build, @NotNull final Long problemId) {
    final List<BuildProblem> buildProblems = getBuildProblems(build);
    for (BuildProblem buildProblem : buildProblems) {
      if (buildProblem.getId() == problemId.intValue()) {
        //todo: TeamCity API (VB): is this right that problem with a given id can only occur once in a build?
        return buildProblem;
      }
    }
    return null;
  }

  @NotNull
  private static List<BuildProblem> getBuildProblems(@NotNull final SBuild build) {
    return ((BuildPromotionEx)build.getBuildPromotion()).getBuildProblems();
  }
}
