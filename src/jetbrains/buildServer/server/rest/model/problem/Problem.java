package jetbrains.buildServer.server.rest.model.problem;

import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.request.ProblemRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problem")
@XmlType(name = "problem", propOrder = {"id", "type", "identity",
  "mutes", "investigations"})
public class Problem {
  @XmlAttribute public String id;
  @XmlAttribute public String type;
  @XmlAttribute public String identity;
  @XmlAttribute public String href;

  @XmlElement public Mutes mutes; // todo: also make this href
  @XmlElement public Href investigations;

  public Problem() {
  }

  public Problem(final @NotNull BuildProblem problem,
                 final @NotNull ServiceLocator serviceLocator,
                 final @NotNull ApiUrlBuilder apiUrlBuilder,
                 final boolean fullDetails) {
    id = String.valueOf(problem.getId());
    type = problem.getBuildProblemData().getType();
    identity = problem.getBuildProblemData().getIdentity();
    href = apiUrlBuilder.transformRelativePath(ProblemRequest.getHref(problem));

    if (fullDetails) {
      final Set<MuteInfo> muteInfos = new HashSet<MuteInfo>();
      final CurrentMuteInfo currentMuteInfo = problem.getCurrentMuteInfo();
      if (currentMuteInfo != null) {
        muteInfos.addAll(currentMuteInfo.getProjectsMuteInfo().values());
        muteInfos.addAll(currentMuteInfo.getBuildTypeMuteInfo().values());
      }
      if (muteInfos.size() > 0) {
        mutes = new Mutes(muteInfos, null, new BeanContext(serviceLocator.getSingletonService(BeanFactory.class), serviceLocator, apiUrlBuilder));
      }
      if (problem.getAllResponsibilities().size() > 0) {
        investigations = new Href(InvestigationRequest.getHref(problem), apiUrlBuilder);
      }
    }
  }
}
