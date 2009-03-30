package jetbrains.buildServer.server.rest.data;

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.serverSide.SBuildType;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
public class BuildTypeRef {
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String href;

  public BuildTypeRef() {}

  public BuildTypeRef(SBuildType buildType) {
    this.href = "/httpAuth/api/projects/id:" + buildType.getProjectId() + "/buildTypes/id:" + buildType.getBuildTypeId();
    this.name = buildType.getName();
  }
}
