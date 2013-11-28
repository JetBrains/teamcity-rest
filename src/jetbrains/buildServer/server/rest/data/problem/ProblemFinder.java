package jetbrains.buildServer.server.rest.data.problem;

import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
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
  private static final String CURRENT = "current";

  @NotNull private final ProjectFinder myProjectFinder;

  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final ServiceLocator myServiceLocator;

  public ProblemFinder(final @NotNull ProjectFinder projectFinder,
                       final @NotNull BuildProblemManager buildProblemManager,
                       final @NotNull ProjectManager projectManager,
                       final @NotNull ServiceLocator serviceLocator) {
    super(new String[]{DIMENSION_ID, "identity", "type", "build", "affectedProject", CURRENT, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME});
    myProjectFinder = projectFinder;
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
  }

  public static String getLocator(final ProblemWrapper problem) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(problem.getId())).getStringRepresentation();
  }

  public static String getLocator(final int problemId) {
    return Locator.createEmptyLocator().setDimension(DIMENSION_ID, String.valueOf(problemId)).getStringRepresentation();
  }

  @Override
  protected ProblemWrapper findSingleItem(@NotNull final Locator locator) {
    Long id = getProblemIdByLocator(locator);
    if (id != null) {
      ProblemWrapper item =  findProblemWrapperById(id);
      if (item == null) {
        throw new NotFoundException("No problem" + " can be found by id '" + id + "'.");
      }
      return item;
    }

    return null;
  }

  @Nullable
  public static Long getProblemIdByLocator(@NotNull final Locator locator){
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      final Long parsedId = locator.getSingleValueAsLong();
      if (parsedId == null) {
        throw new BadRequestException("Expecting id, found empty value.");
      }
      return parsedId;
    }

    // dimension-specific item search
    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      return id;
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
    throw new BadRequestException("Listing all problems is not supported. Try locator dimensions: " + Arrays.toString(getKnownDimensions()));
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

    //todo: TeamCity API: find a way to do this
    throw new BadRequestException("Listing all problems is not supported. Try locator dimensions: " + Arrays.toString(getKnownDimensions()));
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

  //todo: TeamCity API (VB): should find even not current (and also converted) problems
  //todo: TeamCity API: how to do this effectively?
  @Nullable
  public static BuildProblem findProblemById(@NotNull final Long id, @NotNull final ServiceLocator serviceLocator) {
    // find in current problems
    final List<BuildProblem> currentBuildProblemsList = serviceLocator.getSingletonService(BuildProblemManager.class).getCurrentBuildProblemsList(
      serviceLocator.getSingletonService(ProjectManager.class).getRootProject());
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      if (id.equals(Long.valueOf(buildProblem.getId()))) return buildProblem; //todo: TeamCity API: can a single id appear several times here?
    }

    // find in last problems
    final Collection<BuildProblem> lastBuildProblems =
      serviceLocator.getSingletonService(BuildProblemManager.class).getLastBuildProblems(Collections.singleton(id.intValue()), null);
    if (lastBuildProblems.size() > 1){
      throw new InvalidStateException("Several build problems returned for one problem id '" + id + "'");
    }

    if (lastBuildProblems.size() == 1){
      return lastBuildProblems.iterator().next();
    }

    final List<BuildProblem> problemOccurrences = ProblemOccurrenceFinder.getProblemOccurrences(id, serviceLocator, serviceLocator.getSingletonService(BuildFinder.class));

    if (problemOccurrences.size() == 0){
      return null;
    }
    return problemOccurrences.iterator().next();
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
