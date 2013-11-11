package jetbrains.buildServer.server.rest.data.investigations;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.responsibility.*;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public class ResponsibilityEntryBridge extends ItemBridge<InvestigationWrapper> {
  private final BuildTypeResponsibilityFacade myBuildTypeResponsibilityFacade;
  private final TestNameResponsibilityFacade myTestNameResponsibilityFacade;
  private final BuildProblemResponsibilityFacade myBuildProblemResponsibilityFacade;

  public ResponsibilityEntryBridge(final BuildTypeResponsibilityFacade buildTypeResponsibilityFacade,
                                   final TestNameResponsibilityFacade testNameResponsibilityFacade, final BuildProblemResponsibilityFacade buildProblemResponsibilityFacade) {
    myBuildTypeResponsibilityFacade = buildTypeResponsibilityFacade;
    myTestNameResponsibilityFacade = testNameResponsibilityFacade;
    myBuildProblemResponsibilityFacade = buildProblemResponsibilityFacade;
  }

  @Override
  public List<InvestigationWrapper> getAllItems() {
    final ArrayList<InvestigationWrapper> result = new ArrayList<InvestigationWrapper>();

    final List<BuildTypeResponsibilityEntry> buildTypeResponsibilities = myBuildTypeResponsibilityFacade.getUserBuildTypeResponsibilities(null, null);
    result.addAll(CollectionsUtil.convertCollection(buildTypeResponsibilities, new Converter<InvestigationWrapper, BuildTypeResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final BuildTypeResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    final List<TestNameResponsibilityEntry> testResponsibilities = myTestNameResponsibilityFacade.getUserTestNameResponsibilities(null, null);
    result.addAll(CollectionsUtil.convertCollection(testResponsibilities, new Converter<InvestigationWrapper, TestNameResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final TestNameResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    final List<BuildProblemResponsibilityEntry> problemResponsibilities = myBuildProblemResponsibilityFacade.getUserBuildProblemResponsibilities(null, null);
    result.addAll(CollectionsUtil.convertCollection(problemResponsibilities, new Converter<InvestigationWrapper, BuildProblemResponsibilityEntry>() {
      public InvestigationWrapper createFrom(@NotNull final BuildProblemResponsibilityEntry source) {
        return new InvestigationWrapper(source);
      }
    }));

    //todo: sort!
    return result;
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
