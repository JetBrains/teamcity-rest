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

import java.util.Collections;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "trigger")
public class PropEntityTrigger extends PropEntity implements PropEntityEdit<BuildTriggerDescriptor> {

  public PropEntityTrigger() {
  }

  public PropEntityTrigger(@NotNull final BuildTriggerDescriptor descriptor, @NotNull final BuildTypeSettings buildTypeSettings, @NotNull final Fields fields) {
    super(descriptor, buildTypeSettings, fields);
  }

  @NotNull
  public BuildTriggerDescriptor addTo(@NotNull final BuildTypeSettings buildType, @NotNull final ServiceLocator serviceLocator) {
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
  public BuildTriggerDescriptor addToInternal(@NotNull final BuildTypeSettings buildType, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Build trigger cannot have empty 'type'.");
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

  @NotNull
  public BuildTriggerDescriptor replaceIn(@NotNull final BuildTypeSettings buildType, @NotNull final BuildTriggerDescriptor trigger, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Build trigger cannot have empty 'type'.");
    }
    if (!type.equals(trigger.getType())) {
      throw new BadRequestException("Cannot change type of existing trigger.");
    }
    if (properties != null && !buildType.updateBuildTrigger(trigger.getId(), type, properties.getMap())) {
      throw new OperationException("Update failed");
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
