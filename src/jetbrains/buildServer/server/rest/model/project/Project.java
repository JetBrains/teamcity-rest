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
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "project")
@SuppressWarnings("PublicField")
public class Project extends ProjectRef {
  @XmlAttribute
  public String description;

  @XmlAttribute
  public boolean archived;

  @XmlAttribute
  public String webUrl;

  @XmlElement
  public BuildTypes buildTypes;

  @XmlElement
  public BuildTypes templates;

  @XmlElement
  public Properties parameters;

  public Project() {
  }

  public Project(final SProject project, final DataProvider dataProvider, final ApiUrlBuilder apiUrlBuilder) {
    super(project, apiUrlBuilder);
    description = project.getDescription();
    archived = project.isArchived();
    webUrl = dataProvider.getProjectUrl(project);
    buildTypes = BuildTypes.createFromBuildTypes(project.getOwnBuildTypes(), dataProvider, apiUrlBuilder);
    templates = BuildTypes.createFromTemplates(project.getOwnBuildTypeTemplates(), dataProvider, apiUrlBuilder);
    parameters = new Properties(project.getParameters());
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
    }
    throw new NotFoundException("Field '" + field + "' is not supported.");
  }

  public static void setFieldValue(final SProject project, final String field, final String value, @NotNull final DataProvider dataProvider) {
    if ("name".equals(field)) {
      if (StringUtil.isEmpty(value)){
        throw new BadRequestException("Project name cannot be empty.");
      }
      project.setName(value);
      return;
    } else if ("id".equals(field)) {
      //todo: (TeamCity) open API How to set project external id via open API?
      if (StringUtil.isEmpty(value)){
        throw new BadRequestException("Project external id cannot be empty.");
      }
      ((ProjectEx)project).setExternalId(value);
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
}
