/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.project;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.data.parameters.InheritableUserParametersHolderEntityWithParameters;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.buildType.VcsRoots;
import jetbrains.buildServer.server.rest.request.ProjectRequest;
import jetbrains.buildServer.server.rest.request.VcsRootRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "project")
@XmlType(name = "project", propOrder = {"id", "internalId", "uuid", "name", "parentProjectId", "parentProjectInternalId", "parentProjectName", "archived", "description", "href", "webUrl",
  "links", "parentProject", "readOnlyUI", "defaultTemplate", "buildTypes", "templates", "parameters", "vcsRoots", "projectFeatures", "projects"})
@SuppressWarnings("PublicField")
public class Project {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public String internalId;

  @XmlAttribute
  public String uuid;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String href;

  /**
   * Used only for short project entity
   */
  @XmlAttribute
  public String parentProjectId;

  /**
   * @deprecated
   */
  @XmlAttribute
  public String parentProjectName;

  /**
   * @deprecated
   */
  @XmlAttribute
  public String parentProjectInternalId;


  @XmlAttribute
  public String description;

  @XmlAttribute
  public Boolean archived;

  /**
   * "Canonical" URL for the project's web UI page: it is absolute and uses configured Server URL as a base
   */
  @XmlAttribute
  public String webUrl;

  @XmlElement
  public Links links;

  @XmlElement(name = "parentProject")
  public Project parentProject;

  @XmlElement
  public StateField readOnlyUI;

  @XmlElement
  public BuildTypes buildTypes;

  @XmlElement
  public BuildTypes templates;

  @XmlElement
  public BuildType defaultTemplate;

  @XmlElement
  public Properties parameters;

  @XmlElement (name = "vcsRoots")
  public VcsRoots vcsRoots;

  @XmlElement
  public PropEntitiesProjectFeature projectFeatures;

  @XmlElement (name = "projects")
  public Projects projects;

  /**
   * This is used only when posting a link to a project
   */
  @XmlAttribute public String locator;

  public Project() {
  }

