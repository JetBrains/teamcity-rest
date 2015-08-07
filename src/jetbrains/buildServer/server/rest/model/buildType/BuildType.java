/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.BuildsRef;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.server.rest.request.BuildTypeRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.identifiers.BuildTypeIdentifiersManager;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType")
@XmlType(name = "buildType", propOrder = { "id", "internalId", "name", "href", "templateFlag", "webUrl", "description", "paused",
  "project", "template", "builds", "vcsRootEntries", "settings", "parameters", "steps", "features", "triggers", "snapshotDependencies",
  "artifactDependencies", "agentRequirements"})
public class BuildType {
  private static final Logger LOG = Logger.getInstance(BuildType.class.getName());

  protected BuildTypeOrTemplate myBuildType;
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;

  private final boolean canViewSettings;

  public BuildType() {
    canViewSettings = true;
  }

  public BuildType(final BuildTypeOrTemplate buildType, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = buildType;
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
    canViewSettings = !shouldRestrictSettingsViewing(buildType.get(), dataProvider);
  }

  public BuildType(final SBuildType buildType, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = new BuildTypeOrTemplate(buildType);
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
    //noinspection RedundantIfStatement
    if (TeamCityProperties.getBooleanOrTrue("rest.beans.buildType.checkPermissions")) {
      canViewSettings = false;
    } else {
      canViewSettings = true;
    }
  }

  public BuildType(final BuildTypeTemplate buildType, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = new BuildTypeOrTemplate(buildType);
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
    if (TeamCityProperties.getBooleanOrTrue("rest.beans.buildType.checkPermissions")) {
      canViewSettings = false;
    } else {
      canViewSettings = true;
    }
  }

  /**
   * @return External id of the build configuration
   */
  @XmlAttribute
  public String getId() {
    return myBuildType.getId();
  }

  @XmlAttribute
  public String getInternalId() {
    return TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME) ? myBuildType.getInternalId() : null;
  }

  @XmlAttribute
  public String getName() {
    return myBuildType.getName();
  }

  @XmlAttribute
  public String getHref() {
    return myApiUrlBuilder.getHref(myBuildType);
  }

  @XmlAttribute
  public String getDescription() {
    return myBuildType.getDescription();
  }

  @XmlAttribute (name = "templateFlag")
  public Boolean getTemplateFlag() {
    return myBuildType.isBuildType() ? null : true;
  }

  @XmlAttribute
  public Boolean isPaused() {
    return myBuildType.isPaused();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myBuildType.isBuildType() ? myDataProvider.getBuildTypeUrl(myBuildType.getBuildType()) : null; //template has no user link
  }

  @XmlElement(name = "project")
  public ProjectRef getProject() {
    return new ProjectRef(myBuildType.getProject(), myApiUrlBuilder);
  }

  @XmlElement(name = "template")
  public BuildTypeRef getTemplate() {
    if (myBuildType.isTemplate()){
      return null;
    }
    final BuildTypeTemplate template = myBuildType.getBuildType().getTemplate();
    return check(template == null ? null : new BuildTypeRef(template, myDataProvider, myApiUrlBuilder));
  }

  @XmlElement(name = "vcs-root-entries")
  public VcsRootEntries getVcsRootEntries() {
    return check(new VcsRootEntries(myBuildType, myApiUrlBuilder));
  }

  /**
   * Link to builds of this build configuration. Is not present for templates.
   * @return
   */
  @XmlElement(name = "builds")
  public BuildsRef getBuilds() {
    return myBuildType.isBuildType() ? new BuildsRef(myBuildType.getBuildType(), myApiUrlBuilder) : null;
  }

  @XmlElement
  public Properties getParameters() {
    return check(new Properties(myBuildType.get().getParameters()));
  }

  @XmlElement(name = "steps")
  public PropEntitiesStep getSteps() {
    return check(new PropEntitiesStep(myBuildType.get()));
  }

  @XmlElement(name = "features")
  public PropEntitiesFeature getFeatures() {
    return check(new PropEntitiesFeature(myBuildType.get()));
  }

  @XmlElement(name = "triggers")
  public PropEntitiesTrigger getTriggers() {
    return check(new PropEntitiesTrigger(myBuildType.get()));
  }


  @XmlElement(name = "snapshot-dependencies")
  public PropEntitiesSnapshotDep getSnapshotDependencies() {
    return check(new PropEntitiesSnapshotDep(myBuildType.get(),
                                       new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder)));
  }

  @XmlElement(name = "artifact-dependencies")
  public PropEntitiesArtifactDep getArtifactDependencies() {
    return check(new PropEntitiesArtifactDep(myBuildType.get(),
                                       new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder)));
  }

  @XmlElement(name = "agent-requirements")
  public PropEntitiesAgentRequirement getAgentRequirements() {
    return check(new PropEntitiesAgentRequirement(myBuildType.get()));
  }

  @XmlElement(name="settings")
  public Properties getSettings() {
    return check(new Properties(BuildTypeUtil.getSettingsParameters(myBuildType)));
  }

  public static boolean shouldRestrictSettingsViewing(final @NotNull BuildTypeSettings buildType, final @NotNull DataProvider permissionChecker) {
    if (TeamCityProperties.getBoolean("rest.beans.buildType.checkPermissions")) {
      return !permissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, buildType.getProject().getProjectId());
    }
    return false;
  }

  @Nullable
  private <T> T check(@Nullable T t) {
    if (canViewSettings) {
      return t;
    } else {
      return null;
    }
  }
}
