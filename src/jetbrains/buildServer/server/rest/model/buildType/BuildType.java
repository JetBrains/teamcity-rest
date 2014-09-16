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
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.request.BuildTypeRequest;
import jetbrains.buildServer.server.rest.request.InvestigationRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.identifiers.BuildTypeIdentifiersManager;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType")
@XmlType(name = "buildType", propOrder = { "id", "internalId", "name", "templateFlag", "paused", "description", "projectName", "projectId", "projectInternalId", "href", "webUrl",
  "project", "template", "vcsRootEntries", "settings", "parameters", "steps", "features", "triggers", "snapshotDependencies",
  "artifactDependencies", "agentRequirements", "builds", "investigations"})
public class BuildType {
  private static final Logger LOG = Logger.getInstance(BuildType.class.getName());

  @Nullable
  protected BuildTypeOrTemplate myBuildType;
  @NotNull private String myExternalId;
  @Nullable private String myInternalId;

  private Fields myFields = Fields.LONG;
  @NotNull private BeanContext myBeanContext;

  public BuildType() {
  }

  public BuildType(@NotNull final BuildTypeOrTemplate buildType, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myBuildType = buildType;
    myExternalId = buildType.getId();
    myInternalId = buildType.getInternalId();
    myFields = fields;
    myBeanContext = beanContext;
  }

  public BuildType(@NotNull final String externalId, @Nullable final String internalId, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myBuildType = null;
    myExternalId = externalId;
    myInternalId = internalId;
    myFields = fields;
    myBeanContext = beanContext;
  }

  /**
   * @return External id of the build configuration
   */
  @XmlAttribute
  public String getId() {
    return myBuildType == null ? myExternalId : ValueWithDefault.decideDefault(myFields.isIncluded("id", true), myBuildType.getId());
  }

