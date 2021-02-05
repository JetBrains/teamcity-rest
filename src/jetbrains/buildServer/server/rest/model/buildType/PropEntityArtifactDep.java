/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import java.util.*;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.artifacts.RevisionRule;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "artifact-dependency")
@ModelDescription(
    value = "Represents an artifact dependency relation.",
    externalArticleLink = "https://www.jetbrains.com/help/teamcity/dependent-build.html#Artifact+Dependency",
    externalArticleName = "Artifact Dependency"
)
@XmlType
public class PropEntityArtifactDep extends PropEntity implements PropEntityEdit<SArtifactDependency> {

  private static final String ARTIFACT_DEPENDENCY_TYPE_NAME = "artifact_dependency";
  private static final String NAME_SOURCE_BUILD_TYPE_ID = "source_buildTypeId";
  public static final String NAME_PATH_RULES = "pathRules";
  public static final String NAME_REVISION_NAME = "revisionName";
  public static final String NAME_REVISION_VALUE = "revisionValue";
  public static final String NAME_REVISION_BRANCH = "revisionBranch";
  public static final String NAME_CLEAN_DESTINATION_DIRECTORY = "cleanDestinationDirectory";

  @XmlElement(name = PropEntitySnapshotDep.SOURCE_BUILD_TYPE)
  public BuildType sourceBuildType;

  public PropEntityArtifactDep() {
  }

  /**
   *
   * @param dependency
   * @param buildType null is enabled/disabled is not applicable
   * @param fields
   * @param context
   */
  public PropEntityArtifactDep(@NotNull final SArtifactDependency dependency, @Nullable final BuildTypeSettingsEx buildType,
                               @NotNull final Fields fields, @NotNull final BeanContext context) {
    HashMap<String, String> properties = new HashMap<String, String>();
    if (TeamCityProperties.getBoolean(PropEntitySnapshotDep.REST_COMPATIBILITY_INCLUDE_BUILD_TYPE_IN_PROPERTIES)) {
      properties.put(NAME_SOURCE_BUILD_TYPE_ID, dependency.getSourceExternalId());
    }
    properties.put(NAME_PATH_RULES, dependency.getSourcePaths());
    final String revisionName = dependency.getRevisionRule().getName();
    properties.put(NAME_REVISION_NAME, revisionName);
    final String revisionValue = dependency.getRevisionRule().getRevision();
    if (revisionValue != null) {
      if (!RevisionRules.BUILD_ID_NAME.equals(revisionName)) {
        properties.put(NAME_REVISION_VALUE, revisionValue);
      } else {
        final int lastIndex = revisionValue.lastIndexOf(RevisionRules.BUILD_ID_SUFFIX);
        if (lastIndex == -1) {
          properties.put(NAME_REVISION_VALUE, revisionValue);
        } else {
          properties.put(NAME_REVISION_VALUE, revisionValue.substring(0, lastIndex));
        }
      }
    }
    final String branch = dependency.getRevisionRule().getBranch();
    if (!StringUtil.isEmpty(branch)){
      properties.put(NAME_REVISION_BRANCH, branch);
    }
    properties.put(NAME_CLEAN_DESTINATION_DIRECTORY, Boolean.toString(dependency.isCleanDestinationFolder()));

    if (buildType == null){
      init(dependency.getId(), null, ARTIFACT_DEPENDENCY_TYPE_NAME, null, null, properties, fields, context);
    } else{
      init(dependency.getId(), null, ARTIFACT_DEPENDENCY_TYPE_NAME, buildType.isEnabled(dependency.getId()), //can optimize by getting getOwnArtifactDependencies in the caller
           !buildType.getOwnArtifactDependencies().contains(dependency), properties, fields, context);
    }

    sourceBuildType = ValueWithDefault.decideDefault(fields.isIncluded(PropEntitySnapshotDep.SOURCE_BUILD_TYPE, false, true), new ValueWithDefault.Value<BuildType>() {
      @Nullable
      public BuildType get() {
        @Nullable SBuildType dependOn = null;
        try {
          dependOn = dependency.getSourceBuildType();
        } catch (AccessDeniedException e) {
          //ignore, will use ids later
        }
        final Fields nestedField = fields.getNestedField(PropEntitySnapshotDep.SOURCE_BUILD_TYPE);
        if (dependOn != null) {
          return new BuildType(new BuildTypeOrTemplate(dependOn), nestedField, context);
        } else {
          return new BuildType(dependency.getSourceExternalId(), dependency.getSourceBuildTypeId(), nestedField, context);
        }
      }
    });
  }

