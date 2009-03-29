package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
public class BuildTypeRef {
  @XmlAttribute
  public String href;

  public BuildTypeRef() {}

  public BuildTypeRef(SBuildType buildType) {
    this.href = "/httpAuth/api/projects/id:" + buildType.getProjectId() + "/buildTypes/id:" + buildType.getBuildTypeId();
  }
}
