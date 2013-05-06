package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.dependency.CyclicDependencyFoundException;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.serverSide.impl.dependency.DependencyFactoryImpl;
import jetbrains.buildServer.util.Option;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "snapshot-dependency")
public class PropEntitySnapshotDep extends PropEntity {

  public static final String SNAPSHOT_DEPENDENCY_TYPE_NAME = "snapshot_dependency";
  public static final String NAME_SOURCE_BUILD_TYPE_ID = "source_buildTypeId";

  public PropEntitySnapshotDep() {
  }

  public PropEntitySnapshotDep(final Dependency dependency) {
    this.id = dependency.getDependOnId();
    this.type = SNAPSHOT_DEPENDENCY_TYPE_NAME;

    //todo: review id, type here
    HashMap<String, String> properties = new HashMap<String, String>();
    properties.put(NAME_SOURCE_BUILD_TYPE_ID, dependency.getDependOnId());

    addOptionToProperty(properties, dependency, DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED);
    addOptionToProperty(properties, dependency, DependencyOptions.RUN_BUILD_ON_THE_SAME_AGENT);
    addOptionToProperty(properties, dependency, DependencyOptions.TAKE_STARTED_BUILD_WITH_SAME_REVISIONS);
    addOptionToProperty(properties, dependency, DependencyOptions.TAKE_SUCCESSFUL_BUILDS_ONLY);

    this.properties = new Properties(properties);
  }

  public Dependency addSnapshotDependency(final BuildTypeSettings buildType, final DependencyFactoryImpl factory) {
    if (!SNAPSHOT_DEPENDENCY_TYPE_NAME.equals(type)) {
      throw new BadRequestException("Snapshot dependency should have type '" + SNAPSHOT_DEPENDENCY_TYPE_NAME + "'.");
    }

    final Map<String, String> propertiesMap = properties.getMap();
    if (!propertiesMap.containsKey(NAME_SOURCE_BUILD_TYPE_ID)) {
      throw new BadRequestException("Snapshot dependency properties should contian '" + NAME_SOURCE_BUILD_TYPE_ID + "' property.");
    }

    final String buildTypeIdDependOn = propertiesMap.get(NAME_SOURCE_BUILD_TYPE_ID);

    //todo: (TeamCity) for some reason API does not report adding dependency with same id. Seems like it just ignores the call
    if (DataProvider.getSnapshotDepOrNull(buildType, buildTypeIdDependOn) != null) {
      throw new BadRequestException("Snapshot dependency on build type with id '" + buildTypeIdDependOn + "' already exists.");
    }

    final Dependency result = factory.createDependencyByInternalId(buildTypeIdDependOn);
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
