/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeManager;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("PublicField")
@XmlRootElement(name = "compatibilityPolicy")
@XmlType(name = "compatibilityPolicy")
@ModelDescription("Represents a build configuration run policy and included build configurations.")
public class CompatibilityPolicy {
  public static final String POLICY_ANY = "any";
  public static final String POLICY_SELECTED = "selected";

  @XmlAttribute
  public String policy;

  @XmlElement
  public BuildTypes buildTypes;

  public CompatibilityPolicy() {
  }

  private CompatibilityPolicy(@NotNull final SBuildAgent agent, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    final BuildAgentManager.RunConfigurationPolicy policy = beanContext.getSingletonService(BuildAgentManager.class).getRunConfigurationPolicy(agent);
    if (policy == BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS) {
      this.policy = ValueWithDefault.decideDefault(fields.isIncluded("policy"), POLICY_ANY);
      buildTypes = null;
    } else if (policy == BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS) {
      this.policy = ValueWithDefault.decideDefault(fields.isIncluded("policy"), POLICY_SELECTED);

      buildTypes =  ValueWithDefault.decideDefault(fields.isIncluded("buildTypes"), () -> {
        final Set<String> ids = beanContext.getSingletonService(AgentTypeManager.class).getCanRunConfigurations(agent.getAgentTypeId());
        final Collection<SBuildType> buildTypes = beanContext.getSingletonService(ProjectManager.class).findBuildTypes(ids);
        return new BuildTypes(BuildTypes.fromBuildTypes(buildTypes), null, fields.getNestedField("buildTypes", Fields.SHORT, Fields.LONG), beanContext);
      });
    } else {
      throw new IllegalStateException("Unsupported policy '" + policy + "', expected '" + POLICY_ANY + "' or '" + POLICY_SELECTED + "'");
    }
  }

  public void applyTo(@NotNull final SBuildAgent agent, @NotNull final ServiceLocator serviceLocator) {
    if (!AuthUtil.canViewAgentDetails(serviceLocator.getSingletonService(SecurityContext.class).getAuthorityHolder(), agent)) { //can get pool name i from the error message if we do not check this
      throw new AuthorizationFailedException("No permission to view agent details");
    }

    final AgentTypeManager agentTypeManager = serviceLocator.getSingletonService(AgentTypeManager.class);
    final int agentTypeId = agent.getAgentTypeId();

    final String valueUp = policy.trim().toLowerCase();
    if (POLICY_ANY.equals(valueUp)) {
      agentTypeManager.setRunConfigurationPolicy(agentTypeId, BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS);
    } else if (POLICY_SELECTED.equals(valueUp)) {
      if (buildTypes == null) {
        buildTypes = new BuildTypes();
      }
      List<jetbrains.buildServer.BuildType> buildTypesFromPosted = buildTypes.getBuildTypesFromPosted(serviceLocator);

      BuildAgentManager.RunConfigurationPolicy previousPolicy = agentTypeManager.getRunConfigurationPolicy(agentTypeId);
      Set<String> previous_canRunConfigurations = agentTypeManager.getCanRunConfigurations(agentTypeId);

      try {
        agentTypeManager.setRunConfigurationPolicy(agentTypeId, BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS);
        agentTypeManager.excludeRunConfigurationsFromAllowed(agentTypeId, previous_canRunConfigurations.toArray(new String[0]));
        agentTypeManager.includeRunConfigurationsToAllowed(agentTypeId, buildTypesFromPosted.stream().map(jetbrains.buildServer.BuildType::getBuildTypeId).toArray(String[]::new));
      } catch (Exception e) {
        agentTypeManager.setRunConfigurationPolicy(agentTypeId, previousPolicy);
        agentTypeManager.excludeRunConfigurationsFromAllowed(agentTypeId, agentTypeManager.getCanRunConfigurations(agentTypeId).toArray(new String[0]));
        agentTypeManager.includeRunConfigurationsToAllowed(agentTypeId, previous_canRunConfigurations.toArray(new String[0]));
        throw e;
      }
    } else {
      throw new BadRequestException("Unexpected policy '" + policy + "', expected '" + POLICY_ANY + "' or '" + POLICY_SELECTED + "'");
    }
  }

  @NotNull
  public static CompatibilityPolicy getCompatibilityPolicy(@NotNull final SBuildAgent agent, @NotNull final Fields fields, @NotNull final BeanContext beanContext) {
    if (!AuthUtil.canViewAgentDetails(beanContext.getServiceLocator().getSingletonService(SecurityContext.class).getAuthorityHolder(), agent)) {
      throw new AuthorizationFailedException("No permission to view agent details");
    }
    return new CompatibilityPolicy(agent, fields, beanContext);
  }
}