package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.request.TestRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "test")
@XmlType(name = "test", propOrder = {"id", "name",
  "project", "mutes", "investigations"})
public class Test {
  @XmlAttribute public long id;
  @XmlAttribute public String name;
  @XmlAttribute public String href;

  /**
   * Experimental: project is an attribute of a problem in TeamCity API currently, but is subject to be removed
   */
  @XmlElement public ProjectRef project;
  @XmlElement public Mutes mutes;  // todo: also make this href
  @XmlElement public Href investigations;

  public Test() {
  }

  public Test(final @NotNull STest test, final @NotNull BeanContext beanContext, final boolean fullDetails) {
    id = test.getTestNameId();
    name = test.getName().getAsString();

    final ApiUrlBuilder apiUrlBuilder = beanContext.getApiUrlBuilder();
    href = apiUrlBuilder.transformRelativePath(TestRequest.getHref(test));

    if (fullDetails) {
      final SProject projectById = beanContext.getSingletonService(ProjectManager.class).findProjectById(test.getProjectId());
      if (projectById != null) {
        project = new ProjectRef(projectById, apiUrlBuilder);
      } else {
        project = new ProjectRef(null, test.getProjectId(), apiUrlBuilder);
      }

      final ArrayList<MuteInfo> muteInfos = new ArrayList<MuteInfo>();
      final CurrentMuteInfo currentMuteInfo = test.getCurrentMuteInfo(); //todo: TeamCity API: how to get unique mutes?
      if (currentMuteInfo != null) {
        muteInfos.addAll(new LinkedHashSet<MuteInfo>(currentMuteInfo.getProjectsMuteInfo().values())); //add with deduplication
        muteInfos.addAll(new LinkedHashSet<MuteInfo>(currentMuteInfo.getBuildTypeMuteInfo().values())); //add with deduplication
      }
      if (muteInfos.size() > 0) {
        mutes = new Mutes(muteInfos, null, beanContext);
      }
      if (test.getAllResponsibilities().size() > 0) {
        investigations = new Href(InvestigationRequest.getHref(test), apiUrlBuilder);
      }
    }
  }
}
