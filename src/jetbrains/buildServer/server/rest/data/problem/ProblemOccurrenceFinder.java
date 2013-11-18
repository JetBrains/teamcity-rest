package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.investigations.AbstractFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
public class ProblemOccurrenceFinder extends AbstractFinder<BuildProblem> {
  public static final String BUILD = "build";
  public static final String IDENTITY = "identity";
  @NotNull private final BuildProblemBridge myBuildProblemBridge;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final UserFinder myUserFinder;
  @NotNull private final BuildFinder myBuildFinder;

  public ProblemOccurrenceFinder(final @NotNull BuildProblemBridge buildProblemBridge,
                                 final @NotNull ProjectFinder projectFinder,
                                 final @NotNull UserFinder userFinder,
                                 final @NotNull BuildFinder buildFinder) {
    super(buildProblemBridge, new String[]{"identity", "type", "build", "affectedProject"});
    myBuildProblemBridge = buildProblemBridge;
    myProjectFinder = projectFinder;
    myUserFinder = userFinder;
    myBuildFinder = buildFinder;
  }

  public BuildProblemBridge getBuildProblemBridge() {
    return myBuildProblemBridge;
  }

  @Override
  protected BuildProblem findSingleItem(final Locator locator) {
    //todo: searching occurrence by id does not work: review!!!
    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id
      final Long idDimension = locator.getSingleValueAsLong();
      if (idDimension != null) {
        final BuildProblem item = myBuildProblemBridge.findProblemById(idDimension);
        if (item != null) {
          return item;
        }
        throw new NotFoundException("No prblem with id '" + idDimension + "' found.");
      }
    }

    // dimension-specific item search

    Long idDimension = locator.getSingleDimensionValueAsLong("id");
    if (idDimension != null) {
      final BuildProblem item = myBuildProblemBridge.findProblemById(idDimension);
      if (item != null) {
        return item;
      }
      throw new NotFoundException("No problem with id '" + idDimension + "' found.");
    }


    String problemIdentity = locator.getSingleDimensionValue(IDENTITY);
    if (problemIdentity != null) {
      String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        SBuild build = myBuildFinder.getBuild(null, buildDimension);
        final BuildProblem item = myBuildProblemBridge.findProblem(problemIdentity, build);
        if (item == null) {
          throw new NotFoundException("No problem" + " can be found by " + IDENTITY + " '" + problemIdentity + "' in build with id " + build.getBuildId());
        }
        return item;
      }
    }
    return null;
  }

  @Override
  protected List<BuildProblem> getPrefilteredItems(@NotNull final Locator locator) {
    String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      SBuild build = myBuildFinder.getBuild(null, buildDimension);
      return BuildProblemBridge.getBuildProblems(build);
    }

    return super.getPrefilteredItems(locator);
  }

  @Override
  protected AbstractFilter<BuildProblem> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<BuildProblem> result =
      new MultiCheckerFilter<BuildProblem>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

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
    return result;
  }

  public static String getProblemLocator(final BuildProblem problem) {
    final SBuild build = problem.getBuildPromotion().getAssociatedBuild();
    if (build == null){
      throw new InvalidStateException("Build problem with id '" + problem.getId() + "' does not have an associated build.");
    }
    return IDENTITY + ":" + problem.getBuildProblemData().getIdentity() + "," + BUILD + ":(" + BuildRequest.getBuildLocator(build) + ")";//todo: use locator rendering here
  }
}
