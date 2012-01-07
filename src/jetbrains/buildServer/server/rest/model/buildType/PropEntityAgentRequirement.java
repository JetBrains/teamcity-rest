package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.util.StringUtil;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "trigger")
public class PropEntityAgentRequirement extends PropEntity {

  public static final String NAME_PROPERTY_VALUE = "property-value";
  public static final String NAME_PROPERTY_NAME = "property-name";

  public PropEntityAgentRequirement() {
  }

  public PropEntityAgentRequirement(final BuildTriggerDescriptor descriptor) {
    super(descriptor);
  }

  public PropEntityAgentRequirement(final Requirement requirement) {
    id = requirement.getPropertyName();
    type = requirement.getType().getName();

    HashMap<String, String> propertiesMap = new HashMap<String, String>();
    propertiesMap.put(NAME_PROPERTY_NAME, requirement.getPropertyName());
    if (requirement.getPropertyValue() != null) {
      propertiesMap.put(NAME_PROPERTY_VALUE, requirement.getPropertyValue());
    }
    properties = new Properties(propertiesMap);
  }

  public Requirement createRequirement() {
    final Map<String, String> propertiesMap = properties.getMap();
    return new Requirement(getId(), propertiesMap.get(NAME_PROPERTY_VALUE), getType());
  }

  private String getId() {
    final String nameProperty = properties.getMap().get(NAME_PROPERTY_NAME);
    if (!StringUtil.isEmpty(id)) {
      if (!StringUtil.isEmpty(nameProperty) && !nameProperty.equals(id)) {
        throw new BadRequestException("Only one from id attribute and " + NAME_PROPERTY_NAME + " property should be specified, or they should be equal.");
      }
      return id;
    } else {
      if (StringUtil.isEmpty(nameProperty)) {
        throw new BadRequestException("Either id attribute or " + NAME_PROPERTY_NAME + " property should be specified for a requirement.");
      }
      return nameProperty;
    }
  }

  private RequirementType getType() {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Type attribute should be specified for a requirement.");
    }
    final RequirementType foundType = RequirementType.findByName(type);
    if (foundType == null) {
      throw new BadRequestException("Could not create Requirement type by type '" + type + ". Check it is a valid type.");
    }
    return foundType;
  }
}