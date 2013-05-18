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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.CopyOptionsDescription;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.ProjectManagerEx;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 04.01.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "newProjectDescription")
public class NewProjectDescription extends CopyOptionsDescription{
  public NewProjectDescription() {
  }

  public NewProjectDescription(final String name, final String id, final ProjectRef sourceProject, final ProjectRef parentProject, final Boolean copyAllAssociatedSettings) {
    this.copyAllAssociatedSettings = copyAllAssociatedSettings;
    this.name = name;
    this.id = id;
    this.sourceProject = sourceProject;
    this.parentProject = parentProject;
  }

  @XmlAttribute public String name;
  /**
   * Project external id.
   */
  @XmlAttribute public String id;

  /**
   * @deprecated Use 'sourceProject' intead.
   */
  @XmlAttribute public String sourceProjectLocator;

  @XmlElement(name = "sourceProject")
  public ProjectRef sourceProject;

  @XmlElement(name = "parentProject")
  public ProjectRef parentProject;

  @Nullable
  public SProject getSourceProject(@NotNull final ServiceLocator serviceLocator) {
    if (sourceProject == null) {
      if (StringUtil.isEmpty(sourceProjectLocator)) {
        return null;
      } else {
        return serviceLocator.getSingletonService(ProjectFinder.class).getProject(sourceProjectLocator);
      }
    }
    if (!StringUtil.isEmpty(sourceProjectLocator)) {
      throw new BadRequestException("Both 'sourceProject' and 'sourceProjectLocator' are specified. Please use only the former.");
    }
    return sourceProject.getProjectFromPosted(serviceLocator.getSingletonService(ProjectFinder.class));
  }

  @NotNull
  public SProject getParentProject(@NotNull final ServiceLocator serviceLocator) {
    if (parentProject == null){
      return serviceLocator.getSingletonService(ProjectManager.class).getRootProject();
    }
    return parentProject.getProjectFromPosted(serviceLocator.getSingletonService(ProjectFinder.class));
  }

  @NotNull
  public String getId(@NotNull final ServiceLocator serviceLocator) {
    if (id != null){
      return id;
    }
    if (name == null){
      throw new BadRequestException("'name' and 'id' should not be empty at the same time.");
    }
    return ((ProjectManagerEx)serviceLocator.getSingletonService(ProjectManager.class)).getProjectIdentifiersManager().generateNewExternalId(getParentProject(serviceLocator).getExternalId(), name);
  }
}
