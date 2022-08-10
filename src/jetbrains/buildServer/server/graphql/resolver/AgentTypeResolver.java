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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.graphql.model.*;
import jetbrains.buildServer.server.graphql.model.agentPool.AbstractAgentPool;
import jetbrains.buildServer.server.graphql.model.connections.PaginationArguments;
import jetbrains.buildServer.server.graphql.model.connections.agent.AgentTypeAgentsConnection;
import jetbrains.buildServer.server.graphql.resolver.agentPool.AbstractAgentPoolFactory;
import jetbrains.buildServer.server.graphql.util.ModelResolver;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeKey;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class AgentTypeResolver extends ModelResolver<AgentType> {
  private static final Logger LOG = Logger.getInstance(AgentTypeResolver.class.getName());
  private final AbstractAgentPoolFactory myAgentPoolFactory;
  private final AgentTypeFinder myAgentTypeFinder;
  private final CloudManager myCloudManager;

  public AgentTypeResolver(@NotNull AbstractAgentPoolFactory agentPoolFactory, @NotNull AgentTypeFinder agentTypeFinder, @NotNull CloudManager cloudManager) {
    myAgentPoolFactory = agentPoolFactory;
    myAgentTypeFinder = agentTypeFinder;
    myCloudManager = cloudManager;
  }

  @Override
  public String getIdPrefix() {
    return "AgentType";
  }

  @NotNull
  public AbstractAgentPool getAgentPool(@NotNull AgentType agentType) {

    return myAgentPoolFactory.produce(agentType.getSource().getAgentPool());
  }

  @Used("graphql")
  @NotNull
  public AgentTypeAgentsConnection getAgents(@NotNull AgentType agentType) {
    if(!agentType.isCloud()) {
      SBuildAgent realAgent = agentType.getSource().getRealAgent();
      if(realAgent != null) {
        return new AgentTypeAgentsConnection(Collections.singletonList(realAgent), PaginationArguments.everything());
      } else {
        return AgentTypeAgentsConnection.EMPTY;
      }
    }

    AgentTypeKey targetKey = agentType.getSource().getAgentTypeKey();
    CloudImageAndProfilePair respectiveImage = findRespsectiveCloudImage(targetKey);

    if(respectiveImage == null) {
      return AgentTypeAgentsConnection.EMPTY;
    }

    List<SBuildAgent> result = new ArrayList<>();
    for (CloudInstance instance : respectiveImage.getImage().getInstances()) {
      Collection<SBuildAgent> agentOptional = myCloudManager.findAgentByInstance(targetKey.getProfileId(), instance.getInstanceId());
      if(!agentOptional.isEmpty()) {
        result.add(agentOptional.iterator().next());
      }
    }

    return new AgentTypeAgentsConnection(result, PaginationArguments.everything());
  }

  @Used("graphql")
  @Deprecated
  @Nullable
  public AgentEnvironment getEnvironment(@NotNull AgentType agentType) {
    SAgentType realAgentType = agentType.getSource();

    try {
      return new AgentEnvironment(
        new OS(realAgentType.getOperatingSystemName(), OSType.guessByName(realAgentType.getOperatingSystemName())),
        realAgentType.getCpuBenchmarkIndex()
      );
    } catch (AccessDeniedException ade) {
      LOG.debug(ade);
      return null;
    }
  }

  @Used("graphql")
  @Nullable
  public jetbrains.buildServer.server.graphql.model.CloudImage getCloudImage(@NotNull AgentType agentType) {
    AgentTypeKey key = agentType.getSource().getAgentTypeKey();

    CloudImageAndProfilePair imageAndProfile = findRespsectiveCloudImage(key);
    if(imageAndProfile == null) {
      return null;
    }

    return new jetbrains.buildServer.server.graphql.model.CloudImage(imageAndProfile.getImage(), imageAndProfile.getProfile());
  }

  @Override
  public AgentType findById(@NotNull String id) {
    try {
      int agentTypeId = Integer.parseInt(id);
      jetbrains.buildServer.serverSide.agentTypes.AgentType rawAgentType = myAgentTypeFinder.findAgentType(agentTypeId);

      return rawAgentType == null ? null : new AgentType((SAgentType) rawAgentType);
    } catch (NumberFormatException nfe) {
      // no agent type with this id
      return null;
    } catch (ClassCastException cce) {
      LOG.debug("", cce);
      return null;
    }
  }

  @Nullable
  private CloudImageAndProfilePair findRespsectiveCloudImage(@NotNull AgentTypeKey AgentTypeKey) {
    try {
      CloudProfile profile = myCloudManager.findProfileGloballyById(AgentTypeKey.getProfileId());
      if (profile == null) {
        return null;
      }

      CloudImage respectiveImage = null;
      CloudClientEx client = myCloudManager.getClient(profile.getProjectId(), profile.getProfileId());
      for (CloudImage image : client.getImages()) {
        SAgentType imageAgentType = myCloudManager.getDescriptionFor(profile, image.getId());
        if (imageAgentType == null) continue;

        if (AgentTypeKey.equals(imageAgentType.getAgentTypeKey())) {
          respectiveImage = image;
          break;
        }
      }

      return new CloudImageAndProfilePair(respectiveImage, profile);
    } catch (AccessDeniedException ade) {
      LOG.debug(ade);
      return null;
    }
  }

  private class CloudImageAndProfilePair {
    private final CloudImage myImage;
    private final CloudProfile myProfile;

    public CloudImageAndProfilePair(CloudImage image, CloudProfile profile) {
      myImage = image;
      myProfile = profile;
    }

    public CloudImage getImage() {
      return myImage;
    }

    public CloudProfile getProfile() {
      return myProfile;
    }
  }
}
