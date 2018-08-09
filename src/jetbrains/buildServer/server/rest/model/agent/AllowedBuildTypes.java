/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.agent;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeManager;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("PublicField")
@XmlRootElement(name = "allowed")
public class AllowedBuildTypes {
  @XmlAttribute
  public String policy;

  @XmlElement(name = "configuration")
  public List<String> configurations;

  public AllowedBuildTypes() {
  }

  public AllowedBuildTypes(@NotNull final ServiceLocator serviceLocator, @NotNull final SBuildAgent agent) {
    final BuildAgentManager.RunConfigurationPolicy policy = serviceLocator.getSingletonService(BuildAgentManager.class).getRunConfigurationPolicy(agent);
    if (policy == BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS) {
      this.policy = "ALL";
      configurations = null;
    } else if (policy == BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS) {
      this.policy = "SELECTED";
      configurations = new ArrayList<>(serviceLocator.getSingletonService(AgentTypeManager.class).getCanRunConfigurations(agent.getAgentTypeId()));
    } else {
      throw new IllegalStateException("Unsupported policy " + policy);
    }
  }

  public void apply(@NotNull final ServiceLocator serviceLocator, @NotNull final SBuildAgent agent) {
    final AgentTypeManager agentTypeManager = serviceLocator.getSingletonService(AgentTypeManager.class);
    final int agentTypeId = agent.getAgentTypeId();

    final String valueUp = policy.trim().toUpperCase();
    if ("ALL".equals(valueUp)) {
      agentTypeManager.setRunConfigurationPolicy(agentTypeId, BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS);
    } else if ("SELECTED".equals(valueUp)) {
      if (configurations == null) {
        configurations = new ArrayList<>();
      }
      agentTypeManager.setRunConfigurationPolicy(agentTypeId, BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS);
      agentTypeManager.excludeRunConfigurationsFromAllowed(agentTypeId, agentTypeManager.getCanRunConfigurations(agentTypeId).toArray(new String[0]));
      agentTypeManager.includeRunConfigurationsToAllowed(agentTypeId, configurations.toArray(new String[0]));
    } else {
      throw new BadRequestException("Unexpected policy '" + policy + "', expected 'ALL' or 'SELECTED'");
    }
  }
}
