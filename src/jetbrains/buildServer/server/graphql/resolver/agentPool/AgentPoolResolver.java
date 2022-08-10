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

package jetbrains.buildServer.server.graphql.resolver.agentPool;

import com.intellij.openapi.util.Pair;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManagerBase;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.AgentPoolPermissions;
import jetbrains.buildServer.server.graphql.model.agentPool.actions.*;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.ProjectsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agent.AgentPoolAgentTypesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolAgentsConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolCloudImagesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolProjectsConnection;
import jetbrains.buildServer.server.graphql.model.filter.AgentPoolAgentTypesFilter;
import jetbrains.buildServer.server.graphql.model.filter.ProjectsFilter;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeKey;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeManager;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class AgentPoolResolver extends ModelResolver<AgentPool> {
  private final AbstractAgentPoolResolver myDelegate;
  private final AgentPoolActionsAccessChecker myPoolActionsAccessChecker;
  private final ProjectManager myProjectManager;
  private final BuildAgentManagerEx myAgentManager;
  private final CloudManagerBase myCloudManager;
  private final SecurityContext mySecurityContext;
  private final AgentPoolManager myPoolManager;
  private final AgentTypeManager myAgentTypeManager;

  public AgentPoolResolver(@NotNull AbstractAgentPoolResolver delegate,
                           @NotNull AgentPoolActionsAccessChecker agentPoolActionsAccessChecker,
                           @NotNull BuildAgentManagerEx agentManager,
                           @NotNull CloudManagerBase cloudManager,
                           @NotNull SecurityContext securityContext,
                           @NotNull ProjectManager projectManager,
                           @NotNull AgentPoolManager poolManager,
                           @NotNull AgentTypeManager agentTypeManager
                           ) {
    myDelegate = delegate;
    myPoolActionsAccessChecker = agentPoolActionsAccessChecker;
    myProjectManager = projectManager;
    myAgentManager = agentManager;
    myCloudManager = cloudManager;
    mySecurityContext = securityContext;
    myPoolManager = poolManager;
    myAgentTypeManager = agentTypeManager;
  }

  @NotNull
  public AgentPoolAgentTypesConnection agentTypes(@NotNull AgentPool pool, @NotNull AgentPoolAgentTypesFilter filter) {
    return myDelegate.agentTypes(pool, filter);
  }

  @NotNull
  public AgentPoolAgentsConnection agents(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.agents(pool, env);
  }

  @NotNull
  public AgentPoolProjectsConnection projects(@NotNull AgentPool pool, @NotNull ProjectsFilter filter, @NotNull DataFetchingEnvironment env) {
    return myDelegate.projects(pool, filter, env);
  }

  @NotNull
  public AgentPoolPermissions permissions(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.permissions(pool, env);
  }

  @NotNull
  public AgentPoolCloudImagesConnection cloudImages(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    return myDelegate.cloudImages(pool, env);
  }

  @NotNull
  public AgentPoolAgentsConnection assignableAgents(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    if(!myPoolActionsAccessChecker.canManageAgentsInPool(pool.getRealPool())) {
      return AgentPoolAgentsConnection.empty();
    }

    jetbrains.buildServer.serverSide.agentPools.AgentPool realPool = pool.getRealPool();
    boolean includeUnathorized = AuthUtil.hasPermissionToAuthorizeAgentsInPool(mySecurityContext.getAuthorityHolder(), realPool);

    final List<BuildAgentEx> allAgents = myAgentManager.getAllAgents(includeUnathorized);

    Set<Integer> managablePools = myPoolActionsAccessChecker.getManageablePoolIds();

    List<SBuildAgent> agents = allAgents.stream()
                                        .filter(agent -> agent.getAgentPoolId() != pool.getRealPool().getAgentPoolId()) // agents from the same pool can't be assigned to it
                                        .filter(agent -> !agent.getAgentType().isCloud()) // cloud agents are not interesting too, see assignableCloudImages instead
                                        .filter(agent -> managablePools.contains(agent.getAgentPoolId()))
                                        .collect(Collectors.toList());

    return new AgentPoolAgentsConnection(agents, PaginationArguments.everything());
  }

  @NotNull
  public AgentPoolCloudImagesConnection assignableCloudImages(@NotNull AgentPool pool, @NotNull DataFetchingEnvironment env) {
    AuthorityHolder authHolder = mySecurityContext.getAuthorityHolder();

    jetbrains.buildServer.serverSide.agentPools.AgentPool defaultPool = myPoolManager.findAgentPoolById(jetbrains.buildServer.serverSide.agentPools.AgentPool.DEFAULT_POOL_ID);
    if(!AuthUtil.hasGlobalOrPoolProjectsPermission(authHolder, defaultPool, Permission.MANAGE_AGENT_POOLS, Permission.MANAGE_AGENT_POOLS_FOR_PROJECT)) {
      return AgentPoolCloudImagesConnection.empty();
    }
    if(!myPoolActionsAccessChecker.canManageAgentsInPool(pool.getRealPool())) {
      return AgentPoolCloudImagesConnection.empty();
    }

    final Set<String> profileIdsInRootProject = myProjectManager.getRootProject()
                                                                .getOwnFeaturesOfType(CloudConstants.CLOUD_PROFILE_FEATURE_TYPE).stream()
                                                                .map(SProjectFeatureDescriptor::getId)
                                                                .collect(Collectors.toSet());

    List<Pair<CloudProfile, CloudImage>> images = new ArrayList<>();
    profileIdsInRootProject.forEach(profileId -> {
      CloudClientEx client = myCloudManager.getClientIfExists(BuildProject.ROOT_PROJECT_ID, profileId);
      CloudProfile profile = myCloudManager.findProfileById(BuildProject.ROOT_PROJECT_ID, profileId);
      if(client == null || profile == null) return;

      client.getImages().stream()
            .filter(image -> {
              AgentTypeKey key = new AgentTypeKey(profile.getCloudCode(), profileId, image.getId());
              AgentType type = myAgentTypeManager.findAgentTypeByKey(key);

              return type != null && pool.getRealPool().getAgentPoolId() != type.getAgentPoolId();
            })
            .forEach(image -> {
              images.add(new Pair<>(profile, image));
            });
    });

    return new AgentPoolCloudImagesConnection(images, PaginationArguments.everything());
  }

  @NotNull
  public AgentPoolActions actions(@NotNull AgentPool pool) {
    ManageAgentsInPoolUnmetRequirements unmetMoveReqs = myPoolActionsAccessChecker.getUnmetRequirementsToManageAgentsInPool(pool.getRealPool().getAgentPoolId());
    AgentPoolActionStatus moveAgentsActionStatus;
    if(unmetMoveReqs == null) {
      moveAgentsActionStatus = AgentPoolActionStatus.available();
    } else {
      moveAgentsActionStatus = AgentPoolActionStatus.unavailable(new MissingGlobalOrPerProjectPermission(
        unmetMoveReqs.getGlobalPermissions().stream().map(Permission::getName).collect(Collectors.toList()),
        unmetMoveReqs.getPerProjectPermission().getName(),
        new ProjectsConnection(myProjectManager.findProjects(unmetMoveReqs.getProjectsMissingPermission()), PaginationArguments.everything()),
        unmetMoveReqs.getHiddenProjectsMissingPermission()
      ));
    }

    return new AgentPoolActions(
      moveAgentsActionStatus,
      AgentPoolActionStatus.unavailable(null),
      AgentPoolActionStatus.unavailable(null),
      AgentPoolActionStatus.unavailable(null),
      AgentPoolActionStatus.unavailable(null),
      AgentPoolActionStatus.unavailable(null)
    );
  }

  @Override
  public String getIdPrefix() {
    return AgentPool.class.getSimpleName();
  }

  @Override
  @Nullable
  public AgentPool findById(String id) {
    return null;
  }
}