  public Project(@Nullable final String externalId, @Nullable final String internalId, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    //todo: check usages: externalId should actually be NotNull and internal id should never be necessary
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), externalId);
    this.internalId = ValueWithDefault.decideDefault(fields.isIncluded("internalId"), internalId);
  }

  public Project(@NotNull final SProject project, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), project.getExternalId());
    final boolean includeInternal = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME);
    internalId = ValueWithDefault.decideDefault(fields.isIncluded("internalId", includeInternal, includeInternal), project.getProjectId());
    if (beanContext.getSingletonService(PermissionChecker.class).isPermissionGranted(Permission.EDIT_PROJECT, project.getProjectId())) {
      uuid = ValueWithDefault.decideDefault(fields.isIncluded("uuid", false, false), ((ProjectEx)project).getId().getConfigId());
    }
    name = ValueWithDefault.decideDefault(fields.isIncluded("name"), project.getName());

    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().getHref(project));
    webUrl = ValueWithDefault.decideDefault(fields.isIncluded("webUrl"), beanContext.getSingletonService(WebLinks.class).getProjectPageUrl(project.getExternalId()));

    links = getLinks(project, fields, beanContext);

    final String descriptionText = project.getDescription();
    description = ValueWithDefault.decideDefault(fields.isIncluded("description"), StringUtil.isEmpty(descriptionText) ? null : descriptionText);
    archived = ValueWithDefault.decideDefault(fields.isIncluded("archived"), project.isArchived());
    readOnlyUI = StateField.create(project.isReadOnly(), ((ProjectEx)project).isCustomSettingsFormatUsed() ? false : null, fields.getNestedField("readOnlyUI"));

    final BuildTypeFinder buildTypeFinder = beanContext.getSingletonService(BuildTypeFinder.class);
    buildTypes = ValueWithDefault.decideDefault(fields.isIncluded("buildTypes", false), new ValueWithDefault.Value<BuildTypes>() {
      public BuildTypes get() {
        final Fields buildTypesFields = fields.getNestedField("buildTypes", Fields.NONE, Fields.LONG);
        final String buildTypesLocator = buildTypesFields.getLocator();
        final List<BuildTypeOrTemplate> buildTypes = buildTypeFinder.getBuildTypesPaged(project, buildTypesLocator, true).myEntries;
        return new BuildTypes(buildTypes, null, buildTypesFields, beanContext);
      }
    });

    final PermissionChecker permissionChecker = beanContext.getServiceLocator().findSingletonService(PermissionChecker.class);
    assert permissionChecker != null;
    if (!shouldRestrictSettingsViewing(project, permissionChecker)) {
      templates = ValueWithDefault.decideDefault(fields.isIncluded("templates", false), new ValueWithDefault.Value<BuildTypes>() {
        public BuildTypes get() {
          final Fields templateFields = fields.getNestedField("templates", Fields.NONE, Fields.LONG);
          final String templatesLocator = templateFields.getLocator();
          final List<BuildTypeOrTemplate> templates = buildTypeFinder.getBuildTypesPaged(project, templatesLocator, false).myEntries;
          return new BuildTypes(templates, null, templateFields, beanContext);
        }
      });

      defaultTemplate = ValueWithDefault.decideDefault(fields.isIncluded("defaultTemplate", false), () -> {
        final Fields templateFields = fields.getNestedField("defaultTemplate", Fields.NONE, Fields.LONG);
        BuildTypeTemplate defaultTemplate = project.getDefaultTemplate();
        if (defaultTemplate == null) return null;
        return new BuildType(new BuildTypeOrTemplate(defaultTemplate), templateFields, beanContext);
      });

      parameters = ValueWithDefault.decideDefault(fields.isIncluded("parameters", false), new ValueWithDefault.Value<Properties>() {
        public Properties get() {
          return new Properties(createEntity(project), ProjectRequest.getParametersHref(project),
                                null, fields.getNestedField("parameters", Fields.NONE, Fields.LONG), beanContext);
        }
      });
      vcsRoots = ValueWithDefault.decideDefault(fields.isIncluded("vcsRoots", false), new ValueWithDefault.Value<VcsRoots>() {
        public VcsRoots get() {
          return new VcsRoots(project.getOwnVcsRoots(), //consistent with VcsRootFinder
                              new PagerData(VcsRootRequest.getHref(project)), fields.getNestedField("vcsRoots"), beanContext);
        }
      });
      projectFeatures = ValueWithDefault.decideDefault(fields.isIncluded("projectFeatures", false),
                                                () -> {
                                                  Fields nestedFields = fields.getNestedField("projectFeatures", Fields.NONE, Fields.LONG);
                                                  return new PropEntitiesProjectFeature(project, nestedFields.getLocator(), nestedFields, beanContext);
                                                });
    } else {
      templates = null;
      defaultTemplate = null;
      parameters = null;
      vcsRoots = null;
      projectFeatures = null;
    }

    projects = ValueWithDefault.decideDefault(fields.isIncluded("projects", false), new ValueWithDefault.Value<Projects>() {
      public Projects get() {
        final Fields projectsFields = fields.getNestedField("projects", Fields.NONE, Fields.LONG);
        final String projectsLocator = projectsFields.getLocator();
        final ProjectFinder projectFinder = beanContext.getSingletonService(ProjectFinder.class);
        final List<SProject> projects = projectFinder.getItems(project, projectsLocator).myEntries;
        return new Projects(projects, null, projectsFields, beanContext);
      }
    });

    final SProject actualParentProject = project.getParentProject();
    if (actualParentProject != null) {
      parentProject = ValueWithDefault.decideDefault(fields.isIncluded("parentProject", false), new ValueWithDefault.Value<Project>() {
        public Project get() {
          return new Project(actualParentProject, fields.getNestedField("parentProject"), beanContext);
        }
      });

      parentProjectId = ValueWithDefault.decideDefault(fields.isIncluded("parentProjectId"), actualParentProject.getExternalId());

      final boolean forceParentAttributes = TeamCityProperties.getBoolean("rest.beans.project.addParentProjectAttributes");
      parentProjectName = ValueWithDefault.decideDefault(forceParentAttributes || fields.isIncluded("parentProjectName", false, false), actualParentProject.getFullName());
      parentProjectInternalId = ValueWithDefault.decideDefault(forceParentAttributes || fields.isIncluded("parentProjectInternalId", includeInternal, includeInternal),
                                                               actualParentProject.getProjectId());
    }
  }

  @Nullable
  private Links getLinks(@NotNull final SProject project, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    return ValueWithDefault.decideDefault(fields.isIncluded("links", false, false), new ValueWithDefault.Value<Links>() {
      @Nullable
      @Override
      public Links get() {
        WebLinks webLinks = beanContext.getSingletonService(WebLinks.class);
        RelativeWebLinks relativeWebLinks = new RelativeWebLinks();
        Links.LinksBuilder builder = new Links.LinksBuilder();
        builder.add(Link.WEB_VIEW_TYPE, webLinks.getProjectPageUrl(project.getExternalId()), relativeWebLinks.getProjectPageUrl(project.getExternalId()));
        final PermissionChecker permissionChecker = beanContext.getSingletonService(PermissionChecker.class);
        if (permissionChecker.isPermissionGranted(Permission.EDIT_PROJECT, project.getProjectId())) {
          builder.add(Link.WEB_EDIT_TYPE, webLinks.getEditProjectPageUrl(project.getExternalId()), relativeWebLinks.getEditProjectPageUrl(project.getExternalId()));
        } else if (permissionChecker.hasPermissionInAnyProject(Permission.EDIT_PROJECT) && //this grants access to administration area
                   permissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, project.getProjectId())) {
          builder.add(Link.WEB_VIEW_SETTINGS_TYPE, webLinks.getEditProjectPageUrl(project.getExternalId()), relativeWebLinks.getEditProjectPageUrl(project.getExternalId()));
        }
        return builder.build(fields.getNestedField("links"));
      }
    });
  }

  @Nullable
  public static String getFieldValue(final SProject project, final String field) {
    if ("id".equals(field)) {
      return project.getExternalId();
    } else if ("internalId".equals(field)) {
      return project.getProjectId();
    } else if ("description".equals(field)) {
      return project.getDescription();
    } else if ("name".equals(field)) {
      return project.getName();
    } else if ("fullName".equals(field)) {
      return project.getFullName();
    } else if ("archived".equals(field)) {
      return String.valueOf(project.isArchived());
    } else if ("readOnlyUI".equals(field)) {
      return String.valueOf(project.isReadOnly());
    } else if ("parentProjectName".equals(field)) {
      //noinspection ConstantConditions
      return project.getParentProject() == null ? null : project.getParentProject().getName();
    } else if ("parentProjectId".equals(field)) {
      //noinspection ConstantConditions
      return project.getParentProject() == null ? null : project.getParentProject().getExternalId();
    } else if ("parentProjectInternalId".equals(field)) {
      //noinspection ConstantConditions
      return project.getParentProject() == null ? null : project.getParentProject().getProjectId();
    } else if ("status".equals(field)) { //Experimental support
      return project.getStatus().getText();
    }
    throw new NotFoundException("Field '" + field + "' is not supported.  Supported are: id, name, description, archived, internalId.");
  }

  public static void setFieldValueAndPersist(final SProject project,
                                             final String field,
                                             final String value, @NotNull final ServiceLocator serviceLocator) {
    if ("name".equals(field)) {
      if (StringUtil.isEmpty(value)){
        throw new BadRequestException("Project name cannot be empty.");
      }
      project.setName(value);
      project.persist();
      return;
    } else if ("id".equals(field)) {
      if (StringUtil.isEmpty(value)){
        throw new BadRequestException("Project id cannot be empty.");
      }
      project.setExternalId(value);
      return;
    } else if ("description".equals(field)) {
      project.setDescription(value);
      project.persist();
      return;
    } else if ("archived".equals(field)) {
      project.setArchived(Boolean.valueOf(value), serviceLocator.getSingletonService(UserFinder.class).getCurrentUser());
      return;
    } else if ("readOnlyUI".equals(field) && TeamCityProperties.getBoolean("rest.projectRequest.allowSetReadOnlyUI")) {
      ((ProjectEx)project).setEditable(!Boolean.valueOf(value));
      project.persist();
      return;
    }
    throw new BadRequestException("Setting field '" + field + "' is not supported. Supported are: name, description, archived");
  }

  @NotNull
  public SProject getProjectFromPosted(@NotNull ProjectFinder projectFinder) {
    return projectFinder.getItem(getLocatorFromPosted());
  }

  @NotNull
  public String getLocatorFromPosted() {
    //todo: support posted parentProject fields here
    String locatorText = "";
    if (internalId != null) locatorText = "internalId:" + internalId;
    if (id != null) locatorText += (!locatorText.isEmpty() ? "," : "") + "id:" + id;
    if (locatorText.isEmpty()) {
      locatorText = locator;
    } else {
      if (locator != null) {
        throw new BadRequestException("Both 'locator' and 'id' or 'internalId' attributes are specified. Only one should be present.");
      }
    }
    if (StringUtil.isEmpty(locatorText)){
      //find by href for compatibility with 7.0
      if (!StringUtil.isEmpty(href)){
        locatorText = StringUtil.lastPartOf(href, '/');
      } else{
        throw new BadRequestException("No project specified. Either 'id', 'internalId' or 'locator' attribute should be present.");
      }
    }
    return locatorText;
  }

  public static boolean shouldRestrictSettingsViewing(final @NotNull BuildProject project, final @NotNull PermissionChecker permissionChecker) {
    if (TeamCityProperties.getBooleanOrTrue("rest.beans.project.checkPermissions")) {
      return !permissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, project.getProjectId());
    }
    return false;
  }

  public static ParametersPersistableEntity createEntity(@NotNull final SProject project) {
    return new ProjectEntityWithParameters(project);
  }

  private static class ProjectEntityWithParameters extends InheritableUserParametersHolderEntityWithParameters implements
                                                                                                                                              ParametersPersistableEntity {
    @NotNull private final SProject myProject;

    public ProjectEntityWithParameters(@NotNull final SProject project) {
      super(project);
      myProject = project;
    }

    public void persist() {
      myProject.persist();
    }
 }
}
