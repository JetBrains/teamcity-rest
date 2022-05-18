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

package jetbrains.buildServer.server.rest.data;

import java.util.Collection;
import java.util.Date;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 21/08/2019
 */
public class CloudInstanceData {
  @NotNull
  private final CloudInstance myInstance;
  @NotNull
  private final ServiceLocator myServiceLocator;
  @Nullable
  private String myCloudProfileId;

  public CloudInstanceData(@NotNull final CloudInstance instance, @Nullable String cloudProfileId, @NotNull final ServiceLocator serviceLocator) {
    myInstance = instance;
    myCloudProfileId = cloudProfileId;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  public CloudInstance getInstance() {
    return myInstance;
  }

  /**
   * id of the instance unique in the system
   */
  @NotNull
  public String getId() {
    return myServiceLocator.getSingletonService(CloudUtil.class).getId(myInstance);
  }

  @NotNull
  public String getCloudImageId() {
    return myInstance.getImageId();
  }

  @Nullable
  public SBuildAgent getAgent() {
    String profileId = getCloudProfileId();

    if (profileId == null) return null;
    Collection<SBuildAgent> agents = myServiceLocator.getSingletonService(CloudManager.class).findAgentByInstance(profileId, myInstance.getInstanceId());
    return agents.size() > 0 ? agents.iterator().next() : null;
  }

  @Nullable
  private String getCloudProfileId() {
    if(myCloudProfileId == null) {
      myCloudProfileId = myServiceLocator.getSingletonService(CloudInstanceFinder.class).myCloudUtil.getProfileId(myInstance.getImage());
    }

    return myCloudProfileId;
  }

  /**
   * starting, running, shutting down, stopped
   */
  @NotNull
  public String getState() {
    return myInstance.getStatus().getName().toLowerCase();
  }

  @NotNull
  public Date getStartDate() {
    return myInstance.getStartedTime();
  }

  @Nullable
  public String getError() {
    return Util.resolveNull(myInstance.getErrorInfo(), CloudErrorInfo::getMessage);
  }

}
