package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
public class BuildRef {
  @XmlAttribute
  public String number;
  @XmlAttribute
  public String href;

  public BuildRef() {}

  public BuildRef(SBuild build) {
    SBuildType buildType = build.getBuildType();
    //todo: possible NPE?
    this.href = "/httpAuth/api/projects/id:" + buildType.getProjectId() + "/buildTypes/id:" + buildType.getBuildTypeId() + "/builds/id:" + build.getBuildId();
    this.number = build.getBuildNumber();
  }
}
