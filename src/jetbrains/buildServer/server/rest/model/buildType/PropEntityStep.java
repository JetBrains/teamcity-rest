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
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildRunnerDescriptor;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeSettingsEx;
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "step")
public class PropEntityStep extends PropEntity implements PropEntityEdit<SBuildRunnerDescriptor>{
  public PropEntityStep() {
  }

  public PropEntityStep(@NotNull SBuildRunnerDescriptor descriptor, @NotNull final BuildTypeSettingsEx buildType, @NotNull final Fields fields,
                        @NotNull final BeanContext beanContext) {
    super(descriptor.getId(), descriptor.getName(), descriptor.getType(), buildType.isEnabled(descriptor.getId()),
          !buildType.getOwnBuildRunners().contains(descriptor), descriptor.getParameters(), fields, beanContext);
    //can optimize by getting getOwnBuildRunners in the caller
  }

  @NotNull
  @Override
  public SBuildRunnerDescriptor addTo(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    PropEntitiesStep.Storage original = new PropEntitiesStep.Storage(buildType);
    try {
      return addToInternal(buildType, serviceLocator);
    } catch (Exception e) {
      //restore original settings
      original.apply(buildType);
      throw new BadRequestException("Error replacing items", e);
    }
  }

  @NotNull
  public SBuildRunnerDescriptor addToInternal(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    SBuildRunnerDescriptor result = addToInternalMain(buildType, serviceLocator);
    if (disabled != null) {
      buildType.setEnabled(result.getId(), !disabled);
    }
    return result;
  }

  @NotNull
  public SBuildRunnerDescriptor addToInternalMain(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Created step cannot have empty 'type'.");
    }

    SBuildRunnerDescriptor similar = getInheritedOrSameIdSimilar(buildType, serviceLocator);
    if (inherited != null && inherited && similar != null) {
      return similar;
    }
    if (similar != null && id != null && id.equals(similar.getId())) {
      //not inherited, but id is the same
      //todo
      return similar;
    }

    @SuppressWarnings("ConstantConditions")
    final SBuildRunnerDescriptor runnerToCreate =
      buildType.addBuildRunner(StringUtil.isEmpty(name) ? "" : name, type, properties != null ? properties.getMap() : Collections.<String, String>emptyMap());
    return runnerToCreate;
  }

  @Nullable
  public SBuildRunnerDescriptor getInheritedOrSameIdSimilar(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator){
    final List<SBuildRunnerDescriptor> ownItems = buildType.getOwnBuildRunners();
    for (SBuildRunnerDescriptor item : buildType.getBuildRunners()) {
      if (ownItems.contains(item)){
        if (id == null || !id.equals(item.getId())) {
          continue;
        }
      }
      if (isSimilar(new PropEntityStep(item, buildType, Fields.LONG, getFakeBeanContext(serviceLocator)))) return item;
    }
    return null;
  }

  @NotNull
  @Override
  public SBuildRunnerDescriptor replaceIn(@NotNull final BuildTypeSettingsEx buildType, @NotNull SBuildRunnerDescriptor step, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Created step cannot have empty 'type'.");
    }

    if (!buildType.updateBuildRunner(step.getId(), StringUtil.isEmpty(name) ? "" : name, type, properties != null ? properties.getMap() : Collections.<String, String>emptyMap())) {
      throw new InvalidStateException("Update failed");
    }
    if (disabled != null) {
      buildType.setEnabled(step.getId(), !disabled);
    }
    SBuildRunnerDescriptor result = buildType.findBuildRunnerById(step.getId());
    if (result == null){
      throw new OperationException("Cannot find build step by id '" + step.getId() + "' after successful addition");
    }
    return result;
  }

  public static void removeFrom(@NotNull final BuildTypeSettings buildTypeSettings, @NotNull final SBuildRunnerDescriptor step) {
    buildTypeSettings.removeBuildRunner(step.getId());
  }

  public static String getSetting(final BuildTypeSettings buildType, final BuildRunnerDescriptor step, final String name) {
    if ("name".equals(name)) { //todo: move to PropEntity...
      return step.getName();
    }
    if ("disabled".equals(name)) {
      return String.valueOf(!buildType.isEnabled(step.getId()));
    }
    throw new BadRequestException("Only 'name'and 'disabled' setting names are supported. '" + name + "' unknown.");
  }

  public static void setSetting(final BuildTypeSettings buildType,
                                final BuildRunnerDescriptor step,
                                final String name,
                                final String value) {
    if ("name".equals(name)) {
      buildType.updateBuildRunner(step.getId(), value, step.getType(), step.getParameters());
    } else if ("disabled".equals(name)) {
      buildType.setEnabled(step.getId(), !Boolean.parseBoolean(value));
    } else {
      throw new BadRequestException("Only 'name'and 'disabled' setting names are supported. '" + name + "' unknown.");
    }
  }
}
