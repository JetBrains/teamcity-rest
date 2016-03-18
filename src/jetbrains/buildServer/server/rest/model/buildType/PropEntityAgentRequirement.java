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

import java.util.*;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.RequirementFactory;
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

  public PropEntityAgentRequirement(@NotNull final Requirement requirement, @NotNull final BuildTypeSettings buildType, @NotNull final Fields fields) {
    HashMap<String, String> propertiesMap = new HashMap<String, String>(2);
    propertiesMap.put(NAME_PROPERTY_NAME, requirement.getPropertyName());
    if (requirement.getPropertyValue() != null) {
      propertiesMap.put(NAME_PROPERTY_VALUE, requirement.getPropertyValue());
    }
    String id = requirement.getId();
    if (id == null) {
      init(requirement.getPropertyName(), null, requirement.getType().getName(), null, propertiesMap, fields);
    } else {
      init(id, null, requirement.getType().getName(), buildType.isEnabled(id), propertiesMap, fields);
    }
  }

  private RequirementType getSubmittedType() {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Type attribute should be specified for a requirement.");
    }
    final RequirementType foundType = RequirementType.findByName(type);
    if (foundType == null) {
      throw new BadRequestException("Could not create Requirement type by type '" + type + "'. Check it is one of: " + Arrays.toString(RequirementType.values()));
    }
    return foundType;
  }

  public Requirement addRequirement(@NotNull final BuildTypeOrTemplate buildType, @NotNull final RequirementFactory requirementFactory) {
    final Map<String, String> propertiesMap = properties == null ? Collections.emptyMap() : properties.getMap();
    String propertyName = propertiesMap.get(NAME_PROPERTY_NAME);
    if (StringUtil.isEmpty(propertyName)) {
      throw new BadRequestException("No name is specified. Make sure '" + NAME_PROPERTY_NAME + "' property is present and has not empty value");
    }
    final Requirement requirementToAdd = requirementFactory.createRequirement(propertyName, propertiesMap.get(NAME_PROPERTY_VALUE), getSubmittedType());

    buildType.get().addRequirement(requirementToAdd);
    String requirementId = requirementToAdd.getId();
    if (disabled != null) {
      if (requirementId != null) {
        buildType.get().setEnabled(requirementId, !disabled);
      } else {
        throw new OperationException("Cannot disable an entity without id");
      }
    }
    return requirementToAdd;
  }

  public static class Storage {
    private final List<Requirement> deps = new ArrayList<>();
    private final Map<String, Boolean> enabledData = new HashMap<>();

    public Storage(final @NotNull BuildTypeSettings buildTypeSettings) {
      for (Requirement item : buildTypeSettings.getRequirements()) {
        deps.add(item);
        String id = item.getId();
        if (id != null) {
          enabledData.put(id, buildTypeSettings.isEnabled(id));
        }
      }
    }

    public List<Requirement> getItems() {
      return deps;
    }

    public void apply(final @NotNull BuildTypeSettings buildTypeSettings) {
      for (Requirement item : buildTypeSettings.getRequirements()) {
        buildTypeSettings.removeRequirement(item);
      }
      for (Requirement item : deps) {
        buildTypeSettings.addRequirement(item);
        String id = item.getId();
        if (id != null) {
          buildTypeSettings.setEnabled(id, enabledData.get(id));
        }
      }
    }
  }
}