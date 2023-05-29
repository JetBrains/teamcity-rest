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
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.data.CloudUtil;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ValueCondition;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder.Dimension;

@LocatorResource(value = LocatorName.DEPLOYMENT_DASHBOARD,
  extraDimensions = {CommonLocatorDimensionsList.PROPERTY, AbstractFinder.DIMENSION_ITEM},
  baseEntity = "DeploymentDashboard",
  examples = {
    "`id:<dashboardId>` - find dashboard with ID `dashboardId`.",
    "`project:<projectLocator>` - find all deployment dashboards under project found by `projectLocator`."
  }
)
@JerseyContextSingleton
@Component("restDeploymentDashboardFinder")
public class DeploymentDashboardFinder extends DelegatingFinder<DeploymentDashboard> {
  private static final Logger LOG = Logger.getInstance(DeploymentDashboardFinder.class.getName());

  @LocatorDimension("id") public static final Dimension<String> ID = new Dimension<>("id");
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
    setDelegate(new Builder().build());
  }

  @NotNull
  public static String getLocator(@NotNull final DeploymentDashboard item) {
    return Locator.getStringLocator(
      ID.name, item.getId()
    );
  }

  @NotNull
  public static String getLocator(@Nullable String baseLocator, @NotNull final SProject project) {
    if (baseLocator != null && (new Locator(baseLocator)).isSingleValue()) {
      return baseLocator;
    }
    return Locator.setDimensionIfNotPresent(baseLocator, PROJECT.name, ProjectFinder.getLocator(project));
  }

  private class Builder extends TypedFinderBuilder<DeploymentDashboard> {
    Builder() {
      name("DeploymentDashboardFinder");

      dimensionString(ID)
        .description("dashboard id")
        .filter((value, item) -> value.equals(item.getId()))
        .toItems(
           dimension -> Util.resolveNull(
             myDeploymentDashboardManager.getDashboard(dimension),
             Collections::singletonList,
             Collections.emptyList()
           )
         );

      dimensionValueCondition(NAME)
        .description("dashboard name")
        .valueForDefaultFilter(DeploymentDashboard::getName);

      dimensionProjects(PROJECT, myServiceLocator)
        .description("projects where dashboards are defined")
        .valueForDefaultFilter(
          item -> Util.resolveNull(
            myProjectManager.findProjectById(
              myDeploymentDashboardManager.getProjectId(item)
            ),
            p -> Collections.singleton(p),
            Collections.emptySet()
          )
        )
        .toItems(
          getDeploymentDashboardListItemsFromDimension(false)
        );

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator)
        .description("projects where dashboards are accessible")
          .filter(
            (projects, item) -> Util.resolveNull(
              myProjectManager.findProjectById(
                myDeploymentDashboardManager.getProjectId(item)
              ),
              p -> CloudUtil.containProjectOrParent(projects, p),
              false
            )
          )
          .toItems(
            getDeploymentDashboardListItemsFromDimension(true)
          );

      multipleConvertToItemHolder(
        DimensionCondition.ALWAYS,
        dimensions -> ItemHolder.of(
          myDeploymentDashboardManager.getAllDashboards().values()
        )
      );

      locatorProvider(DeploymentDashboardFinder::getLocator);
    }

    @NotNull
    private ItemsFromDimension<DeploymentDashboard, List<SProject>> getDeploymentDashboardListItemsFromDimension(boolean includeFromSubprojects) {
      return projects -> projects
        .stream()
        .flatMap(
          project -> myDeploymentDashboardManager
            .getAllDashboards(project.getProjectId(), includeFromSubprojects)
            .values()
            .stream()
        )
        .collect(Collectors.toList()
        );
    }
  }
}

