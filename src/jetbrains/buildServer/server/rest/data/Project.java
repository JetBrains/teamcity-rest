package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "project")
public class Project {
  @XmlAttribute
  public String id;
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String description;

  public Project() {
  }

  public Project(SProject project) {
    id = project.getProjectId();
    name = project.getName();
    description = project.getDescription();
  }

}
