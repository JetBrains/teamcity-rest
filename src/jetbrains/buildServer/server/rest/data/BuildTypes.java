package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.serverSide.SBuildType;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import java.util.ArrayList;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name="buildTypes")
public class BuildTypes {
  @XmlElement(name="buildType")
  public List<BuildTypeRef> buildTypes;

  public BuildTypes() {
  }

  public BuildTypes(List<SBuildType> buildTypesObjects) {
    buildTypes = new ArrayList<BuildTypeRef>(buildTypesObjects.size());
    for (SBuildType buildType : buildTypesObjects) {
      buildTypes.add(new BuildTypeRef(buildType));
    }
  }
}
