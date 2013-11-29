package jetbrains.buildServer.server.rest.data.problem;

import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a problem (which can have occurrencies in the builds) as there is no appropriate class in TeamCity API (TeamCity API issue)
 *
 * @author Yegor.Yarko
 *         Date: 21.11.13
 */
public class ProblemWrapper implements Comparable<ProblemWrapper>{
  private final Long id;
  private final String type;
  private final String identity;
//  private final SProject project;
  @NotNull private final ServiceLocator myServiceLocator;
  private List<MuteInfo> mutes;
  private List<InvestigationWrapper> investigations;

  public ProblemWrapper(final int problemId, final @NotNull ServiceLocator serviceLocator) {
    id = Long.valueOf(problemId);
    myServiceLocator = serviceLocator;
    //todo: TeamCity API (VB): get all the fields +mutes +investigations by id
  //todo: TeamCity API (VB): what is buildProblemInfo.getBuildProblemDescription()
    final BuildProblem problemById = ProblemFinder.findProblemById(id, serviceLocator);
    if (problemById != null){
      type = problemById.getBuildProblemData().getType();
      identity = problemById.getBuildProblemData().getIdentity();
    }else{
      type = null;
      identity = null;
    }
     //todo: also add type desciption?
  }

  @NotNull
  public Long getId() {
    return id;
  }

  @Nullable
  public String getType() {
    return type;
  }

  @Nullable
  public String getIdentity() {
    return identity;
  }

  /*
  @NotNull
  public SProject getProject() {  //todo TeamCity API (VB): this assumes no problems for not existent proects are reported which might not be true
    return project;
  }
  */

  @NotNull
  public List<MuteInfo> getMutes() {
    if (mutes == null) {
      initMutesAndInvestigations();
    }
    return mutes;
  }

  @NotNull
  public List<InvestigationWrapper> getInvestigations() {
    if (investigations == null){
      initMutesAndInvestigations();
    }
    return investigations;
  }

  private void initMutesAndInvestigations() {
    Set<MuteInfo>  mutesSet = new TreeSet<MuteInfo>();
    Set<InvestigationWrapper> investigationsSet = new LinkedHashSet<InvestigationWrapper>();
    final SProject rootProject = myServiceLocator.getSingletonService(ProjectManager.class).getRootProject();
    final List<BuildProblem> currentBuildProblemsList = myServiceLocator.getSingletonService(BuildProblemManager.class).getCurrentBuildProblemsList(rootProject);
    //todo: bug: searches only inside current problems: mutes and investigations from non-current problems are not returned
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      if (id.equals(Long.valueOf(buildProblem.getId()))){
        final CurrentMuteInfo currentMuteInfo = buildProblem.getCurrentMuteInfo();
        if (currentMuteInfo != null){
          mutesSet.addAll(currentMuteInfo.getProjectsMuteInfo().values());
          mutesSet.addAll(currentMuteInfo.getBuildTypeMuteInfo().values());
        }
        final BuildProblemResponsibilityEntry responsibility = buildProblem.getResponsibility();
        if (responsibility != null){
          investigationsSet.add(new InvestigationWrapper(responsibility)); //todo: check that deduplication works OK here
        }
      }
    }

    mutes = new ArrayList<MuteInfo>(mutesSet);
    investigations = new ArrayList<InvestigationWrapper>(investigationsSet);
  }


  //todo: review all methods below
  public int compareTo(@NotNull final ProblemWrapper o) {
    return id.compareTo(o.getId());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ProblemWrapper problemWrapper = (ProblemWrapper)o;

    return id.equals(problemWrapper.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
