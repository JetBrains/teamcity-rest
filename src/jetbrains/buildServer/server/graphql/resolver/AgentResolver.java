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
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.controllers.agent.OSKind;
import jetbrains.buildServer.server.graphql.model.*;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import jetbrains.buildServer.server.graphql.model.connections.agent.AssociatedAgentBuildTypesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agent.AssociatedAgentBuildTypesConnectionBuilder;
import jetbrains.buildServer.server.graphql.model.connections.agent.DiassociatedAgentBuildTypesConnection;
import jetbrains.buildServer.server.graphql.model.filter.AgentBuildTypesFilter;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentResolver implements GraphQLResolver<Agent> {

  @Autowired
  @NotNull
  private AgentPoolFinder myAgentPoolFinder;

  @Autowired
  @NotNull
  private BuildTypeFinder myBuildTypeFinder;

  @Autowired
  @NotNull
  private BuildAgentManager myAgentManager;

  @Autowired
  @NotNull
  private ProjectFinder myProjectFinder;

  @NotNull
  public AgentPool agentPool(@NotNull Agent agent, @NotNull DataFetchingEnvironment env) {
    SBuildAgent realAgent = env.getLocalContext();

    return new AgentPool(realAgent.getAgentPool());
  }

  @NotNull
  public AgentEnvironment environment(@NotNull Agent agent, @NotNull DataFetchingEnvironment env) {
    SBuildAgent realAgent = env.getLocalContext();

    return new AgentEnvironment(new OS(realAgent.getOperatingSystemName(), OSType.guessByName(realAgent.getOperatingSystemName())));
  }

  @NotNull
  public AssociatedAgentBuildTypesConnection associatedBuildTypes(@NotNull Agent agent, @Nullable AgentBuildTypesFilter filter, @NotNull DataFetchingEnvironment env) {
    return buildTypes(
      agent,
      filter == null ? null : filter.getCompatible(),
      filter == null ? null : filter.getAssigned(),
      true,
      env
    );
  }

  @NotNull
  public DiassociatedAgentBuildTypesConnection dissociatedBuildTypes(@NotNull Agent agent, @Nullable AgentBuildTypesFilter filter, @NotNull DataFetchingEnvironment env) {
    return buildTypes(
      agent,
      filter == null ? null : filter.getCompatible(),
      filter == null ? null : filter.getAssigned(),
      true,
      env
    );
  }

  @NotNull
  private DiassociatedAgentBuildTypesConnection buildTypes(
    @NotNull Agent agent,
    @Nullable Boolean compatible,
    @Nullable Boolean assigned,
    @NotNull Boolean associatedWithPool,
    @NotNull DataFetchingEnvironment env) {
    SBuildAgent realAgent = env.getLocalContext();
    AgentRunPolicy policy = (myAgentManager.getRunConfigurationPolicy(realAgent) == BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS) ?
                           AgentRunPolicy.ALL :
                           AgentRunPolicy.ASSIGNED;

    // TODO: we can probably add optimized data gathering for specific cases
    List<BuildType> buildTypes = getByCompatible(compatible, realAgent)
      .filter(filterByAssignedAgent(assigned, realAgent))
      .filter(filterByPoolAssigned(associatedWithPool, realAgent))
      .map(BuildType::new)
      .collect(Collectors.toList());

    return new AssociatedAgentBuildTypesConnectionBuilder(buildTypes, policy, realAgent).get(env);
  }

  @NotNull
  private Stream<SBuildType> getByCompatible(@Nullable Boolean compatible, @NotNull SBuildAgent agent) {
    Stream<SBuildType> allBuildTypes = myBuildTypeFinder.getItems(null).myEntries
      .stream()
      .filter(btt -> btt.isBuildType())
      .map(btt -> btt.getBuildType());

    if(compatible == null) {
      return allBuildTypes;
    }

    return allBuildTypes
      .filter(bt -> compatible == bt.getAgentCompatibility(agent).isCompatible());
  }

  @NotNull
  private Predicate<SBuildType> filterByAssignedAgent(@Nullable Boolean assigned, @NotNull SBuildAgent agent) {
    if(assigned == null) {
      return bt -> true;
    }

    Set<String> assignedBuildTypes = AgentFinder.getAssignedBuildTypes(agent);

    return bt -> assigned == assignedBuildTypes.contains(bt.getInternalId());
  }

  @NotNull
  private Predicate<SBuildType> filterByPoolAssigned(@Nullable Boolean assignedToPool, @NotNull SBuildAgent agent) {
    if(assignedToPool == null) {
      return bt -> true;
    }

    jetbrains.buildServer.serverSide.agentPools.AgentPool pool = myAgentPoolFinder.getAgentPool(agent);

    Set<String> assignedBuildTypes = pool.getProjectIds().stream()
                                             .map(id -> myProjectFinder.findSingleItem(Locator.locator("internalId:" + id)))
                                             .filter(Objects::nonNull)
                                             .flatMap(p -> p.getOwnBuildTypes().stream())
                                             .map(bt -> bt.getInternalId())
                                             .collect(Collectors.toCollection(HashSet::new));

    return bt -> assignedToPool == assignedBuildTypes.contains(bt.getInternalId());
  }
}
