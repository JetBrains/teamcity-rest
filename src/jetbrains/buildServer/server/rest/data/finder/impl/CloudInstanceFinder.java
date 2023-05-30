/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.StubDimension;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@LocatorResource(value = LocatorName.CLOUD_INSTANCE,
  extraDimensions = {CommonLocatorDimensionsList.PROPERTY, AbstractFinder.DIMENSION_ITEM},
  baseEntity = "CloudInstance",
  examples = {
    "`agent:<agentLocator>` - find cloud instance which hosts agent found by `agentLocator`.",
    "`profile:<profileLocator>` - find all cloud instances in cloud profile found by `profileLocator`."
  }
)
@JerseyContextSingleton
@Component("restCloudInstanceFinder")
public class CloudInstanceFinder extends DelegatingFinder<CloudInstanceData> {
  private static final Logger LOG = Logger.getInstance(CloudInstanceFinder.class.getName());

  @LocatorDimension("id")
  private static final Dimension ID = new StubDimension("id");
  private static final Dimension ERROR = new StubDimension("errorMessage");
  private static final Dimension STATE = new StubDimension("state");
  @LocatorDimension("networkAddress")
  private static final Dimension NETWORK_ADDRESS = new StubDimension("networkAddress");
  private static final Dimension START_DATE = new StubDimension("startDate");
  @LocatorDimension(value = "agent", format = LocatorName.AGENT, notes = "Agent locator.")
  private static final Dimension AGENT = new StubDimension("agent");
  @LocatorDimension(value = "instance", format = LocatorName.CLOUD_IMAGE, notes = "Cloud image locator.")
  private static final Dimension IMAGE = new StubDimension("image");
  @LocatorDimension(value = "profile", format = LocatorName.CLOUD_PROFILE, notes = "Cloud profile locator.")
  private static final Dimension PROFILE = new StubDimension("profile");
  @LocatorDimension(value = "project", format = LocatorName.PROJECT, notes = "Project locator.")
  private static final Dimension PROJECT = new StubDimension("project");
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
  private static final Dimension AFFECTED_PROJECT = new StubDimension("affectedProject");

  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final CloudManager myCloudManager;
  @NotNull private final CloudUtil myCloudUtil;
  @NotNull private final TimeCondition myTimeCondition;

  public CloudInstanceFinder(@NotNull final ServiceLocator serviceLocator,
                             @NotNull final CloudUtil cloudUtil,
                             @NotNull final TimeCondition timeCondition) {
    myServiceLocator = serviceLocator;
    myCloudUtil = cloudUtil;
    myTimeCondition = timeCondition;
    myCloudManager = myServiceLocator.getSingletonService(CloudManager.class);
    setDelegate(new Builder().build());
  }

  @NotNull
  public static String getLocator(@NotNull final CloudInstanceData item) {
    return Locator.getStringLocator(ID, item.getId());
  }

  @NotNull
  public static String getLocator(@NotNull final SBuildAgent agent) {
    return Locator.getStringLocator(AGENT, AgentFinder.getLocator(agent));
  }

  @NotNull
  public static String getLocator(@NotNull final CloudImage image, @NotNull final CloudUtil cloudUtil) {
    return Locator.getStringLocator(IMAGE, CloudImageFinder.getLocator(image, cloudUtil));
  }

