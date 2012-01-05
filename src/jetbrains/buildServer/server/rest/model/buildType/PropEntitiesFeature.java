package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="features")
@SuppressWarnings("PublicField")
public class PropEntitiesFeature {
  @XmlElement(name = "feature")
  public List<PropEntity> propEntities;

  public PropEntitiesFeature() {
  }

  public PropEntitiesFeature(List<PropEntity> propEntitiesParam) {
    propEntities = propEntitiesParam;
  }
 }
