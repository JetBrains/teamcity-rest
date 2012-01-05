package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="artifact-dependencies")
@SuppressWarnings("PublicField")
public class PropEntitiesArtifactDep {
  @XmlElement(name = "artifact-dependency")
  public List<PropEntity> propEntities;

  public PropEntitiesArtifactDep() {
  }

  public PropEntitiesArtifactDep(List<PropEntity> propEntitiesParam) {
    propEntities = propEntitiesParam;
  }
 }
