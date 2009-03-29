package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SBuild;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.text.SimpleDateFormat;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
//todo: add changes
//todo: reuse fields code from DataProvider
@XmlRootElement(name = "build")
public class Build {
  @XmlAttribute
  public long id;
  @XmlAttribute
  public String status;
  @XmlElement
  public BuildTypeRef buildType;

  //todo: investigate common date formats approach in REST
  @XmlElement
  public String startDate;
  @XmlElement
  public String finishDate;

  public Build() {
  }

  public Build(SBuild build) {
    id = build.getBuildId();
    status = build.getStatusDescriptor().getStatus().getText();
    startDate = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getStartDate());
    finishDate = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getFinishDate());
    buildType = new BuildTypeRef(build.getBuildType());
  }
}

