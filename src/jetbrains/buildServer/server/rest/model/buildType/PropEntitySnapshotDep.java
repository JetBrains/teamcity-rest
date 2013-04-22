package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.dependency.CyclicDependencyFoundException;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.serverSide.impl.dependency.DependencyFactoryImpl;
import jetbrains.buildServer.util.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "snapshot-dependency")
@XmlType
public class PropEntitySnapshotDep extends PropEntity {

  public static final String SOURCE_BUILD_TYPE = "source-buildType";
  public static final String REST_COMPATIBILITY_INCLUDE_BUILD_TYPE_IN_PROPERTIES =
    "rest.compatibility.includeSourceBuildTypeInDependencyProperties";
  public static final String SNAPSHOT_DEPENDENCY_TYPE_NAME = "snapshot_dependency";
  public static final String NAME_SOURCE_BUILD_TYPE_ID = "source_buildTypeId";


  @XmlElement(name = SOURCE_BUILD_TYPE)
  public BuildTypeRef sourceBuildType;

  public PropEntitySnapshotDep() {
  }

  public PropEntitySnapshotDep(@NotNull final Dependency dependency, @NotNull final BeanContext context) {
    this.id = dependency.getDependOnId();
    this.type = SNAPSHOT_DEPENDENCY_TYPE_NAME;

    //todo: review id, type here
    HashMap<String, String> properties = new HashMap<String, String>();
    if (TeamCityProperties.getBoolean(REST_COMPATIBILITY_INCLUDE_BUILD_TYPE_IN_PROPERTIES)) {
      properties.put(NAME_SOURCE_BUILD_TYPE_ID, dependency.getDependOnId());
    }

    addOptionToProperty(properties, dependency, DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED);
    addOptionToProperty(properties, dependency, DependencyOptions.RUN_BUILD_ON_THE_SAME_AGENT);
    addOptionToProperty(properties, dependency, DependencyOptions.TAKE_STARTED_BUILD_WITH_SAME_REVISIONS);
    addOptionToProperty(properties, dependency, DependencyOptions.TAKE_SUCCESSFUL_BUILDS_ONLY);

    this.properties = new Properties(properties);

    final SBuildType dependOn = dependency.getDependOn();
    if (dependOn != null) {
      sourceBuildType = new BuildTypeRef(dependOn, context.getSingletonService(DataProvider.class),
                                         context.getContextService(ApiUrlBuilder.class));
    } else {
      sourceBuildType = new BuildTypeRef(null, dependency.getDependOnId(), context.getSingletonService(DataProvider.class),
                                         context.getContextService(ApiUrlBuilder.class));
    }
  }

  public Dependency addSnapshotDependency(final BuildTypeSettings buildType,
                                          final BeanContext context) {
    if (!SNAPSHOT_DEPENDENCY_TYPE_NAME.equals(type)) {
      throw new BadRequestException("Snapshot dependency should have type '" + SNAPSHOT_DEPENDENCY_TYPE_NAME + "'.");
    }

    final Map<String, String> propertiesMap = properties.getMap();
    final String buildTypeIdFromProperty = propertiesMap.get(NAME_SOURCE_BUILD_TYPE_ID); //compatibility mode with pre-8.0
    String buildTypeIdDependOn = getBuildTypeInternalIdForDependency(sourceBuildType, buildTypeIdFromProperty, context);

    //todo: (TeamCity) for some reason API does not report adding dependency with same id. Seems like it just ignores the call
    if (DataProvider.getSnapshotDepOrNull(buildType, buildTypeIdDependOn) != null) {
      throw new BadRequestException("Snapshot dependency on build type with internal id '" + buildTypeIdDependOn + "' already exists.");
    }

    final Dependency result = context.getSingletonService(DependencyFactoryImpl.class).createDependency(buildTypeIdDependOn);
    for (Map.Entry<String, String> property : propertiesMap.entrySet()) {
      if (!NAME_SOURCE_BUILD_TYPE_ID.equals(property.getKey())) {
        setDependencyOption(property.getKey(), property.getValue(), result);
      }
    }
    try {
      buildType.addDependency(result);
    } catch (CyclicDependencyFoundException e) {
      throw new BadRequestException("Error adding dependnecy", e);
    }
    return DataProvider.getSnapshotDep(buildType, result.getDependOnId());
  }

  @NotNull
  public static String getBuildTypeInternalIdForDependency(@Nullable final BuildTypeRef buildTypeRef,
                                                            @Nullable final String buildTypeIdFromProperty,
                                                            @NotNull final BeanContext context) {
    if (buildTypeIdFromProperty != null) {
      if (buildTypeRef == null) {
        return buildTypeIdFromProperty;
      } else {
        final String internalIdFromPosted = buildTypeRef.getInternalIdFromPosted(context);
        if (internalIdFromPosted == null || buildTypeIdFromProperty.equals(internalIdFromPosted)) {
          return buildTypeIdFromProperty;
        }
        throw new BadRequestException("Dependency descriptor has conflicting '" + NAME_SOURCE_BUILD_TYPE_ID + "' property and '"
                                      + SOURCE_BUILD_TYPE + "' element with not matching ids. Leave only one or make them match.");
      }
    }

    if (buildTypeRef == null) {
      throw new BadRequestException("Dependency properties should contian '" + SOURCE_BUILD_TYPE + "' element" +
                                    (TeamCityProperties.getBoolean(REST_COMPATIBILITY_INCLUDE_BUILD_TYPE_IN_PROPERTIES)
                                     ? " or '" + NAME_SOURCE_BUILD_TYPE_ID + "' property."
                                     : "."));
    }

    final String internalIdFromPosted = buildTypeRef.getInternalIdFromPosted(context);
    if (internalIdFromPosted != null) {
      return internalIdFromPosted;
    }
    throw new NotFoundException("Cound not find internal id of the build configuration defined by '" + SOURCE_BUILD_TYPE +
                                "' element. No such build configuration exists?");
  }

  private void addOptionToProperty(final HashMap<String, String> properties, final Dependency dependency,
                                   final Option<Boolean> option) {
    properties.put(option.getKey(), dependency.getOption(option).toString());
  }

  private void setDependencyOption(final String name, final String value, final Dependency dependency) {
    final Option option = Option.fromKey(name);
    if (option == null) {
      throw new IllegalArgumentException("No option found for name '" + name + "'");
    }
    //noinspection unchecked
    dependency.setOption(option, option.fromString(value));
  }
}
