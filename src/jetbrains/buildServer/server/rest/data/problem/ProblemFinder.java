package jetbrains.buildServer.server.rest.data.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class ProblemFinder extends AbstractFinder<ProblemWrapper> {
  public static final String CURRENT = "current";

  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final BuildFinder myBuildFinder;

  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull final ServiceLocator myServiceLocator;

  public ProblemFinder(final @NotNull ProjectFinder projectFinder,
                       final @NotNull UserFinder userFinder,
                       final @NotNull BuildFinder buildFinder,
                       final @NotNull BuildProblemManager buildProblemManager,
                       final @NotNull ProjectManager projectManager,
                       final @NotNull ServiceLocator serviceLocator) {
    super(new String[]{"identity", "type", "build", "affectedProject", CURRENT});
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myBuildFinder = buildFinder;
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
  }

  @Override
  protected ProblemWrapper findSingleItem(@NotNull final Locator locator) {
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting id, found empty value.");
      }
      ProblemWrapper item = findProblemWrapperById(parsedId);
      if (item == null) {
        throw new NotFoundException("No problem can be found by id '" + parsedId + "'.");
      }
      locator.checkLocatorFullyProcessed();
      return item;
    }

    // dimension-specific item search
    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      ProblemWrapper item =  findProblemWrapperById(id);
      if (item == null) {
        throw new NotFoundException("No problem" + " can be found by " + DIMENSION_ID + " '" + id + "'.");
      }
      return item;
    }

    return null;
  }

  @NotNull
  public ProblemWrapper getSingleItem(@NotNull final String locator) {
    final ProblemWrapper singleItem = findSingleItem(getLocatorOrNull(locator));
    if (singleItem == null){
      throw new NotFoundException("Cannot find problem by locator '" + locator + "'");
    }
    return singleItem;
  }

  @Override
  @NotNull
  public List<ProblemWrapper> getAllItems() {
    throw new BadRequestException("Listing all problems is not supported. Consider using locator: '" + CURRENT + "=true'");
  }

  @Override
  protected List<ProblemWrapper> getPrefilteredItems(@NotNull final Locator locator) {
    Boolean currentDimension = locator.getSingleDimensionValueAsBoolean(CURRENT);
    if (currentDimension!= null && currentDimension) {
      final String affectedProjectDimension = locator.getSingleDimensionValue("affectedProject");
      if (affectedProjectDimension != null) {
        @NotNull final SProject project = myProjectFinder.getProject(affectedProjectDimension);
        return getCurrentProblemsList(project);
      }
      return getCurrentProblemsList(null);
    }

    throw new BadRequestException("Listing all problems is not supported. Consider using locator: '" + CURRENT + "=true'");
  }

  @Override
  protected AbstractFilter<ProblemWrapper> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<ProblemWrapper> result =
      new MultiCheckerFilter<ProblemWrapper>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String identityDimension = locator.getSingleDimensionValue("identity");
    if (identityDimension != null) {
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          return identityDimension.equals(item.getIdentity());
        }
      });
    }

    final String typeDimension = locator.getSingleDimensionValue("type");
    if (typeDimension != null) {
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          return typeDimension.equals(item.getType());
        }
      });
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue("affectedProject");
    if (affectedProjectDimension != null) {
      @NotNull final SProject project = myProjectFinder.getProject(affectedProjectDimension);
      final Set<ProblemWrapper> currentProjectProblems = new TreeSet<ProblemWrapper>(getCurrentProblemsList(project));
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          return currentProjectProblems.contains(item);  //todo: TeamCity API (VB): is there a dedicated API call for this?
        }
      });
    }

    final String currentDimension = locator.getSingleDimensionValue(CURRENT);
    if (currentDimension != null) {
      final Set<ProblemWrapper> currentProblems = new TreeSet<ProblemWrapper>(getCurrentProblemsList(null));
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          return currentProblems.contains(item);
        }
      });
    }

    return result;
  }

  //todo: TeamCity API: how to do this effectively?
  @Nullable
  private ProblemWrapper findProblemWrapperById(@NotNull final Long id) {
    final BuildProblem problemById = findProblemById(id, myServiceLocator);
    if (problemById == null){
      throw new NotFoundException("Cannot find problem instance by id '" + id + "'");
    }
    return new ProblemWrapper(problemById.getId(), myServiceLocator);
  }

  //todo: TeamCity API (VB): should find even not current problems
  //todo: TeamCity API: how to do this effectively?
  @Nullable
  public static BuildProblem findProblemById(@NotNull final Long id, @NotNull final ServiceLocator serviceLocator) {
    final List<BuildProblem> currentBuildProblemsList = serviceLocator.getSingletonService(BuildProblemManager.class).getCurrentBuildProblemsList(
      serviceLocator.getSingletonService(ProjectManager.class).getRootProject());
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      if (id.equals(Long.valueOf(buildProblem.getId()))) return buildProblem; //todo: TeamCity API: can a single id appear several times here?
    }
    return null;
  }

  @NotNull
  private List<ProblemWrapper> getCurrentProblemsList(@Nullable SProject project) {
    if (project == null){
      project = myProjectManager.getRootProject();
    }
    final List<BuildProblem> currentBuildProblemsList = myBuildProblemManager.getCurrentBuildProblemsList(project);

    @NotNull final Set<ProblemWrapper> resultSet = new TreeSet<ProblemWrapper>();
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      resultSet.add(new ProblemWrapper(buildProblem.getId(), myServiceLocator));
    }

    return new ArrayList<ProblemWrapper>(resultSet);
  }
}
