package jetbrains.buildServer.server.rest.model.problem;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.request.ProblemOccurrenceRequest;
import jetbrains.buildServer.server.rest.request.ProblemRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "problem")
@XmlType(name = "problem", propOrder = {"id", "type", "identity", "href",
  "mutes", "investigations", "problemOccurrences"})
public class Problem {
  @XmlAttribute public String id;
  @XmlAttribute public String type;
  @XmlAttribute public String identity;
  @XmlAttribute public String href;

  /**
   * Experimental: project is an attribute of a problem in TeamCity API currently, but is subject to be removed
   */
//  @XmlElement public ProjectRef project;
  @XmlElement public Mutes mutes; // todo: also make this href
  @XmlElement public Href investigations;
  @XmlElement public Href problemOccurrences;

  public Problem() {
  }

  public Problem(final @NotNull ProblemWrapper problem,
                 final @NotNull ServiceLocator serviceLocator,
                 final @NotNull ApiUrlBuilder apiUrlBuilder,
                 final @NotNull Fields fields) {
    id = String.valueOf(problem.getId());
    final long problemId = (long)problem.getId();

    type = problem.getType();
    identity = problem.getIdentity();
    href = apiUrlBuilder.transformRelativePath(ProblemRequest.getHref(problem));

    if (fields.isAllFieldsIncluded()) {
//      project = new ProjectRef(problem.getProject(), apiUrlBuilder);

      final List<MuteInfo> actualMutes = problem.getMutes();
      if (actualMutes.size() > 0) {
        mutes = new Mutes(actualMutes, null, null, new BeanContext(serviceLocator.getSingletonService(BeanFactory.class), serviceLocator, apiUrlBuilder));
      }
      if (problem.getInvestigations().size() > 0) {
        investigations = new Href(InvestigationRequest.getHref(problem), apiUrlBuilder);
      }
      problemOccurrences = new Href(ProblemOccurrenceRequest.getHref(problem), apiUrlBuilder);
    }
  }
}
