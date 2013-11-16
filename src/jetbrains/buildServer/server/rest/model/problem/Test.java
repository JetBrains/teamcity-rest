package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.mute.CurrentMuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "test")
@XmlType(name = "test", propOrder = {"id", "name",
  "project", "mutes"})
public class Test {
  @XmlAttribute public long id;
  @XmlAttribute public String name;

  @XmlElement public ProjectRef project;
  @XmlElement public Mutes mutes;
  //todo: add investigations

  public Test() {
  }

  public Test(final @NotNull STest test, final @NotNull BeanContext beanContext) {
    id = test.getTestNameId();
    name = test.getName().getAsString();
    final SProject projectById = beanContext.getSingletonService(ProjectManager.class).findProjectById(test.getProjectId());
    if (projectById != null) {
      project = new ProjectRef(projectById, beanContext.getContextService(ApiUrlBuilder.class));
    } else {
      project = new ProjectRef(null, test.getProjectId(), beanContext.getContextService(ApiUrlBuilder.class));
    }

    final ArrayList<MuteInfo> muteInfos = new ArrayList<MuteInfo>();
    final CurrentMuteInfo currentMuteInfo = test.getCurrentMuteInfo();
    if (currentMuteInfo != null) {
      muteInfos.addAll(currentMuteInfo.getProjectsMuteInfo().values());
      muteInfos.addAll(currentMuteInfo.getBuildTypeMuteInfo().values());
    }
    if (muteInfos.size() > 0) {
      mutes = new Mutes(muteInfos, null, beanContext);
    }
  }
}
