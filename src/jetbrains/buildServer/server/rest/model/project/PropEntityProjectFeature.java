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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.data.AbstractTypedFinder;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.data.ValueCondition;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.PropEntity;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.ProjectFeatureDescriptorFactory;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 04/06/2016
 */
@XmlRootElement(name = "project-feature") //todo: is this OK that it clashes with PropEntityFeature???
public class PropEntityProjectFeature extends PropEntity {
  public PropEntityProjectFeature() {
  }

  public PropEntityProjectFeature(@NotNull final SProjectFeatureDescriptor descriptor,
                                  @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    init(descriptor.getId(), null, descriptor.getType(), null, null, descriptor.getParameters(), fields, beanContext.getServiceLocator());
  }

  /**
   * @param featureId id of the feature to search in the project
   */
  @NotNull
  public static SProjectFeatureDescriptor getFeatureByLocator(@NotNull final SProject project, final @NotNull String featureLocator) {
    return new ProjectFeatureFinder(project).getItem(featureLocator);
  }

  @NotNull
  public SProjectFeatureDescriptor addTo(@NotNull final SProject project, @NotNull final ServiceLocator serviceLocator) {
    PropEntitiesProjectFeature.Storage original = new PropEntitiesProjectFeature.Storage(project);
    try {
      return addToInternal(project, serviceLocator);
    } catch (Exception e) {
      original.apply(project);
      throw e;
    }
  }

  @NotNull
  public SProjectFeatureDescriptor addToInternal(@NotNull final SProject project, @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Project feature cannot have empty 'type'.");
    }

    @NotNull final ProjectFeatureDescriptorFactory factory = serviceLocator.getSingletonService(ProjectFeatureDescriptorFactory.class);
    String forcedId = null;
    //special case for "overriden" entities
    if (id != null) {
      for (SProjectFeatureDescriptor item : project.getFeatures()) {
        if (id.equals(item.getId())) {
          forcedId = id;
          break;
        }
      }
    }

    SProjectFeatureDescriptor newFeature;
    if (forcedId != null) {
      newFeature = factory.createProjectFeature(forcedId, type, properties != null ? properties.getMap() : new HashMap<String, String>());
    } else {
      newFeature = factory.createNewProjectFeature(type, properties != null ? properties.getMap() : new HashMap<String, String>());
    }

    try {
      project.addFeature(newFeature);
    } catch (Exception e) {
      final String details = getDetails(project, newFeature, e);
      throw new BadRequestException("Error adding feature: " + details, e);
    }
    return getFeatureByLocator(project, newFeature.getId());
  }

  private String getDetails(@NotNull final SProject project, @NotNull final SProjectFeatureDescriptor newFeature, @NotNull final Exception e) {
    final SProjectFeatureDescriptor existingFeature = project.findFeatureById(newFeature.getId());
    if (existingFeature != null) {
      return "Feature with id '" + newFeature.getId() + "' already exists.";
    }
    return e.toString();
  }

  @NotNull
  public SProjectFeatureDescriptor replaceIn(@NotNull final SProject project,
                                             @NotNull final SProjectFeatureDescriptor entityToReplace,
                                             @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Project feature cannot have empty 'type'.");
    }
    if (!type.equals(entityToReplace.getType())) {
      throw new BadRequestException("Cannot change type of existing build feature.");
    }
    if (properties != null && !project.updateFeature(entityToReplace.getId(), type, properties.getMap())) {
      throw new InvalidStateException("Update of the project feature with id '" + entityToReplace.getId() + "' failed");
    }
    return getFeatureByLocator(project, entityToReplace.getId());
  }

  public static class ProjectFeatureFinder extends AbstractTypedFinder<SProjectFeatureDescriptor> {
    private static final Dimension<String> ID = new Dimension<>("id");
    private static final Dimension<ValueCondition> TYPE = new Dimension<>("type");
    private static final Dimension<ParameterCondition> PROPERTY = new Dimension<>("property");

    public ProjectFeatureFinder(@NotNull final SProject project) {
//      description("Project features of project with id '" + project.getExternalId() + "'");
      singleDimension(dimension -> {
        // no dimensions found, assume it's id
        SProjectFeatureDescriptor result = project.findFeatureById(dimension);
        if (result == null) throw new NotFoundException("Cannot find project feature with id '" + dimension + "' in the project with id '" + project.getExternalId() + "'");
        return Collections.singletonList(result);
      });

      dimensionString(ID).description("feature id")
                         .filter((value, item) -> value.equals(item.getId()))
                         .toItems(dimension -> {
                           SProjectFeatureDescriptor result = project.findFeatureById(dimension);
                           return result == null ? null : Collections.singletonList(result);
                         });

      dimensionValueCondition(TYPE).description("feature type")
                           .valueForDefaultFilter(item -> item.getType());

      dimensionParameterCondition(PROPERTY).description("feature property").valueForDefaultFilter(item -> new MapParametersProviderImpl(item.getParameters()));

      multipleConvertToItems(DimensionConditionsImpl.ALWAYS, dimensions -> new ArrayList<>(project.getFeatures()));
    }

    @NotNull
    @Override
    public String getItemLocator(@NotNull final SProjectFeatureDescriptor projectFeatureDescriptor) {
      return projectFeatureDescriptor.getId();
    }
  }
}
