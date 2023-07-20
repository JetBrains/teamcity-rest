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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentInstance;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentState;
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.DashboardNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.syntax.DeploymentInstanceDimensions.*;

@JerseyContextSingleton
@Component("restDeploymentInstanceFinder")
public class DeploymentInstanceFinder extends DelegatingFinder<DeploymentInstance> implements FinderLocatorDefinition {
  @NotNull private final DeploymentDashboardFinder myDeploymentDashboardFinder;
  @NotNull private final DeploymentDashboardManager myDeploymentDashboardManager;

  public DeploymentInstanceFinder(
    @NotNull final DeploymentDashboardFinder deploymentDashboardFinder,
    @NotNull DeploymentDashboardManager deploymentDashboardManager
  ) {
    myDeploymentDashboardFinder = deploymentDashboardFinder;
    myDeploymentDashboardManager = deploymentDashboardManager;
    setDelegate(new Builder().build());
  }

  @NotNull
  public static String getItemLocator(
    @NotNull DeploymentInstance deploymentInstance,
    @NotNull DeploymentDashboardManager deploymentDashboardManager
  ) {
    return Locator.getStringLocator(
      ID.getName(),
      deploymentInstance.getId(),
      DASHBOARD.getName(),
      DeploymentDashboardFinder.getLocator(
        Objects.requireNonNull(
          getDashboard(deploymentInstance, deploymentDashboardManager)
        )
      )
    );
  }

  @NotNull
  public static String getLocator(
    @NotNull final DeploymentInstance instance,
    @NotNull final DeploymentDashboardManager deploymentDashboardManager
  ) {
    try {
      DeploymentDashboard dashboard = deploymentDashboardManager.getDashboard(
        instance.getDashboardId()
      );

      return Locator.getStringLocator(
        ID.getName(), instance.getId(),
        DASHBOARD.getName(), DeploymentDashboardFinder.getLocator(dashboard)
      );
    } catch (DashboardNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  private static DeploymentDashboard getDashboard(
    @NotNull DeploymentInstance instance,
    @NotNull DeploymentDashboardManager deploymentDashboardManager
  ) {
    try {
      return deploymentDashboardManager.getDashboard(instance.getDashboardId());
    } catch (DashboardNotFoundException e) {
      return null;
    }
  }

  private class Builder extends TypedFinderBuilder<DeploymentInstance> {
    Builder() {
      name("DeploymentInstanceFinder");

      dimensionString(ID)
        .filter((value, item) -> value.equals(item.getId()));

      dimensionEnum(CURRENT_STATE, DeploymentState.class)
        .valueForDefaultFilter(item -> item.getCurrentState());

      dimensionWithFinder(DASHBOARD, () -> myDeploymentDashboardFinder, "Deployment dashboard finder")
        .filter((value, item) -> hasInstanceInDashboards(value, item))
        .toItems(dashboards ->
                   getInstancesFromDashboards(dashboards)
        );

      locatorProvider(
        image -> getLocator(image, myDeploymentDashboardManager)
      );
    }

    private boolean hasInstanceInDashboards(List<DeploymentDashboard> value, DeploymentInstance item) {
      return value
        .stream()
        .anyMatch(
          dashboard -> dashboard
            .getInstances()
            .values()
            .contains(item)
        );
    }

    @NotNull
    private List<DeploymentInstance> getInstancesFromDashboards(List<DeploymentDashboard> dashboards) {
      return dashboards
        .stream()
        .flatMap(
          dashboard -> dashboard
            .getInstances()
            .values()
            .stream()
        )
        .collect(Collectors.toList());
    }
  }
}

