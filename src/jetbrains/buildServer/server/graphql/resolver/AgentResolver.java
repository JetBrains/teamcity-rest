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

package jetbrains.buildServer.server.graphql.resolver;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.graphql.model.*;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import jetbrains.buildServer.server.graphql.model.connections.agent.AssociatedAgentBuildTypesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agent.AssociatedAgentBuildTypesConnectionBuilder;
import jetbrains.buildServer.server.graphql.model.connections.agent.DiassociatedAgentBuildTypesConnection;
import jetbrains.buildServer.server.graphql.model.filter.AgentBuildTypesFilter;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.server.graphql.util.UnexpectedServerGraphQLError;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentResolver extends ModelResolver<Agent> {

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

  @Autowired
  @NotNull
  private AbstractAgentPoolFactory myPoolFactory;

  @Autowired
  @NotNull
  private AgentTypeFinder myAgentTypeFinder;

  @NotNull
  public AbstractAgentPool agentPool(@NotNull Agent agent, @NotNull DataFetchingEnvironment env) {
    return myPoolFactory.produce(agent.getRealAgent().getAgentPool());
  }

  @NotNull
  @Deprecated
  public AgentEnvironment environment(@NotNull Agent agent, @NotNull DataFetchingEnvironment env) {
    SBuildAgent realAgent = agent.getRealAgent();

    return new AgentEnvironment(
      new OS(realAgent.getOperatingSystemName(), OSType.guessByName(realAgent.getOperatingSystemName())),
      realAgent.getCpuBenchmarkIndex()
    );
  }

  @NotNull
  public DataFetcherResult<AgentType> agentType(@NotNull Agent agent) {
    DataFetcherResult.Builder<AgentType> result = DataFetcherResult.newResult();
    int agentTypeId = agent.getRealAgent().getAgentTypeId();

    SAgentType agentType = myAgentTypeFinder.findAgentType(agentTypeId);
    if(agentType == null) {
      return result.error(new UnexpectedServerGraphQLError("Unable to find agentType by id.")).build();
    }

    return result.data(new AgentType(agentType)).build();
  }

  @NotNull
  public AssociatedAgentBuildTypesConnection associatedBuildTypes(@NotNull Agent agent, @Nullable AgentBuildTypesFilter filter, @NotNull DataFetchingEnvironment env) {
    return buildTypes(
      agent.getRealAgent(),
      filter == null ? null : filter.getCompatible(),
      filter == null ? null : filter.getAssigned(),
      true,
      env
    );
  }

  @NotNull
  public DiassociatedAgentBuildTypesConnection dissociatedBuildTypes(@NotNull Agent agent, @Nullable AgentBuildTypesFilter filter, @NotNull DataFetchingEnvironment env) {
    return buildTypes(
      agent.getRealAgent(),
      filter == null ? null : filter.getCompatible(),
      filter == null ? null : filter.getAssigned(),
      true,
      env
    );
  }

  @NotNull
  private DiassociatedAgentBuildTypesConnection buildTypes(
    @NotNull SBuildAgent realAgent,
    @Nullable Boolean compatible,
    @Nullable Boolean assigned,
    @NotNull Boolean associatedWithPool,
    @NotNull DataFetchingEnvironment env) {
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
      .filter(BuildTypeOrTemplate::isBuildType)
      .map(BuildTypeOrTemplate::getBuildType)
      .filter(Objects::nonNull)
      .filter(it -> !((BuildTypeEx)it).isAgentLessBuildType());

    if (compatible == null) {
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

  @Override
  public String getIdPrefix() {
    return Agent.class.getSimpleName();
  }

  @Override
  public Agent findById(@NotNull String id) {
    return null;
  }
}
