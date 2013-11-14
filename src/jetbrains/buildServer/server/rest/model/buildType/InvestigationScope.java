package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.problem.Problem;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.serverSide.problems.BuildProblemManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType
public class InvestigationScope {
  @XmlElement
  public String type; //todo: make this typed

  @XmlElement
  public BuildTypeRef buildType;

  /**
   * Experimental! will change in future versions.
   */
  @XmlElement
  public String testName;

  /**
   * Experimental! will change in future versions.
   */
  @XmlElement
  public Problem problem;

  @XmlElement
  public ProjectRef project;

   public InvestigationScope() {
  }

  public InvestigationScope(final @NotNull InvestigationWrapper investigation,
                            @NotNull final ServiceLocator serviceLocator,
                            final ApiUrlBuilder apiUrlBuilder) {
    type = investigation.getType();
    if (investigation.isBuildType()) {
      buildType = new BuildTypeRef((SBuildType)investigation.getBuildTypeRE().getBuildType(), serviceLocator, apiUrlBuilder);  //TeamCity open API issue: cast
    } else if (investigation.isTest()) {
      final TestNameResponsibilityEntry testRE = investigation.getTestRE();
      testName = testRE.getTestName().getAsString();
      project = new ProjectRef((SProject)testRE.getProject(), apiUrlBuilder); //TeamCity open API issue: cast
    } else if (investigation.isProblem()) {
      final BuildProblemResponsibilityEntry problemRE = investigation.getProblemRE();
      problem = new Problem(getBuildProblem(problemRE.getBuildProblemInfo().getId(), serviceLocator), serviceLocator, apiUrlBuilder);
      project = new ProjectRef((SProject)problemRE.getProject(), apiUrlBuilder); //TeamCity open API issue: cast
    } else {
      throw new InvalidStateException("Investigation wrapper type is not supported");
    }
  }

  //todo: TeamCity API: how to do this effectively?
  private BuildProblem getBuildProblem(final int id, final ServiceLocator serviceLocator) {
    final List<BuildProblem> currentBuildProblemsList =
      serviceLocator.getSingletonService(BuildProblemManager.class).getCurrentBuildProblemsList(serviceLocator.getSingletonService(ProjectManager.class).getRootProject());
    for (BuildProblem buildProblem : currentBuildProblemsList) {
      if (id == buildProblem.getId()) return buildProblem;
    }
    return null;
  }
}
