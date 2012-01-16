package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.util.StringUtil;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "agent-requirement")
public class PropEntityAgentRequirement extends PropEntity {

  public static final String NAME_PROPERTY_VALUE = "property-value";
  public static final String NAME_PROPERTY_NAME = "property-name";

  public PropEntityAgentRequirement() {
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

  private String getId() {
    final String nameProperty = properties.getMap().get(NAME_PROPERTY_NAME);
    if (StringUtil.isEmpty(nameProperty)) {
      throw new BadRequestException("Prperty " + NAME_PROPERTY_NAME + " with the parameter name should be specified for a requirement.");
    }
    return nameProperty;
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

  public PropEntityAgentRequirement addRequirement(final BuildTypeOrTemplate buildType) {
    final Map<String, String> propertiesMap = properties.getMap();
    final Requirement requirementToAdd = new Requirement(getId(), propertiesMap.get(NAME_PROPERTY_VALUE), getType());

    //todo: (TeamCity) API allows to add several requirements, but we will limit it as it is not supported duly
    final Requirement requirement = DataProvider.getAgentRequirementOrNull(buildType.get(), requirementToAdd.getPropertyName());
    if (requirement != null){
      throw new BadRequestException("Requirement for parameter with name '" + getId() + "' already exists.");
    }
    buildType.get().addRequirement(requirementToAdd);

    return new PropEntityAgentRequirement(DataProvider.getAgentRequirementOrNull(buildType.get(), requirementToAdd.getPropertyName()));
  }
}