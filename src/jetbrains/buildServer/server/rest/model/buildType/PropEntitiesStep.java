package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="steps")
@SuppressWarnings("PublicField")
public class PropEntitiesStep {
  @XmlElement(name = "step")
  public List<PropEntity> propEntities;

  public PropEntitiesStep() {
  }

  public PropEntitiesStep(List<PropEntity> propEntitiesParam) {
    propEntities = propEntitiesParam;
  }
 }
