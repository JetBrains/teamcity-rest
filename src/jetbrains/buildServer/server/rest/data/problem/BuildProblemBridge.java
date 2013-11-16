package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.server.rest.data.investigations.ItemBridge;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.11.13
 */
public class BuildProblemBridge extends ItemBridge<BuildProblem> {
  @NotNull private final BuildProblemManager myBuildProblemManager;
  @NotNull private final ProjectManager myProjectManager;

  public BuildProblemBridge(@NotNull final BuildProblemManager buildProblemManager, @NotNull final ProjectManager projectManager) {
    myBuildProblemManager = buildProblemManager;
    myProjectManager = projectManager;
  }

  @NotNull
  @Override
  public List<BuildProblem> getAllItems() {
    return myBuildProblemManager.getCurrentBuildProblemsList(myProjectManager.getRootProject());
  }

  @NotNull
  public BuildProblem getBuildProblem(final @NotNull BuildProblemInfo buildProblemInfo) {
    final BuildProblem problemById = findProblemById((long)buildProblemInfo.getId());
    if (problemById == null){
      throw new NotFoundException("Cannot find build problem with id '" + buildProblemInfo.getId() + "'");
    }
    return problemById;
  }

  //todo: TeamCity API: how to do this effectively?
  @Nullable
  public BuildProblem findProblemById(@NotNull final Long id) {
    final List<BuildProblem> currentBuildProblemsList = myBuildProblemManager.getCurrentBuildProblemsList(myProjectManager.getRootProject());
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      if (id == buildProblem.getId()) return buildProblem; //todo: TeamCity API: can a single id apper several times here?
    }
    return null;
  }
}
