package jetbrains.buildServer.server.rest.data.problem;

import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AnstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class ProblemFinder extends AnstractFinder<BuildProblem> {
  @NotNull private final BuildProblemBridge myBuildProblemBridge;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final BuildFinder myBuildFinder;

  public ProblemFinder(final @NotNull BuildProblemBridge buildProblemBridge,
                       final @NotNull ProjectFinder projectFinder,
                       final @NotNull UserFinder userFinder,
                       final @NotNull BuildFinder buildFinder) {
    super(buildProblemBridge, new String[]{"identity", "affectedProject"});
    myBuildProblemBridge = buildProblemBridge;
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myBuildFinder = buildFinder;
  }

  public BuildProblemBridge getBuildProblemBridge() {
    return myBuildProblemBridge;
  }

  @Override
  protected BuildProblem findSingleItemAsList(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    /*
    // dimension-specific item search
    String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (id != null) {
      BuildProblem item = findItemById(id);
      if (item == null) {
        throw new NotFoundException("No investigation" + " can be found by " + DIMENSION_ID + " '" + id + "'.");
      }
      return item;
    }
    */

    return null;
  }

  @Override
  protected AbstractFilter<BuildProblem> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<BuildProblem> result =
      new MultiCheckerFilter<BuildProblem>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String identityDimension = locator.getSingleDimensionValue("identity");
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

    final String buildDimension = locator.getSingleDimensionValue("build");
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
    return result;
  }
}
