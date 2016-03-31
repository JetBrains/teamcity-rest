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

import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildFeatureDescriptorFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "features")
@SuppressWarnings("PublicField")
public class PropEntitiesFeature {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "feature")
  public List<PropEntityFeature> propEntities;

  public PropEntitiesFeature() {
  }

  public PropEntitiesFeature(final BuildTypeSettings buildType, @NotNull final Fields fields) {
    final Collection<SBuildFeatureDescriptor> buildFeatures = buildType.getBuildFeatures();
    propEntities = ValueWithDefault.decideDefault(fields.isIncluded("feature"), new ValueWithDefault.Value<List<PropEntityFeature>>() {
      @Nullable
      public List<PropEntityFeature> get() {
        return CollectionsUtil.convertCollection(buildFeatures, new Converter<PropEntityFeature, SBuildFeatureDescriptor>() {
          public PropEntityFeature createFrom(@NotNull final SBuildFeatureDescriptor source) {
            return new PropEntityFeature(source, buildType, fields.getNestedField("feature", Fields.NONE, Fields.LONG));
          }
        });
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), buildFeatures.size());
  }

  public boolean setToBuildType(@NotNull final BuildTypeSettings buildTypeSettings, @NotNull final ServiceLocator serviceLocator) {
    final Collection<SBuildFeatureDescriptor> originals = buildTypeSettings.getBuildFeatures();    //todo: process enabled
    removeAllFeatures(buildTypeSettings);
    try {
      if (propEntities != null) {
        for (PropEntityFeature entity : propEntities) {
          entity.addFeature(buildTypeSettings, serviceLocator.getSingletonService(BuildFeatureDescriptorFactory.class));
        }
      }
      return true;
    } catch (Exception e) {
      //restore original settings
      removeAllFeatures(buildTypeSettings);
      for (SBuildFeatureDescriptor entry : originals) {
        buildTypeSettings.addBuildFeature(entry);
      }
      throw new BadRequestException("Error replacing items", e);
    }
  }

  private void removeAllFeatures(@NotNull final BuildTypeSettings buildType) {
    for (SBuildFeatureDescriptor entry : buildType.getBuildFeatures()) {
      buildType.removeBuildFeature(entry.getId());  //todo: (TeamCity API): why string and not object?
    }
  }
}