  @NotNull
  @Override
  public SArtifactDependency addTo(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    PropEntitiesArtifactDep.Storage original = new PropEntitiesArtifactDep.Storage(buildType);
    try {
      final List<SArtifactDependency> dependencies = new ArrayList<SArtifactDependency>(buildType.getArtifactDependencies());
      SArtifactDependency result = addToInternal(buildType, serviceLocator);
      dependencies.add(result);
      buildType.setArtifactDependencies(dependencies); //todo: use  buildType.addArtifactDependency, see TW-45262
      return result;
    } catch (Exception e) {
      original.apply(buildType);
      throw new BadRequestException("Error adding artifact dependency: " + e.toString(), e);
    }
  }

  @NotNull
  public SArtifactDependency addToInternal(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    SArtifactDependency result = addToInternalMain(buildType, serviceLocator);
    if (disabled != null) {
      buildType.setEnabled(result.getId(), !disabled);
    }
    return result;
  }

  @NotNull
  public SArtifactDependency addToInternalMain(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    SArtifactDependency similar = getInheritedOrSameIdSimilar(buildType, serviceLocator);
    if (inherited != null && inherited && similar != null) {
      return similar;
    }
    if (similar != null && id != null && id.equals(similar.getId())) {
      //not inherited, but id is the same
      //todo
      return similar;
    }

    //special case for "overriden" entities
    if (id != null) {
      for (SArtifactDependency item : buildType.getArtifactDependencies()) {
        if (id.equals(item.getId())) {
          return createDependency(id, serviceLocator);
        }
      }
    }

    return createDependency(null, serviceLocator);
  }

  @Nullable
  public SArtifactDependency getInheritedOrSameIdSimilar(@NotNull final BuildTypeSettingsEx buildType, @NotNull final ServiceLocator serviceLocator) {
    final List<SArtifactDependency> ownItems = buildType.getOwnArtifactDependencies();
    for (SArtifactDependency item : buildType.getArtifactDependencies()) {
      if (ownItems.contains(item)) {
        if (id == null || !id.equals(item.getId())) {
          continue;
        }
      }
      if (isSimilar(new PropEntityArtifactDep(item, buildType, Fields.LONG, getFakeBeanContext(serviceLocator)))) return item;
    }
    return null;
  }

  @NotNull
  @Override
  public SArtifactDependency replaceIn(@NotNull final BuildTypeSettingsEx buildType, @NotNull final SArtifactDependency originalDep, @NotNull final ServiceLocator serviceLocator) {
    final SArtifactDependency newDependency = createDependency(null, serviceLocator);

    PropEntitiesArtifactDep.Storage original = new PropEntitiesArtifactDep.Storage(buildType);
    final List<SArtifactDependency> newDependencies = new ArrayList<>(original.deps.size());
    for (SArtifactDependency currentDependency : original.deps) {
      if (currentDependency.equals(originalDep)) {
        newDependencies.add(newDependency);
      } else {
        newDependencies.add(currentDependency);
      }
    }
    try {
      buildType.setArtifactDependencies(newDependencies);
      buildType.setEnabled(newDependency.getId(), disabled == null || !disabled);
    } catch (Exception e) {
      //restore
      original.apply(buildType);
      throw new BadRequestException("Error updating artifact dependencies: " + e.toString(), e);
    }
    return newDependency;
  }

