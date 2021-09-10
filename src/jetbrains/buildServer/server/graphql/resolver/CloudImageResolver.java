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
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.graphql.model.*;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.agentPool.ProjectAgentPool;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.agent.CloudImageInstancesConnection;
import jetbrains.buildServer.server.graphql.model.connections.agentPool.AgentPoolsConnection;
import jetbrains.buildServer.server.graphql.util.EntityNotFoundGraphQLError;
import jetbrains.buildServer.server.rest.data.CloudUtil;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudImageResolver implements GraphQLResolver<CloudImage> {
  @Autowired
  @NotNull
  private CloudManager myCloudManager;

  @Autowired
  @NotNull
  private CloudUtil myCloudUtil;

  @Autowired
  @NotNull
  private AgentPoolManager myAgentPoolManager;

  public int agentTypeId(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    return -1;
  }

  @NotNull
  public AgentEnvironment environment(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.clouds.CloudImage realImage = getRealImage(image, env);

    CloudProfile profile = myCloudUtil.getProfile(realImage);
    if(profile == null) {
      return AgentEnvironment.UNKNOWN;
    }
    SAgentType type = myCloudManager.getDescriptionFor(profile, realImage.getId());
    if(type == null) {
      return AgentEnvironment.UNKNOWN;
    }

    return new AgentEnvironment(new OS(type.getOperatingSystemName(), OSType.guessByName(type.getOperatingSystemName())));
  }

  @NotNull
  public CloudImageInstancesConnection instances(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.clouds.CloudImage realImage = getRealImage(image, env);
    CloudProfile profile = myCloudUtil.getProfile(realImage);
    if(profile == null) {
      return CloudImageInstancesConnection.EMPTY;
    }

    List<SBuildAgent> result = realImage.getInstances().stream()
                                        .map(instance -> myCloudManager.findAgentByInstance(profile.getProfileId(), instance.getInstanceId()))
                                        .flatMap(agents -> agents.stream())
                                        .collect(Collectors.toList());


    return new CloudImageInstancesConnection(result, PaginationArguments.everything());
  }

  @NotNull
  public DataFetcherResult<Project> project(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.clouds.CloudImage realImage = getRealImage(image, env);
    SProject project = myCloudUtil.getProject(realImage);

    DataFetcherResult.Builder<Project> result = new DataFetcherResult.Builder<>();

    if(project == null) {
      result.error(new EntityNotFoundGraphQLError(String.format("Could not find project for instance id=%s", image.getId())));
    } else {
      result.data(new Project(project)).localContext(project);
    }

    return result.build();
  }

  @NotNull
  public DataFetcherResult<AbstractAgentPool> agentPool(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.clouds.CloudImage realImage = getRealImage(image, env);
    DataFetcherResult.Builder<AbstractAgentPool> result = new DataFetcherResult.Builder<>();

    AgentPool pool = realImage.getAgentPoolId() != null ? myAgentPoolManager.findAgentPoolById(realImage.getAgentPoolId()) : null;

    if(realImage.getAgentPoolId() == null || pool == null) {
      result.error(new EntityNotFoundGraphQLError(String.format("Could not find agent pool for instance id=%s", image.getId())));
      return result.build();
    }

    return result.data(new ProjectAgentPool(pool)).localContext(pool).build();
  }

  @NotNull
  public AgentPoolsConnection assignableAgentPools(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    return null;
  }

  @NotNull
  private jetbrains.buildServer.clouds.CloudImage getRealImage(@NotNull CloudImage image, @NotNull DataFetchingEnvironment env) {
    jetbrains.buildServer.clouds.CloudImage realImage = env.getLocalContext();
    if(realImage != null)
      return realImage;

    jetbrains.buildServer.clouds.CloudImage result = myCloudUtil.getImage(image.getProfileId(), image.getId());
    if(result == null) {
      throw new NotFoundException("Cloud image not found.");
    }

    return result;
  }
}
