package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="triggers")
@SuppressWarnings("PublicField")
public class PropEntitiesTrigger {
  @XmlElement(name = "trigger")
  public List<PropEntity> propEntities;

  public PropEntitiesTrigger() {
  }

  public PropEntitiesTrigger(List<PropEntity> propEntitiesParam) {
    propEntities = propEntitiesParam;
  }
 }
