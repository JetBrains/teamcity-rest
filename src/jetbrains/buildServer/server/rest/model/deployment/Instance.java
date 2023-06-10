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

import java.util.HashMap;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentInstance;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentState;
import jetbrains.buildServer.serverSide.deploymentDashboards.exceptions.DashboardNotFoundException;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "deploymentInstance")
@ModelDescription(
  value = "Represents an instance that has been deployed to external system, along with deployment history."
)
public class Instance {

  @XmlAttribute
  public String id;
  @XmlElement(name = "deploymentStateEntries")
  public StateEntries deploymentStateEntries;
  @XmlAttribute
  public DeploymentState currentState;
  @XmlElement
  public HashMap<String, String> attributes;
  @XmlElement(name = "deploymentDashboard")
  public Dashboard deploymentDashboard;

  public Instance() {
  }

  public Instance(
    @NotNull final DeploymentInstance deploymentInstance,
    final @NotNull Fields fields,
    @NotNull final BeanContext beanContext
  ) {
    id = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("id"),
      deploymentInstance.getId()
    );

    deploymentStateEntries = ValueWithDefault.decideDefault(
      fields.isIncluded("deploymentStateEntries", false),
      () -> {
        Fields nestedFields = fields.getNestedField("deploymentStateEntries", Fields.NONE, Fields.LONG);

        return new StateEntries(
          deploymentInstance.getKnownStates(),
          nestedFields,
          beanContext
        );
      });

    currentState = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("currentState"),
      deploymentInstance.getCurrentState()
    );

    attributes = (HashMap<String, String>)ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("attributes"),
      deploymentInstance.getAttributes()
    );

    deploymentDashboard = ValueWithDefault.decideDefault(
      fields.isIncluded("deploymentDashboard", false),
      () -> {
        DeploymentDashboard dashboard;
        try {
          dashboard = beanContext
            .getSingletonService(DeploymentDashboardManager.class)
            .getDashboard(deploymentInstance.getDashboardId());

          return new Dashboard(
            dashboard,
            fields.getNestedField("deploymentDashboard"),
            beanContext
          );
        } catch (DashboardNotFoundException e) {
          return null;
        }
      });
  }
}
