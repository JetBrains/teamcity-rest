package jetbrains.buildServer.server.rest.model.problem;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.model.build.BuildRef;
import jetbrains.buildServer.server.rest.request.ProblemOccurrenceRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problemOccurrence")
@XmlType(name = "problemOccurrence")
public class ProblemOccurrence {
//  @XmlAttribute public String id;
  @XmlAttribute public String type;
  @XmlAttribute public String identity;
  @XmlAttribute public String href;

  @XmlElement public String details;
  @XmlElement public String additionalData;

  @XmlElement public Problem problem;
  @XmlElement public Mute mute;

  @XmlElement public BuildRef build;

  public ProblemOccurrence() {
  }

  public ProblemOccurrence(final @NotNull BuildProblem problemP,
                           final @NotNull BeanContext beanContext,
                           final boolean fullDetails) {
//    id = String.valueOf(problemP.getId());
    type = problemP.getBuildProblemData().getType();
    identity = problemP.getBuildProblemData().getIdentity();
    href = beanContext.getApiUrlBuilder().transformRelativePath(ProblemOccurrenceRequest.getHref(problemP));

    if (fullDetails) {
      details = problemP.getBuildProblemData().getDescription();
      additionalData = problemP.getBuildProblemData().getAdditionalData();

      problem = new Problem(new ProblemWrapper(problemP.getId(), beanContext.getServiceLocator()), beanContext.getServiceLocator(), beanContext.getApiUrlBuilder(), false);

      final MuteInfo muteInfo = problemP.getMuteInBuildInfo();
      if (muteInfo != null) {
        mute = new Mute(muteInfo, beanContext);
      }

      final BuildPromotion buildPromotion = problemP.getBuildPromotion();
      final SBuild associatedBuild = buildPromotion.getAssociatedBuild();
      if (associatedBuild != null) {
        build = new BuildRef(associatedBuild, beanContext.getServiceLocator(), beanContext.getApiUrlBuilder());
      }
    }
  }
}
