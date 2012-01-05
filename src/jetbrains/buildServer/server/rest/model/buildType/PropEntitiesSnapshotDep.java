package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="snapshot-dependencies")
@SuppressWarnings("PublicField")
public class PropEntitiesSnapshotDep {
  @XmlElement(name = "snapshot-dependency")
  public List<PropEntity> propEntities;

  public PropEntitiesSnapshotDep() {
  }

  public PropEntitiesSnapshotDep(List<PropEntity> propEntitiesParam) {
    propEntities = propEntitiesParam;
  }
 }
