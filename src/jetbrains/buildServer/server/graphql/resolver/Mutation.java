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

import com.intellij.openapi.diagnostic.Logger;
import graphql.GraphQLContext;
import graphql.execution.DataFetcherResult;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.LicenseNotGrantedException;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.server.graphql.model.*;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.buildType.BuildType;
import jetbrains.buildServer.server.graphql.model.mutation.*;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.server.graphql.util.Context;
import jetbrains.buildServer.server.graphql.util.EntityNotFoundGraphQLError;
import jetbrains.buildServer.server.graphql.util.OperationFailedGraphQLError;
import jetbrains.buildServer.server.rest.data.finder.impl.AgentFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.finder.impl.ProjectFinder;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.*;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Mutation implements GraphQLMutationResolver {
  private static final Logger LOG = Logger.getInstance(Mutation.class.getName());

  @Autowired
  @NotNull
  private ProjectFinder myProjectFinder;

  @Autowired
  @NotNull
  private BuildTypeFinder myBuildTypeFinder;

  @Autowired
  @NotNull
  private AgentTypeManager myAgentTypeManager;

  @Autowired
  @NotNull
  private AgentPoolManager myAgentPoolManager;

  @Autowired
  @NotNull
  private BuildAgentManagerEx myBuildAgentManager;

  @Autowired
  @NotNull
  private AbstractAgentPoolFactory myAgentPoolFactory;

  @Used("graphql")
  @NotNull
  public DataFetcherResult<SetAgentRunPolicyPayload> setAgentRunPolicy(@NotNull SetAgentRunPolicyInput input) {
    return runWithAgent(
      input.getAgentRawId(),
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

  @Used("graphql")
  @NotNull
  public DataFetcherResult<AssignBuildTypeWithAgentPayload> assignBuildTypeWithAgent(@NotNull AssignBuildTypeWithAgentInput input) {
    return runWithAgent(
      input.getAgentRawId(),
      agent -> {
        DataFetcherResult.Builder<AssignBuildTypeWithAgentPayload> result = DataFetcherResult.newResult();
        SBuildType bt = myBuildTypeFinder.getItem("id:" + input.getBuildTypeRawId()).getBuildType();
        if(bt == null) {
          final String errorMessage = String.format("Build type with id=%s is not found.", input.getBuildTypeRawId());
          return result.error(new EntityNotFoundGraphQLError(errorMessage)).build();
        }

        myAgentTypeManager.includeRunConfigurationsToAllowed(agent.getAgentTypeId(), new String[] { bt.getInternalId() });

        return result.data(new AssignBuildTypeWithAgentPayload(new Agent(agent), new BuildType(bt))).build();
      }
    );
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<UnassignBuildTypeFromAgentPayload> unassignBuildTypeFromAgent(@NotNull UnassignBuildTypeFromAgentInput input) {
    return runWithAgent(
      input.getAgentRawId(),
      agent -> {
        DataFetcherResult.Builder<UnassignBuildTypeFromAgentPayload> result = DataFetcherResult.newResult();
        SBuildType bt = myBuildTypeFinder.getItem("id:" + input.getBuildTypeRawId()).getBuildType();

        if(bt == null) {
          final String errorMessage = String.format("Build type with id=%s is not found.", input.getBuildTypeRawId());
          return result.error(new EntityNotFoundGraphQLError(errorMessage)).build();
        }

        myAgentTypeManager.excludeRunConfigurationsFromAllowed(agent.getAgentTypeId(), new String[]{ bt.getInternalId() });

        return result.data(new UnassignBuildTypeFromAgentPayload(new Agent(agent), new BuildType(bt))).build();
      }
    );
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<AssignProjectBuildTypesWithAgentPayload> assignProjectBuildTypesWithAgent(@NotNull AssignProjectBuildTypesWithAgentInput input) {
    return runWithAgent(
      input.getAgentRawId(),
      agent -> {
        SProject project = myProjectFinder.getItem("id:" + input.getProjectRawId());
        String[] bts = project.getBuildTypes().stream().map(bt -> bt.getInternalId()).collect(Collectors.toSet()).toArray(new String[0]);
        myAgentTypeManager.includeRunConfigurationsToAllowed(agent.getAgentTypeId(), bts);

        return DataFetcherResult.<AssignProjectBuildTypesWithAgentPayload>newResult()
                                .data(new AssignProjectBuildTypesWithAgentPayload(new Agent(agent), new Project(project)))
                                .build();
      }
    );
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<UnassignProjectBuildTypesFromAgentPayload> unassignProjectBuildTypesFromAgent(@NotNull UnassignProjectBuildTypesFromAgentInput input) {
    return runWithAgent(
      input.getAgentRawId(),
      agent -> {
        SProject project = myProjectFinder.getItem("id:" + input.getProjectRawId());
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
      input.getAgentRawId(),
      agent -> {
        Set<String> assignedBuildTypes = AgentFinder.getAssignedBuildTypes(agent);

        myAgentTypeManager.excludeBuildTypesFromAllowed(agent.getAgentTypeId(), assignedBuildTypes);

        return DataFetcherResult.<UnassignAllAgentBuildTypesPayload>newResult()
                                .data(new UnassignAllAgentBuildTypesPayload(new Agent(agent)))
                                .build();
      }
    );
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<AuthorizeAgentPayload> authorizeAgent(@NotNull AuthorizeAgentInput input, @NotNull DataFetchingEnvironment dfe) {
    return runWithAgent(
      input.getAgentRawId(),
      agent -> {
        DataFetcherResult.Builder<AuthorizeAgentPayload> result = DataFetcherResult.newResult();
        GraphQLContext context = dfe.getGraphQlContext();
        String authReason = input.getReason() == null ? "" : input.getReason();

        // Move agent to another pool first as we don't want some cheeky build to start while agent is in a wrong pool.
        if(input.getTargetAgentPoolRawId() != null) {
          try {
            myAgentPoolManager.moveAgentToPool(input.getTargetAgentPoolRawId(), agent);
          } catch (NoSuchAgentPoolException e) {
            return result.error(new EntityNotFoundGraphQLError(String.format("Agent pool with id=%d is not found.", input.getTargetAgentPoolRawId()))).build();
          } catch (PoolQuotaExceededException e) {
            LOG.debug(e.getMessage());
            return result.error(new OperationFailedGraphQLError(String.format("Agent pool with id=%d does not accept agents.", input.getTargetAgentPoolRawId()))).build();
          } catch (AgentTypeCannotBeMovedException e) {
            LOG.debug(e.getMessage());
            return result.error(new OperationFailedGraphQLError(String.format("Agent with id=%d can not be moved.", input.getAgentRawId()))).build();
          }
        }
        agent.setAuthorized(true, context.get(Context.CURRENT_USER), authReason);

        Agent agentModel = new Agent(agent);
        AbstractAgentPool targetPoolModel = null;
        if(input.getTargetAgentPoolRawId() != null) {
          AgentPool realPool = myAgentPoolManager.findAgentPoolById(input.getTargetAgentPoolRawId());
          if(realPool != null) {
            targetPoolModel = myAgentPoolFactory.produce(realPool);
          }
        }
        return result.data(new AuthorizeAgentPayload(agentModel, targetPoolModel)).build();
      }
    );
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<BulkAuthorizeAgentsPayload> bulkAuthorizeAgents(@NotNull BulkAuthorizeAgentsInput input, @NotNull DataFetchingEnvironment dfe) {
    DataFetcherResult.Builder<BulkAuthorizeAgentsPayload> result = DataFetcherResult.newResult();
    GraphQLContext context = dfe.getGraphQlContext();
    String authReason = input.getReason() == null ? "" : input.getReason();

    Set<Integer> agentTypeIds = new HashSet<>(input.getAgentRawIds().size());
    List<BuildAgentEx> agents = new ArrayList<>();

    for(int agentId : input.getAgentRawIds()) {
      BuildAgentEx agent = myBuildAgentManager.findAgentById(agentId, true);
      if(agent == null) {
        return result.error(new EntityNotFoundGraphQLError(String.format("Agent with id=%d is not found.", agentId))).build();
      }

      agentTypeIds.add(agent.getAgentTypeId());
      agents.add(agent);
    }

    if(input.getTargetAgentPoolRawId() != null) {
      try {
        myAgentPoolManager.moveAgentTypesToPool(input.getTargetAgentPoolRawId(), agentTypeIds);
      } catch (NoSuchAgentPoolException e) {
        return result.error(new EntityNotFoundGraphQLError("Agent pool is not found.")).build();
      } catch (AgentTypeCannotBeMovedException e) {
        LOG.debug(e.getMessage());
        return result.error(new OperationFailedGraphQLError("One of the given agents can't be moved.")).build();
      } catch (PoolQuotaExceededException e) {
        LOG.debug(e.getMessage());
        return result.error(new OperationFailedGraphQLError(String.format("Agent pool can't accept %d agents.", agentTypeIds.size()))).build();
      }
    }

    AbstractAgentPool poolModel = null;
    if(input.getTargetAgentPoolRawId() != null) {
      AgentPool targetRealPool = myAgentPoolManager.findAgentPoolById(input.getTargetAgentPoolRawId());
      if(targetRealPool != null) {
        poolModel = myAgentPoolFactory.produce(targetRealPool);
      } else {
        result.error(new EntityNotFoundGraphQLError("Agent pool is not found after successfully moving agents to it. Possibly it was deleted already."));
      }
    }

    List<Agent> agentModels = new ArrayList<>();
    for(BuildAgentEx agent : agents) {
      try {
        agent.setAuthorized(true, context.get(Context.CURRENT_USER), authReason);
      } catch (LicenseNotGrantedException e) {
        result.error(new OperationFailedGraphQLError(e.getMessage()));
      }
      agentModels.add(new Agent(agent));
    }

    return result.data(new BulkAuthorizeAgentsPayload(agentModels, poolModel)).build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<UnauthorizeAgentPayload> unauthorizeAgent(@NotNull UnauthorizeAgentInput input, @NotNull DataFetchingEnvironment dfe) {
    return runWithAgent(
      input.getAgentRawId(),
      agent -> {
        DataFetcherResult.Builder<UnauthorizeAgentPayload> result = DataFetcherResult.newResult();
        GraphQLContext context = dfe.getGraphQlContext();
        String authReason = input.getReason() == null ? "" : input.getReason();

        try {
          agent.setAuthorized(false, context.get(Context.CURRENT_USER), authReason);
        } catch (LicenseNotGrantedException e) {
          return result.error(new OperationFailedGraphQLError(e.getMessage())).build();
        }

        Agent agentModel = new Agent(agent);
        return result.data(new UnauthorizeAgentPayload(agentModel)).build();
      }
    );
  }

  @NotNull
  private <T> DataFetcherResult<T> runWithAgent(int agentId, @NotNull Function<BuildAgentEx, DataFetcherResult<T>> action) {
    BuildAgentEx agent = myBuildAgentManager.findAgentById(agentId, true);

    if(agent == null) {
      return DataFetcherResult.<T>newResult().error(new EntityNotFoundGraphQLError(String.format("Agent with id=%s does not exist.", agentId))).build();
    }

    return action.apply(agent);
  }
}
