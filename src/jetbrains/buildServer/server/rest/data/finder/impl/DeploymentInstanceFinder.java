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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentInstance;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder.Dimension;

@LocatorResource(value = LocatorName.DEPLOYMENT_INSTANCE,
  extraDimensions = {CommonLocatorDimensionsList.PROPERTY, AbstractFinder.DIMENSION_ITEM},
  baseEntity = "DeploymentInstance",
  examples = {
    "`id:<instanceId>,dashboard:<dashboardLocator>` - find instance with ID `instanceId` under dashboard found by `dashboardLocator`."
  }
)
@JerseyContextSingleton
@Component("restDeploymentInstanceFinder")
public class DeploymentInstanceFinder extends DelegatingFinder<DeploymentInstance> {
  private static final Logger LOG = Logger.getInstance(DeploymentInstanceFinder.class.getName());

  @LocatorDimension("id") private static final Dimension<CloudUtil.InstanceIdData> ID = new Dimension<>("id");
  @LocatorDimension(value = "state", notes = "Current state of deployment.")
  private static final Dimension<DeploymentState> CURRENT_STATE = new Dimension<>("currentState");
  @LocatorDimension(value = "dashboard", format = LocatorName.DEPLOYMENT_DASHBOARD, notes = "Deployment dashboard locator.")
  public static final Dimension<List<DeploymentDashboard>> DASHBOARD = new Dimension<>("dashboard");
  @LocatorDimension(value = "project", format = LocatorName.PROJECT, notes = "Project locator.")
  private static final Dimension<List<SProject>> PROJECT = new Dimension<>("project");
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  private static final Dimension<List<SProject>> AFFECTED_PROJECT = new Dimension<>("affectedProject");

  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final DeploymentDashboardManager myDeploymentDashboardManager;
  @NotNull private final ProjectManager myProjectManager;

  public DeploymentInstanceFinder(
    @NotNull final ServiceLocator serviceLocator,
    @NotNull DeploymentDashboardManager deploymentDashboardManager,
    @NotNull ProjectManager projectManager
  ) {
    myServiceLocator = serviceLocator;
    myDeploymentDashboardManager = deploymentDashboardManager;
    myProjectManager = projectManager;
    setDelegate(new Builder().build());
  }

  @NotNull
  public static String getLocator(@NotNull final DeploymentInstance item) {
    return Locator.getStringLocator(ID.name, item.getId());
  }

  @NotNull
  public static String getLocator(@NotNull final DeploymentDashboard dashboard) {
    return Locator.getStringLocator(
      DASHBOARD.name, DeploymentDashboardFinder.getLocator(dashboard)
    );
  }

  private class Builder extends TypedFinderBuilder<DeploymentInstance> {
    Builder() {
      name("DeploymentInstanceFinder");

      dimensionEnum(CURRENT_STATE, DeploymentState.class)
        .description("current deployment state of an instance")
        .valueForDefaultFilter(
          instance -> instance.getCurrentState()
        );

      dimensionWithFinder(
        DASHBOARD,
        () -> myServiceLocator.getSingletonService(DeploymentDashboardFinder.class),
        "dashboards where instances are published"
      )
        .toItems(dashboards -> dashboards
          .stream()
          .flatMap(
            dashboard -> dashboard.getInstances().values().stream()
          )
          .collect(Collectors.toList())
        );

      dimensionProjects(
        PROJECT,
        "projects defining dashboards containing the instances",
        false
      );

      dimensionProjects(
        AFFECTED_PROJECT,
        "projects where dashboards containing the instances are accessible",
        true
      );

      locatorProvider(DeploymentInstanceFinder::getLocator);
    }

    private void dimensionProjects(Dimension<List<SProject>> affectedProject, String description, boolean includeFromSubprojects) {
      dimensionProjects(affectedProject, myServiceLocator)
        .description(description)
        .valueForDefaultFilter(item -> Collections.singleton(getProjectFromInstance(item)))
        .toItems(
          getDeploymentInstanceListItemsFromDimension(includeFromSubprojects)
        );
    }

    @NotNull
    private ItemsFromDimension<DeploymentInstance, List<SProject>> getDeploymentInstanceListItemsFromDimension(boolean includeFromSubprojects) {
      return projects -> projects
        .stream()
        .flatMap(
          project -> myDeploymentDashboardManager
            .getAllDashboards(project.getProjectId(), includeFromSubprojects)
            .values()
            .stream()
        )
        .flatMap(dashboard -> dashboard.getInstances().values().stream())
        .collect(Collectors.toList());
    }

    @Nullable
    private SProject getProjectFromInstance(DeploymentInstance instance) {
      DeploymentDashboard dashboard = myDeploymentDashboardManager.getDashboard(
        instance.getDashboardId()
      );

      if (dashboard != null) {
        return myProjectManager.findProjectById(
          myDeploymentDashboardManager.getProjectId(dashboard)
        );
      }

      return null;
    }
  }
}

