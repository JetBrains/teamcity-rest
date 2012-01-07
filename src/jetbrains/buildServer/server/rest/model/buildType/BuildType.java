/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import java.util.HashMap;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.BuildsRef;
import jetbrains.buildServer.server.rest.model.project.ProjectRef;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType")
@XmlType(propOrder = {"paused", "description", "webUrl", "href", "name", "id",
  "project", "vcsRootEntries", "builds", "settings", "parameters", "steps", "features", "triggers", "snapshotDependencies",
  "artifactDependencies", "agentRequirements"})
public class BuildType {
  private static final Logger LOG = Logger.getInstance(BuildType.class.getName());

  protected SBuildType myBuildType;
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;

  public BuildType() {
  }

  public BuildType(final SBuildType buildType, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = buildType;
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlAttribute
  public String getId() {
    return myBuildType.getBuildTypeId();
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

  @XmlAttribute
  public boolean isPaused() {
    return myBuildType.isPaused();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myDataProvider.getBuildTypeUrl(myBuildType);
  }

  @XmlElement
  public ProjectRef getProject() {
    return new ProjectRef(myBuildType.getProject(), myApiUrlBuilder);
  }

  @XmlElement(name = "vcs-root")
  public VcsRootEntries getVcsRootEntries() {
    return new VcsRootEntries(myBuildType.getVcsRootEntries(), myApiUrlBuilder);
  }

  @XmlElement
  public BuildsRef getBuilds() {
    return new BuildsRef(myBuildType, myApiUrlBuilder);
  }

  @XmlElement
  public Properties getParameters() {
    return new Properties(myBuildType.getParameters());
  }

  @XmlElement(name = "steps")
  public PropEntitiesStep getSteps() {
    return new PropEntitiesStep(BuildTypeUtil.getSteps(myBuildType));
  }

  @XmlElement(name = "features")
  public PropEntitiesFeature getFeatures() {
    return new PropEntitiesFeature(BuildTypeUtil.getFeatures(myBuildType));
  }

  @XmlElement(name = "triggers")
  public PropEntitiesTrigger getTriggers() {
    return new PropEntitiesTrigger(myBuildType);
  }


  @XmlElement(name = "snapshot-dependencies")
  public PropEntitiesSnapshotDep getSnapshotDependencies() {
    return new PropEntitiesSnapshotDep(myBuildType);
  }

  @XmlElement(name = "artifact-dependencies")
  public PropEntitiesArtifactDep getArtifactDependencies() {
    return new PropEntitiesArtifactDep(myBuildType);
  }

  @XmlElement(name = "agent-requirements")
  public PropEntitiesAgentRequirement getAgentRequirements() {
    return new PropEntitiesAgentRequirement(CollectionsUtil.convertCollection(myBuildType.getRequirements(), new Converter<PropEntity, Requirement>() {
      public PropEntity createFrom(@NotNull final Requirement source) {
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("property-name", source.getPropertyName());
        if (source.getPropertyValue() != null) {
          properties.put("property-value", source.getPropertyValue());
        }
        properties.put("type", source.getType().getName());
        return new PropEntity(source.getPropertyName(), source.getType().getName(), properties);
      }
    }));
  }

  @XmlElement(name="settings")
  public Properties getSettings() {
    return new Properties(BuildTypeUtil.getSettingsParameters(myBuildType));
  }
}
