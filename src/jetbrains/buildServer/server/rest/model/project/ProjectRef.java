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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "project-ref")
@XmlType(name = "project-ref", propOrder = {"id", "internalId", "name", "href"})
public class ProjectRef {
  @XmlAttribute
  public String id;

  @XmlAttribute
  public String internalId;

  @XmlAttribute
  public String name;

  @XmlAttribute
  public String href;
  /**
   * This is used only when posting a link to a build type.
   */
  @XmlAttribute public String locator;

  public ProjectRef() {
  }

  public ProjectRef(SProject project, final ApiUrlBuilder apiUrlBuilder) {
    id = project.getExternalId();
    internalId = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME) ? project.getProjectId() : null;
    name = project.getName();
    href = apiUrlBuilder.getHref(project);
  }

  @Nullable
  public String getInternalIdFromPosted(@NotNull final BeanContext context) {
    if (internalId != null) {
      if (id == null) {
        return internalId;
      }
      String internalByExternal = context.getSingletonService(ProjectManager.class).getProjectInternalIdByExternalId(id);
      if (internalByExternal == null || internalId.equals(internalByExternal)) {
        return internalId;
      }
      throw new BadRequestException("Both id '" + id + "' and internal id '" + internalId + "' attributes are present and they reference different projects.");
    }
    if (id != null) {
      return context.getSingletonService(ProjectManager.class).getProjectInternalIdByExternalId(id);
    }
    if (locator != null){
      return context.getSingletonService(ProjectFinder.class).getProject(locator).getProjectId();
    }
    throw new BadRequestException("Could not find project by the data. Either 'id' or 'internalId' or 'locator' attributes should be specified.");
  }

}