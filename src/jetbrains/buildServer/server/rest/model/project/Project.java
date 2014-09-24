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

package jetbrains.buildServer.server.rest.model.project;

import com.intellij.openapi.util.text.StringUtil;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.request.ProjectRequest;
import jetbrains.buildServer.server.rest.request.VcsRootRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.WebLinks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "project")
@XmlType(name = "project", propOrder = {"id", "internalId", "name", "parentProjectId", "parentProjectInternalId", "parentProjectName", "archived", "description", "href", "webUrl",
  "parentProject", "buildTypes", "templates", "parameters", "vcsRoots", "projects"})
@SuppressWarnings("PublicField")
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

  @XmlAttribute
  public String webUrl;

  @XmlElement(name = "parentProject")
  public Project parentProject;

  @XmlElement
  public BuildTypes buildTypes;

  @XmlElement
  public BuildTypes templates;

  @XmlElement
  public Properties parameters;

  @XmlElement (name = "vcsRoots")
  public Href vcsRoots;

  @XmlElement (name = "projects")
  public Projects projects;

  /**
   * This is used only when posting a link to a project
   */
  @XmlAttribute public String locator;

  public Project() {
  }

  public Project(@NotNull final SProject project, final @NotNull Fields fields, @NotNull final BeanContext beanContext) {
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), project.getExternalId());
    final boolean includeInternal = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME);
    internalId = ValueWithDefault.decideDefault(fields.isIncluded("internalId", includeInternal, includeInternal), project.getProjectId());
    name = ValueWithDefault.decideDefault(fields.isIncluded("name"), project.getName());

    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().getHref(project));
    webUrl = ValueWithDefault.decideDefault(fields.isIncluded("webUrl"), beanContext.getSingletonService(WebLinks.class).getProjectPageUrl(project.getExternalId()));

    final SProject actulParentProject = project.getParentProject();
    final String descriptionText = project.getDescription();
    description = ValueWithDefault.decideDefault(fields.isIncluded("description"), StringUtil.isEmpty(descriptionText) ? null : descriptionText);
    archived = ValueWithDefault.decideDefault(fields.isIncluded("archived"), project.isArchived());

    parentProject = actulParentProject == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("parentProject", false), new ValueWithDefault.Value<Project>() {
      public Project get() {
        return new Project(actulParentProject, fields.getNestedField("parentProject"), beanContext);
      }
    });
    buildTypes = ValueWithDefault.decideDefault(fields.isIncluded("buildTypes", false), new ValueWithDefault.Value<BuildTypes>() {
      public BuildTypes get() {
        return new BuildTypes(BuildTypes.fromBuildTypes(project.getOwnBuildTypes()), null, fields.getNestedField("buildTypes", Fields.NONE, Fields.LONG), beanContext);
      }
    });
    templates = ValueWithDefault.decideDefault(fields.isIncluded("templates", false), new ValueWithDefault.Value<BuildTypes>() {
      public BuildTypes get() {
        return new BuildTypes(BuildTypes.fromTemplates(project.getOwnBuildTypeTemplates()), null, fields.getNestedField("templates", Fields.NONE, Fields.LONG), beanContext);
      }
    });
    parameters = ValueWithDefault.decideDefault(fields.isIncluded("parameters", false), new ValueWithDefault.Value<Properties>() {
      public Properties get() {
        return new Properties(project.getParametersCollection(),project.getOwnParametersCollection(), ProjectRequest.getParametersHref(project),
                              fields.getNestedField("parameters", Fields.NONE, Fields.LONG), beanContext.getServiceLocator());
      }
    });
    vcsRoots = ValueWithDefault.decideDefault(fields.isIncluded("vcsRoots", false), new ValueWithDefault.Value<Href>() {
      public Href get() {
        return new Href(VcsRootRequest.API_VCS_ROOTS_URL + "?locator=project:(id:" + project.getExternalId() + ")", beanContext.getApiUrlBuilder());
      }
    });

    projects = ValueWithDefault.decideDefault(fields.isIncluded("projects", false), new ValueWithDefault.Value<Projects>() {
      public Projects get() {
        return new Projects(project.getOwnProjects(), null, fields.getNestedField("projects", Fields.NONE, Fields.LONG), beanContext);
      }
    });

    parentProjectId = ValueWithDefault.decideDefault(fields.isIncluded("parentProjectId"), actulParentProject == null ? null : actulParentProject.getExternalId());

    final boolean forceParentAttributes = TeamCityProperties.getBoolean("rest.beans.project.addParentProjectAttributes");
    parentProjectName = actulParentProject == null
                        ? null
                        : ValueWithDefault.decideDefault(fields.isIncluded("parentProjectName", false, false) || forceParentAttributes, actulParentProject.getFullName());
    parentProjectInternalId = actulParentProject == null
                              ? null
                              : ValueWithDefault.decideDefault(forceParentAttributes || fields.isIncluded("parentProjectInternalId", includeInternal, includeInternal),
                                                               actulParentProject.getProjectId());
  }

  public Project(@Nullable final String externalId, @Nullable final String internalId, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    id = externalId;
    this.internalId = internalId; //todo: check usages: externalId should actually be NotNull and internal id should never be necessary
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
    } else if ("archived".equals(field)) {
      return String.valueOf(project.isArchived());
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

  public static void setFieldValueAndPersist(final SProject project, final String field, final String value, @NotNull final DataProvider dataProvider) {
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
      project.setArchived(Boolean.valueOf(value), dataProvider.getCurrentUser());
      return;
    }
    throw new BadRequestException("Setting field '" + field + "' is not supported. Supported are: name, description, archived");
  }

  @NotNull
  public SProject getProjectFromPosted(@NotNull ProjectFinder projectFinder) {
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
    if (jetbrains.buildServer.util.StringUtil.isEmpty(locatorText)){
      //find by href for compatibility with 7.0
      if (!jetbrains.buildServer.util.StringUtil.isEmpty(href)){
        return projectFinder.getProject(jetbrains.buildServer.util.StringUtil.lastPartOf(href, '/'));
      }
      throw new BadRequestException("No project specified. Either 'id', 'internalId' or 'locator' attribute should be present.");
    }
    return projectFinder.getProject(locatorText);
  }
}
