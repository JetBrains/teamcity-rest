/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeSettingsEx;
import jetbrains.buildServer.serverSide.RequirementFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "agent-requirement")
public class PropEntityAgentRequirement extends PropEntity implements PropEntityEdit<Requirement> {

  public static final String NAME_PROPERTY_VALUE = "property-value";
  public static final String NAME_PROPERTY_NAME = "property-name";

  public PropEntityAgentRequirement() {
  }

  public PropEntityAgentRequirement(@NotNull final Requirement requirement,
                                    @NotNull final BuildTypeSettingsEx buildType,
                                    @NotNull final Fields fields,
                                    @NotNull final BeanContext beanContext) {
    HashMap<String, String> propertiesMap = new HashMap<String, String>(2);
    propertiesMap.put(NAME_PROPERTY_NAME, requirement.getPropertyName());
    if (requirement.getPropertyValue() != null) {
      propertiesMap.put(NAME_PROPERTY_VALUE, requirement.getPropertyValue());
    }
    String id = requirement.getId();
    if (id == null) {
      //can optimize by getting getOwnRequirements in the caller
      init(requirement.getPropertyName(), null, requirement.getType().getName(), null,
           !buildType.getOwnRequirements().contains(requirement), propertiesMap, fields, beanContext);
    } else {
      init(id, null, requirement.getType().getName(), buildType.isEnabled(id), !buildType.getOwnRequirements().contains(requirement), propertiesMap, fields, beanContext);
    }
  }

  private RequirementType getSubmittedType() {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Type attribute should be specified for a requirement.");
    }
    final RequirementType foundType = RequirementType.findByName(type);
    if (foundType == null) {
      List<String> supportedNames = CollectionsUtil.convertCollection(RequirementType.ALL_REQUIREMENT_TYPES, new Converter<String, RequirementType>() {
        @Override
        public String createFrom(@NotNull final RequirementType source) {
          return source.getName();
        }
      });
      throw new BadRequestException("Could not create Requirement type by type '" + type + "'. Check it is one of: [" + StringUtil.join(", ", supportedNames) + "]");
    }
    return foundType;
  }

  @NotNull
  @Override
  public Requirement addTo(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    Requirement result = addToMain(buildType, serviceLocator);
    if (disabled != null) {
      buildType.setEnabled(result.getId(), !disabled);
    }
    return result;
  }

  @NotNull
  public Requirement addToMain(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    final Map<String, String> propertiesMap = properties == null ? Collections.emptyMap() : properties.getMap();
    String propertyName = propertiesMap.get(NAME_PROPERTY_NAME);
    if (StringUtil.isEmpty(propertyName)) {
      throw new BadRequestException("No name is specified. Make sure '" + NAME_PROPERTY_NAME + "' property is present and has not empty value");
    }

    Requirement similar = getInheritedOrSameIdSimilar(buildType, serviceLocator);
    if (inherited != null && inherited && similar != null) {
      return similar;
    }
    if (similar != null && id != null && id.equals(similar.getId())) {
      //not inherited, but id is the same
      //todo
      return similar;
    }

    @NotNull final RequirementFactory factory = serviceLocator.getSingletonService(RequirementFactory.class);
    String forcedId = null;
    //special case for "overriden" entities
    if (id != null){
      for (Requirement item : buildType.getRequirements()) {
        if (id.equals(item.getId())) {
          forcedId = id;
          break;
        }
      }
    }

    Requirement requirementToAdd;
    if (forcedId != null) {
      requirementToAdd = factory.createRequirement(forcedId, propertyName, propertiesMap.get(NAME_PROPERTY_VALUE), getSubmittedType());
    } else {
      requirementToAdd = factory.createRequirement(propertyName, propertiesMap.get(NAME_PROPERTY_VALUE), getSubmittedType());
    }

    String requirementId = requirementToAdd.getId();
    if (requirementId == null && disabled != null) {
      //throw exception before adding requirement to the model
      throw new OperationException("Cannot set disabled state for an entity without id");
    }
    buildType.addRequirement(requirementToAdd);
    if (disabled != null) {
      buildType.setEnabled(requirementId, !disabled);
    }
    return requirementToAdd;
  }


  @Nullable
  public Requirement getInheritedOrSameIdSimilar(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    final List<Requirement> ownItems = buildType.getRequirements();
    for (Requirement item : buildType.getRequirements()) {
      if (ownItems.contains(item)) {
        if (id == null || !id.equals(item.getId())) {
          continue;
        }
      }
      if (isSimilar(new PropEntityAgentRequirement(item, buildType, Fields.LONG, getFakeBeanContext(serviceLocator)))) return item;
    }
    return null;
  }

  @NotNull
  @Override
  public Requirement replaceIn(@NotNull final BuildTypeSettingsEx buildType, @NotNull final Requirement entityToReplace, @NotNull final ServiceLocator serviceLocator) {
    PropEntitiesAgentRequirement.Storage original = new PropEntitiesAgentRequirement.Storage(buildType);
    buildType.removeRequirement(entityToReplace);

    try {
      return addTo(buildType, serviceLocator);
    } catch (Exception e) {
      //restore
      original.apply(buildType);
      throw new BadRequestException("Error setting new agent requirement", e);
    }
  }

  public static void removeFrom(@NotNull final BuildTypeSettings buildType, @NotNull final Requirement requirement) {
    buildType.removeRequirement(requirement);
  }
}