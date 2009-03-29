package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SProject;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
public class ProjectRef {
  @XmlAttribute
  public String href;

  public ProjectRef() {}

  public ProjectRef(SProject project) {
    this.href = "/httpAuth/api/projects/id:" + project.getProjectId();
  }
}
