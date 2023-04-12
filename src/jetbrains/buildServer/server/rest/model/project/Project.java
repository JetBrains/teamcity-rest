/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.CloudProfileFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.UserFinder;
import jetbrains.buildServer.server.rest.data.parameters.InheritableUserParametersHolderEntityWithParameters;
import jetbrains.buildServer.server.rest.data.parameters.ParametersPersistableEntity;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.buildType.VcsRoots;
import jetbrains.buildServer.server.rest.model.cloud.CloudProfiles;
import jetbrains.buildServer.server.rest.request.CloudRequest;
import jetbrains.buildServer.server.rest.request.ProjectRequest;
import jetbrains.buildServer.server.rest.request.VcsRootRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
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
@XmlType(name = "project", propOrder = {"id", "internalId", "uuid", "name", "parentProjectId", "parentProjectInternalId", "parentProjectName", "archived", "virtual", "description",
  "href", "webUrl",
  "links", "parentProject", "readOnlyUI", "defaultTemplate", "buildTypes", "templates", "parameters", "vcsRoots", "projectFeatures", "projects", "cloudProfiles",
  "ancestorProjects"})
@SuppressWarnings("PublicField")
@ModelDescription(
  value = "Represents a project.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/project.html",
  externalArticleName = "Project"
)
public class Project {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public String internalId;

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

  @XmlElement(name = "vcsRoots")
  public VcsRoots vcsRoots;

  @XmlElement
  public PropEntitiesProjectFeature projectFeatures;

  @XmlElement(name = "projects")
  public Projects projects;

  @XmlElement(name = "cloudProfiles")
  public CloudProfiles cloudProfiles;

  /**
   * This is used only when posting a link to a project
   */
  @XmlAttribute
  public String locator;

  private Fields myFields;
  private SProject myProject;
  private BeanContext myBeanContext;

  public Project() {
  }

