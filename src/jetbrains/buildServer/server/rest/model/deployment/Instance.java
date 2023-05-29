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

import com.intellij.openapi.diagnostic.Logger;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.AgentRestrictorType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.controllers.agent.OSKind;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.impl.*;
import jetbrains.buildServer.server.rest.data.util.LocatorUtil;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.*;
import jetbrains.buildServer.server.rest.model.agent.*;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.model.cloud.CloudImage;
import jetbrains.buildServer.server.rest.model.cloud.CloudInstance;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.request.BuildRequest;
import jetbrains.buildServer.server.rest.request.DeploymentInstanceRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.auth.AccessChecker;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.deploymentDashboards.DeploymentDashboardManager;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentDashboard;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentInstance;
import jetbrains.buildServer.serverSide.deploymentDashboards.entities.DeploymentState;
import jetbrains.buildServer.serverSide.impl.AgentUpgradeUtil;
import jetbrains.buildServer.serverSide.impl.agent.DeadAgent;
import jetbrains.buildServer.serverSide.impl.agent.DummyAgentType;
import jetbrains.buildServer.serverSide.impl.agent.PollingRemoteAgentConnection;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @XmlAttribute
  public Map<String, String> attributes;
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

    attributes = ValueWithDefault.decideIncludeByDefault(
      fields.isIncluded("attributes"),
      deploymentInstance.getAttributes()
    );

    deploymentDashboard = ValueWithDefault.decideDefault(
      fields.isIncluded("deploymentDashboard", false),
      () -> {
        DeploymentDashboard dashboard = beanContext
          .getSingletonService(DeploymentDashboardManager.class)
          .getDashboard(deploymentInstance.getDashboardId());

        if (dashboard != null) {
          return new Dashboard(
            dashboard,
            fields.getNestedField("deploymentDashboard"),
            beanContext
          );
        } else {
          return null;
        }
      });
  }
}
