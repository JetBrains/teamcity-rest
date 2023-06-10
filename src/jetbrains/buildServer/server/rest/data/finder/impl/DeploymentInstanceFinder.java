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
import java.util.Objects;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.FinderImpl;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.util.MultiCheckerFilter;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.PagerData;
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
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.DashboardNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder.Dimension;

@LocatorResource(value = LocatorName.DEPLOYMENT_INSTANCE,
  extraDimensions = {FinderImpl.DIMENSION_ID, PagerData.START, PagerData.COUNT, AbstractFinder.DIMENSION_ITEM},
  baseEntity = "DeploymentInstance",
  examples = {
    "`id:<instanceId>,dashboard:<dashboardLocator>` - find instance with ID `instanceId` under dashboard found by `dashboardLocator`."
  }
)
@JerseyContextSingleton
@Component("restDeploymentInstanceFinder")
public class DeploymentInstanceFinder extends AbstractFinder<DeploymentInstance> {
  private static final Logger LOG = Logger.getInstance(DeploymentInstanceFinder.class.getName());

  @LocatorDimension("id") private static final Dimension<CloudUtil.InstanceIdData> ID = new Dimension<>("id");
  @LocatorDimension(value = "state", notes = "Current state of deployment.")
  private static final Dimension<DeploymentState> CURRENT_STATE = new Dimension<>("currentState");
  @LocatorDimension(value = "dashboard", format = LocatorName.DEPLOYMENT_DASHBOARD, notes = "Deployment dashboard locator.")
  public static final Dimension<List<DeploymentDashboard>> DASHBOARD = new Dimension<>("dashboard");

  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final DeploymentDashboardManager myDeploymentDashboardManager;

  public DeploymentInstanceFinder(
    @NotNull final ServiceLocator serviceLocator,
    @NotNull DeploymentDashboardManager deploymentDashboardManager
  ) {
    myServiceLocator = serviceLocator;
    myDeploymentDashboardManager = deploymentDashboardManager;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull DeploymentInstance deploymentInstance) {
    return Locator.getStringLocator(
      ID.name,
      deploymentInstance.getId(),
      DASHBOARD.name,
      DeploymentDashboardFinder.getLocator(
        Objects.requireNonNull(
          getDashboard(deploymentInstance)
        )
      )
    );
  }

  @NotNull
  public static String getLocator(@NotNull final DeploymentDashboard dashboard) {
    return Locator.getStringLocator(
      DASHBOARD.name, DeploymentDashboardFinder.getLocator(dashboard)
    );
  }

  @NotNull
  @Override
  public ItemHolder<DeploymentInstance> getPrefilteredItems(@NotNull Locator locator) {
    final String dashboardDimension = locator.getSingleDimensionValue(DASHBOARD.name);

    if (dashboardDimension == null) {
      throw new BadRequestException("Dimension 'dashboard' is required");
    }

    DeploymentDashboardFinder dashboardFinder = myServiceLocator.getSingletonService(DeploymentDashboardFinder.class);
    final DeploymentDashboard dashboard = dashboardFinder.getItem(dashboardDimension);

    return ItemHolder.of(
      dashboard.getInstances().values()
    );
  }

  @NotNull
  @Override
  public ItemFilter<DeploymentInstance> getFilter(@NotNull Locator locator) {
    final MultiCheckerFilter<DeploymentInstance> result = new MultiCheckerFilter<DeploymentInstance>();

    String id = locator.getSingleDimensionValue(DIMENSION_ID);
    if (id != null) {
      result.add(item -> id.equals(item.getId()));
    }

    String currentState = locator.getSingleDimensionValue(CURRENT_STATE.name);
    if (currentState != null) {
      result.add(
        item -> DeploymentState.valueOf(currentState) == item.getCurrentState()
      );
    }

    final String dashboardDimension = locator.getSingleDimensionValue(DASHBOARD.name);

    if (dashboardDimension == null) {
      throw new BadRequestException("Dimension 'dashboard' is required");
    }

    DeploymentDashboardFinder dashboardFinder = myServiceLocator.getSingletonService(DeploymentDashboardFinder.class);
    final DeploymentDashboard dashboard = dashboardFinder.getItem(dashboardDimension);
    result.add(
      item -> getDashboard(item).equals(dashboard)
    );

    return result;
  }

  @Nullable
  private DeploymentDashboard getDashboard(DeploymentInstance instance) {
    try {
      return myDeploymentDashboardManager.getDashboard(instance.getDashboardId());
    } catch (DashboardNotFoundException e) {
      return null;
    }
  }
}

