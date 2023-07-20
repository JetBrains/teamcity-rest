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

package jetbrains.buildServer.server.rest.model.deployment;

import java.util.Collection;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.DeploymentDashboardFinder;
import jetbrains.buildServer.server.rest.data.finder.syntax.DeploymentInstanceDimensions;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.request.DeploymentDashboardRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentInstance;
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.ImplicitDashboardCreationDisabledException;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@XmlRootElement(name = "deploymentDashboard")
@XmlType(name = "deploymentDashboard")
@ModelDescription(
  value = "Represents a deployment dashboard instance."
)
public class Dashboard {
  @XmlAttribute
  public String id;
  @XmlAttribute
  public String name;
  @XmlElement(name = "deploymentInstances")
  public Instances deploymentInstances;
  @XmlElement(name = "project")
  public Project project;

  public Dashboard() {
  }

  public Dashboard(
    @NotNull final DeploymentDashboard dashboard,
    @NotNull final Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    id = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("id"),
      dashboard.getId()
    );

    name = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("name"),
      dashboard.getName()
    );

    if (fields.isIncluded("deploymentInstances", true, true)) {
      deploymentInstances = resolveDeploymentInstances(dashboard, fields, beanContext);
    }

    project = ValueWithDefault.decideDefault(
      fields.isIncluded("project", false),
      resolveProject(dashboard, fields, beanContext));
  }

  @NotNull
  private Instances resolveDeploymentInstances(@NotNull DeploymentDashboard dashboard, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    Fields nestedFields = fields.getNestedField("deploymentInstances", Fields.NONE, Fields.LONG);

    Collection<DeploymentInstance> instances = dashboard.getInstances().values();

    String locator = Locator.getStringLocator(
      DeploymentInstanceDimensions.DASHBOARD,
      DeploymentDashboardFinder.getLocator(dashboard)
    );

    return new Instances(
      instances,
      new PagerDataImpl(
        getItemsHref(locator)
      ),
      nestedFields,
      beanContext
    );
  }

  @Nullable
  private static Project resolveProject(@NotNull DeploymentDashboard dashboard, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    SProject project = beanContext
      .getSingletonService(ProjectManager.class)
      .findProjectById(dashboard.getProjectExtId());

    if (project != null) {
      return new Project(project, fields.getNestedField("project"), beanContext);
    } else {
      return null;
    }
  }

  private String getItemsHref(String locator) {
    return DeploymentDashboardRequest.API_DEPLOYMENT_DASHBOARDS_URL + "/instances?locator=" + locator;
  }

  @NotNull
  public DeploymentDashboard getDashboardFromPosted(@NotNull final DeploymentDashboardFinder deploymentDashboardFinder) {
    if (id != null) {
      Locator resultLocator = Locator.createEmptyLocator();
      resultLocator.setDimension(
        AbstractFinder.DIMENSION_ID,
        String.valueOf(id)
      );
      return deploymentDashboardFinder.getItem(
        resultLocator.getStringRepresentation()
      );
    } else {
      throw new BadRequestException("Attribute 'id' should be specified.");
    }
  }

  public static String getFieldValue(
    @NotNull final DeploymentDashboard dashboard,
    @NotNull final String fieldName
  ) {
    if ("name".equals(fieldName)) {
      return dashboard.getName();
    }

    throw new NotFoundException("Field '" + fieldName + "' is not supported. Supported field: name.");
  }

  public static void setFieldValue(
    @NotNull final DeploymentDashboard dashboard,
    @NotNull final String fieldName,
    @Nullable final String newValue,
    @NotNull final BeanContext beanContext
  ) {
    if ("name".equals(fieldName)) {
      if (StringUtil.isEmpty(newValue)) {
        throw new BadRequestException("Dashboard name cannot be empty");
      }

      try {
        dashboard.setName(newValue);
        beanContext
          .getSingletonService(DeploymentDashboardManager.class)
          .persistDashboard(dashboard);
      } catch (ImplicitDashboardCreationDisabledException e) {
        throw new BadRequestException(e.getMessage());
      }
    } else {
      throw new BadRequestException("Setting field '" + fieldName + "' is not supported. Supported field: name.");
    }
  }
}
