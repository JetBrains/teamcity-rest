package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;

/**
 * Author: Yegor.Yarko
 */
public class PropEntities {
  @SuppressWarnings("PublicField")
  @XmlElement(name = "entity")
  public List<PropEntity> propEntities;

  public PropEntities() {
  }

  public PropEntities(List<PropEntity> propEntitiesParam) {
    propEntities = new ArrayList<PropEntity>(propEntitiesParam.size());
    propEntities.addAll(propEntitiesParam);
  }
 }
