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
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.serverSide.BuildFeatureDescriptorFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.ParametersDescriptor;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.DuplicateIdException;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "feature")
public class PropEntityFeature extends PropEntity {
  public PropEntityFeature() {
  }

  public PropEntityFeature(@NotNull ParametersDescriptor descriptor, @NotNull final BuildTypeSettings buildType, @NotNull final Fields fields) {
    super(descriptor, buildType, fields);
  }

  public SBuildFeatureDescriptor addFeature(final BuildTypeSettings buildType, final BuildFeatureDescriptorFactory factory) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Created build feature cannot have empty 'type'.");
    }
    final SBuildFeatureDescriptor newBuildFeature = factory.createNewBuildFeature(type, properties != null ? properties.getMap() : new HashMap<String, String>());
    try {
      buildType.addBuildFeature(newBuildFeature);
    } catch (DuplicateIdException e) {
      final String details = getDetails(buildType, newBuildFeature, e);
      throw new BadRequestException("Error adding feature." + (details != null ? " " + details : ""));
    }
    if (disabled != null) {
      buildType.setEnabled(newBuildFeature.getId(), !disabled);
    }
    return BuildTypeUtil.getBuildTypeFeatureOrNull(buildType, newBuildFeature.getId());
  }

  public SBuildFeatureDescriptor updateFeature(@NotNull final BuildTypeSettings buildType, @NotNull final SBuildFeatureDescriptor feature) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Build feature cannot have empty 'type'.");
    }
    if (!type.equals(feature.getType())) {
      throw new BadRequestException("Cannot change type of existing build feature.");
    }
    if (!buildType.updateBuildFeature(feature.getId(), type, properties.getMap())) {
      throw new InvalidStateException("Update failed");
    }
    if (disabled != null) {
      buildType.setEnabled(feature.getId(), !disabled);
    }
    return BuildTypeUtil.getBuildTypeFeatureOrNull(buildType, feature.getId());
  }

  private String getDetails(final BuildTypeSettings buildType, final SBuildFeatureDescriptor newBuildFeature, final Exception e) {
    final SBuildFeatureDescriptor existingFeature = BuildTypeUtil.getBuildTypeFeatureOrNull(buildType, newBuildFeature.getId());
    if (existingFeature != null) {
      return "Feature with id '" + newBuildFeature.getId() + "' already exists.";
    }
    return e.getClass().getName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
  }
}
