/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildCustomizationSettings;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeSettingsEx;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "trigger")
@ModelDescription("Represents a build trigger.")
public class PropEntityTrigger extends PropEntity implements PropEntityEdit<BuildTriggerDescriptor> {

  @XmlElement(name = "buildCustomization")
  private BuildTriggerCustomization buildTriggerCustomization;

  public PropEntityTrigger() {
  }

  public PropEntityTrigger(@NotNull final BuildTriggerDescriptor descriptor,
                           @NotNull final BuildTypeSettingsEx buildTypeSettings,
                           @NotNull final Fields fields,
                           @NotNull final BeanContext beanContext) {
    super(descriptor, !buildTypeSettings.getOwnBuildTriggers().contains(descriptor), buildTypeSettings, fields, beanContext);
    buildTriggerCustomization = ValueWithDefault.decideDefault(fields.isIncluded("buildCustomization"), new BuildTriggerCustomization(descriptor, fields, beanContext));
    //can optimize by getting getOwnBuildTriggers in the caller
  }

  @NotNull
  @Override
  public BuildTriggerDescriptor addTo(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    PropEntitiesTrigger.Storage original = new PropEntitiesTrigger.Storage(buildType);
    try {
      return addToInternal(buildType, serviceLocator);
    } catch (Exception e) {
      //restore original settings
      original.apply(buildType);
      throw new BadRequestException("Error replacing items", e);
    }
  }

  @NotNull
  public BuildTriggerDescriptor addToInternal(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    BuildTriggerDescriptor result = addToInternalMain(buildType, serviceLocator);   //todo: disabled is done twice, adds within  addToInternalMain unlike artifact dependencies
    if (disabled != null) {
      buildType.setEnabled(result.getId(), !disabled);
    }
    return result;
  }

  @NotNull
  public BuildTriggerDescriptor addToInternalMain(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Build trigger cannot have empty 'type'.");
    }

    BuildTriggerDescriptor similar = getInheritedOrSameIdSimilar(buildType, serviceLocator);
    if (inherited != null && inherited && similar != null) {
      return similar;
    }
    if (similar != null && id != null && id.equals(similar.getId())) {
      //not inherited, but id is the same
      //todo
      return similar;
    }

    final BuildTriggerDescriptor triggerToAdd = serviceLocator
      .getSingletonService(BuildTriggerDescriptorFactory.class).createTriggerDescriptor(type, properties == null ? Collections.emptyMap() : properties.getMap());

    if (!buildType.addBuildTrigger(triggerToAdd)) {
      String additionalMessage = getDetails(buildType, triggerToAdd);
      throw new OperationException("Build trigger addition failed." + (additionalMessage != null ? " " + additionalMessage : ""));
    }
    if (disabled != null) {
      buildType.setEnabled(triggerToAdd.getId(), !disabled);
    }
    BuildTriggerDescriptor result = buildType.findTriggerById(triggerToAdd.getId());
    if (result == null){
      throw new OperationException("Cannot find just added trigger with id '" + triggerToAdd.getId() + "'");
    }
    return result;
  }

  @Nullable
  public BuildTriggerDescriptor getInheritedOrSameIdSimilar(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator){
    final List<BuildTriggerDescriptor> ownItems = buildType.getOwnBuildTriggers();
    for (BuildTriggerDescriptor item : buildType.getBuildTriggersCollection()) {
      if (ownItems.contains(item)){
        if (id == null || !id.equals(item.getId())) {
          continue;
        }
      }
      if (isSimilar(new PropEntityTrigger(item, buildType, Fields.LONG, getFakeBeanContext(serviceLocator)))) return item;
    }
    return null;
  }

  @NotNull
  public BuildTriggerDescriptor replaceIn(@NotNull final BuildTypeSettingsEx buildType, @NotNull final BuildTriggerDescriptor trigger, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Build trigger cannot have empty 'type'.");
    }
    if ((properties != null || buildTriggerCustomization != null)) {
      BuildTriggerDescriptor triggerDescriptor = serviceLocator.getSingletonService(BuildTriggerDescriptorFactory.class).createTriggerDescriptor(trigger.getId(), trigger.getTriggerName(),
                                                                                                    properties != null ? properties.getMap() : Collections.emptyMap(),
                                                                                                    buildTriggerCustomization != null
                                                                                                    ? buildTriggerCustomization.toBuildCustomizationSettings(serviceLocator)
                                                                                                    : BuildCustomizationSettings.empty());
      if (!buildType.updateBuildTrigger(triggerDescriptor)) {
        throw new OperationException("Update failed");
      }
    }
    if (disabled != null) {
      buildType.setEnabled(trigger.getId(), !disabled);
    }
    BuildTriggerDescriptor result = buildType.findTriggerById(trigger.getId());
    if (result == null){
      throw new OperationException("Cannot find just added trigger with id '" + trigger.getId() + "'");
    }
    return result;
  }

  public static void removeFrom(final BuildTypeSettings buildType, final BuildTriggerDescriptor trigger) {
    if (!buildType.removeBuildTrigger(trigger)) {
      throw new InvalidStateException("Build trigger removal failed");
    }
  }

  private String getDetails(final BuildTypeSettings buildType, final BuildTriggerDescriptor triggerToAdd) {
    final BuildTriggerDescriptor foundTriggerWithSameId = buildType.findTriggerById(triggerToAdd.getId());
    if (foundTriggerWithSameId != null) {
      return "Trigger with id '" + triggerToAdd.getId() + "'already exists.";
    }
    return null;
  }
}
