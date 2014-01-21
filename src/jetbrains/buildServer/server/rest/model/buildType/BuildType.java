/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.build.BuildsRef;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.identifiers.BuildTypeIdentifiersManager;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType")
@XmlType(name = "buildType", propOrder = { "id", "internalId", "name", "templateFlag", "paused", "description", "projectName", "projectId", "projectInternalId", "href", "webUrl",
  "project", "template", "builds", "vcsRootEntries", "settings", "parameters", "steps", "features", "triggers", "snapshotDependencies",
  "artifactDependencies", "agentRequirements", "investigations"})
public class BuildType {
  private static final Logger LOG = Logger.getInstance(BuildType.class.getName());

  protected BuildTypeOrTemplate myBuildType;

  private Fields myFields = Fields.LONG;
  @NotNull private BeanContext myBeanContext;

  public BuildType() {
  }

  public BuildType(@NotNull final BuildTypeOrTemplate buildType, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myBuildType = buildType;
    myFields = fields;
    myBeanContext = beanContext;
  }

  /**
   * @return External id of the build configuration
   */
  @XmlAttribute
  public String getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id", true), myBuildType.getId());
  }

  @XmlAttribute
  public String getInternalId() {
    final boolean includeProperty = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME);
    return ValueWithDefault.decideDefault(myFields.isIncluded("internalId", includeProperty, includeProperty), myBuildType.getInternalId());
  }

  @XmlAttribute
  public String getName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("name"), myBuildType.getName());
  }

  @XmlAttribute
  public String getProjectId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("projectId"), myBuildType.getProject().getExternalId());
  }

  @XmlAttribute
  public String getProjectInternalId() {
    final boolean includeProperty = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME);
    return ValueWithDefault.decideDefault(myFields.isIncluded("projectInternalId", includeProperty ,includeProperty), myBuildType.getProject().getProjectId());
  }

  /**
   * @deprecated
   * @return
   */
  @XmlAttribute
  public String getProjectName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("projectName"), myBuildType.getProject().getName());
  }

  @XmlAttribute
  public String getHref() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("href"), myBeanContext.getApiUrlBuilder().getHref(myBuildType));
  }

  @XmlAttribute
  public String getDescription() {
    final String description = myBuildType.getDescription();
    return ValueWithDefault.decideDefault(myFields.isIncluded("description"), StringUtil.isEmpty(myBuildType.getDescription()) ? null : description);
  }

  @XmlAttribute (name = "templateFlag")
  public Boolean getTemplateFlag() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("templateFlag"), !myBuildType.isBuildType());
  }

  @XmlAttribute
  public Boolean isPaused() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("paused"), myBuildType.isPaused());
  }

  @XmlAttribute
  public String getWebUrl() {
    //template has no user link
    return !myBuildType.isBuildType()
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded("webUrl"),
                                            myBeanContext.getSingletonService(WebLinks.class).getConfigurationHomePageUrl(myBuildType.getBuildType()));
  }

  @XmlElement(name = "project")
  public Project getProject() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("project", false), new ValueWithDefault.Value<Project>() {
      public Project get() {
        return new Project(myBuildType.getProject(), myFields.getNestedField("project"), myBeanContext);
      }
    });
  }

  @XmlElement(name = "template")
  public BuildType getTemplate() {
    if (myBuildType.isTemplate()){
      return null;
    }
    final BuildTypeTemplate template = myBuildType.getBuildType().getTemplate();
    return template == null
           ? null
           : ValueWithDefault
             .decideDefault(myFields.isIncluded("template", false), new ValueWithDefault.Value<BuildType>() {
               public BuildType get() {
                 return new BuildType(new BuildTypeOrTemplate(template), myFields.getNestedField("template"), myBeanContext);
               }
             });
  }

  @XmlElement(name = "vcs-root-entries")
  public VcsRootEntries getVcsRootEntries() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("vcs-root-entries", false), new ValueWithDefault.Value<VcsRootEntries>() {
      public VcsRootEntries get() {
        return new VcsRootEntries(myBuildType, myBeanContext.getApiUrlBuilder());
      }
    });
  }

  /**
   * Link to builds of this build configuration. Is not present for templates.
   * @return
   */
  @XmlElement(name = "builds")
  public BuildsRef getBuilds() {
    return !myBuildType.isBuildType()
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded("builds", false), new ValueWithDefault.Value<BuildsRef>() {
             public BuildsRef get() {
               return new BuildsRef(myBuildType.getBuildType(), myBeanContext.getApiUrlBuilder());
             }
           });
  }

  @XmlElement
  public Properties getParameters() {
    return ValueWithDefault
      .decideDefault(myFields.isIncluded("parameters", false), new ValueWithDefault.Value<Properties>() {
        public Properties get() {
          return new Properties(myBuildType.get().getParameters(), null, myFields.getNestedField("parameters", Fields.NONE, Fields.LONG));
        }
      });
  }

  @XmlElement(name = "steps")
  public PropEntitiesStep getSteps() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("steps", false), new ValueWithDefault.Value<PropEntitiesStep>() {
      public PropEntitiesStep get() {
        return new PropEntitiesStep(myBuildType.get());
      }
    });
  }

  @XmlElement(name = "features")
  public PropEntitiesFeature getFeatures() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("features", false), new ValueWithDefault.Value<PropEntitiesFeature>() {
      public PropEntitiesFeature get() {
        return new PropEntitiesFeature(myBuildType.get());
      }
    });
  }

  @XmlElement(name = "triggers")
  public PropEntitiesTrigger getTriggers() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("triggers", false), new ValueWithDefault.Value<PropEntitiesTrigger>() {
      public PropEntitiesTrigger get() {
        return new PropEntitiesTrigger(myBuildType.get());
      }
    });
  }


  @XmlElement(name = "snapshot-dependencies")
  public PropEntitiesSnapshotDep getSnapshotDependencies() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("snapshot-dependencies", false), new ValueWithDefault.Value<PropEntitiesSnapshotDep>() {
      public PropEntitiesSnapshotDep get() {
        return new PropEntitiesSnapshotDep(myBuildType.get(), myBeanContext);
      }
    });
  }

  @XmlElement(name = "artifact-dependencies")
  public PropEntitiesArtifactDep getArtifactDependencies() {
    return ValueWithDefault
      .decideDefault(myFields.isIncluded("artifact-dependencies", false), new ValueWithDefault.Value<PropEntitiesArtifactDep>() {
        public PropEntitiesArtifactDep get() {
          return new PropEntitiesArtifactDep(myBuildType.get().getArtifactDependencies(), myBeanContext);
        }
      });
  }

  @XmlElement(name = "agent-requirements")
  public PropEntitiesAgentRequirement getAgentRequirements() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("agent-requirements", false), new ValueWithDefault.Value<PropEntitiesAgentRequirement>() {
      public PropEntitiesAgentRequirement get() {
        return new PropEntitiesAgentRequirement(myBuildType.get());
      }
    });
  }

  @XmlElement(name="settings")
  public Properties getSettings() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("settings", false), new ValueWithDefault.Value<Properties>() {
      public Properties get() {
        return new Properties(BuildTypeUtil.getSettingsParameters(myBuildType), null, myFields.getNestedField("settings", Fields.NONE, Fields.LONG));
      }
    });
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
    if (myFields.isIncluded("investigations", false, true)) {
      final ResponsibilityEntry.State state = myBuildType.getBuildType().getResponsibilityInfo().getState();
      if (!state.equals(ResponsibilityEntry.State.NONE)) {
        return new Investigations(null, new Href(InvestigationRequest.getHref(myBuildType.getBuildType()), myBeanContext.getApiUrlBuilder()),
                                  myFields.getNestedField("investigations"), null, myBeanContext);
      }
    }
    return null;
  }

  /**
   * This is used only when posting a link to the build
   */
  private String submittedId;
  private String submittedInternalId;
  private String submittedLocator;

  public void setId(String id) {
    submittedId = id;
  }

  public void setInternalId(String id) {
    submittedInternalId = id;
  }

  @XmlAttribute
  public String getLocator() {
    return null;
  }

  public void setLocator(final String locator) {
    submittedLocator = locator;
  }

  @Nullable
  public String getExternalIdFromPosted(@NotNull final ServiceLocator serviceLocator) {
    if (submittedId != null) {
      if (submittedInternalId == null) {
        return submittedId;
      }
      String externalByInternal = serviceLocator.getSingletonService(BuildTypeIdentifiersManager.class).internalToExternal(submittedInternalId);
      if (externalByInternal == null || submittedId.equals(externalByInternal)) {
        return submittedId;
      }
      throw new BadRequestException(
        "Both external id '" + submittedId + "' and internal id '" + submittedInternalId + "' attributes are present and they reference different build types.");
    }
    if (submittedInternalId != null) {
      return serviceLocator.getSingletonService(BuildTypeIdentifiersManager.class).internalToExternal(submittedInternalId);
    }
    if (submittedLocator != null) {
      return serviceLocator.getSingletonService(BuildTypeFinder.class).getBuildType(null, submittedLocator).getExternalId();
    }
    throw new BadRequestException("Could not find build type by the data. Either 'id' or 'internalId' or 'locator' attributes should be specified.");
  }

  @NotNull
  public BuildTypeOrTemplate getBuildTypeFromPosted(@NotNull final BuildTypeFinder buildTypeFinder) {
    String locatorText = "";
    if (submittedInternalId != null) locatorText = "internalId:" + submittedInternalId;
    if (submittedId != null) locatorText += (!locatorText.isEmpty() ? "," : "") + "id:" + submittedId;
    if (locatorText.isEmpty()) {
      locatorText = submittedLocator;
    } else {
      if (submittedLocator != null) {
        throw new BadRequestException("Both 'locator' and 'id' or 'internalId' attributes are specified. Only one should be present.");
      }
    }
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("No build type specified. Either 'id', 'internalId' or 'locator' attribute should be present.");
    }
    return buildTypeFinder.getBuildTypeOrTemplate(null, locatorText);
  }
}
