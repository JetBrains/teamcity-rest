package jetbrains.buildServer.server.rest.data.problem;

import java.util.List;
import jetbrains.buildServer.server.rest.data.investigations.ItemBridge;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;

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

  @Override
  public List<BuildProblem> getAllItems() {
    return myBuildProblemManager.getCurrentBuildProblemsList(myProjectManager.getRootProject());
  }
}