  private class Builder extends TypedFinderBuilder<CloudInstanceData> {
    Builder() {
      name("CloudInstanceFinder");

      dimension(ID, mapper(value -> new CloudUtil.InstanceIdData(value)).acceptingType("Specially formatted text"))
        .description("instance id as provided by list instances call")
        .filter((instanceIdData, instanceData) -> checkInstanceHasGivenIds(instanceIdData, instanceData))
        .toItems(instanceIdData -> getCloudInstanceDataById(instanceIdData));

      dimensionValueCondition(ERROR).description("instance error message").valueForDefaultFilter(instance -> instance.getError());
      dimensionValueCondition(NETWORK_ADDRESS).description("instance network address").valueForDefaultFilter(instance -> instance.getInstance().getNetworkIdentity());
      dimensionTimeCondition(START_DATE, myTimeCondition).description("instance start time").valueForDefaultFilter(instance -> instance.getInstance().getStartedTime());
      dimensionEnum(STATE, InstanceStatus.class).description("instance state").valueForDefaultFilter(instance -> instance.getInstance().getStatus());


      dimensionProjects(PROJECT, myServiceLocator).description("projects defining the cloud profiles/images").
        valueForDefaultFilter(item -> Collections.singleton(myCloudUtil.getInstanceProject(item))).
        toItems(projects -> projects.stream()
                                    .flatMap(project -> myCloudManager.listProfilesByProject(project.getProjectId(), false).stream())
                                    .flatMap(profile -> myCloudUtil.getInstancesByProfile(profile))
                                    .collect(Collectors.toList()));

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator).description("projects where the cloud profiles/images are accessible").
        filter((projects, item) -> Util.resolveNull(myCloudUtil.getInstanceProject(item), p -> CloudUtil.containProjectOrParent(projects, p), false)).
        toItems(projects -> projects.stream()
                                    .flatMap(project -> myCloudManager.listProfilesByProject(project.getProjectId(), true).stream())
                                    .flatMap(profile -> myCloudUtil.getInstancesByProfile(profile))
                                    .collect(Collectors.toList()));

      dimensionWithFinder(IMAGE, () -> myServiceLocator.getSingletonService(CloudImageFinder.class), "images of the instances").
        valueForDefaultFilter(item -> Collections.singleton(item.getInstance().getImage())).
        toItems(images -> images.stream()
                                .flatMap(image -> image.getInstances().stream()
                                                       .map(instance -> new CloudInstanceData(instance, image.getProfileId(), myServiceLocator))
                                )
                                .collect(Collectors.toList()));

      dimensionWithFinder(PROFILE, () -> myServiceLocator.getSingletonService(CloudProfileFinder.class), "profiles of the instances").
        valueForDefaultFilter(item -> Collections.singleton(myCloudUtil.getProfile(item.getInstance().getImage()))).
        toItems(profiles -> profiles.stream()
                                .flatMap(profile -> myCloudUtil.getInstancesByProfile(profile))
                                .collect(Collectors.toList()));

      dimensionAgents(AGENT, myServiceLocator).description("agents running on the instances").
        filter((agents, instance) -> agents.stream().anyMatch(agent -> instance.getInstance().containsAgent(agent))).
        toItems(agents -> agents.stream()
                                .map(agent -> myCloudManager.findInstanceByAgent(agent))
                                .filter(Objects::nonNull)
                                .map(pair -> new CloudInstanceData(pair.getSecond(), pair.getFirst().getProfileId(), myServiceLocator))
                                .collect(Collectors.toList()));

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> {
        return myCloudUtil.getAllInstancesProcessor();
      });

      locatorProvider(CloudInstanceFinder::getLocator);
    }
  }

  private boolean checkInstanceHasGivenIds(@NotNull CloudUtil.InstanceIdData instanceIdData, @NotNull CloudInstanceData instanceData) {
    return instanceIdData.id.equals(instanceData.getInstance().getInstanceId()) && instanceIdData.imageId.equals(instanceData.getCloudImageId())
           && Util.resolveNull(myCloudUtil.getProfile(instanceData.getInstance().getImage()), p -> instanceIdData.profileId.equals(p.getProfileId()), false);
  }

  @NotNull
  private List<CloudInstanceData> getCloudInstanceDataById(@NotNull CloudUtil.InstanceIdData instanceIdData) {
    return Util.resolveNull(myCloudUtil.getInstance(instanceIdData.profileId, instanceIdData.imageId, instanceIdData.id),
                            i -> Collections.singletonList(new CloudInstanceData(i, instanceIdData.profileId, myServiceLocator)), Collections.emptyList());
  }
}

