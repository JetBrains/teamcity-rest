package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="agent-requirements")
@SuppressWarnings("PublicField")
public class PropEntitiesAgentRequirement {
  @XmlElement(name = "agent-requirement")
  public List<PropEntity> propEntities;

  public PropEntitiesAgentRequirement() {
  }

  public PropEntitiesAgentRequirement(List<PropEntity> propEntitiesParam) {
    propEntities = propEntitiesParam;
  }
 }
