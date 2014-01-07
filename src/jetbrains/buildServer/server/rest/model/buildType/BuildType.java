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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.BuildsRef;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType")
@XmlType(name = "buildType", propOrder = { "id", "internalId", "name", "href", "templateFlag", "webUrl", "description", "paused",
  "project", "template", "builds", "vcsRootEntries", "settings", "parameters", "steps", "features", "triggers", "snapshotDependencies",
  "artifactDependencies", "agentRequirements", "investigations"})
public class BuildType {
  private static final Logger LOG = Logger.getInstance(BuildType.class.getName());

  protected BuildTypeOrTemplate myBuildType;
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;

  private Fields myFields = Fields.DEFAULT_FIELDS;

  public BuildType() {
  }

  public BuildType(final BuildTypeOrTemplate buildType, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = buildType;
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
  }

  public BuildType(final SBuildType buildType, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = new BuildTypeOrTemplate(buildType);
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
  }

  public BuildType(final BuildTypeTemplate buildType, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = new BuildTypeOrTemplate(buildType);
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
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
  public Project getProject() {
    return new Project(myBuildType.getProject(), myFields.getNestedField("project"), new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder));
  }

  @XmlElement(name = "template")
  public BuildTypeRef getTemplate() {
    if (myBuildType.isTemplate()){
      return null;
    }
    final BuildTypeTemplate template = myBuildType.getBuildType().getTemplate();
    return template == null ? null : new BuildTypeRef(template, myDataProvider, myApiUrlBuilder);
  }

  @XmlElement(name = "vcs-root-entries")
  public VcsRootEntries getVcsRootEntries() {
    return new VcsRootEntries(myBuildType, myApiUrlBuilder);
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
    return new Properties(myBuildType.get().getParameters());
  }

  @XmlElement(name = "steps")
  public PropEntitiesStep getSteps() {
    return new PropEntitiesStep(myBuildType.get());
  }

  @XmlElement(name = "features")
  public PropEntitiesFeature getFeatures() {
    return new PropEntitiesFeature(myBuildType.get());
  }

  @XmlElement(name = "triggers")
  public PropEntitiesTrigger getTriggers() {
    return new PropEntitiesTrigger(myBuildType.get());
  }


  @XmlElement(name = "snapshot-dependencies")
  public PropEntitiesSnapshotDep getSnapshotDependencies() {
    return new PropEntitiesSnapshotDep(myBuildType.get(),
                                       new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder));
  }

  @XmlElement(name = "artifact-dependencies")
  public PropEntitiesArtifactDep getArtifactDependencies() {
    return new PropEntitiesArtifactDep(myBuildType.get().getArtifactDependencies(), new BeanContext(myDataProvider.getBeanFactory(), myDataProvider.getServer(), myApiUrlBuilder));
  }

  @XmlElement(name = "agent-requirements")
  public PropEntitiesAgentRequirement getAgentRequirements() {
    return new PropEntitiesAgentRequirement(myBuildType.get());
  }

  @XmlElement(name="settings")
  public Properties getSettings() {
    return new Properties(BuildTypeUtil.getSettingsParameters(myBuildType));
  }

  /**
   * Link to investigations for this build type
   *
   * @return
   */
  @XmlElement(name = "investigations")
  public Investigations getInvestigations() {
    if (!myBuildType.isBuildType()) {
      return null;
    }

    final ResponsibilityEntry.State state = myBuildType.getBuildType().getResponsibilityInfo().getState();
    if (!state.equals(ResponsibilityEntry.State.NONE)) {
      return new Investigations(null, new Href(InvestigationRequest.getHref(myBuildType.getBuildType()), myApiUrlBuilder), new Fields(), null, myDataProvider.getServer(), myApiUrlBuilder);
//      return new Href(InvestigationRequest.getHref(myBuildType.getBuildType()), myApiUrlBuilder);
    }
    return null;
  }
}
