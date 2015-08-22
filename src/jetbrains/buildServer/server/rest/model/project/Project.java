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

package jetbrains.buildServer.server.rest.model.project;

import com.intellij.openapi.util.text.StringUtil;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.buildType.VcsRoots;
import jetbrains.buildServer.server.rest.request.ProjectRequest;
import jetbrains.buildServer.server.rest.request.VcsRootRequest;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "project")
@XmlType(name = "project", propOrder = {"id", "internalId", "name", "href", "description", "archived", "webUrl",
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

  @XmlAttribute
  public String description;

  @XmlAttribute
  public boolean archived;

  @XmlAttribute
  public String webUrl;

  @XmlElement(name = "parentProject")
  public ProjectRef parentProject;

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

  public Project() {
  }

  public Project(final SProject project, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    id = project.getExternalId();
    internalId = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME) ? project.getProjectId() : null;
    name = project.getName();
    href = apiUrlBuilder.getHref(project);
    description = project.getDescription();
    archived = project.isArchived();
    webUrl = dataProvider.getProjectUrl(project);

    final SProject actulParentProject = project.getParentProject();
    parentProject = actulParentProject == null ? null : new ProjectRef(actulParentProject, apiUrlBuilder);
    buildTypes = BuildTypes.createFromBuildTypes(project.getOwnBuildTypes(), dataProvider, apiUrlBuilder);
    if (!shouldRestrictSettingsViewing(project, dataProvider)) {
      templates = BuildTypes.createFromTemplates(project.getOwnBuildTypeTemplates(), dataProvider, apiUrlBuilder);
      parameters = new Properties(project.getParameters());
      vcsRoots = new Href(VcsRootRequest.API_VCS_ROOTS_URL + "?locator=project:(id:" + project.getExternalId() + ")", apiUrlBuilder);
    } else {
      templates = null;
      parameters = null;
      vcsRoots = null;
    }
    projects = new Projects(project.getOwnProjects(), apiUrlBuilder);
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

  public static void setFieldValue(final SProject project, final String field, final String value, @NotNull final DataProvider dataProvider) {
    if ("name".equals(field)) {
      if (StringUtil.isEmpty(value)){
        throw new BadRequestException("Project name cannot be empty.");
      }
      project.setName(value);
      return;
    } else if ("id".equals(field)) {
      if (StringUtil.isEmpty(value)){
        throw new BadRequestException("Project id cannot be empty.");
      }
      project.setExternalId(value);
      return;
    } else if ("description".equals(field)) {
      project.setDescription(value);
      return;
    } else if ("archived".equals(field)) {
      project.setArchived(Boolean.valueOf(value), dataProvider.getCurrentUser());
      return;
    }
    throw new BadRequestException("Setting field '" + field + "' is not supported. Supported are: name, description, archived");
  }

  public static boolean shouldRestrictSettingsViewing(final @NotNull BuildProject project, final @NotNull DataProvider permissionChecker) {
    if (TeamCityProperties.getBooleanOrTrue("rest.beans.project.checkPermissions")) {
      return !permissionChecker.isPermissionGranted(Permission.VIEW_BUILD_CONFIGURATION_SETTINGS, project.getProjectId());
    }
    return false;
  }
}
