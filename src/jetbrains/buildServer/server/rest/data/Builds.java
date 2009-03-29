package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import java.util.ArrayList;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "builds")
public class Builds {
  @XmlElement(name = "build")
  public List<BuildRef> builds;

  public Builds() {
  }

  public Builds(List buildsObjects) {
    builds = new ArrayList<BuildRef>(buildsObjects.size());
    for (Object build : buildsObjects) {
      builds.add(new BuildRef((SBuild)build));
    }
  }
}
