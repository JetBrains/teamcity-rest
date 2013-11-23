package jetbrains.buildServer.server.rest.data.problem;

import java.util.ArrayList;
import java.util.List;
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
    super(new String[]{"identity", "type", "build", "affectedProject"});
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myBuildFinder = buildFinder;
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
  }

  @Override
  protected ProblemWrapper findSingleItem(final Locator locator) {
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
  public List<ProblemWrapper> getAllItems() {
    final List<BuildProblem> currentBuildProblemsList = myBuildProblemManager.getCurrentBuildProblemsList(myProjectManager.getRootProject());
    final ArrayList<ProblemWrapper> result = new ArrayList<ProblemWrapper>(currentBuildProblemsList.size());
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      result.add(new ProblemWrapper(buildProblem, myServiceLocator));
    }
    return result;
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
      result.add(new FilterConditionChecker<ProblemWrapper>() {
        public boolean isIncluded(@NotNull final ProblemWrapper item) {
          return project.getProjects().contains(item.getProject()); //todo: is there a dedicaed API call for this?
        }
      });
    }
    return result;
  }

  //todo: TeamCity API: how to do this effectively?
  @Nullable
  private ProblemWrapper findProblemWrapperById(@NotNull final Long id) {
    final BuildProblem problemById = ProblemOccurrenceFinder.findProblemById(id, myServiceLocator);
    if (problemById == null){
      throw new NotFoundException("Cannot find problem instance by id '" + id + "'");
    }
    return new ProblemWrapper(problemById, myServiceLocator);
  }
}
