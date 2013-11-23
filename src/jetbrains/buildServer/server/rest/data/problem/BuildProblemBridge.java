package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildPromotionEx;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.11.13
 */
public class BuildProblemBridge {
  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull final ServiceLocator myServiceLocator;

  public BuildProblemBridge(@NotNull final BuildProblemManager buildProblemManager, @NotNull final ProjectManager projectManager, final @NotNull ServiceLocator serviceLocator) {
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  public BuildProblem getBuildProblem(final @NotNull BuildProblemInfo buildProblemInfo) {
    final BuildProblem problemById = findProblemById((long)buildProblemInfo.getId(), myServiceLocator);
    if (problemById == null){
      throw new NotFoundException("Cannot find build problem with id '" + buildProblemInfo.getId() + "'");
    }
    return problemById;
  }

  //todo: TeamCity API: how to do this effectively?
  @Nullable
  public ProblemWrapper findProblemWrapperById(@NotNull final Long id) {
    final BuildProblem problemById = findProblemById(id, myServiceLocator);
    if (problemById == null){
      throw new NotFoundException("Cannot find problem instance by id '" + id + "'");
    }
    return new ProblemWrapper(problemById, myServiceLocator);
  }

  /*
  //todo: TeamCity API: how to do this effectively?
  @Nullable
  public BuildProblem findProblemById(@NotNull final Long id) {
    final List<BuildProblem> currentBuildProblemsList = myBuildProblemManager.getCurrentBuildProblemsList(myProjectManager.getRootProject());
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      if (id.equals(Long.valueOf(buildProblem.getId()))) return buildProblem; //todo: TeamCity API: can a single id apper several times here?
    }
    return null;
  }
  */

  //todo: TeamCity API: how to do this effectively?
  @Nullable
  public static BuildProblem findProblemById(@NotNull final Long id, @NotNull final jetbrains.buildServer.ServiceLocator serviceLocator) {
    final List<BuildProblem> currentBuildProblemsList = serviceLocator.getSingletonService(BuildProblemManager.class).getCurrentBuildProblemsList(
      serviceLocator.getSingletonService(ProjectManager.class).getRootProject());
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      if (id.equals(Long.valueOf(buildProblem.getId()))) return buildProblem; //todo: TeamCity API: can a single id apper several times here?
    }
    return null;
  }

  @Nullable
  public BuildProblem findProblem(@NotNull final String problemIdentity, @NotNull final SBuild build) {
    final List<BuildProblem> buildProblems = getBuildProblems(build);
    for (BuildProblem buildProblem : buildProblems) {
      if (buildProblem.getBuildProblemData().getIdentity().equals(problemIdentity)){
        return buildProblem;
      }
    }
    return null;
  }

  @Nullable
  public static List<BuildProblem> getBuildProblems(@NotNull final SBuild build) {
    return ((BuildPromotionEx)build.getBuildPromotion()).getBuildProblems();
  }
}
