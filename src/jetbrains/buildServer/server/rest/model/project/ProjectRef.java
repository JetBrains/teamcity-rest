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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
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

  public ProjectRef(@NotNull final SProject project, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    id = project.getExternalId();
    internalId = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME) ? project.getProjectId() : null;
    name = project.getName();
    href = apiUrlBuilder.getHref(project);
  }

  public ProjectRef(@Nullable final String externalId, @Nullable final String internalId, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    id = externalId;
    this.internalId = internalId; //todo: check usages: externalId should actually be NotNull and internal id should never be necessary
  }

  @NotNull
  public SProject getProjectFromPosted(@NotNull ProjectFinder projectFinder) {
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
      throw new BadRequestException("No project specified. Either 'id', 'internalId' or 'locator' attribute should be present.");
    }
    return projectFinder.getProject(locatorText);
  }
}