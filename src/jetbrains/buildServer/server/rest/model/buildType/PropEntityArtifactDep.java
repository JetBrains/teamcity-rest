package jetbrains.buildServer.server.rest.model.buildType;

import com.intellij.openapi.util.text.StringUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.ArtifactDependencyFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "artifact-dependency")
@XmlType
public class PropEntityArtifactDep extends PropEntity {

  private static final String ARTIFACT_DEPENDENCY_TYPE_NAME = "artifact_dependency";
  private static final String NAME_SOURCE_BUILD_TYPE_ID = "source_buildTypeId";
  public static final String NAME_PATH_RULES = "pathRules";
  public static final String NAME_REVISION_NAME = "revisionName";
  public static final String NAME_REVISION_VALUE = "revisionValue";
  public static final String NAME_CLEAN_DESTINATION_DIRECTORY = "cleanDestinationDirectory";

  @XmlElement(name = PropEntitySnapshotDep.SOURCE_BUILD_TYPE)
  public BuildTypeRef sourceBuildType;

  public PropEntityArtifactDep() {
  }

  public PropEntityArtifactDep(final SArtifactDependency dependency, final int orderNum, @NotNull final BeanContext context) {
    init(dependency, orderNum, context);

  }

  private void init(final SArtifactDependency dependency, final int orderNum, @NotNull final BeanContext context) {
    //todo: review id, type here
    this.id = Integer.toString(orderNum);
    this.type = ARTIFACT_DEPENDENCY_TYPE_NAME;

    HashMap<String, String> properties = new HashMap<String, String>();
    //todo: review internal/external id usage
    if (TeamCityProperties.getBoolean(PropEntitySnapshotDep.REST_COMPATIBILITY_INCLUDE_BUILD_TYPE_IN_PROPERTIES)) {
      properties.put(NAME_SOURCE_BUILD_TYPE_ID, dependency.getSourceBuildTypeId());
    }
    properties.put(NAME_PATH_RULES, dependency.getSourcePaths());
    properties.put(NAME_REVISION_NAME, dependency.getRevisionRule().getName());
    properties.put(NAME_REVISION_VALUE, dependency.getRevisionRule().getRevision());
    properties.put(NAME_CLEAN_DESTINATION_DIRECTORY, Boolean.toString(dependency.isCleanDestinationFolder()));
    this.properties = new Properties(properties);

    final SBuildType dependOn = dependency.getSourceBuildType();
    if (dependOn != null) {
      sourceBuildType = new BuildTypeRef(dependOn, context.getSingletonService(DataProvider.class),
                                         context.getContextService(ApiUrlBuilder.class));
    } else {
      sourceBuildType = new BuildTypeRef(null, dependency.getSourceBuildTypeId(), context.getSingletonService(DataProvider.class),
                                         context.getContextService(ApiUrlBuilder.class));
    }
  }

  public PropEntityArtifactDep(final SArtifactDependency artifactDependency,
                               final BuildTypeSettings buildType,
                               @NotNull final BeanContext context) {
    final List<SArtifactDependency> artifactDependencies = buildType.getArtifactDependencies();

    int orderNumber = 0;
    for (SArtifactDependency dependency : artifactDependencies) {
      if (dependency.equals(artifactDependency)) {
        init(artifactDependency, orderNumber, context);
        return;
      }
      orderNumber++;
    }
    throw new IllegalArgumentException("Specified build type does not have specified artifact dependency.");
  }

  public SArtifactDependency createDependency(final BeanContext context) {
    if (!ARTIFACT_DEPENDENCY_TYPE_NAME.equals(type)){
      throw new BadRequestException("Artifact dependency should have type '" + ARTIFACT_DEPENDENCY_TYPE_NAME + "'.");
    }

    final Map<String,String> propertiesMap = properties.getMap();
    //todo: review internal/external id usage
    final String buildTypeIdFromProperty = propertiesMap.get(NAME_SOURCE_BUILD_TYPE_ID); //compatibility mode with pre-8.0
    String buildTypeIdDependOn = PropEntitySnapshotDep.getBuildTypeInternalIdForDependency(sourceBuildType, buildTypeIdFromProperty,
                                                                                           context);

    final String revisionName = propertiesMap.get(NAME_REVISION_NAME);
    if (StringUtil.isEmpty(revisionName)){
      throw new BadRequestException("Missing or empty artifact dependency property '" + NAME_REVISION_NAME + "'. Should contain one of supported values.");
    }

    //todo: review internal/external id usage
    final SArtifactDependency artifactDependency = context.getSingletonService(ArtifactDependencyFactory.class).createArtifactDependencyByInternalId(
      buildTypeIdDependOn,
      propertiesMap.get(NAME_PATH_RULES),
      RevisionRules.newRevisionRule(revisionName, propertiesMap.get(NAME_REVISION_VALUE)));
    final String cleanDir = propertiesMap.get(NAME_CLEAN_DESTINATION_DIRECTORY);
    if (cleanDir != null) {
      artifactDependency.setCleanDestinationFolder(Boolean.parseBoolean(cleanDir));
    }
    return artifactDependency;
  }
}
