/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.resolver;

import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.server.graphql.model.buildType.incompatibility.*;
import jetbrains.buildServer.server.graphql.model.connections.agent.AgentBuildTypeEdge;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.serverSide.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentBuildTypeEdgeResolver implements GraphQLResolver<AgentBuildTypeEdge> {
  @Autowired
  @NotNull
  private BuildTypeFinder myBuildTypeFinder;

  @Autowired
  @NotNull
  private BuildAgentManager myAgentManager;

  @NotNull
  public Boolean getAssigned(@NotNull AgentBuildTypeEdge edge, @NotNull DataFetchingEnvironment env) {
    SBuildAgent realAgent = env.getLocalContext();
    SBuildType bt = myBuildTypeFinder.getItem("id:" + edge.getNode().getId()).getBuildType();

    return AgentFinder.getAssignedBuildTypes(realAgent).contains(bt.getInternalId());
  }

  @NotNull
  public Boolean getCompatible(@NotNull AgentBuildTypeEdge agentBuildTypeEdge, @NotNull DataFetchingEnvironment env) {
    SBuildAgent realAgent = env.getLocalContext();
    String btId = agentBuildTypeEdge.getNode().getId();
    return myAgentManager.getAgentCompatibilities(realAgent).stream()
                         .filter(compatibility -> compatibility.getBuildType().getExternalId().equals(btId))
                         .allMatch(AgentCompatibility::isCompatible);
  }

  @Nullable
  public List<AgentBuildTypeIncompatibility> getIncompatibilities(@NotNull AgentBuildTypeEdge agentBuildTypeEdge, @NotNull DataFetchingEnvironment env) {
    SBuildAgent realAgent = env.getLocalContext();
    String btId = agentBuildTypeEdge.getNode().getId();

    List<AgentBuildTypeIncompatibility> result = new ArrayList<>();
    for (AgentCompatibility compatibility : myAgentManager.getAgentCompatibilities(realAgent)) {
      if (compatibility.isCompatible() || !compatibility.getBuildType().getExternalId().equals(btId)) {
        continue;
      }

      if(compatibility.getIncompatibleRunner() != null) {
        result.add(new RunnerAgentBuildTypeIncompatibility(compatibility.getIncompatibleRunner().getDisplayName()));
      }

      compatibility.getMissedVcsPluginsOnAgent().forEach((pluginName, displayName) -> {
        result.add(new MissedVCSPluginAgentBuildTypeIncompatibility(displayName));
      });

      compatibility.getInvalidRunParameters().forEach(prop -> {
        result.add(new InvalidRunParameterAgentBuildTypeIncompatibility(prop.getPropertyName(), prop.getInvalidReason()));
      });

      compatibility.getUndefinedParameters().forEach((name, origin) -> {
        result.add(new UndefinedRunParameterAgentBuildTypeIncompatibility(name, origin));
      });

      compatibility.getNonMatchedRequirements().forEach(r -> {
        result.add(new UnmetRequirementAgentBuildTypeIncompatibility(r.getPropertyName(), r.getPropertyValue(), r.getType().toString()));
      });
    }

    if(result.isEmpty())
      return null;

    return result;
  }
}
