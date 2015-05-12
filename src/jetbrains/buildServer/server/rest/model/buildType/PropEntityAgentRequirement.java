/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

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

  public PropEntityAgentRequirement(final Requirement requirement, @NotNull final Fields fields) {
    HashMap<String, String> propertiesMap = new HashMap<String, String>(2);
    propertiesMap.put(NAME_PROPERTY_NAME, requirement.getPropertyName());
    if (requirement.getPropertyValue() != null) {
      propertiesMap.put(NAME_PROPERTY_VALUE, requirement.getPropertyValue());
    }
    init(requirement.getPropertyName(), null, requirement.getType().getName(), null, propertiesMap, fields);
  }

  private String getSubmittedId() {
    final String nameProperty = properties.getMap().get(NAME_PROPERTY_NAME);
    if (StringUtil.isEmpty(nameProperty)) {
      throw new BadRequestException("Prperty " + NAME_PROPERTY_NAME + " with the parameter name should be specified for a requirement.");
    }
    return nameProperty;
  }

  private RequirementType getSubmittedType() {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Type attribute should be specified for a requirement.");
    }
    final RequirementType foundType = RequirementType.findByName(type);
    if (foundType == null) {
      throw new BadRequestException("Could not create Requirement type by type '" + type + ". Check it is a valid type.");
    }
    return foundType;
  }

  public Requirement addRequirement(@NotNull final BuildTypeOrTemplate buildType) {
    final Map<String, String> propertiesMap = properties.getMap();
    final Requirement requirementToAdd = new Requirement(getSubmittedId(), propertiesMap.get(NAME_PROPERTY_VALUE), getSubmittedType());

    //todo: (TeamCity) API allows to add several requirements, but we will limit it as it is not supported duly
    final String requirementPropertyName = requirementToAdd.getPropertyName();
    final BuildTypeSettings buildTypeSettings = buildType.get();

    final Requirement requirement = DataProvider.getAgentRequirementOrNull(buildTypeSettings, requirementPropertyName);
    if (requirement != null){
      if (buildType.isBuildType() && buildTypeSettings.getTemplate() != null &&
          DataProvider.getAgentRequirementOrNull(buildTypeSettings.getTemplate(), requirementPropertyName) != null) {
        buildTypeSettings.removeRequirement(requirementPropertyName); //todo (TeamCity) not clear how not present is handled
      }else{
        throw new BadRequestException("Requirement for parameter with name '" + getSubmittedId() + "' already exists.");
      }
    }
    buildTypeSettings.addRequirement(requirementToAdd);

    return DataProvider.getAgentRequirementOrNull(buildTypeSettings, requirementPropertyName);
  }
}