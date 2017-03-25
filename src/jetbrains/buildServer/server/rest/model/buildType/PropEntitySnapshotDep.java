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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeSettingsEx;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.dependency.CyclicDependencyFoundException;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.util.Option;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "snapshot-dependency")
@XmlType
public class PropEntitySnapshotDep extends PropEntity implements PropEntityEdit<Dependency> {

  public static final String SOURCE_BUILD_TYPE = "source-buildType";
  public static final String REST_COMPATIBILITY_INCLUDE_BUILD_TYPE_IN_PROPERTIES =
    "rest.compatibility.includeSourceBuildTypeInDependencyProperties";
  public static final String SNAPSHOT_DEPENDENCY_TYPE_NAME = "snapshot_dependency";
  public static final String NAME_SOURCE_BUILD_TYPE_ID = "source_buildTypeId";


  @XmlElement(name = SOURCE_BUILD_TYPE)
  public BuildType sourceBuildType;

  public PropEntitySnapshotDep() {
  }

  public PropEntitySnapshotDep(@NotNull final Dependency dependency,
                               @NotNull final BuildTypeSettingsEx buildType,
                               @NotNull final Fields fields,
                               @NotNull final BeanContext context) {
    HashMap<String, String> properties = new HashMap<String, String>();
    if (TeamCityProperties.getBoolean(REST_COMPATIBILITY_INCLUDE_BUILD_TYPE_IN_PROPERTIES)) {
      properties.put(NAME_SOURCE_BUILD_TYPE_ID, dependency.getDependOnExternalId());
    }

    addOptionToProperty(properties, dependency, DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED);
    addOptionToProperty(properties, dependency, DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED_TO_START);
    addOptionToProperty(properties, dependency, DependencyOptions.RUN_BUILD_ON_THE_SAME_AGENT);
    addOptionToProperty(properties, dependency, DependencyOptions.TAKE_STARTED_BUILD_WITH_SAME_REVISIONS);
    addOptionToProperty(properties, dependency, DependencyOptions.TAKE_SUCCESSFUL_BUILDS_ONLY);

    //todo: review id, type here
    init(dependency.getDependOnExternalId(), null, SNAPSHOT_DEPENDENCY_TYPE_NAME, null,
         !buildType.getOwnDependencies().contains(dependency), properties, fields, context.getServiceLocator()); //can optimize by getting getOwnDependencies in the caller

    sourceBuildType = ValueWithDefault.decideDefault(fields.isIncluded(PropEntitySnapshotDep.SOURCE_BUILD_TYPE, false, true), new ValueWithDefault.Value<BuildType>() {
      @Nullable
      public BuildType get() {
        @Nullable SBuildType dependOn = null;
        try {
          dependOn = dependency.getDependOn();
        } catch (AccessDeniedException e) {
          //ignore, will use ids later
        }
        final Fields nestedField = fields.getNestedField(PropEntitySnapshotDep.SOURCE_BUILD_TYPE);
        if (dependOn != null) {
          return new BuildType(new BuildTypeOrTemplate(dependOn), nestedField, context);
        } else {
          return new BuildType(dependency.getDependOnExternalId(), dependency.getDependOnId(), nestedField, context);
        }
      }
    });
  }

  @NotNull
  @Override
  public Dependency addTo(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    if (!SNAPSHOT_DEPENDENCY_TYPE_NAME.equals(type)) {
      throw new BadRequestException("Snapshot dependency should have type '" + SNAPSHOT_DEPENDENCY_TYPE_NAME + "'.");
    }

    final Map<String, String> propertiesMap = properties == null ? Collections.emptyMap() : properties.getMap();
    final String buildTypeIdFromProperty = propertiesMap.get(NAME_SOURCE_BUILD_TYPE_ID); //compatibility mode with pre-8.0
    String buildTypeIdDependOn = getBuildTypeExternalIdForDependency(sourceBuildType, buildTypeIdFromProperty, serviceLocator);
    BuildTypeUtil.checkCanUseBuildTypeAsDependency(buildTypeIdDependOn, serviceLocator);

    Dependency similar = getInheritedOrSameIdSimilar(buildType, serviceLocator);
    if (inherited != null && inherited && similar != null) {
      return similar;
    }
    if (similar != null && id != null && id.equals(similar.getDependOnExternalId())) {
      //not inherited, but id is the same
      //todo
      return similar;
    }

    //todo: (TeamCity) for some reason API does not report adding dependency with same id. Seems like it just ignores the call
    if (getSnapshotDepOrNull(buildType, buildTypeIdDependOn) != null) {
      throw new BadRequestException("Snapshot dependency on build type with id '" + buildTypeIdDependOn + "' already exists.");
    }

    final Dependency result = serviceLocator.getSingletonService(DependencyFactory.class).createDependency(buildTypeIdDependOn);
    for (Map.Entry<String, String> property : propertiesMap.entrySet()) {
      if (!NAME_SOURCE_BUILD_TYPE_ID.equals(property.getKey())) {
        setDependencyOption(property.getKey(), property.getValue(), result);
      }
    }
    try {
      buildType.addDependency(result);
    } catch (CyclicDependencyFoundException e) {
      throw new BadRequestException("Error adding dependency", e);
    }
    return getSnapshotDep(buildType, result.getDependOnExternalId(), serviceLocator.getSingletonService(BuildTypeFinder.class));
  }

  @Nullable
  public Dependency getInheritedOrSameIdSimilar(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator){
    final List<Dependency> ownItems = buildType.getOwnDependencies();
    for (Dependency item : buildType.getDependencies()) {
      if (ownItems.contains(item)){
        if (id == null || !id.equals(item.getDependOnExternalId())) {
          continue;
        }
      }
      if (isSimilar(new PropEntitySnapshotDep(item, buildType, Fields.LONG, getFakeBeanContext(serviceLocator)))) return item;
    }
    return null;
  }

