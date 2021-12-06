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
import com.intellij.openapi.util.Pair;
import graphql.execution.DataFetcherResult;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManagerBase;
import jetbrains.buildServer.server.graphql.model.Agent;
import jetbrains.buildServer.server.graphql.model.CloudImage;
import jetbrains.buildServer.server.graphql.model.Project;
import jetbrains.buildServer.server.graphql.model.mutation.*;
import jetbrains.buildServer.server.graphql.model.mutation.agentPool.*;
import jetbrains.buildServer.server.graphql.util.EntityNotFoundGraphQLError;
import jetbrains.buildServer.server.graphql.util.OperationFailedGraphQLError;
import jetbrains.buildServer.server.graphql.util.UnexpectedServerGraphQLError;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.*;
import jetbrains.buildServer.serverSide.agentTypes.*;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
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
  private final BuildAgentManagerEx myBuildAgentManager;

  @NotNull
  private final AgentPoolActionsAccessChecker myAgentPoolActionsAccessChecker;

  @NotNull
  private final CloudManagerBase myCloudManager;

  @NotNull
  private final AgentTypeFinder myAgentTypeFinder;

  private final SecurityContext mySecurityContext;

  public AgentPoolMutation(@NotNull AgentPoolManager agentPoolManager,
                           @NotNull ProjectManager projectManager,
                           @NotNull BuildAgentManagerEx buildAgentManager,
                           @NotNull CloudManagerBase cloudManager,
                           @NotNull AgentTypeFinder agentTypeFinder,
                           @NotNull SecurityContext securityContext,
                           @NotNull AgentPoolActionsAccessChecker agentPoolActionsAccessChecker) {
    myAgentPoolManager = agentPoolManager;
    myProjectManager = projectManager;
    myBuildAgentManager = buildAgentManager;
    myCloudManager = cloudManager;
    myAgentTypeFinder = agentTypeFinder;
    mySecurityContext = securityContext;
    myAgentPoolActionsAccessChecker = agentPoolActionsAccessChecker;
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<CreateAgentPoolPayload> createAgentPool(@NotNull CreateAgentPoolInput input) {
    DataFetcherResult.Builder<CreateAgentPoolPayload> result = DataFetcherResult.newResult();
    try {
      AgentPool resultPool;
      if (input.getMaxAgentsNumber() != null) {
        resultPool = myAgentPoolManager.createNewAgentPool(input.getName(), new AgentPoolLimitsImpl(AgentPoolLimits.DEFAULT.getMinAgents(), input.getMaxAgentsNumber()));
      } else {
        resultPool = myAgentPoolManager.createNewAgentPool(input.getName());
      }

      result.data(new CreateAgentPoolPayload(new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(resultPool)));
    } catch (AgentPoolCannotBeRenamedException e) {
      LOG.debug(e);
      result.error(new OperationFailedGraphQLError(e.getMessage()));
    }

    return result.build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<UpdateAgentPoolPayload> updateAgentPool(@NotNull UpdateAgentPoolInput input) {
    DataFetcherResult.Builder<UpdateAgentPoolPayload> result = DataFetcherResult.newResult();
    int poolId = input.getRawId();
    AgentPool poolOfInterest = myAgentPoolManager.findAgentPoolById(poolId);
    if (poolOfInterest == null) {
      return result.error(new EntityNotFoundGraphQLError("Pool with given id does not exist.")).build();
    }

    int maxAgents = input.getMaxAgentsNumber() == null ? poolOfInterest.getMaxAgents() : input.getMaxAgentsNumber();
    String name = input.getName() == null ? poolOfInterest.getName() : input.getName();

    try {
      myAgentPoolManager.updateAgentPool(poolId, name, new AgentPoolLimitsImpl(AgentPoolLimits.DEFAULT.getMinAgents(), maxAgents));
    } catch (AgentPoolCannotBeRenamedException e) {
      LOG.debug(e);
      return result.data(new UpdateAgentPoolPayload(new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(poolOfInterest)))
                   .error(new OperationFailedGraphQLError(e.getMessage()))
                   .build();
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Pool with given id does not exist.")).build();
    }

    AgentPool updatedPool = myAgentPoolManager.findAgentPoolById(poolId);
    if (updatedPool == null) {
      LOG.warn(String.format("Agent pool with id=%d is missing right after update operation.", poolId));

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
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError(e.getMessage())).build();
    }
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<MoveAgentToAgentPoolPayload> moveAgentToAgentPool(@NotNull MoveAgentToAgentPoolInput input, @NotNull DataFetchingEnvironment env) {
    DataFetcherResult.Builder<MoveAgentToAgentPoolPayload> result = DataFetcherResult.newResult();
    int agentId = input.getAgentRawId();
    int targetPoolId = input.getTargetAgentPoolRawId();

    BuildAgentEx agent = myBuildAgentManager.findAgentById(agentId, true);
    if(agent == null) {
      return result.error(new EntityNotFoundGraphQLError(String.format("Agent with id=%d is not found.", agentId))).build();
    }
    int sourcePoolId = agent.getAgentPoolId();

    try {
      myAgentPoolManager.moveAgentToPool(targetPoolId, agent);
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError(String.format("Agent pool with id=%d is not found.", targetPoolId))).build();
    } catch (AgentTypeCannotBeMovedException e) {
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError("Agent can't be moved.")).build();
    } catch (PoolQuotaExceededException e) {
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError("Agent can't be moved, target agent pool is full.")).build();
    }

    AgentPool sourcePool = myAgentPoolManager.findAgentPoolById(sourcePoolId);
    AgentPool targetPool = myAgentPoolManager.findAgentPoolById(targetPoolId);

    // Neither of those pools should not be null as we've just moved an agent from one pool to another.
    // However, someone could have deleted a pool in between.
    // Strictly speaking, the same is true for an agent, but let's not bother.
    return result.data(new MoveAgentToAgentPoolPayload(
      new Agent(agent),
      sourcePool == null ? null : new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(sourcePool),
      targetPool == null ? null : new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(targetPool)
    )).build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<MoveCloudImageToAgentPoolPayload> moveCloudImageToAgentPool(@NotNull MoveCloudImageToAgentPoolInput input) {
    DataFetcherResult.Builder<MoveCloudImageToAgentPoolPayload> result = DataFetcherResult.newResult();
    final int targetPoolId = input.getTargetAgentPoolRawId();

    SAgentType agentType = myAgentTypeFinder.findAgentType(input.getAgentTypeRawId());
    if (agentType == null) {
      return result.error(new EntityNotFoundGraphQLError(String.format("Cloud image with agent type=%d does not exist.", input.getAgentTypeRawId()))).build();
    }
    final int sourcePoolId = agentType.getAgentPoolId();

    if (!agentType.isCloud()) {
      return result.error(new OperationFailedGraphQLError(String.format("Agent type=%d does not correspond to a cloud agent.", input.getAgentTypeRawId()))).build();
    }

    final AgentTypeKey typeKey = agentType.getAgentTypeKey();
    CloudProfile profile = myCloudManager.findProfileGloballyById(typeKey.getProfileId());
    if(profile == null) {
      return result.error(new UnexpectedServerGraphQLError(String.format("Cloud profile with id=%s does not exist.", typeKey.getProfileId()))).build();
    }

    CloudClientEx client = myCloudManager.getClient(profile.getProjectId(), profile.getProfileId());
    try {
      myAgentPoolManager.moveAgentTypesToPool(targetPoolId, Collections.singleton(agentType.getAgentTypeId()));
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError(String.format("Agent pool with id=%d is not found.", targetPoolId))).build();
    } catch (AgentTypeCannotBeMovedException e) {
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError("Image can't be moved.")).build();
    } catch (PoolQuotaExceededException e) {
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError("Image can't be moved, target agent pool is full.")).build();
    }

    AgentPool sourcePool = myAgentPoolManager.findAgentPoolById(sourcePoolId);
    AgentPool targetPool = myAgentPoolManager.findAgentPoolById(targetPoolId);
    jetbrains.buildServer.clouds.CloudImage image = client.findImageById(typeKey.getTypeId());

    return result.data(new MoveCloudImageToAgentPoolPayload(
      new CloudImage(image, profile),
      new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(sourcePool),
      new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(targetPool)
    )).build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<AssignProjectWithAgentPoolPayload> assignProjectWithAgentPool(@NotNull AssignProjectWithAgentPoolInput input) {
    DataFetcherResult.Builder<AssignProjectWithAgentPoolPayload> result = DataFetcherResult.newResult();
    if(!myAgentPoolActionsAccessChecker.canManageProjectsInPool(input.getAgentPoolRawId())) {
      return result.error(new OperationFailedGraphQLError("Can't assign project.")).build();
    }

    SProject project = myProjectManager.findProjectByExternalId(input.getProjectRawId());
    if(project == null) {
      return result.error(new EntityNotFoundGraphQLError("Project with given id does not exist.")).build();
    }

    try {
      myAgentPoolManager.associateProjectsWithPool(input.getAgentPoolRawId(), Collections.singleton(project.getProjectId()));
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Agent pool with given id does not exist.")).build();
    }

    if(BooleanUtils.isTrue(input.getExclusively())) {
      myAgentPoolManager.dissociateProjectsFromOtherPools(input.getAgentPoolRawId(), Collections.singleton(project.getProjectId()));
    }

    AgentPool agentPool = myAgentPoolManager.findAgentPoolById(input.getAgentPoolRawId());
    if(agentPool == null) {
      LOG.warn(String.format("Agent pool with id=%d is missing after associating project id=%s", input.getAgentPoolRawId(), project.getProjectId()));
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

    SProject project = myProjectManager.findProjectByExternalId(input.getProjectRawId());
    if(project == null) {
      return result.error(new EntityNotFoundGraphQLError("Project with given id does not exist.")).build();
    }

    AuthorityHolder authorityHolder = mySecurityContext.getAuthorityHolder();
    boolean canRemoveThisProject = AuthUtil.hasPermissionToManageAgentPoolsWithProject(authorityHolder, project.getProjectId());
    boolean thereAreOtherAssociatedPools = false;

    if(canRemoveThisProject) {
      // let's count other pools iff we are sure that we can potentially remove given project.
      thereAreOtherAssociatedPools = myAgentPoolManager.getAgentPoolsWithProject(project.getProjectId()).stream()
                                                       .map(poolId -> myAgentPoolManager.findAgentPoolById(poolId))
                                                       .filter(Objects::nonNull)
                                                       .filter(pool -> !pool.isProjectPool())
                                                       .count() > 1;
    }

    if(!canRemoveThisProject || !thereAreOtherAssociatedPools) {
      if(!canRemoveThisProject) {
        return result.error(new OperationFailedGraphQLError("Can't unassign project, not enough permissions.")).build();
      }

      return result.error(new OperationFailedGraphQLError("Can't unassign project, there are no other pools associated with this project.")).build();
    }

    Set<String> projectsToDisassociate;
    if(input.isRecursive()) {
      projectsToDisassociate = new HashSet<>();
      projectsToDisassociate.add(project.getProjectId());
      project.getProjects().stream().map(p -> p.getProjectId()).forEach(projectsToDisassociate::add);
    } else {
      projectsToDisassociate = Collections.singleton(project.getProjectId());
    }

    try {
      myAgentPoolManager.dissociateProjectsFromPool(input.getAgentPoolRawId(), projectsToDisassociate);
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Agent pool with given id does not exist.")).build();
    }

    AgentPool agentPool = myAgentPoolManager.findAgentPoolById(input.getAgentPoolRawId());
    if(agentPool == null) {
      LOG.warn(String.format("Agent pool with id=%d is missing after associating project id=%s", input.getAgentPoolRawId(), project.getProjectId()));
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

    if(!myAgentPoolActionsAccessChecker.canManageProjectsInPool(input.getAgentPoolRawId())) {
      return result.error(new OperationFailedGraphQLError("Can't assign projects, not enough permissions to manage projects in target pool.")).build();
    }

    Set<String> projectIds = myProjectManager.findProjectsByExternalIds(input.getProjectRawIds()).stream().map(p -> p.getProjectId()).collect(Collectors.toSet());

    try {
      myAgentPoolManager.associateProjectsWithPool(input.getAgentPoolRawId(), projectIds);
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Agent pool with given id does not exist.")).build();
    }

    if(input.getExclusively()) {
      myAgentPoolManager.dissociateProjectsFromOtherPools(input.getAgentPoolRawId(), projectIds);
    }

    AgentPool agentPool = myAgentPoolManager.findAgentPoolById(input.getAgentPoolRawId());
    if(agentPool == null) {
      LOG.warn(String.format("Agent pool with id=%d is missing after bulk association request", input.getAgentPoolRawId()));
      return result.error(new UnexpectedServerGraphQLError("Agent pool with given id could not be found after operation.")).build();
    }

    return result.data(new BulkAssignProjectWithAgentPoolPayload(new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(agentPool))).build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<BulkMoveAgentToAgentsPoolPayload> bulkMoveAgentsToAgentPool(@NotNull BulkMoveAgentsToAgentPoolInput input) {
    DataFetcherResult.Builder<BulkMoveAgentToAgentsPoolPayload> result = DataFetcherResult.newResult();

    AgentPool targetPool = myAgentPoolManager.findAgentPoolById(input.getTargetAgentPoolRawId());
    if(targetPool == null) {
      return result.error(new EntityNotFoundGraphQLError("Target pool is not found.")).build();
    }

    if(targetPool.isProjectPool() || targetPool instanceof ReadOnlyAgentPool) {
      return result.error(new OperationFailedGraphQLError("Can't move agents to target pool.")).build();
    }

    if(!myAgentPoolActionsAccessChecker.canManageAgentsInPool(targetPool)) {
      return result.error(new OperationFailedGraphQLError("Can't move agents to target pool.")).build();
    }

    Set<String> projectsToCheck = new HashSet<>();
    Set<Integer> agentTypes = new HashSet<>();
    for(Integer agentId : input.getAgentRawIds()) {
      SBuildAgent agent = myBuildAgentManager.findAgentById(agentId, true);
      if(agent == null) {
        return result.error(new OperationFailedGraphQLError("One of the agents with given ids is not found.")).build();
      }

      agentTypes.add(agent.getAgentTypeId());
      projectsToCheck.addAll(agent.getAgentPool().getProjectIds());
    }

    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();
    if(!AuthUtil.hasPermissionToManageAgentPoolsWithProjects(authHolder, projectsToCheck)) {
      return result.error(new OperationFailedGraphQLError("Not enough permissions on one of the agent pools.")).build();
    }

    try {
      myAgentPoolManager.moveAgentTypesToPool(input.getTargetAgentPoolRawId(), agentTypes);
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError("Target pool does not exist.")).build();
    } catch (PoolQuotaExceededException e) {
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError("Target pool does not accept agents.")).build();
    } catch (AgentTypeCannotBeMovedException e) {
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError("One of the selected agents can not be moved.")).build();
    }

    List<Agent> agents = new ArrayList<>();
    for(Integer agentId : input.getAgentRawIds()) {
      SBuildAgent agent = myBuildAgentManager.findAgentById(agentId, true);
      if(agent == null) {
        continue;
      }

      agents.add(new Agent(agent));
    }

    AgentPool updatedTargetPool = myAgentPoolManager.findAgentPoolById(input.getTargetAgentPoolRawId()); // should not be null at this stage
    BulkMoveAgentToAgentsPoolPayload payload = new BulkMoveAgentToAgentsPoolPayload(agents, new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(updatedTargetPool));
    return result.data(payload).build();
  }

  @Used("graphql")
  @NotNull
  public DataFetcherResult<BulkMoveCloudImagesToAgentPoolPayload> bulkMoveCloudImagesToAgentPool(@NotNull BulkMoveCloudImagesToAgentPoolInput input) {
    DataFetcherResult.Builder<BulkMoveCloudImagesToAgentPoolPayload> result = DataFetcherResult.newResult();
    final int targetPoolId = input.getTargetAgentPoolRawId();
    AgentPool targetPool = myAgentPoolManager.findAgentPoolById(targetPoolId);
    if(targetPool == null) {
      return result.error(new EntityNotFoundGraphQLError("Target agent pool is not found.")).build();
    }

    Map<Integer, Pair<CloudClientEx, AgentTypeKey>> cloudClientsAndTypeKeys = new HashMap<>(); // agentTypeId -> CloudClient, AgentTypeKey
    input.getAgentTypeRawIds().forEach(agentTypeId -> {
      SAgentType agentType = myAgentTypeFinder.findAgentType(agentTypeId);

      if (agentType == null) {
        result.error(new EntityNotFoundGraphQLError(String.format("Cloud image with agent type=%d does not exist.", agentTypeId)));
        return;
      }

      if (!agentType.isCloud()) {
        result.error(new OperationFailedGraphQLError(String.format("Agent type=%d does not correspond to a cloud agent.", agentTypeId)));
        return;
      }

      final AgentTypeKey typeKey = agentType.getAgentTypeKey();
      CloudProfile profile = myCloudManager.findProfileGloballyById(typeKey.getProfileId());
      if(profile == null) {
        result.error(new UnexpectedServerGraphQLError(String.format("Cloud profile with id=%s does not exist.", typeKey.getProfileId())));
        return;
      }

      CloudClientEx client = myCloudManager.getClient(profile.getProjectId(), profile.getProfileId());
      cloudClientsAndTypeKeys.put(agentTypeId, new Pair<>(client, typeKey));
    });

    try {
      myAgentPoolManager.moveAgentTypesToPool(targetPoolId, cloudClientsAndTypeKeys.keySet());
    } catch (NoSuchAgentPoolException e) {
      return result.error(new EntityNotFoundGraphQLError(String.format("Agent pool with id=%d is not found.", targetPoolId))).build();
    } catch (AgentTypeCannotBeMovedException e) {
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError("Some of the images can't be moved.")).build();
    } catch (PoolQuotaExceededException e) {
      LOG.debug(e.getMessage());
      return result.error(new OperationFailedGraphQLError("Image can't be moved, target agent pool is full.")).build();
    }

    List<CloudImage> cloudImages = new ArrayList<>(cloudClientsAndTypeKeys.size());
    cloudClientsAndTypeKeys.forEach((agentTypeId, clientAndTypeKey) -> {
      CloudClientEx client = clientAndTypeKey.getFirst();
      AgentTypeKey typeKey = clientAndTypeKey.getSecond();

      jetbrains.buildServer.clouds.CloudImage image = client.findImageById(typeKey.getTypeId());
      CloudProfile profile = myCloudManager.findProfileGloballyById(typeKey.getProfileId());
      if(image != null && profile != null) {
        cloudImages.add(new CloudImage(image, profile));
      }
    });

    return result.data(new BulkMoveCloudImagesToAgentPoolPayload(cloudImages, new jetbrains.buildServer.server.graphql.model.agentPool.AgentPool(targetPool)))
                 .build();
  }
}
