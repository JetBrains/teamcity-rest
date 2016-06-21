/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.project;

import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.request.ProjectRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 04/06/2016
 */
@XmlRootElement(name = "projectFeatures")  //todo: ok that it clashes???
public class PropEntitiesProjectFeature {
  @XmlAttribute
  public Integer count;

  @XmlAttribute(required = false)
  @Nullable
  public String href;

  @XmlElement(name = "projectFeature")
  public List<PropEntityProjectFeature> propEntities;

  public PropEntitiesProjectFeature() {
  }

  public PropEntitiesProjectFeature(@NotNull final SProject project, @Nullable final String featureLocator, @NotNull final Fields fields, final BeanContext beanContext) {
    final List<SProjectFeatureDescriptor> features = new PropEntityProjectFeature.ProjectFeatureFinder(project).getItems(featureLocator).myEntries;
    propEntities = ValueWithDefault.decideDefault(fields.isIncluded("projectFeature"), new ValueWithDefault.Value<List<PropEntityProjectFeature>>() {
      @Nullable
      public List<PropEntityProjectFeature> get() {
        return CollectionsUtil.convertCollection(features, new Converter<PropEntityProjectFeature, SProjectFeatureDescriptor>() {
          public PropEntityProjectFeature createFrom(@NotNull final SProjectFeatureDescriptor source) {
            return new PropEntityProjectFeature(source, fields.getNestedField("projectFeature", Fields.NONE, Fields.LONG), beanContext);
          }
        });
      }
    });
    count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), features.size());
    href = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("href"), ProjectRequest.getFeaturesHref(project));
  }

  public boolean setTo(@NotNull final SProject project, @NotNull final ServiceLocator serviceLocator) {
    Storage original = new Storage(project);
    removeAll(project);
    try {
      if (propEntities != null) {
        for (PropEntityProjectFeature entity : propEntities) {
          entity.addToInternal(project, serviceLocator);
        }
      }
      return true;
    } catch (Exception e) {
      original.apply(project);
      throw new BadRequestException("Error replacing items", e);
    }
  }

  private static void removeAll(@NotNull final SProject project) {
    for (SProjectFeatureDescriptor entry : project.getOwnFeatures()) {
      project.removeFeature(entry.getId());
    }
  }

  public static class Storage{
    private final Collection<SProjectFeatureDescriptor> myFeatures;

    public Storage(final @NotNull SProject project) {
      myFeatures = project.getOwnFeatures();
    }

    public void apply(final @NotNull SProject project){
      removeAll(project);
      for (SProjectFeatureDescriptor entry : myFeatures) {
        project.addFeature(entry);
      }
    }
  }
}
