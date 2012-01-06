package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
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

    properties.put(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED.getKey(), dependency.getOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED).toString());
    properties.put(DependencyOptions.RUN_BUILD_ON_THE_SAME_AGENT.getKey(), dependency.getOption(
      DependencyOptions.RUN_BUILD_ON_THE_SAME_AGENT).toString());
    properties.put(DependencyOptions.TAKE_STARTED_BUILD_WITH_SAME_REVISIONS.getKey(), dependency.getOption(
      DependencyOptions.TAKE_STARTED_BUILD_WITH_SAME_REVISIONS).toString());
    properties.put(DependencyOptions.TAKE_SUCCESSFUL_BUILDS_ONLY.getKey(), dependency.getOption(DependencyOptions.TAKE_SUCCESSFUL_BUILDS_ONLY).toString());

    this.properties = new Properties(properties);
  }

  public Dependency createDependency(final DependencyFactoryImpl factory) {
    if (!SNAPSHOT_DEPENDENCY_TYPE_NAME.equals(type)){
      throw new BadRequestException("Snapshot dependency should have type '" + SNAPSHOT_DEPENDENCY_TYPE_NAME + "'.");
    }

    final Map<String,String> propertiesMap = properties.getMap();
    if (!propertiesMap.containsKey(NAME_SOURCE_BUILD_TYPE_ID)){
      throw new BadRequestException("Snapshot dependency properties should contian '" + NAME_SOURCE_BUILD_TYPE_ID + "' property.");
    }

    final Dependency result = factory.createDependency(propertiesMap.get(NAME_SOURCE_BUILD_TYPE_ID));
    for (Map.Entry<String, String> property : propertiesMap.entrySet()) {
      if (!NAME_SOURCE_BUILD_TYPE_ID.equals(property.getKey())){
        final Option option = Option.fromKey(property.getKey());
        if (option == null) {
          throw new IllegalArgumentException("No option found for name '" + property.getKey() + "'");
        }
        //noinspection unchecked
        result.setOption(option, option.fromString(property.getValue()));
      }
    }
    return result;
  }
}
