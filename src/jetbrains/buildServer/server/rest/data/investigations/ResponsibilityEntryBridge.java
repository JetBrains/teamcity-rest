package jetbrains.buildServer.server.rest.data.investigations;

import java.util.List;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityFacade;
import jetbrains.buildServer.responsibility.BuildTypeResponsibilityEntry;
import jetbrains.buildServer.responsibility.BuildTypeResponsibilityFacade;
import jetbrains.buildServer.responsibility.TestNameResponsibilityFacade;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class ResponsibilityEntryBridge {
  private final BuildTypeResponsibilityFacade myBuildTypeResponsibilityFacade;
  private final TestNameResponsibilityFacade myTestNameResponsibilityFacade;
  private final BuildProblemResponsibilityFacade myBuildProblemResponsibilityFacade;

  public ResponsibilityEntryBridge(final BuildTypeResponsibilityFacade buildTypeResponsibilityFacade,
                                   final TestNameResponsibilityFacade testNameResponsibilityFacade, final BuildProblemResponsibilityFacade buildProblemResponsibilityFacade) {
    myBuildTypeResponsibilityFacade = buildTypeResponsibilityFacade;
    myTestNameResponsibilityFacade = testNameResponsibilityFacade;
    myBuildProblemResponsibilityFacade = buildProblemResponsibilityFacade;
  }

  public BuildTypeResponsibilityEntry getBuildTypeRE(@NotNull final SBuildType buildType) {
    final List<BuildTypeResponsibilityEntry> userBuildTypeResponsibilities = myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(null, null);
    for (BuildTypeResponsibilityEntry responsibility : userBuildTypeResponsibilities) {
      if (responsibility.getBuildType().equals(buildType)){
        return responsibility;
      }
    }
    throw new NotFoundException("Build type with id '" + buildType.getExternalId() + "' does not have associated investigation.");
  }

  @SuppressWarnings("RedundantIfStatement")
  public boolean isInvestigationRelatedToProject(final InvestigationWrapper item, final SProject project) {
    if (myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(null, project.getProjectId()).contains(item.getBuildTypeRE())){
      return true;
    }
    if (myTestNameResponsibilityFacade.getUserTestNameResponsibilities(null, project.getProjectId()).contains(item.getTestRE())){
      return true;
    }
    if (myBuildProblemResponsibilityFacade.getUserBuildProblemResponsibilities(null, project.getProjectId()).contains(item.getProblemRE())){
      return true;
    }
    return false;
  }
}