  @NotNull
  @Override
  public Dependency replaceIn(@NotNull final BuildTypeSettingsEx buildType, @NotNull final Dependency entityToReplace, @NotNull final ServiceLocator serviceLocator) {
    buildType.removeDependency(entityToReplace);

    try {
      return addTo(buildType, serviceLocator);
    } catch (Exception e) {
      //restore
      buildType.addDependency(entityToReplace);
      throw new BadRequestException("Error updating snapshot dependency", e);
    }
  }

  public static void removeFrom(final BuildTypeSettings buildType, final Dependency dependency) {
    buildType.removeDependency(dependency);
  }

  @NotNull
  public static String getBuildTypeExternalIdForDependency(@Nullable final BuildType buildTypeRef,
                                                           @Nullable final String buildTypeIdFromProperty,
                                                           @NotNull final ServiceLocator serviceLocator) {
    if (buildTypeIdFromProperty != null) {
      if (buildTypeRef == null) {
        return buildTypeIdFromProperty;
      } else {
        final String externalIdFromPosted = buildTypeRef.getExternalIdFromPosted(serviceLocator);
        if (externalIdFromPosted == null || buildTypeIdFromProperty.equals(externalIdFromPosted)) {
          return buildTypeIdFromProperty;
        }
        throw new BadRequestException("Dependency descriptor has conflicting '" + NAME_SOURCE_BUILD_TYPE_ID + "' property and '"
                                      + SOURCE_BUILD_TYPE + "' element with not matching ids. Leave only one or make them match.");
      }
    }

    if (buildTypeRef == null) {
      throw new BadRequestException("Dependency properties should contain '" + SOURCE_BUILD_TYPE + "' element" +
                                    (TeamCityProperties.getBoolean(REST_COMPATIBILITY_INCLUDE_BUILD_TYPE_IN_PROPERTIES)
                                     ? " or '" + NAME_SOURCE_BUILD_TYPE_ID + "' property."
                                     : "."));
    }

    final String externalIdFromPosted = buildTypeRef.getExternalIdFromPosted(serviceLocator);
    if (externalIdFromPosted != null) {
      return externalIdFromPosted;
    }
    throw new NotFoundException(
      "Could not find id of the build configuration defined by '" + SOURCE_BUILD_TYPE + "' element. Make sure to specify existing build configuration 'id' or 'internalId'.");
  }

  private void addOptionToProperty(final HashMap<String, String> properties, final Dependency dependency,
                                   final Option option) {
    properties.put(option.getKey(), dependency.getOption(option).toString());
  }

  private void setDependencyOption(final String name, final String value, final Dependency dependency) {
    final Option option = Option.fromKey(name);
    if (option == null) {
      throw new IllegalArgumentException("No option found for name '" + name + "'");
    }
    //noinspection unchecked
    dependency.setOption(option, option.fromString(value));  //todo: try to provide possible values for ENUM options in error message
  }

  public static Dependency getSnapshotDep(@NotNull final BuildTypeSettings buildType, @Nullable final String snapshotDepLocator, @NotNull final BuildTypeFinder buildTypeFinder) {
    if (StringUtil.isEmpty(snapshotDepLocator)) {
      throw new BadRequestException("Empty snapshot dependency locator is not supported.");
    }

    final Locator locator = new Locator(snapshotDepLocator, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME, "buildType");

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's id of the dependency (build type external id)
      final String snapshotDepId = locator.getSingleValue();
      //todo (TeamCity) seems like no way to get snapshot dependency by source build type
      Dependency foundDependency = getSnapshotDepOrNull(buildType, snapshotDepId);
      if (foundDependency != null) {
        return foundDependency;
      }
      // fall back to internal id for compatibility
      foundDependency = getSnapshotDepByInternalIdOrNull(buildType, snapshotDepId);
      if (foundDependency != null) {
        return foundDependency;
      }
    }

    String buildTypeLocator = locator.getSingleDimensionValue("buildType");
    if (buildTypeLocator != null) {
      final String externalId = buildTypeFinder.getBuildType(null, buildTypeLocator, false).getExternalId();
      final Dependency foundDependency = getSnapshotDepOrNull(buildType, externalId);
      if (foundDependency != null) {
        return foundDependency;
      }
      throw new NotFoundException("No snapshot dependency found to build type with external id '" + externalId + "'.");
    }

    locator.checkLocatorFullyProcessed();
    throw new NotFoundException("No snapshot dependency found by locator '" + snapshotDepLocator + "'. Locator should be existing dependency source build type external id.");
  }

  public static Dependency getSnapshotDepOrNull(final BuildTypeSettings buildType, final String sourceBuildTypeExternalId){
    for (Dependency dependency : buildType.getDependencies()) {
      if (dependency.getDependOnExternalId().equals(sourceBuildTypeExternalId)) {
        return dependency;
      }
    }
    return null;
  }

  public static Dependency getSnapshotDepByInternalIdOrNull(final BuildTypeSettings buildType, final String sourceBuildTypeInternalId){
    for (Dependency dependency : buildType.getDependencies()) {
      if (dependency.getDependOnId().equals(sourceBuildTypeInternalId)) {
        return dependency;
      }
    }
    return null;
  }

  private boolean isSimilar(@Nullable final PropEntitySnapshotDep that) {
    return that != null &&
           (sourceBuildType == that.sourceBuildType || (sourceBuildType != null && sourceBuildType.isSimilar(that.sourceBuildType))) &&
           super.isSimilar(that);
  }
}
