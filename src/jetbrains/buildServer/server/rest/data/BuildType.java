package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.server.rest.data.ProjectRef;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name="buildType")
public class BuildType {
  @XmlAttribute public String id;
  @XmlAttribute public String name;
  @XmlAttribute public String description;
  @XmlElement  public ProjectRef project;

  public BuildType() {}

  public BuildType(SBuildType buildType) {
    id = buildType.getBuildTypeId();
    name = buildType.getName();
    description = buildType.getDescription();
    project = new ProjectRef(buildType.getProject());
  }
}