  public Project(@Nullable final String externalId, @Nullable final String internalId, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    myFields = fields;
    //todo: check usages: externalId should actually be NotNull and internal id should never be necessary
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), externalId);
    this.internalId = ValueWithDefault.decideDefault(fields.isIncluded("internalId"), internalId);
  }

  public Project(@NotNull final SProject project, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    myFields = fields;
    myProject = project;
    myBeanContext = beanContext;

    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), project::getExternalId);
    final boolean includeInternal = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME);
    internalId = ValueWithDefault.decideDefault(fields.isIncluded("internalId", includeInternal, includeInternal), project::getProjectId);
    name = ValueWithDefault.decideDefault(fields.isIncluded("name"), project::getName);

    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), () -> beanContext.getApiUrlBuilder().getHref(project));
    webUrl = ValueWithDefault.decideDefaultIgnoringAccessDenied(
      fields.isIncluded("webUrl"),
      () -> beanContext.getSingletonService(WebLinks.class).getProjectPageUrl(project.getExternalId())
    );

    links = getLinks(project, fields, beanContext);

    description = ValueWithDefault.decideDefault(
      fields.isIncluded("description"),
      () -> Util.resolveNull(project.getDescription(), d -> StringUtil.isEmpty(d) ? null : d)
    );

    archived = ValueWithDefault.decideDefault(fields.isIncluded("archived"), project::isArchived);
    readOnlyUI = ValueWithDefault.decideDefault(
      fields.isIncluded("readOnlyUI"),
      () -> StateField.create(
        project.isReadOnly(),
        ((ProjectEx)project).isCustomSettingsFormatUsed() ? false : null,
        fields.getNestedField("readOnlyUI")
      )
    );

    final CachingValue<BuildTypeFinder> buildTypeFinder = CachingValue.simple(() -> beanContext.getSingletonService(BuildTypeFinder.class));
    buildTypes = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("buildTypes", false), () -> {
        final Fields buildTypesFields = fields.getNestedField("buildTypes", Fields.NONE, Fields.LONG);
        final String buildTypesLocator = buildTypesFields.getLocator();
        final List<BuildTypeOrTemplate> buildTypes = buildTypeFinder.get().getBuildTypesPaged(project, buildTypesLocator, true).getEntries();
        return new BuildTypes(buildTypes, null, buildTypesFields, beanContext);
    });

    CachingValue<Boolean> canViewSettings = CachingValue.simple(() -> {
      final PermissionChecker permissionChecker = beanContext.getServiceLocator().findSingletonService(PermissionChecker.class);
      assert permissionChecker != null;
      return !shouldRestrictSettingsViewing(project, permissionChecker); //use lazy calculation in order not to have performance impact when no related fields are retrieved
    });

    templates = ValueWithDefault.decideDefault(fields.isIncluded("templates", false), () -> {
        if (!canViewSettings.get()) return null;
        final Fields templateFields = fields.getNestedField("templates", Fields.NONE, Fields.LONG);
        final String templatesLocator = templateFields.getLocator();
        final List<BuildTypeOrTemplate> templates = buildTypeFinder.get().getBuildTypesPaged(project, templatesLocator, false).getEntries();
        return new BuildTypes(templates, null, templateFields, beanContext);
    });

    defaultTemplate = ValueWithDefault.decideDefault(
      fields.isIncluded("defaultTemplate", false),
      () -> !canViewSettings.get()
            ? null
            : getDefaultTemplate(project, fields.getNestedField("defaultTemplate", Fields.NONE, Fields.SHORT), beanContext)
    );

    parameters = ValueWithDefault.decideDefault(
      fields.isIncluded("parameters", false),
      () -> !canViewSettings.get() ? null :
            new Properties(createEntity(project), ProjectRequest.getParametersHref(project), null, fields.getNestedField("parameters", Fields.NONE, Fields.LONG), beanContext)
    );

    vcsRoots = ValueWithDefault.decideDefault(
      fields.isIncluded("vcsRoots", false),
      () -> !canViewSettings.get() ? null : new VcsRoots(
        project.getOwnVcsRoots(), //consistent with VcsRootFinder
        new PagerDataImpl(VcsRootRequest.getHref(project)),
        fields.getNestedField("vcsRoots"),
        beanContext)
    );

    projectFeatures = ValueWithDefault.decideDefault(
      fields.isIncluded("projectFeatures", false),
      () -> {
        if (!canViewSettings.get()) return null;
        Fields nestedFields = fields.getNestedField("projectFeatures", Fields.NONE, Fields.LONG);
        return new PropEntitiesProjectFeature(project, nestedFields.getLocator(), nestedFields, beanContext);
      });

    projects = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("projects", false), () -> {
        final Fields projectsFields = fields.getNestedField("projects", Fields.NONE, Fields.LONG);
        final String projectsLocator = projectsFields.getLocator();
        final ProjectFinder projectFinder = beanContext.getSingletonService(ProjectFinder.class);
        final List<SProject> projects = projectFinder.getItems(project, projectsLocator).getEntries();
        return new Projects(projects, null, projectsFields, beanContext);
    });

    cloudProfiles = ValueWithDefault.decideDefault(fields.isIncluded("cloudProfiles", false, false), () -> {
      final Fields nestedFields = fields.getNestedField("cloudProfiles", Fields.NONE, Fields.LONG);
      String locator = CloudProfileFinder.getLocator(nestedFields.getLocator(), project);

      final CloudProfileFinder finder = beanContext.getSingletonService(CloudProfileFinder.class);
      final List<CloudProfile> items = finder.getItems(locator).getEntries();
      return new CloudProfiles(items, new PagerDataImpl(CloudRequest.getProfilesHref(nestedFields.getLocator(), project)), nestedFields, beanContext);
    });

    // use lazy calculation in order not to have performance impact when no related fields are retrieved
    final CachingValueNullable<SProject> actualParentProject = CachingValueNullable.simple(project::getParentProject);
    parentProject = ValueWithDefault.decideDefault(
      fields.isIncluded("parentProject", false),
      () -> Util.resolveNull(actualParentProject.get(), (v) -> new Project(v, fields.getNestedField("parentProject"), beanContext))
    );

    parentProjectId = ValueWithDefault.decideDefault(
      fields.isIncluded("parentProjectId"),
      () -> Util.resolveNull(actualParentProject.get(), parent -> parent.getExternalId())
    );

    final boolean forceParentAttributes = TeamCityProperties.getBoolean("rest.beans.project.addParentProjectAttributes");
    parentProjectName = ValueWithDefault.decideDefault(forceParentAttributes || fields.isIncluded("parentProjectName", false, false),
                                                       () -> Util.resolveNull(actualParentProject.get(), v -> v.getFullName()));
    parentProjectInternalId = ValueWithDefault.decideDefault(forceParentAttributes || fields.isIncluded("parentProjectInternalId", includeInternal, includeInternal),
                                                             () -> Util.resolveNull(actualParentProject.get(), v -> v.getProjectId()));

  }

  @XmlAttribute(name = "uuid")
  public String getUuid() {
    if (myProject == null || myBeanContext == null) {
      return null;
    }

    return ValueWithDefault.decideDefault(
      myFields.isIncluded("uuid", false, false), () -> {
        return myBeanContext
                 .getSingletonService(PermissionChecker.class)
                 .isPermissionGranted(Permission.EDIT_PROJECT, myProject.getProjectId())
               ? ((ProjectEx)myProject).getId().getConfigId()
               : null;
      }
    );
  }

  @XmlAttribute(name = "virtual")
  public Boolean isVirtual() {
    if (myProject == null || myBeanContext == null) {
      return null;
    }

    return ValueWithDefault.decideDefault(myFields.isIncluded("virtual", false, true), myProject.isVirtual());
  }

  @XmlElement(name = "ancestorProjects")
  public Projects getAncestorProjects() {
    if (myProject == null || myBeanContext == null) {
      return null;
    }

    return ValueWithDefault.decideDefault(
      myFields.isIncluded("ancestorProjects", false, false),
      () -> {
        if (myProject.isRootProject()) {
          return new Projects(Collections.emptyList(), null, myFields.getNestedField("ancestorProjects"), myBeanContext);
        }

        List<SProject> projectPath = myProject.getProjectPath();

        return new Projects(
          projectPath.subList(0, projectPath.size() - 1),
          null,
          myFields.getNestedField("ancestorProjects"),
          myBeanContext
        );
      }
    );
  }

  @Nullable
  public static BuildType getDefaultTemplate(final @NotNull SProject project, final @NotNull Fields fields, final @NotNull BeanContext beanContext) {
    BuildTypeTemplate defaultTemplate = project.getDefaultTemplate();
    if (defaultTemplate == null) return null;
    return new BuildType(new BuildTypeOrTemplate(defaultTemplate).markInherited(project.getOwnDefaultTemplate() == null), fields, beanContext);
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
        } else if (permissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, project.getProjectId())
                   && AuthUtil.adminSpaceAvailable(permissionChecker.getCurrent())) {
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
      if (StringUtil.isEmpty(value)) {
        throw new BadRequestException("Project name cannot be empty.");
      }
      project.setName(value);
      project.schedulePersisting("Project name changed");
      return;
    } else if ("id".equals(field)) {
      if (StringUtil.isEmpty(value)) {
        throw new BadRequestException("Project id cannot be empty.");
      }
      project.setExternalId(value);
      return;
    } else if ("description".equals(field)) {
      project.setDescription(value);
      project.schedulePersisting("Project description changed");
      return;
    } else if ("archived".equals(field)) {
      project.setArchived(Boolean.parseBoolean(value), serviceLocator.getSingletonService(UserFinder.class).getCurrentUser());
      return;
    } else if ("readOnlyUI".equals(field) && TeamCityProperties.getBoolean("rest.projectRequest.allowSetReadOnlyUI")) {
      boolean editable = !Boolean.parseBoolean(value);
      ((ProjectEx)project).setEditable(editable);
      project.schedulePersisting("Project editing is " + (editable ? "enabled" : "disabled"));
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
    if (StringUtil.isEmpty(locatorText)) {
      //find by href for compatibility with 7.0
      if (!StringUtil.isEmpty(href)) {
        locatorText = StringUtil.lastPartOf(href, '/');
      } else {
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

  private static class ProjectEntityWithParameters extends InheritableUserParametersHolderEntityWithParameters implements ParametersPersistableEntity {
    private final SProject myProject;

    public ProjectEntityWithParameters(@NotNull final SProject project) {
      super(project);
      myProject = project;
    }

    public void persist(@NotNull String description) {
      myProject.schedulePersisting(description);
    }
  }
}
