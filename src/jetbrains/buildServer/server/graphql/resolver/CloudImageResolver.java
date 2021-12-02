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

package jetbrains.buildServer.server.graphql.resolver;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import java.util.*;
import java.util.stream.Stream;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.server.graphql.model.*;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.ProjectAgentPool;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.agent.CloudImageInstancesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolsConnection;
import jetbrains.buildServer.server.graphql.util.EntityNotFoundGraphQLError;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeKey;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudImageResolver extends ModelResolver<CloudImage> {
  @Autowired
  @NotNull
  private AgentPoolManager myAgentPoolManager;

  @Autowired
  @NotNull
  private AgentTypeFinder myAgentTypeFinder;

  @Autowired
  @NotNull
  private ProjectManager myProjectManager;

  @Autowired
  @NotNull
  private BuildAgentManager myAgentManager;

  public void initForTests(@NotNull AgentPoolManager agentPoolManager,
                           @NotNull ProjectManager projectManager,
                           @NotNull BuildAgentManager agentManager,
                           @NotNull AgentTypeFinder agentTypeFinder) {
    myAgentPoolManager = agentPoolManager;
    myProjectManager = projectManager;
    myAgentManager = agentManager;
    myAgentTypeFinder = agentTypeFinder;
  }

  @NotNull
  public DataFetcherResult<Integer> agentTypeRawId(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    DataFetcherResult.Builder<Integer> result = DataFetcherResult.newResult();

    SAgentType agentType = findAgentType(image);
    if(agentType == null) {
      return result.error(new EntityNotFoundGraphQLError(String.format("Agent type for image id=%s is no found.", image.getRawId()))).build();
    }

    return result.data(agentType.getAgentTypeId()).build();
  }

  @NotNull
  public AgentEnvironment environment(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    SAgentType agentType = findAgentType(image);
    if(agentType == null) {
      return AgentEnvironment.UNKNOWN;
    }

    return new AgentEnvironment(new OS(agentType.getOperatingSystemName(), OSType.guessByName(agentType.getOperatingSystemName())));
  }

  @Nullable
  private SAgentType findAgentType(@NotNull CloudImage image) {
    Stream<SAgentType> agentTypeStream;
    if(image.getRealImage().getAgentPoolId() != null) {
      agentTypeStream = myAgentTypeFinder.getAgentTypesByPool(image.getRealImage().getAgentPoolId()).stream();
    } else {
      agentTypeStream = myAgentTypeFinder.getActiveCloudAgentTypes().stream();
    }

    Optional<SAgentType> typeOptional = agentTypeStream.filter(agentType -> agentType.isCloud())
                                                       .filter(agentType -> {
                                                         AgentTypeKey agentTypeKey = agentType.getAgentTypeKey();
                                                         return agentTypeKey.getProfileId().equals(image.getProfileId()) && agentTypeKey.getTypeId().equals(image.getRawId());
                                                       })
                                                       .findFirst();

    if(!typeOptional.isPresent()) {
      return null;
    }

    return typeOptional.get();
  }

  @NotNull
  public CloudImageInstancesConnection instances(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.clouds.CloudImage realImage = image.getRealImage();

    List<CloudInstance> instancesToCheck = new ArrayList<>(realImage.getInstances());
    boolean[] instanceAgentIsFound = new boolean[instancesToCheck.size()]; Arrays.fill(instanceAgentIsFound, false);
    int agentsFound = 0;

    List<SBuildAgent> resultingAgents = new ArrayList<>(instancesToCheck.size());
    for(SBuildAgent agent : myAgentManager.getRegisteredAgents()) {
      if(!agent.isCloudAgent()) {
        continue;
      }

      for(int i = 0; i < instanceAgentIsFound.length; i++) {
        if(instanceAgentIsFound[i]) {
          continue;
        }

        if(instancesToCheck.get(i).containsAgent(agent)) {
          agentsFound++;
          instanceAgentIsFound[i] = true;
          resultingAgents.add(agent);
        }
      }

      if(agentsFound == instanceAgentIsFound.length) {
        break;
      }
    }

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
    jetbrains.buildServer.clouds.CloudImage realImage = image.getRealImage();
    DataFetcherResult.Builder<AbstractAgentPool> result = new DataFetcherResult.Builder<>();

    AgentPool pool = realImage.getAgentPoolId() != null ? myAgentPoolManager.findAgentPoolById(realImage.getAgentPoolId()) : null;

    if(realImage.getAgentPoolId() == null || pool == null) {
      result.error(new EntityNotFoundGraphQLError(String.format("Could not find agent pool for instance id=%s", image.getRawId())));
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
