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

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.FinderImpl;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.util.MultiCheckerFilter;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.DashboardNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder.Dimension;

@LocatorResource(value = LocatorName.DEPLOYMENT_DASHBOARD,
  extraDimensions = {FinderImpl.DIMENSION_ID, PagerData.START, PagerData.COUNT, AbstractFinder.DIMENSION_ITEM},
  baseEntity = "DeploymentDashboard",
  examples = {
    "`id:<dashboardId>` - find dashboard with ID `dashboardId`.",
    "`project:<projectLocator>` - find all deployment dashboards under project found by `projectLocator`."
  }
)
@JerseyContextSingleton
@Component("restDeploymentDashboardFinder")
public class DeploymentDashboardFinder extends AbstractFinder<DeploymentDashboard> {
  private static final Logger LOG = Logger.getInstance(DeploymentDashboardFinder.class.getName());
  @LocatorDimension("name") private static final Dimension<ValueCondition> NAME = new Dimension<>("name");
  @LocatorDimension(value = "project", format = LocatorName.PROJECT, notes = "Project locator.")
  private static final Dimension<List<SProject>> PROJECT = new Dimension<>("project");
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  private static final Dimension<List<SProject>> AFFECTED_PROJECT = new Dimension<>("affectedProject");

  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final DeploymentDashboardManager myDeploymentDashboardManager;
  @NotNull private final ProjectManager myProjectManager;

  public DeploymentDashboardFinder(@NotNull final ServiceLocator serviceLocator,
                                   @NotNull DeploymentDashboardManager deploymentDashboardManager,
                                   @NotNull final ProjectManager projectManager) {
    myServiceLocator = serviceLocator;
    myDeploymentDashboardManager = deploymentDashboardManager;
    myProjectManager = projectManager;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull DeploymentDashboard deploymentDashboard) {
    return getLocator(deploymentDashboard);
  }

  @NotNull
  public static String getLocator(@NotNull final DeploymentDashboard item) {
    return Locator.getStringLocator(
      DIMENSION_ID,
      item.getId()
    );
  }

  @NotNull
  public static String getLocator(@Nullable String baseLocator, @NotNull final SProject project) {
    if (baseLocator != null && (new Locator(baseLocator)).isSingleValue()) {
      return baseLocator;
    }
    return Locator.setDimensionIfNotPresent(baseLocator, PROJECT.name, ProjectFinder.getLocator(project));
  }

  @NotNull
  @Override
  public ItemHolder<DeploymentDashboard> getPrefilteredItems(@NotNull Locator locator) {
    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT.name);
    if (affectedProjectDimension != null) {
      return getDeploymentDashboadsByProject(affectedProjectDimension, true);
    }

    final String projectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT.name);
    if (projectDimension != null) {
      return getDeploymentDashboadsByProject(projectDimension, false);
    }

    return ItemHolder.of(
      myDeploymentDashboardManager
        .getAllDashboards()
        .values()
    );
  }

  @NotNull
  private ItemHolder<DeploymentDashboard> getDeploymentDashboadsByProject(String affectedProjectDimension, boolean includeFromSubprojects) {
    ProjectFinder projectFinder = myServiceLocator.getSingletonService(ProjectFinder.class);
    @NotNull final SProject project = projectFinder.getItem(affectedProjectDimension);
    return ItemHolder.of(
      myDeploymentDashboardManager
        .getAllDashboards(project.getExternalId(), includeFromSubprojects)
        .values()
    );
  }

  @NotNull
  @Override
  public ItemFilter<DeploymentDashboard> getFilter(@NotNull Locator locator) {
    final MultiCheckerFilter<DeploymentDashboard> result = new MultiCheckerFilter<DeploymentDashboard>();

    String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (id != null) {
      result.add(item -> id.equals(item.getId()));
    }

    String name = locator.getSingleDimensionValue(NAME.name);
    if (name != null) {
      result.add(item -> name.equals(item.getName()));
    }

    final String projectDimension = locator.getSingleDimensionValue(PROJECT.name);
    if (projectDimension != null) {
      ProjectFinder projectFinder = myServiceLocator.getSingletonService(ProjectFinder.class);
      final SProject project = projectFinder.getItem(projectDimension);
      result.add(
        item -> getProject(item) == project
      );
    }

    final String affectedProjectDimension = locator.getSingleDimensionValue(AFFECTED_PROJECT.name);
    if (affectedProjectDimension != null) {
      ProjectFinder projectFinder = myServiceLocator.getSingletonService(ProjectFinder.class);
      @NotNull final SProject parentProject = projectFinder.getItem(affectedProjectDimension);
      result.add(
        item -> affectedProjectFilter(parentProject, item)
      );
    }

    return result;
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
    ProjectManager projectManager = myServiceLocator.getSingletonService(ProjectManager.class);
    return projectManager.findProjectByExternalId(dashboard.getProjectExtId());
  }

  @Nullable
  @Override
  public DeploymentDashboard findSingleItem(@NotNull Locator locator) {
    String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (id != null) {
      try {
        return myDeploymentDashboardManager.getDashboard(id);
      } catch (DashboardNotFoundException e) {
        throw new NotFoundException(e.getMessage(), e);
      }
    }

    return super.findSingleItem(locator);
  }
}

