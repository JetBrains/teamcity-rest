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

import graphql.execution.DataFetcherResult;
import graphql.kickstart.tools.GraphQLMutationResolver;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.graphql.model.Agent;
import jetbrains.buildServer.server.graphql.model.AgentRunPolicy;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import jetbrains.buildServer.server.graphql.model.mutation.*;
import jetbrains.buildServer.server.graphql.util.EntityNotFoundGraphQLError;
import jetbrains.buildServer.server.rest.data.AgentFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ProjectFinder;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Mutation implements GraphQLMutationResolver {
  @Autowired
  @NotNull
  private AgentFinder myAgentFinder;

  @Autowired
  @NotNull
  private ProjectFinder myProjectFinder;

  @Autowired
  @NotNull
  private BuildTypeFinder myBuildTypeFinder;

  @Autowired
  @NotNull
  private AgentTypeManager myAgentTypeManager;

  @NotNull
  public DataFetcherResult<SetAgentRunPolicyPayload> setAgentRunPolicy(@NotNull SetAgentRunPolicyInput input) {
    return runWithAgent(
      input.getAgentId(),
      realAgent -> {
        BuildAgentManager.RunConfigurationPolicy policy = input.getAgentRunPolicy() == AgentRunPolicy.ALL ?
                                                          BuildAgentManager.RunConfigurationPolicy.ALL_COMPATIBLE_CONFIGURATIONS :
                                                          BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS;

        myAgentTypeManager.setRunConfigurationPolicy(realAgent.getAgentTypeId(), policy);

        return DataFetcherResult.<SetAgentRunPolicyPayload>newResult()
                                .data(new SetAgentRunPolicyPayload(new Agent(realAgent)))
                                .build();
      }
    );
  }

  @NotNull
  public DataFetcherResult<AssignBuildTypeWithAgentPayload> assignBuildTypeWithAgent(@NotNull AssignBuildTypeWithAgentInput input) {
    return runWithAgent(
      input.getAgentId(),
      agent -> {
        DataFetcherResult.Builder<AssignBuildTypeWithAgentPayload> result = DataFetcherResult.newResult();
        SBuildType bt = myBuildTypeFinder.getItem("id:" + input.getBuildTypeId()).getBuildType();
        if(bt == null) {
          final String errorMessage = String.format("Build type with id=%s is not found.", input.getBuildTypeId());
          return result.error(new EntityNotFoundGraphQLError(errorMessage)).build();
        }

        myAgentTypeManager.includeRunConfigurationsToAllowed(agent.getAgentTypeId(), new String[] { bt.getInternalId() });

        return result.data(new AssignBuildTypeWithAgentPayload(new Agent(agent), new BuildType(bt))).build();
      }
    );
  }

  @NotNull
  public DataFetcherResult<UnassignBuildTypeFromAgentPayload> unassignBuildTypeFromAgent(@NotNull UnassignBuildTypeFromAgentInput input) {
    return runWithAgent(
      input.getAgentId(),
      agent -> {
        DataFetcherResult.Builder<UnassignBuildTypeFromAgentPayload> result = DataFetcherResult.newResult();
        SBuildType bt = myBuildTypeFinder.getItem("id:" + input.getBuildTypeId()).getBuildType();

        if(bt == null) {
          final String errorMessage = String.format("Build type with id=%s is not found.", input.getBuildTypeId());
          return result.error(new EntityNotFoundGraphQLError(errorMessage)).build();
        }

        myAgentTypeManager.excludeRunConfigurationsFromAllowed(agent.getAgentTypeId(), new String[]{ bt.getInternalId() });

        return result.data(new UnassignBuildTypeFromAgentPayload(new Agent(agent), new BuildType(bt))).build();
      }
    );
  }

  @NotNull
  public DataFetcherResult<AssignProjectBuildTypesWithAgentPayload> assignProjectBuildTypesWithAgent(@NotNull AssignProjectBuildTypesWithAgentInput input) {
    return runWithAgent(
      input.getAgentId(),
      agent -> {
        SProject project = myProjectFinder.getItem("id:" + input.getProjectId());
        String[] bts = project.getBuildTypes().stream().map(bt -> bt.getInternalId()).collect(Collectors.toSet()).toArray(new String[0]);
        myAgentTypeManager.includeRunConfigurationsToAllowed(agent.getAgentTypeId(), bts);

        return DataFetcherResult.<AssignProjectBuildTypesWithAgentPayload>newResult()
                                .data(new AssignProjectBuildTypesWithAgentPayload(new Agent(agent), new Project(project)))
                                .build();
      }
    );
  }

  @NotNull
  public DataFetcherResult<UnassignProjectBuildTypesFromAgentPayload> unassignProjectBuildTypesFromAgent(@NotNull UnassignProjectBuildTypesFromAgentInput input) {
    return runWithAgent(
      input.getAgentId(),
      agent -> {
        SProject project = myProjectFinder.getItem("id:" + input.getProjectId());
        List<String> bts = project.getBuildTypes().stream().map(bt -> bt.getInternalId()).collect(Collectors.toList());
        myAgentTypeManager.excludeRunConfigurationsFromAllowed(agent.getAgentTypeId(), bts.toArray(new String[0]));

        return DataFetcherResult.<UnassignProjectBuildTypesFromAgentPayload>newResult()
                                .data(new UnassignProjectBuildTypesFromAgentPayload(new Agent(agent), new Project(project)))
                                .build();
      }
    );
  }

  @NotNull
  public DataFetcherResult<UnassignAllAgentBuildTypesPayload> unassignAllAgentBuildTypes(@NotNull UnassignAllAgentBuildTypesInput input) {
    return runWithAgent(
      input.getAgentId(),
      agent -> {
        Set<String> assignedBuildTypes = AgentFinder.getAssignedBuildTypes(agent);

        myAgentTypeManager.excludeBuildTypesFromAllowed(agent.getAgentTypeId(), assignedBuildTypes);

        return DataFetcherResult.<UnassignAllAgentBuildTypesPayload>newResult()
                                .data(new UnassignAllAgentBuildTypesPayload(new Agent(agent)))
                                .build();
      }
    );
  }

  @NotNull
  private <T> DataFetcherResult<T> runWithAgent(int agentId, @NotNull Function<SBuildAgent, DataFetcherResult<T>> action) {
    SBuildAgent agent = myAgentFinder.findSingleItem(Locator.locator("id:" + agentId));

    if(agent == null) {
      return DataFetcherResult.<T>newResult().error(new EntityNotFoundGraphQLError(String.format("Agent with id=%s does not exist", agentId))).build();
    }

    return action.apply(agent);
  }
}