  @XmlAttribute
  public String getInternalId() {
    final boolean includeProperty = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME);
    return myBuildType == null ? myInternalId : ValueWithDefault.decideDefault(myFields.isIncluded("internalId", includeProperty, includeProperty), myBuildType.getInternalId());
  }

  @XmlAttribute
  public String getName() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("name"), myBuildType.getName());
  }

  @XmlAttribute
  public String getProjectId() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("projectId"), myBuildType.getProject().getExternalId());
  }

  @XmlAttribute
  public String getProjectInternalId() {
    final boolean includeProperty = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME);
    return myBuildType == null
           ? null
           : ValueWithDefault.decideDefault(myFields.isIncluded("projectInternalId", includeProperty, includeProperty), myBuildType.getProject().getProjectId());
  }

  /**
   * @deprecated
   * @return
   */
  @XmlAttribute
  public String getProjectName() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("projectName"), myBuildType.getProject().getFullName());
  }

  @XmlAttribute
  public String getHref() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("href"), myBeanContext.getApiUrlBuilder().getHref(myBuildType));
  }

  @XmlAttribute
  public String getDescription() {
    if (myBuildType == null){
      return null;
    }
    final String description = myBuildType.getDescription();
    return ValueWithDefault.decideDefault(myFields.isIncluded("description"), StringUtil.isEmpty(description) ? null : description);
  }

  @XmlAttribute (name = "templateFlag")
  public Boolean getTemplateFlag() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("templateFlag"), !myBuildType.isBuildType());
  }

  @XmlAttribute
  public Boolean isPaused() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("paused"), myBuildType.isPaused());
  }

  @XmlAttribute
  public String getWebUrl() {
    //template has no user link
    return  myBuildType == null || myBuildType.getBuildType() == null
           ? null
           : ValueWithDefault
             .decideDefault(myFields.isIncluded("webUrl"), myBeanContext.getSingletonService(WebLinks.class).getConfigurationHomePageUrl(myBuildType.getBuildType()));
  }

  @XmlElement(name = "project")
  public Project getProject() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("project", false), new ValueWithDefault.Value<Project>() {
      public Project get() {
        return myBuildType == null ? null : new Project(myBuildType.getProject(), myFields.getNestedField("project"), myBeanContext);
      }
    });
  }

  @XmlElement(name = "template")
  public BuildType getTemplate() {
    if (myBuildType == null || myBuildType.getBuildType() == null){
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
        return myBuildType == null ? null : new VcsRootEntries(myBuildType, myBeanContext.getApiUrlBuilder());
      }
    });
  }

  /**
   * Link to builds of this build configuration. Is not present for templates.
   * @return
   */
  @XmlElement(name = "builds")
  public Builds getBuilds() {
    if (myBuildType == null || !myBuildType.isBuildType()) return null;
    if (!myFields.isIncluded("builds", false, true)){
      return null;
    }

    return ValueWithDefault.decideDefault(myFields.isIncluded("builds", false), new ValueWithDefault.Value<Builds>() {
      public Builds get() {
        String buildsHref;
        List<BuildPromotion> builds = null;
        final Fields buildsFields = myFields.getNestedField("builds");
        final String buildsLocator = buildsFields.getLocator();
        if (buildsLocator != null){
          builds = BuildFinder.getBuildPromotions(myBeanContext.getSingletonService(BuildFinder.class).getBuildsSimplified(myBuildType.getBuildType(), buildsLocator));
          buildsHref = BuildTypeRequest.getBuildsHref(myBuildType.getBuildType(), buildsLocator);
        }else{
          buildsHref = BuildTypeRequest.getBuildsHref(myBuildType.getBuildType());
        }
        return new Builds(builds, new PagerData(buildsHref), buildsFields, myBeanContext);
      }
    });
  }

  @XmlElement
  public Properties getParameters() {
    return myBuildType == null ? null : ValueWithDefault
      .decideDefault(myFields.isIncluded("parameters", false), new ValueWithDefault.Value<Properties>() {
        public Properties get() {
          return new Properties(myBuildType.get().getParametersCollection(), myBuildType.get().getOwnParametersCollection(), BuildTypeRequest.getParametersHref(myBuildType),
                                myFields.getNestedField("parameters", Fields.NONE, Fields.LONG), myBeanContext.getServiceLocator());
        }
      });
  }

  @XmlElement(name = "steps")
  public PropEntitiesStep getSteps() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("steps", false), new ValueWithDefault.Value<PropEntitiesStep>() {
      public PropEntitiesStep get() {
        return new PropEntitiesStep(myBuildType.get());
      }
    });
  }

  @XmlElement(name = "features")
  public PropEntitiesFeature getFeatures() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("features", false), new ValueWithDefault.Value<PropEntitiesFeature>() {
      public PropEntitiesFeature get() {
        return new PropEntitiesFeature(myBuildType.get());
      }
    });
  }

  @XmlElement(name = "triggers")
  public PropEntitiesTrigger getTriggers() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("triggers", false), new ValueWithDefault.Value<PropEntitiesTrigger>() {
      public PropEntitiesTrigger get() {
        return new PropEntitiesTrigger(myBuildType.get());
      }
    });
  }


  @XmlElement(name = "snapshot-dependencies")
  public PropEntitiesSnapshotDep getSnapshotDependencies() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("snapshot-dependencies", false), new ValueWithDefault.Value<PropEntitiesSnapshotDep>() {
      public PropEntitiesSnapshotDep get() {
        return new PropEntitiesSnapshotDep(myBuildType.get(), myBeanContext);
      }
    });
  }

  @XmlElement(name = "artifact-dependencies")
  public PropEntitiesArtifactDep getArtifactDependencies() {
    return myBuildType == null ? null : ValueWithDefault
      .decideDefault(myFields.isIncluded("artifact-dependencies", false), new ValueWithDefault.Value<PropEntitiesArtifactDep>() {
        public PropEntitiesArtifactDep get() {
          return new PropEntitiesArtifactDep(myBuildType.get().getArtifactDependencies(), myBeanContext);
        }
      });
  }

  @XmlElement(name = "agent-requirements")
  public PropEntitiesAgentRequirement getAgentRequirements() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("agent-requirements", false), new ValueWithDefault.Value<PropEntitiesAgentRequirement>() {
      public PropEntitiesAgentRequirement get() {
        return new PropEntitiesAgentRequirement(myBuildType.get());
      }
    });
  }

  @XmlElement(name="settings")
  public Properties getSettings() {
    return myBuildType == null ? null : ValueWithDefault.decideDefault(myFields.isIncluded("settings", false), new ValueWithDefault.Value<Properties>() {
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
    if (myBuildType == null || myBuildType.getBuildType() == null) {
      return null;
    }
    if (myFields.isIncluded("investigations", false, true)) {
      final ResponsibilityEntry.State state = myBuildType.getBuildType().getResponsibilityInfo().getState();
      if (!state.equals(ResponsibilityEntry.State.NONE)) {
        //todo: include list by default, add support for locator + filter here, like for builds in BuildType
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
    if (submittedInternalId != null) {
      locatorText = "internalId:" + submittedInternalId;
    } else {
      if (submittedId != null) locatorText += (!locatorText.isEmpty() ? "," : "") + "id:" + submittedId;
    }
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

  @Nullable private  String submittedProjectId;
  @Nullable private  Project submittedProject;
  @Nullable private  String submittedName;
  @Nullable private  String submittedDescription;
  @Nullable private  Boolean submittedTemplateFlag;
  @Nullable private  Boolean submittedPaused;
  @Nullable private  BuildType submittedTemplate;
  @Nullable private  VcsRootEntries submittedVcsRootEntries;
  @Nullable private  Properties submittedParameters;
  @Nullable private  PropEntitiesStep submittedSteps;
  @Nullable private  PropEntitiesFeature submittedFeatures;
  @Nullable private  PropEntitiesTrigger submittedTriggers;
  @Nullable private  PropEntitiesSnapshotDep submittedSnapshotDependencies;
  @Nullable private  PropEntitiesArtifactDep submittedArtifactDependencies;
  @Nullable private  PropEntitiesAgentRequirement submittedAgentRequirements;
  @Nullable private  Properties submittedSettings;

  public void setProjectId(@Nullable final String submittedProjectId) {
    this.submittedProjectId = submittedProjectId;
  }

  public void setProject(@Nullable final Project submittedProject) {
    this.submittedProject = submittedProject;
  }

  public void setName(@Nullable final String submittedName) {
    this.submittedName = submittedName;
  }

  public void setDescription(@Nullable final String submittedDescription) {
    this.submittedDescription = submittedDescription;
  }

  public void setTemplateFlag(@Nullable final Boolean submittedTemplateFlag) {
    this.submittedTemplateFlag = submittedTemplateFlag;
  }

  public void setPaused(@Nullable final Boolean submittedPaused) {
    this.submittedPaused = submittedPaused;
  }

  public void setTemplate(@Nullable final BuildType submittedTemplate) {
    this.submittedTemplate = submittedTemplate;
  }

  public void setVcsRootEntries(@Nullable final VcsRootEntries submittedVcsRootEntries) {
    this.submittedVcsRootEntries = submittedVcsRootEntries;
  }

  public void setParameters(@Nullable final Properties submittedParameters) {
    this.submittedParameters = submittedParameters;
  }

  public void setSteps(@Nullable final PropEntitiesStep submittedSteps) {
    this.submittedSteps = submittedSteps;
  }

  public void setFeatures(@Nullable final PropEntitiesFeature submittedFeatures) {
    this.submittedFeatures = submittedFeatures;
  }

  public void setTriggers(@Nullable final PropEntitiesTrigger submittedTriggers) {
    this.submittedTriggers = submittedTriggers;
  }

  public void setSnapshotDependencies(@Nullable final PropEntitiesSnapshotDep submittedSnapshotDependencies) {
    this.submittedSnapshotDependencies = submittedSnapshotDependencies;
  }

  public void setArtifactDependencies(@Nullable final PropEntitiesArtifactDep submittedArtifactDependencies) {
    this.submittedArtifactDependencies = submittedArtifactDependencies;
  }

  public void setAgentRequirements(@Nullable final PropEntitiesAgentRequirement submittedAgentRequirements) {
    this.submittedAgentRequirements = submittedAgentRequirements;
  }

  public void setSettings(@Nullable final Properties submittedSettings) {
    this.submittedSettings = submittedSettings;
  }

  @NotNull
  public BuildTypeOrTemplate createNewBuildTypeFromPosted(@NotNull final ServiceLocator serviceLocator) {
    SProject project;
    if (submittedProject == null) {
      if (submittedProjectId == null) {
        throw new BadRequestException("Build type creation request should contain project node.");
      }
      //noinspection ConstantConditions
      project = serviceLocator.findSingletonService(ProjectManager.class).findProjectByExternalId(submittedProjectId);
      if (project == null) {
        throw new BadRequestException("Cannot find project with id '" + submittedProjectId + "'.");
      }
    } else {
      //noinspection ConstantConditions
      project = submittedProject.getProjectFromPosted(serviceLocator.findSingletonService(ProjectFinder.class));
    }

    if (StringUtil.isEmpty(submittedName)) {
      throw new BadRequestException("When creating a build type, non empty name should be provided.");
    }

    BuildTypeOrTemplate resultingBuildType;
    if (submittedTemplateFlag == null || !submittedTemplateFlag) {
      resultingBuildType = new BuildTypeOrTemplate(project.createBuildType(getIdForBuildType(serviceLocator, project, submittedName), submittedName));
    } else {
      resultingBuildType = new BuildTypeOrTemplate(project.createBuildTypeTemplate(getIdForBuildType(serviceLocator, project, submittedName), submittedName));
    }


    if (submittedDescription != null) {
      resultingBuildType.setDescription(submittedDescription);
    }
    if (submittedPaused != null) {
      if (resultingBuildType.getBuildType() == null) {
        throw new BadRequestException("Cannot set paused state for a template");
      }
      resultingBuildType.getBuildType().setPaused(Boolean.valueOf(submittedPaused), serviceLocator.getSingletonService(DataProvider.class).getCurrentUser(),
                                                  TeamCityProperties.getProperty("rest.defaultActionComment"));
    }
    if (submittedTemplate != null) {
      if (resultingBuildType.getBuildType() == null) {
        throw new BadRequestException("Cannot set template for a template");
      }
      //noinspection ConstantConditions
      final BuildTypeOrTemplate templateFromPosted = submittedTemplate.getBuildTypeFromPosted(serviceLocator.findSingletonService(BuildTypeFinder.class));
      if (templateFromPosted.getTemplate() == null) {
        throw new BadRequestException("emplate should reference a template, not build type");
      }
      resultingBuildType.getBuildType().attachToTemplate(templateFromPosted.getTemplate());
    }
    if (submittedVcsRootEntries != null && submittedVcsRootEntries.vcsRootAssignments != null) {
      for (VcsRootEntry entity : submittedVcsRootEntries.vcsRootAssignments) {
        BuildTypeRequest.addVcsRoot(resultingBuildType, entity, serviceLocator.getSingletonService(VcsRootFinder.class));
      }
    }
    if (submittedParameters != null && submittedParameters.properties != null) {
      for (Property p : submittedParameters.properties) {
        BuildTypeUtil.changeParameter(p.name, p.value, resultingBuildType.get(), serviceLocator);
      }
    }
    if (submittedSteps != null && submittedSteps.propEntities != null) {
      for (PropEntityStep entity : submittedSteps.propEntities) {
        entity.addStep(resultingBuildType.get());
      }
    }
    if (submittedFeatures != null && submittedFeatures.propEntities != null) {
      for (PropEntityFeature entity : submittedFeatures.propEntities) {
        entity.addFeature(resultingBuildType.get(), serviceLocator.getSingletonService(BuildFeatureDescriptorFactory.class));
      }
    }
    if (submittedTriggers != null && submittedTriggers.propEntities != null) {
      for (PropEntityTrigger entity : submittedTriggers.propEntities) {
        entity.addTrigger(resultingBuildType.get(), serviceLocator.getSingletonService(BuildTriggerDescriptorFactory.class));
      }
    }
    if (submittedSnapshotDependencies != null && submittedSnapshotDependencies.propEntities != null) {
      for (PropEntitySnapshotDep entity : submittedSnapshotDependencies.propEntities) {
        entity.addSnapshotDependency(resultingBuildType.get(), serviceLocator);
      }
    }
    if (submittedArtifactDependencies != null && submittedArtifactDependencies.propEntities != null) {
      final List<SArtifactDependency> dependencyObjects =
        CollectionsUtil.convertCollection(submittedArtifactDependencies.propEntities, new Converter<SArtifactDependency, PropEntityArtifactDep>() {
          public SArtifactDependency createFrom(@NotNull final PropEntityArtifactDep source) {
            return source.createDependency(serviceLocator);
          }
        });
      resultingBuildType.get().setArtifactDependencies(dependencyObjects);
    }
    if (submittedAgentRequirements != null&& submittedAgentRequirements.propEntities != null) {
          for (PropEntityAgentRequirement entity : submittedAgentRequirements.propEntities) {
            entity.addRequirement(resultingBuildType);
          }
        }
    if (submittedSettings != null && submittedSettings.properties != null) {
        for (Property property : submittedSettings.properties) {
          BuildTypeRequest.setSetting(resultingBuildType, property.name, property.value);
        }
    }

    return resultingBuildType;
  }

  @NotNull
  public String getIdForBuildType(@NotNull final ServiceLocator serviceLocator, @NotNull SProject project, @NotNull final String name) {
    if (submittedId != null) {
      return submittedId;
    }
    return serviceLocator.getSingletonService(BuildTypeIdentifiersManager.class).generateNewExternalId(project.getExternalId(), name, null);
  }
}
