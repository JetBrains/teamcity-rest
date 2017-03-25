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

import java.util.HashMap;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildFeatureDescriptorFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeSettingsEx;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "feature")
public class PropEntityFeature extends PropEntity implements PropEntityEdit<SBuildFeatureDescriptor>{
  public PropEntityFeature() {
  }

  public PropEntityFeature(@NotNull SBuildFeatureDescriptor descriptor, @NotNull final BuildTypeSettingsEx buildType, @NotNull final Fields fields,
                           @NotNull final BeanContext beanContext) {
    super(descriptor, !buildType.getOwnBuildFeatures().contains(descriptor), buildType, fields, beanContext.getServiceLocator()); //can optimize by getting getOwnBuildFeatures in the caller
  }

  @NotNull
  public SBuildFeatureDescriptor addToInternal(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    SBuildFeatureDescriptor result = addToInternalMain(buildType, serviceLocator);
    if (disabled != null) {
      buildType.setEnabled(result.getId(), !disabled);
    }
    return result;
  }

  @NotNull
  public SBuildFeatureDescriptor addToInternalMain(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Created build feature cannot have empty 'type'.");
    }
    SBuildFeatureDescriptor similar = getInheritedOrSameIdSimilar(buildType, serviceLocator);
    if (inherited != null && inherited && similar != null) {
      return similar;
    }
    if (similar != null && id != null && id.equals(similar.getId())) {
      //not inherited, but id is the same
      //todo
      return similar;
    }

    @NotNull final BuildFeatureDescriptorFactory factory = serviceLocator.getSingletonService(BuildFeatureDescriptorFactory.class);
    String forcedId = null;
    //special case for "overriden" entities
    if (id != null) {
      for (SBuildFeatureDescriptor item : buildType.getBuildFeatures()) {
        if (id.equals(item.getId())) {
          forcedId = id;
          break;
        }
      }
    }

    SBuildFeatureDescriptor newBuildFeature;
    if (forcedId != null) {
      newBuildFeature = factory.createBuildFeature(forcedId, type, properties != null ? properties.getMap() : new HashMap<String, String>());
    } else {
      newBuildFeature = factory.createNewBuildFeature(type, properties != null ? properties.getMap() : new HashMap<String, String>());
    }

    try {
      buildType.addBuildFeature(newBuildFeature);
    } catch (Exception e) {
      final String details = getDetails(buildType, newBuildFeature, e);
      throw new BadRequestException("Error adding feature: " + details, e);
    }
    if (disabled != null) {
      buildType.setEnabled(newBuildFeature.getId(), !disabled);
    }
    return BuildTypeUtil.getBuildTypeFeatureOrNull(buildType, newBuildFeature.getId());
  }

  @Nullable
  public SBuildFeatureDescriptor getInheritedOrSameIdSimilar(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    final List<SBuildFeatureDescriptor> ownItems = buildType.getOwnBuildFeatures();
    for (SBuildFeatureDescriptor item : buildType.getBuildFeatures()) {
      if (ownItems.contains(item)) {
        if (id == null || !id.equals(item.getId())) {
          continue;
        }
      }
      if (isSimilar(new PropEntityFeature(item, buildType, Fields.LONG, getFakeBeanContext(serviceLocator)))) return item;
    }
    return null;
  }

  @NotNull
  @Override
  public SBuildFeatureDescriptor addTo(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    PropEntitiesFeature.Storage original = new PropEntitiesFeature.Storage(buildType);
    try {
      return addToInternal(buildType, serviceLocator);
    } catch (Exception e) {
      original.apply(buildType);
      throw e;
    }
  }

  @NotNull
  @Override
  public SBuildFeatureDescriptor replaceIn(@NotNull final BuildTypeSettingsEx buildType,
                                           @NotNull final SBuildFeatureDescriptor feature,
                                           @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Build feature cannot have empty 'type'.");
    }
    if (properties != null && !buildType.updateBuildFeature(feature.getId(), type, properties.getMap())) {
      throw new InvalidStateException("Update failed");
    }
    if (disabled != null) {
      buildType.setEnabled(feature.getId(), !disabled);
    }
    return BuildTypeUtil.getBuildTypeFeatureOrNull(buildType, feature.getId());
  }

  public static void removeFrom(final BuildTypeSettings buildType, final SBuildFeatureDescriptor feature) {
    buildType.removeBuildFeature(feature.getId());
  }

  private String getDetails(final BuildTypeSettings buildType, final SBuildFeatureDescriptor newBuildFeature, final Exception e) {
    final SBuildFeatureDescriptor existingFeature = BuildTypeUtil.getBuildTypeFeatureOrNull(buildType, newBuildFeature.getId());
    if (existingFeature != null) {
      return "Feature with id '" + newBuildFeature.getId() + "' already exists.";
    }
    return e.toString();
  }
}
