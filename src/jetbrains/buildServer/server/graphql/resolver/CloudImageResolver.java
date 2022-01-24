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
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import java.util.*;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.graphql.model.*;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.ProjectAgentPool;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.agent.CloudImageInstancesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolsConnection;
import jetbrains.buildServer.server.graphql.util.EntityNotFoundGraphQLError;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeKey;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudImageResolver extends ModelResolver<CloudImage> {
  private static final Logger LOG = Logger.getInstance(CloudImageResolver.class.getName());

  @Autowired
  @NotNull
  private AgentPoolManager myAgentPoolManager;

  @Autowired
  @NotNull
  private AgentTypeManager myAgentTypeManager;

  @Autowired
  @NotNull
  private ProjectManager myProjectManager;

  @Autowired
  private CloudManager myCloudManager;


  public void initForTests(@NotNull AgentPoolManager agentPoolManager,
                           @NotNull ProjectManager projectManager,
                           @NotNull AgentTypeManager agentTypeManager) {
    myAgentPoolManager = agentPoolManager;
    myProjectManager = projectManager;
    myAgentTypeManager = agentTypeManager;
  }

  @NotNull
  public DataFetcherResult<Integer> agentTypeRawId(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    DataFetcherResult.Builder<Integer> result = DataFetcherResult.newResult();

    AgentType agentType = findAgentType(image);
    if(agentType == null) {
      return result.error(new EntityNotFoundGraphQLError(String.format("Agent type for image id=%s is no found.", image.getRawId()))).build();
    }

    return result.data(agentType.getAgentTypeId()).build();
  }

  @NotNull
  public AgentEnvironment environment(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    AgentType agentType = findAgentType(image);
    if(agentType == null) {
      return AgentEnvironment.UNKNOWN;
    }

    return new AgentEnvironment(new OS(agentType.getOperatingSystemName(), OSType.guessByName(agentType.getOperatingSystemName())));
  }

  @Nullable
  private AgentType findAgentType(@NotNull CloudImage image) {
    AgentTypeKey agentTypeKey = new AgentTypeKey(image.getRealProfile().getCloudCode(), image.getProfileId(), image.getRawId());
    return myAgentTypeManager.findAgentTypeByKey(agentTypeKey);
  }

  @NotNull
  public CloudImageInstancesConnection instances(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.clouds.CloudImage realImage = image.getRealImage();

    Collection<? extends CloudInstance> instances = realImage.getInstances();
    List<SBuildAgent> resultingAgents = new ArrayList<>(instances.size());
    instances.forEach(instance -> {
      Collection<SBuildAgent> agent = myCloudManager.findAgentByInstance(image.getProfileId(), instance.getInstanceId());

      if(agent.size() == 0) return;
      if(agent.size() > 1) {
        LOG.info(String.format(
          "Found more then one agent for instance (id:%s, image:%s, profile:%s), proceeding with a first one.",
          instance.getInstanceId(), realImage.getId(), image.getProfileId()
        ));
      }

      resultingAgents.add(agent.iterator().next());
    });

    return new CloudImageInstancesConnection(resultingAgents, PaginationArguments.everything());
  }

  @NotNull
  public DataFetcherResult<Project> project(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    String projectId = image.getRealProfile().getProjectId();

    SProject project;
    if(SProject.ROOT_PROJECT_ID.equals(projectId)) {
      project = myProjectManager.getRootProject();
    } else {
      project = myProjectManager.findProjectById(projectId);
    }

    DataFetcherResult.Builder<Project> result = new DataFetcherResult.Builder<>();

    if(project == null) {
      result.error(new EntityNotFoundGraphQLError(String.format("Could not find project for instance id=%s", image.getRawId())));
    } else {
      result.data(new Project(project)).localContext(project);
    }

    return result.build();
  }

  @NotNull
  public DataFetcherResult<AbstractAgentPool> agentPool(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    DataFetcherResult.Builder<AbstractAgentPool> result = new DataFetcherResult.Builder<>();

    AgentType agentType = findAgentType(image);
    AgentPool pool = agentType != null ? myAgentPoolManager.findAgentPoolById(agentType.getAgentPoolId()) : null;

    if(agentType == null || pool == null) {
      result.error(new EntityNotFoundGraphQLError(String.format(
        "Could not find agent pool for image id=%s in profile id=%s",
        image.getRawId(), image.getProfileId()
      )));
      return result.build();
    }

    return result.data(new ProjectAgentPool(pool)).localContext(pool).build();
  }

  @Deprecated
  @NotNull
  public AgentPoolsConnection assignableAgentPools(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    // deprecated, remove after ui migration
    return AgentPoolsConnection.empty();
  }

  @Override
  public String getId(CloudImage imageModel) {
    return getIdPrefix() + SEPARATOR + imageModel.getProfileId() + SEPARATOR + imageModel.getRawId();
  }

  @Override
  public String getIdPrefix() {
    return CloudImage.class.getSimpleName();
  }

  @Nullable
  @Override
  public CloudImage findById(@NotNull String id) {
    return null;
  }
}
