/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.BuildsRef;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType")
@XmlType(name = "buildType", propOrder = { "id", "name", "href", "templateFlag", "webUrl", "description", "paused",
  "project", "template", "vcsRootEntries", "builds", "settings", "parameters", "steps", "features", "triggers", "snapshotDependencies",
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

  @XmlAttribute
  public String getId() {
    return myBuildType.getId();
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

  @XmlAttribute (name = "template")
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
    return check(new VcsRootEntries(myBuildType.get().getVcsRootEntries(), myApiUrlBuilder));
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
    return check(new PropEntitiesSnapshotDep(myBuildType.get()));
  }

  @XmlElement(name = "artifact-dependencies")
  public PropEntitiesArtifactDep getArtifactDependencies() {
    return check(new PropEntitiesArtifactDep(myBuildType.get()));
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