  public static void removeFrom(@NotNull final BuildTypeSettings buildType, @NotNull final SArtifactDependency artifactDependency) {
    final List<SArtifactDependency> dependencies = buildType.getArtifactDependencies();
    if (!dependencies.remove(artifactDependency)) {
      throw new NotFoundException("Specified artifact dependency is not found in the build type.");
    }
    buildType.setArtifactDependencies(dependencies);
  }

  @NotNull
  public SArtifactDependency createDependency(@Nullable String forcedId, @NotNull final ServiceLocator serviceLocator) {
    if (!ARTIFACT_DEPENDENCY_TYPE_NAME.equals(type)){
      throw new BadRequestException("Artifact dependency should have type '" + ARTIFACT_DEPENDENCY_TYPE_NAME + "'.");
    }

    final Map<String,String> propertiesMap = properties == null ? Collections.emptyMap() : properties.getMap();
    final String buildTypeIdFromProperty = propertiesMap.get(NAME_SOURCE_BUILD_TYPE_ID); //compatibility mode with pre-8.0
    String buildTypeIdDependOn = PropEntitySnapshotDep.getBuildTypeExternalIdForDependency(sourceBuildType, buildTypeIdFromProperty, serviceLocator);
    BuildTypeUtil.checkCanUseBuildTypeAsDependency(buildTypeIdDependOn, serviceLocator);
    final String revisionName = propertiesMap.get(NAME_REVISION_NAME);
    if (StringUtil.isEmpty(revisionName)){
      throw new BadRequestException("Missing or empty artifact dependency property '" + NAME_REVISION_NAME + "'. Should contain one of supported values.");
    }

    String sourcePaths = propertiesMap.get(NAME_PATH_RULES);
    if (sourcePaths == null) {
      throw new BadRequestException("Missing source path property '" + NAME_PATH_RULES + "'. Should be specified.");
    }
    ArtifactDependencyFactory factory = serviceLocator.getSingletonService(ArtifactDependencyFactory.class);
    final SArtifactDependency artifactDependency;
    if (forcedId == null) {
      artifactDependency = factory.createArtifactDependency(buildTypeIdDependOn, sourcePaths,
                                                            getRevisionRule(revisionName, propertiesMap.get(NAME_REVISION_VALUE), propertiesMap.get(NAME_REVISION_BRANCH)));
    } else {
      artifactDependency = factory.createArtifactDependency(forcedId, buildTypeIdDependOn, sourcePaths,
                                                            getRevisionRule(revisionName, propertiesMap.get(NAME_REVISION_VALUE), propertiesMap.get(NAME_REVISION_BRANCH)));
    }

    final String cleanDir = propertiesMap.get(NAME_CLEAN_DESTINATION_DIRECTORY);
    if (cleanDir != null) {
      artifactDependency.setCleanDestinationFolder(Boolean.parseBoolean(cleanDir));
    }
    //noinspection SimplifiableConditionalExpression
    return artifactDependency;
  }

  @NotNull
  private RevisionRule getRevisionRule(@NotNull final String revisionName, @Nullable final String revisionValue, @Nullable final String revisionBranch) {
    try {
      return RevisionRules.newBranchRevisionRule(revisionName, revisionValue, revisionBranch);
    } catch (UnsupportedOperationException e) {
      //support revisions like "67999.tcbuildid" for compatibility, see https://youtrack.jetbrains.com/issue/TW-38876
      if (revisionValue == null) {
        throw new BadRequestException("Cannot create revision for name '" + revisionName + "' and empty revision value (read from '" + NAME_REVISION_VALUE + "' element): "
                                      + e.getMessage());
      }
      final RevisionRule result = RevisionRules.newBranchRevisionRule(revisionValue, revisionBranch);
      if (result == null) {
        throw new BadRequestException("Cannot create revision for name '" + revisionName + "' and value '" + revisionValue + "'");
      }
      return result;
    }
  }

  private boolean isSimilar(@Nullable final PropEntityArtifactDep that) {
    return that != null &&
           (sourceBuildType == that.sourceBuildType || (sourceBuildType != null && sourceBuildType.isSimilar(that.sourceBuildType))) &&
           super.isSimilar(that);
  }
}
