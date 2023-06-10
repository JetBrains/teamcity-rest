/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.finder.impl.DeploymentDashboardFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.DeploymentInstanceFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.deployment.*;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentInstance;
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.DashboardAlreadyExistsException;
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.DashboardNotFoundException;
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.ImplicitDashboardCreationDisabledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 07.11.13
 */
@Path(DeploymentDashboardRequest.API_DEPLOYMENT_DASHBOARDS_URL)
@Api("DeploymentDashboard")
public class DeploymentDashboardRequest {
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private ProjectFinder myProjectFinder;
  @Context @NotNull private DeploymentDashboardFinder myDeploymentDashboardFinder;
  @Context @NotNull private DeploymentInstanceFinder myDeploymentInstanceFinder;

  public static final String API_DEPLOYMENT_DASHBOARDS_URL = Constants.API_URL + "/deploymentDashboards";

  public static String getHref() {
    return API_DEPLOYMENT_DASHBOARDS_URL;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all deployment dashboards.")
  public Dashboards getAllDashboards(
    @ApiParam(format = LocatorName.DEPLOYMENT_DASHBOARD) @QueryParam("locator") String locator,
    @QueryParam("fields") String fields,
    @Context UriInfo uriInfo,
    @Context HttpServletRequest request
  ) {
    PagedSearchResult<DeploymentDashboard> result = myDeploymentDashboardFinder.getItems(locator);

    final PagerData pager = new PagerDataImpl(
      uriInfo.getRequestUriBuilder(),
      request.getContextPath(),
      result,
      locator,
      "locator"
    );

    return new Dashboards(result.getEntries(), pager, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{deploymentDashboardLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get the deployment dashboard matching the locator.")
  public Dashboard getDashboard(
    @ApiParam(format = LocatorName.DEPLOYMENT_DASHBOARD) @PathParam("deploymentDashboardLocator") String deploymentDashboardLocator,
    @QueryParam("fields") String fields
  ) {
    return new Dashboard(
      myDeploymentDashboardFinder.getItem(deploymentDashboardLocator),
      new Fields(fields),
      myBeanContext
    );
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Create a new deployment dashboard.")
  public Dashboard createDashboard(Dashboard dashboard) {
    try {
      DeploymentDashboard newDashboard = myServiceLocator
        .getSingletonService(DeploymentDashboardManager.class)
        .createDashboard(dashboard.project.id, dashboard.id, dashboard.name);

      return new Dashboard(
        newDashboard,
        Fields.LONG,
        myBeanContext
      );
    } catch (DashboardAlreadyExistsException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
  }

  @DELETE
  @Path("/{deploymentDashboardLocator}")
  @ApiOperation(value = "Delete the deployment dashboard matching the locator.")
  public void deleteDashboard(
    @ApiParam(format = LocatorName.DEPLOYMENT_DASHBOARD) @PathParam("deploymentDashboardLocator") String deploymentDashboardLocator
  ) {
    final DeploymentDashboard dashboard = myDeploymentDashboardFinder.getItem(deploymentDashboardLocator);
    try {
      myServiceLocator
        .getSingletonService(DeploymentDashboardManager.class)
        .removeDashboard(
          dashboard.getId()
        );
    } catch (DashboardNotFoundException e) {
      throw new BadRequestException(e.getMessage(), e);
    }

  }

  private String updateInstanceLocatorWithDashboardData(
    @NotNull String dashboardLocator,
    @Nullable String instanceLocator
  ) {
    Locator resultLocator;

    if (instanceLocator != null) {
      resultLocator = Locator.locator(instanceLocator);
    } else {
      resultLocator = Locator.createEmptyLocator();
    }

    resultLocator.setDimension(
      DeploymentInstanceFinder.DASHBOARD.name,
      dashboardLocator
    );

    return resultLocator.toString();
  }

  @GET
  @Path("/{deploymentDashboardLocator}/instances")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get deployment instances for a given deployment dashboard.")
  public Instances getInstances(
    @ApiParam(format = LocatorName.DEPLOYMENT_DASHBOARD) @PathParam("deploymentDashboardLocator") String deploymentDashboardLocator,
    @ApiParam(format = LocatorName.DEPLOYMENT_INSTANCE) @QueryParam("locator") String locator,
    @QueryParam("fields") String fields,
    @Context UriInfo uriInfo,
    @Context HttpServletRequest request
  ) {
    PagedSearchResult<DeploymentInstance> result = myDeploymentInstanceFinder
      .getItems(
        updateInstanceLocatorWithDashboardData(deploymentDashboardLocator, locator)
      );

    final PagerData pager = new PagerDataImpl(
      uriInfo.getRequestUriBuilder(),
      request.getContextPath(),
      result,
      locator,
      "locator"
    );

    return new Instances(result.getEntries(), pager, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{deploymentDashboardLocator}/instances/{deploymentInstanceLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get the deployment instance matching the locator.")
  public Instance getInstance(
    @ApiParam(format = LocatorName.DEPLOYMENT_DASHBOARD) @PathParam("deploymentDashboardLocator") String deploymentDashboardLocator,
    @ApiParam(format = LocatorName.DEPLOYMENT_INSTANCE) @PathParam("deploymentInstanceLocator") String deploymentInstanceLocator,
    @QueryParam("fields") String fields
  ) {
    return new Instance(
      myDeploymentInstanceFinder.getItem(
        updateInstanceLocatorWithDashboardData(deploymentDashboardLocator, deploymentInstanceLocator)
      ),
      new Fields(fields),
      myBeanContext
    );
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @Path("/{deploymentDashboardLocator}/instances")
  @ApiOperation(value = "Create a new deployment instance.")
  public Instance createInstance(
    @ApiParam(format = LocatorName.DEPLOYMENT_DASHBOARD) @PathParam("deploymentDashboardLocator") String deploymentDashboardLocator,
    Instance instance
  ) {
    DeploymentDashboard dashboard = myDeploymentDashboardFinder.getItem(deploymentDashboardLocator);

    DeploymentInstance newInstance = new DeploymentInstance(
      instance.id,
      instance.deploymentStateEntries.getHistoryFromPosted(),
      instance.attributes,
      dashboard.getId()
    );

    dashboard.addOrUpdateInstance(newInstance);
    try {
      myServiceLocator
        .getSingletonService(DeploymentDashboardManager.class)
        .persistDashboard(dashboard);
    } catch (ImplicitDashboardCreationDisabledException e) {
      throw new BadRequestException(e.getMessage(), e);
    }

    return new Instance(
      newInstance,
      Fields.LONG,
      myBeanContext
    );
  }

  @POST
  @Path("/{deploymentDashboardLocator}/instances/{deploymentInstanceLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Report a new deployment for instance.")
  public Instance reportNewDeploymentForInstance(
    @ApiParam(format = LocatorName.DEPLOYMENT_DASHBOARD) @PathParam("deploymentDashboardLocator") String deploymentDashboardLocator,
    @ApiParam(format = LocatorName.DEPLOYMENT_INSTANCE) @PathParam("deploymentInstanceLocator") String deploymentInstanceLocator,
    StateEntry stateEntry
  ) {
    DeploymentInstance instance = myDeploymentInstanceFinder.getItem(
      updateInstanceLocatorWithDashboardData(deploymentDashboardLocator, deploymentInstanceLocator)
    );

    instance.addNewState(stateEntry.getEntryFromPosted());

    myServiceLocator
      .getSingletonService(DeploymentDashboardManager.class)
      .persistInstance(instance);

    return new Instance(
      instance,
      Fields.LONG,
      myBeanContext
    );
  }

  @DELETE
  @Path("/{deploymentDashboardLocator}/instances/{deploymentInstanceLocator}")
  @ApiOperation(value = "Delete the deployment instance matching the locator.", nickname = "deleteInstance")
  public void deleteInstance(
    @ApiParam(format = LocatorName.DEPLOYMENT_DASHBOARD) @PathParam("deploymentDashboardLocator") String deploymentDashboardLocator,
    @ApiParam(format = LocatorName.DEPLOYMENT_INSTANCE) @PathParam("deploymentInstanceLocator") String deploymentInstanceLocator
  ) {
    DeploymentDashboard dashboard = myDeploymentDashboardFinder.getItem(deploymentDashboardLocator);

    DeploymentInstance instance = myDeploymentInstanceFinder.getItem(
      updateInstanceLocatorWithDashboardData(deploymentDashboardLocator, deploymentInstanceLocator)
    );

    dashboard.removeInstance(instance);

    try {
      myServiceLocator
        .getSingletonService(DeploymentDashboardManager.class)
        .persistDashboard(dashboard);
    } catch (ImplicitDashboardCreationDisabledException e) {
      throw new RuntimeException(e);
    }
  }
}

