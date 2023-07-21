/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.impl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.CloudUtil;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.FinderImpl;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.DashboardNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.syntax.DeploymentDashboardDimensions.*;

@JerseyContextSingleton
@Component("restDeploymentDashboardFinder")
public class DeploymentDashboardFinder extends DelegatingFinder<DeploymentDashboard> {
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final DeploymentDashboardManager myDeploymentDashboardManager;
  @NotNull private final ProjectManager myProjectManager;

  public DeploymentDashboardFinder(
    @NotNull final ServiceLocator serviceLocator,
    @NotNull final ProjectFinder projectFinder,
    @NotNull DeploymentDashboardManager deploymentDashboardManager,
    @NotNull final ProjectManager projectManager
  ) {
    myServiceLocator = serviceLocator;
    myProjectFinder = projectFinder;
    myDeploymentDashboardManager = deploymentDashboardManager;
    myProjectManager = projectManager;
    setDelegate(new Builder().build());
  }

  @NotNull
  public static String getLocator(@NotNull final DeploymentDashboard item) {
    return Locator.getStringLocator(
      FinderImpl.DIMENSION_ID,
      item.getId()
    );
  }

  @NotNull
  public static String getLocator(@Nullable String baseLocator, @NotNull final SProject project) {
    if (baseLocator != null && (new Locator(baseLocator)).isSingleValue()) {
      return baseLocator;
    }
    return Locator.setDimensionIfNotPresent(baseLocator, PROJECT, ProjectFinder.getLocator(project));
  }

  @NotNull
  private ItemHolder<DeploymentDashboard> getDeploymentDashboadsByProject(String affectedProjectDimension, boolean includeFromSubprojects) {
    @NotNull final SProject project = myProjectFinder.getItem(affectedProjectDimension);
    return ItemHolder.of(
      myDeploymentDashboardManager
        .getAllDashboards(project.getExternalId(), includeFromSubprojects)
        .values()
    );
  }

  private Boolean affectedProjectFilter(
    @NotNull SProject parentProject,
    @NotNull DeploymentDashboard dashboard
  ) {
    SProject dashboardProject = getProject(dashboard);

    if (dashboardProject == null) {
      return false;
    }

    return ProjectFinder.isSameOrParent(parentProject, dashboardProject);
  }

  @Nullable
  private SProject getProject(@NotNull DeploymentDashboard dashboard) {
    return myProjectManager.findProjectByExternalId(dashboard.getProjectExtId());
  }

  private class Builder extends TypedFinderBuilder<DeploymentDashboard> {
    Builder() {
      name("DeploymentDashboardFinder");

      dimensionString(ID)
        .filter((value, item) -> value.equals(item.getId()))
        .toItems(
          this::getDeploymentDashboardByIdDimension
        );

      dimensionString(NAME)
        .filter((value, item) -> value.equals(item.getName()));

      dimensionProjects(PROJECT, myServiceLocator)
        .filter(
          (projects, item) ->
            filterProjects(projects, item)
        )
        .toItems(
          projects -> getDeploymentDashboardsForProjects(projects, false)
        );

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator)
        .filter(
          (projects, item) ->
            filterProjects(projects, item)
        )
        .toItems(
          projects -> getDeploymentDashboardsForProjects(projects, true)
        );

      fallbackItemRetriever(
        dimensions -> ItemHolder.of(
          myDeploymentDashboardManager.getAllDashboards().values()
        )
      );

      locatorProvider(
        DeploymentDashboardFinder::getLocator
      );
    }

    @NotNull
    private List<DeploymentDashboard> getDeploymentDashboardByIdDimension(String dimension) {
      try {
        return Collections.singletonList(
          myDeploymentDashboardManager.getDashboard(dimension)
        );
      } catch (DashboardNotFoundException e) {
        return Collections.emptyList();
      }
    }

    @NotNull
    private Boolean filterProjects(List<SProject> projects, DeploymentDashboard item) {
      return Util.resolveNull(
        myProjectManager.findProjectByExternalId(item.getProjectExtId()),
        p -> CloudUtil.containProjectOrParent(projects, p),
        false
      );
    }

    @NotNull
    private List<DeploymentDashboard> getDeploymentDashboardsForProjects(List<SProject> projects, boolean includeFromSubprojects) {
      return projects
        .stream()
        .flatMap(
          project -> myDeploymentDashboardManager.getAllDashboards(project.getExternalId(), includeFromSubprojects).values().stream()
        )
        .collect(Collectors.toList());
    }
  }
}

