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

package jetbrains.buildServer.server.rest.model.problem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.mute.MuteScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlType(name = "mute"/*, propOrder = {"id",
  "project"}*/)
public class Mute {
  @XmlAttribute public long id;

  @XmlElement public Project boundingProject; //todo:review whether this is necessary, might also be the same in Problem
  @XmlElement public Comment comment;

  @XmlElement public Projects projects;
  @XmlElement public BuildTypes buildTypes;

  public Mute() {
  }

  public Mute(final @NotNull MuteInfo item, final @NotNull BeanContext beanContext) {
    final Fields fields = Fields.LONG;

    id = item.getId();

    final SProject projectById = beanContext.getSingletonService(ProjectManager.class).findProjectById(item.getProjectId());
    if (projectById != null) {
      boundingProject = new Project(projectById, fields.getNestedField("project"), beanContext);
    } else {
      boundingProject = new Project(null, item.getProjectId(), beanContext.getApiUrlBuilder());
    }
    comment = new Comment(item.getMutingUser(), item.getMutingTime(), item.getMutingComment(), beanContext.getApiUrlBuilder());

    final MuteScope scope = item.getScope();
    switch (scope.getScopeType()) {
      case IN_ONE_BUILD:
        // seems like it makes no sense to expose this here
        //todo: should be expose on the build level
        break;
      case IN_CONFIGURATION:
        buildTypes =
          new BuildTypes(BuildTypes.fromBuildTypes(getBuildTypesByInternalIds(scope.getBuildTypeIds(), beanContext)), fields.getNestedField("buildTypes", Fields.NONE, Fields.LONG),
                         beanContext);
        break;
      case IN_PROJECT:
        projects = new Projects(Collections.singletonList(getProjectByInternalId(scope.getProjectId(), beanContext)), fields.getNestedField("projects", Fields.NONE, Fields.LONG),
                                beanContext);
        break;
      default:
        //unsupported scope
        //todo: report it somehow
    }
  }

  private List<SBuildType> getBuildTypesByInternalIds(final Collection<String> buildTypeIds, final BeanContext beanContext) {
    final ArrayList<SBuildType> result = new ArrayList<SBuildType>(buildTypeIds.size());
    for (String buildTypeId : buildTypeIds) {
      final SBuildType buildType = getBuildTypeByInternalId(buildTypeId, beanContext);
      result.add(buildType);
    }
    return result;
  }

  // consider moving into utility class/finder
  @NotNull
  private SBuildType getBuildTypeByInternalId(final String buildTypeInternalId, final BeanContext beanContext) {
    final SBuildType result = beanContext.getSingletonService(ProjectManager.class).findBuildTypeById(buildTypeInternalId);
    if (result == null) {
      throw new NotFoundException("No buildType found by internal id '" + buildTypeInternalId + "'.");
    }
    return result;
  }

  // consider moving into utility class/finder
  @NotNull
  private SProject getProjectByInternalId(final String projectInternalId, final BeanContext beanContext) {
    final SProject project = beanContext.getSingletonService(ProjectManager.class).findProjectById(projectInternalId);
    if (project == null) {
      throw new NotFoundException("No project found by internal id '" + projectInternalId + "'.");
    }
    return project;
  }
}
