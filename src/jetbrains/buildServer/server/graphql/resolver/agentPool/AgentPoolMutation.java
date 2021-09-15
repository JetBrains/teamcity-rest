/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.graphql.resolver.agentPool;

import com.intellij.openapi.diagnostic.Logger;
import graphql.execution.DataFetcherResult;
import graphql.kickstart.tools.GraphQLMutationResolver;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.model.mutation.*;
import jetbrains.buildServer.server.graphql.model.mutation.agentPool.*;
import jetbrains.buildServer.server.graphql.util.EntityNotFoundGraphQLError;
import jetbrains.buildServer.server.graphql.util.OperationFailedGraphQLError;
import jetbrains.buildServer.server.graphql.util.UnexpectedServerGraphQLError;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.*;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class AgentPoolMutation implements GraphQLMutationResolver {
  private static final Logger LOG = Logger.getInstance(AgentPoolMutation.class.getName());

  @NotNull
  private final AgentPoolManager myAgentPoolManager;

  @NotNull
  private final ProjectManager myProjectManager;

  @NotNull
  private final AgentPoolActionsAccessChecker myAgentPoolActionsAccessChecker;

  public AgentPoolMutation(@NotNull AgentPoolManager agentPoolManager,
                           @NotNull ProjectManager projectManager,
                           @NotNull AgentPoolActionsAccessChecker agentPoolActionsAccessChecker) {
    myAgentPoolManager = agentPoolManager;
    myProjectManager = projectManager;
    myAgentPoolActionsAccessChecker = agentPoolActionsAccessChecker;
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<CreateAgentPoolPayload> createAgentPool(@NotNull CreateAgentPoolInput input) {
    DataFetcherResult.Builder<CreateAgentPoolPayload> result = DataFetcherResult.newResult();
    try {
      AgentPool resultPool;
      if (input.getMaxAgents() != null) {
        resultPool = myAgentPoolManager.createNewAgentPool(input.getName(), new AgentPoolLimitsImpl(AgentPoolLimits.DEFAULT.getMinAgents(), input.getMaxAgents()));
      } else {
        resultPool = myAgentPoolManager.createNewAgentPool(input.getName());
      }

      result.data(new CreateAgentPoolPayload(new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(resultPool)));
    } catch (AgentPoolCannotBeRenamedException e) {
      result.error(new OperationFailedGraphQLError(e.getMessage()));
    }

    return result.build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<UpdateAgentPoolPayload> updateAgentPool(@NotNull UpdateAgentPoolInput input) {
    DataFetcherResult.Builder<UpdateAgentPoolPayload> result = DataFetcherResult.newResult();
    int poolId = input.getId();
    AgentPool poolOfInterest = myAgentPoolManager.findAgentPoolById(poolId);
    if (poolOfInterest == null) {
      return result.error(new EntityNotFoundGraphQLError("Pool with given id does not exist.")).build();
    }

    int maxAgents = input.getMaxAgents() == null ? poolOfInterest.getMaxAgents() : input.getMaxAgents();
    String name = input.getName() == null ? poolOfInterest.getName() : input.getName();

    try {
      myAgentPoolManager.updateAgentPool(poolId, name, new AgentPoolLimitsImpl(AgentPoolLimits.DEFAULT.getMinAgents(), maxAgents));
    } catch (AgentPoolCannotBeRenamedException e) {
      return result.data(new UpdateAgentPoolPayload(new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(poolOfInterest)))
                   .error(new OperationFailedGraphQLError(e.getMessage()))
                   .build();
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Pool with given id does not exist.")).build();
    }

    AgentPool updatedPool = myAgentPoolManager.findAgentPoolById(poolId);
    if (updatedPool == null) {
      LOG.error(String.format("Agent pool with id=%d is missing after update operation.", poolId));

      return result.error(new UnexpectedServerGraphQLError("Pool is missing after update.")).build();
    }

    result.data(new UpdateAgentPoolPayload(new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(updatedPool)));

    return result.build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<RemoveAgentPoolPayload> removeAgentPool(@NotNull RemoveAgentPoolInput input) {
    DataFetcherResult.Builder<RemoveAgentPoolPayload> result = DataFetcherResult.newResult();

    int poolId = input.getAgentPoolId();

    try {
      AgentPool removedPool = myAgentPoolManager.deleteAgentPool(poolId);

      return result.data(new RemoveAgentPoolPayload(new ShallowAgentPool(poolId, removedPool.getName()))).build();
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Pool with given id does not exist.")).build();
    } catch (AgentPoolCannotBeDeletedException e) {
      return result.error(new OperationFailedGraphQLError(e.getMessage())).build();
    }
  }

  @NotNull
  public MoveAgentToAgentPoolPayload moveAgentToAgentPool(@NotNull MoveAgentToAgentPoolInput input) {
    return null;
  }

  @NotNull
  public MoveCloudImageToAgentPoolPayload moveCloudImageToAgentPool(@NotNull MoveCloudImageToAgentPoolInput input) {
    return null;
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<AssignProjectWithAgentPoolPayload> assignProjectWithAgentPool(@NotNull AssignProjectWithAgentPoolInput input) {
    DataFetcherResult.Builder<AssignProjectWithAgentPoolPayload> result = DataFetcherResult.newResult();
    if(!myAgentPoolActionsAccessChecker.canManageProjectsInPool(input.getAgentPoolId())) {
      return result.error(new OperationFailedGraphQLError("Can't assign project.")).build();
    }

    SProject project = myProjectManager.findProjectByExternalId(input.getProjectId());
    if(project == null) {
      return result.error(new EntityNotFoundGraphQLError("Project with given id does not exist.")).build();
    }

    try {
      myAgentPoolManager.associateProjectsWithPool(input.getAgentPoolId(), Collections.singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Agent pool with given id does not exist.")).build();
    }

    if(BooleanUtils.isTrue(input.getExclusively())) {
      myAgentPoolManager.dissociateProjectsFromOtherPools(input.getAgentPoolId(), Collections.singleton(project.getProjectId()));
    }

    AgentPool agentPool = myAgentPoolManager.findAgentPoolById(input.getAgentPoolId());
    if(agentPool == null) {
      LOG.error(String.format("Agent pool with id=%d is missing after associating project id=%s", input.getAgentPoolId(), project.getProjectId()));
      return result.error(new UnexpectedServerGraphQLError("Agent pool with given id could not be found after operation.")).build();
    }

    return result.data(new AssignProjectWithAgentPoolPayload(
      new Project(project),
      new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(agentPool)
    )).build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<UnassignProjectFromAgentPoolPayload> unassignProjectFromAgentPool(@NotNull UnassignProjectFromAgentPoolInput input) {
    DataFetcherResult.Builder<UnassignProjectFromAgentPoolPayload> result = DataFetcherResult.newResult();
    if(!myAgentPoolActionsAccessChecker.canManageProjectsInPool(input.getAgentPoolId())) {
      return result.error(new OperationFailedGraphQLError("Can't assign project.")).build();
    }

    SProject project = myProjectManager.findProjectByExternalId(input.getProjectId());
    if(project == null) {
      return result.error(new EntityNotFoundGraphQLError("Project with given id does not exist.")).build();
    }

    try {
      myAgentPoolManager.dissociateProjectsFromPool(input.getAgentPoolId(), Collections.singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Agent pool with given id does not exist.")).build();
    }

    AgentPool agentPool = myAgentPoolManager.findAgentPoolById(input.getAgentPoolId());
    if(agentPool == null) {
      LOG.error(String.format("Agent pool with id=%d is missing after associating project id=%s", input.getAgentPoolId(), project.getProjectId()));
      return result.error(new UnexpectedServerGraphQLError("Agent pool with given id could not be found after operation.")).build();
    }

    return result.data(new UnassignProjectFromAgentPoolPayload(
      new Project(project),
      new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(agentPool)
    )).build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<BulkAssignProjectWithAgentPoolPayload> bulkAssignProjectWithAgentPool(@NotNull BulkAssignProjectWithAgentPoolInput input) {
    DataFetcherResult.Builder<BulkAssignProjectWithAgentPoolPayload> result = DataFetcherResult.newResult();

    if(!myAgentPoolActionsAccessChecker.canManageProjectsInPool(input.getAgentPoolId())) {
      return result.error(new OperationFailedGraphQLError("Can't assign projects.")).build();
    }

    Set<String> projectIds = myProjectManager.findProjectsByExternalIds(input.getProjectIds()).stream().map(p -> p.getProjectId()).collect(Collectors.toSet());

    try {
      myAgentPoolManager.associateProjectsWithPool(input.getAgentPoolId(), projectIds);
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Agent pool with given id does not exist.")).build();
    }

    if(input.getExclusively()) {
      myAgentPoolManager.dissociateProjectsFromOtherPools(input.getAgentPoolId(), projectIds);
    }

    AgentPool agentPool = myAgentPoolManager.findAgentPoolById(input.getAgentPoolId());
    if(agentPool == null) {
      LOG.error(String.format("Agent pool with id=%d is missing after bulk association request", input.getAgentPoolId()));
      return result.error(new UnexpectedServerGraphQLError("Agent pool with given id could not be found after operation.")).build();
    }

    return result.data(new BulkAssignProjectWithAgentPoolPayload(new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(agentPool))).build();
  }
}
