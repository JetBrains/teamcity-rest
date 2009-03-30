package jetbrains.buildServer.server.rest.data;

import java.text.SimpleDateFormat;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.serverSide.SBuild;

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
  public String number;
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
    number = build.getBuildNumber();
    status = build.getStatusDescriptor().getStatus().getText();
    startDate = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getStartDate());
    finishDate = (new SimpleDateFormat("yyyyMMdd'T'HHmmssZ")).format(build.getFinishDate());
    buildType = new BuildTypeRef(build.getBuildType());
  }
}

