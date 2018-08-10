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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeManager;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("PublicField")
@XmlRootElement(name = "allowed")
public class AllowedBuildTypes {
  @XmlAttribute
  public String policy;

  @XmlElement(name = "buildType")
  public List<BuildType> buildTypes;

  public AllowedBuildTypes() {
  }

  public AllowedBuildTypes(@NotNull final ServiceLocator serviceLocator, @NotNull final SBuildAgent agent, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    final BuildAgentManager.RunConfigurationPolicy policy = serviceLocator.getSingletonService(BuildAgentManager.class).getRunConfigurationPolicy(agent);
    if (policy == BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS) {
      this.policy = "all";
      buildTypes = null;
    } else if (policy == BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS) {
      this.policy = "selected";
      final Set<String> ids = serviceLocator.getSingletonService(AgentTypeManager.class).getCanRunConfigurations(agent.getAgentTypeId());

      if (fields.isIncluded("buildType", false, true)) {
        final Collection<SBuildType> buildTypes = serviceLocator.getSingletonService(ProjectManager.class).findBuildTypes(ids);
        this.buildTypes = new ArrayList<>(buildTypes.size());
        for (SBuildType buildType : buildTypes) {
          this.buildTypes.add(new BuildType(new BuildTypeOrTemplate(buildType), fields.getNestedField("buildType"), beanContext));
        }
      }
    } else {
      throw new IllegalStateException("Unsupported policy '" + policy + "', expected 'all' or 'selected'");
    }
  }

  public void apply(@NotNull final ServiceLocator serviceLocator, @NotNull final SBuildAgent agent) {
    final AgentTypeManager agentTypeManager = serviceLocator.getSingletonService(AgentTypeManager.class);
    final int agentTypeId = agent.getAgentTypeId();

    final String valueUp = policy.trim().toLowerCase();
    if ("all".equals(valueUp)) {
      agentTypeManager.setRunConfigurationPolicy(agentTypeId, BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS);
    } else if ("selected".equals(valueUp)) {
      if (buildTypes == null) {
        buildTypes = new ArrayList<>();
      }
      agentTypeManager.setRunConfigurationPolicy(agentTypeId, BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS);
      agentTypeManager.excludeRunConfigurationsFromAllowed(agentTypeId, agentTypeManager.getCanRunConfigurations(agentTypeId).toArray(new String[0]));
      agentTypeManager.includeRunConfigurationsToAllowed(agentTypeId, buildTypes.stream().map(BuildType::getId).toArray(String[]::new));
    } else {
      throw new BadRequestException("Unexpected policy '" + policy + "', expected 'all' or 'selected'");
    }
  }
}
