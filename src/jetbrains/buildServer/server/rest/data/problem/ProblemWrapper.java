package jetbrains.buildServer.server.rest.data.problem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a problem (which can have occurrencies in the builds) as there is no appropriate class in TeamCity API (TeamCity API issue)
 *
 * @author Yegor.Yarko
 *         Date: 21.11.13
 */
public class ProblemWrapper {
  private final Long id;
  private final String type;
  private final String identity;
  private final SProject project;
  private List<MuteInfo> mutes;
  private List<InvestigationWrapper> investigations;

  private final BuildProblem myProblem;

  public ProblemWrapper(final @NotNull BuildProblemInfo buildProblemInfo, final @NotNull ServiceLocator serviceLocator) {
    id = (long)buildProblemInfo.getId();
    myProblem = ProblemOccurrenceFinder.findProblemById(id, serviceLocator);
    if (myProblem == null) {
      throw new NotFoundException("No instances of problem with id '" + id + "' found.");
    }
    type = myProblem.getBuildProblemData().getType();
    identity = myProblem.getBuildProblemData().getIdentity();
    project = serviceLocator.getSingletonService(ProjectManager.class).findProjectById(buildProblemInfo.getProjectId());
  }

  public ProblemWrapper(final @NotNull BuildProblem buildProblem, final @NotNull ServiceLocator serviceLocator) {
    id = (long)buildProblem.getId();
    myProblem = buildProblem;

    type = myProblem.getBuildProblemData().getType();
    identity = myProblem.getBuildProblemData().getIdentity();
    project = serviceLocator.getSingletonService(ProjectManager.class).findProjectById(buildProblem.getProjectId());
  }

  @NotNull
  public Long getId() {
    return id;
  }

  @NotNull
  public String getType() {
    return type;
  }

  @NotNull
  public String getIdentity() {
    return identity;
  }

  @NotNull
  public SProject getProject() {  //this assumes no problems for not existent proects are reported which might nt
    return project;
  }

  @NotNull
  public List<MuteInfo> getMutes() {
    if (mutes == null) {
      mutes = new ArrayList<MuteInfo>();
      final CurrentMuteInfo currentMuteInfo = myProblem.getCurrentMuteInfo(); //todo: TeamCity API: how to get unique mutes?
      if (currentMuteInfo != null) {
        mutes.addAll(new LinkedHashSet<MuteInfo>(currentMuteInfo.getProjectsMuteInfo().values())); //add with deduplication
        mutes.addAll(new LinkedHashSet<MuteInfo>(currentMuteInfo.getBuildTypeMuteInfo().values())); //add with deduplication
      }
    }
    return mutes;
  }

  @NotNull
  public List<InvestigationWrapper> getInvestigations() {
    if (investigations == null){
      investigations = new ArrayList<InvestigationWrapper>();
      final BuildProblemResponsibilityEntry responsibility = myProblem.getResponsibility();
      if (responsibility != null){
        investigations.add(new InvestigationWrapper(responsibility));
      }
    }
    return investigations;
  }
}
