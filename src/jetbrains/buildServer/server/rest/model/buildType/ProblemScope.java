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

package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Collection;
import java.util.Collections;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.mute.MuteScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 31.01.14
 */
@SuppressWarnings("PublicField")
@XmlType(name = "problemScope")
public class ProblemScope {
  @XmlElement public Project project;
  @XmlElement public BuildTypes buildTypes;

  /**
   * Used only in compatibility mode
   *
   * @deprecated
   */
  @XmlElement public BuildType buildType;

  public ProblemScope() {
  }

  public ProblemScope(@NotNull final InvestigationWrapper investigation,
                      @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    if (investigation.getBuildTypeRE() != null) {
      buildTypes = ValueWithDefault.decideDefault(fields.isIncluded("buildTypes"), new ValueWithDefault.Value<BuildTypes>() {
        public BuildTypes get() {
          final BuildTypeOrTemplate buildType = new BuildTypeOrTemplate((SBuildType)investigation.getBuildTypeRE().getBuildType());
          return new BuildTypes(Collections.singletonList(buildType),
                                null, fields.getNestedField("buildTypes", Fields.NONE, Fields.LONG), beanContext);  //TeamCity open API issue: cast;
        }
      });
      //support for pre-8.1
      if (TeamCityProperties.getBoolean(Investigation.REST_BEANS_INVESTIGATIONS_COMPATIBILITY)) {
        buildType = new BuildType(new BuildTypeOrTemplate((SBuildType)investigation.getBuildTypeRE().getBuildType()), Fields.SHORT, beanContext);
      }
    } else {
      final BuildProject assignmentProject = investigation.getAssignmentProject();
      if (assignmentProject != null) {
        project = ValueWithDefault.decideDefault(fields.isIncluded("project"), new ValueWithDefault.Value<Project>() {
          public Project get() {
            return new Project((SProject)assignmentProject, fields.getNestedField("project"), beanContext); //TeamCity open API issue: cast;
          }
        });
      }
    }
  }

  public ProblemScope(@NotNull final MuteScope scope,
                      @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    switch (scope.getScopeType()) {
      case IN_ONE_BUILD:
        // seems like it makes no sense to expose this here
        break;
      case IN_CONFIGURATION:
        buildTypes = ValueWithDefault.decideDefault(fields.isIncluded("buildTypes"), new ValueWithDefault.Value<BuildTypes>() {
          public BuildTypes get() {
            final Collection<String> buildTypeIds = scope.getBuildTypeIds();
            final ProjectManager projectManager = beanContext.getSingletonService(ProjectManager.class);
            return buildTypeIds == null ? null : new BuildTypes(BuildTypes.fromBuildTypes(BuildTypeFinder.getBuildTypesByInternalIds(buildTypeIds, projectManager)),
                                                                null, fields.getNestedField("buildTypes", Fields.NONE, Fields.LONG), beanContext);
          }
        });
        break;
      case IN_PROJECT:
        project = ValueWithDefault.decideDefault(fields.isIncluded("project"), new ValueWithDefault.Value<Project>() {
          public Project get() {
            final String projectId = scope.getProjectId();
            return projectId == null
                   ? null
                   : new Project(ProjectFinder.getProjectByInternalId(projectId, beanContext.getSingletonService(ProjectManager.class)),
                                 fields.getNestedField("project"), beanContext);
          }
        });
        break;
      default:
        //unsupported scope
    }
  }
}
