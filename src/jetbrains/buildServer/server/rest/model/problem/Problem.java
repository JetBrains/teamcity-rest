package jetbrains.buildServer.server.rest.model.problem;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.build.BuildRef;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType
public class Problem {
  @XmlAttribute public String id;
  @XmlAttribute public String type;
  @XmlAttribute public String identity;
  @XmlAttribute public String description;
  @XmlAttribute public String additionalData;

//  @XmlElement public ProjectRef project;
  @XmlElement(name = "lastAffectedBuild") public BuildRef buildRef;

  public Problem() {
  }

  public Problem(final @NotNull BuildProblem problem,
                 final @NotNull ServiceLocator serviceLocator,
                 final @NotNull ApiUrlBuilder apiUrlBuilder) {

    identity = problem.getBuildProblemData().getIdentity();
    type = problem.getBuildProblemData().getType();
    description = problem.getBuildProblemData().getDescription();
    additionalData = problem.getBuildProblemData().getAdditionalData();
    //final SProject projectById = serviceLocator.getSingletonService(ProjectManager.class).findProjectById(problem.getProjectId());
    //if (projectById != null){
    //  project = new ProjectRef(projectById, apiUrlBuilder);
    //}else{
    //  project = new ProjectRef(null, problem.getProjectId(), apiUrlBuilder);
    //}
    final BuildPromotion buildPromotion = problem.getBuildPromotion();
    final SBuild associatedBuild = buildPromotion.getAssociatedBuild();
    if (associatedBuild != null){
      buildRef = new BuildRef(associatedBuild, serviceLocator, apiUrlBuilder);
    }
  }
}
