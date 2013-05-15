package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.artifacts.RevisionRule;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.serverSide.ArtifactDependencyFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "artifact-dependency")
public class PropEntityArtifactDep extends PropEntity {

  private static final String ARTIFACT_DEPENDENCY_TYPE_NAME = "artifact_dependency";
  private static final String NAME_SOURCE_BUILD_TYPE_ID = "source_buildTypeId";
  public static final String NAME_PATH_RULES = "pathRules";
  public static final String NAME_REVISION_NAME = "revisionName";
  public static final String NAME_REVISION_VALUE = "revisionValue";
  public static final String NAME_CLEAN_DESTINATION_DIRECTORY = "cleanDestinationDirectory";

  public PropEntityArtifactDep() {
  }

  public PropEntityArtifactDep(final SArtifactDependency dependency, final int orderNum) {
    init(dependency, orderNum);

  }

  private void init(final SArtifactDependency dependency, final int orderNum) {
    //todo: review id, type here
    this.id = Integer.toString(orderNum);
    this.type = ARTIFACT_DEPENDENCY_TYPE_NAME;

    HashMap<String, String> properties = new HashMap<String, String>();
    properties.put(NAME_SOURCE_BUILD_TYPE_ID, dependency.getSourceBuildTypeId());
    properties.put(NAME_PATH_RULES, dependency.getSourcePaths());
    properties.put(NAME_REVISION_NAME, dependency.getRevisionRule().getName());
    properties.put(NAME_REVISION_VALUE, dependency.getRevisionRule().getRevision());
    properties.put(NAME_CLEAN_DESTINATION_DIRECTORY, Boolean.toString(dependency.isCleanDestinationFolder()));
    this.properties = new Properties(properties);
  }

  public PropEntityArtifactDep(final SArtifactDependency artifactDependency, final BuildTypeSettings buildType) {
    final List<SArtifactDependency> artifactDependencies = buildType.getArtifactDependencies();

    int orderNumber = 0;
    for (SArtifactDependency dependency : artifactDependencies) {
      if (dependency.equals(artifactDependency)) {
        init(artifactDependency, orderNumber);
        return;
      }
      orderNumber++;
    }
    throw new IllegalArgumentException("Specified build type does not have specified artifact dependency.");
  }

  public SArtifactDependency createDependency(final ArtifactDependencyFactory factory) {
    if (!ARTIFACT_DEPENDENCY_TYPE_NAME.equals(type)){
      throw new BadRequestException("Artifact dependency should have type '" + ARTIFACT_DEPENDENCY_TYPE_NAME + "'.");
    }

    if (properties == null){
      throw new BadRequestException("Artifact dependency properties should contian properties.");
    }

    final Map<String,String> propertiesMap = properties.getMap();
    if (!propertiesMap.containsKey(NAME_SOURCE_BUILD_TYPE_ID)){
      throw new BadRequestException("Artifact dependency properties should contain '" + NAME_SOURCE_BUILD_TYPE_ID + "' property.");
    }

    final String revisionName = propertiesMap.get(NAME_REVISION_NAME);
    if (StringUtil.isEmpty(revisionName)){
      throw new BadRequestException("Missing or empty artifact dependency property '" + NAME_REVISION_NAME + "'. Should contain one of supported values.");
    }

    final SArtifactDependency artifactDependency = factory.createArtifactDependencyByInternalId(
      propertiesMap.get(NAME_SOURCE_BUILD_TYPE_ID),
      propertiesMap.get(NAME_PATH_RULES),
      RevisionRules.newRevisionRule(revisionName, propertiesMap.get(NAME_REVISION_VALUE)));
    final String cleanDir = propertiesMap.get(NAME_CLEAN_DESTINATION_DIRECTORY);
    if (cleanDir != null) {
      artifactDependency.setCleanDestinationFolder(Boolean.parseBoolean(cleanDir));
    }
    return artifactDependency;
  }
}
