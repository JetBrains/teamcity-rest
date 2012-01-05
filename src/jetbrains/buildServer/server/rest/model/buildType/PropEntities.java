package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Author: Yegor.Yarko
 */
@XmlRootElement(name="property-described-entities")
@SuppressWarnings("PublicField")
public class PropEntities {
  @XmlElement(name = "entity")
  public List<PropEntity> propEntities;

  public PropEntities() {
  }

  public PropEntities(List<PropEntity> propEntitiesParam) {
    propEntities = propEntitiesParam;
  }
 }
