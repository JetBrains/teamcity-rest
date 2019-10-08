/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudInstancesProvider;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Dimension;

public class CloudImageFinder extends DelegatingFinder<CloudImage> {
  private static final Logger LOG = Logger.getInstance(CloudImageFinder.class.getName());

  private static final Dimension<CloudUtil.ImageIdData> ID = new Dimension<>("id");
  private static final Dimension<ValueCondition> NAME = new Dimension<>("name");
  private static final Dimension<ValueCondition> ERROR = new Dimension<>("errorMessage");
  private static final Dimension<List<AgentPool>> AGENT_POOL = new Dimension<>("agentPool");
  private static final Dimension<List<CloudInstanceData>> INSTANCE = new Dimension<>("instance");
  private static final Dimension<List<CloudProfile>> PROFILE = new Dimension<>("profile");
  private static final Dimension<List<SProject>> PROJECT = new Dimension<>("project");
  private static final Dimension<List<SProject>> AFFECTED_PROJECT = new Dimension<>("affectedProject");

  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final CloudManager myCloudManager;
  @NotNull private final CloudUtil myCloudUtil;

  public CloudImageFinder(@NotNull final ServiceLocator serviceLocator,
                          @NotNull final CloudUtil cloudUtil) {
    myServiceLocator = serviceLocator;
    myCloudUtil = cloudUtil;
    myCloudManager = myServiceLocator.getSingletonService(CloudManager.class);
    setDelegate(new Builder().build());
  }

  public static String getLocatorById(@NotNull final String id) {
    return Locator.getStringLocator(ID.name, id);
  }

  @NotNull
  public static String getLocator(@NotNull final CloudImage item, @NotNull final CloudUtil cloudUtil) {
    return Locator.getStringLocator(ID.name, cloudUtil.getId(item));
  }

  @NotNull
  public static String getLocator(@NotNull final CloudProfile item) {
    return Locator.getStringLocator(PROFILE.name, CloudProfileFinder.getLocator(item));
  }

  private class Builder extends TypedFinderBuilder<CloudImage> {
    Builder() {
      name("CloudImageFinder");

      dimension(ID, type(value -> new CloudUtil.ImageIdData(value)).description("Specially formatted text")).description("image id as provided by list images call").
        filter((value, item) -> value.id.equals(item.getId()) && Util.resolveNull(myCloudUtil.getProfile(item), p -> value.profileId.equals(p.getProfileId()), false)).
        toItems(dimension -> Util.resolveNull(myCloudUtil.getImage(dimension.profileId, dimension.id), Collections::singletonList, Collections.emptyList()));

      dimensionValueCondition(NAME).description("image name").valueForDefaultFilter(CloudImage::getName);
      dimensionValueCondition(ERROR).description("image error message").valueForDefaultFilter(cloudImage -> Util.resolveNull(cloudImage.getErrorInfo(), CloudErrorInfo::getMessage));

      dimensionWithFinder(AGENT_POOL, () -> myServiceLocator.getSingletonService(AgentPoolFinder.class), "agent pools of the images").
        filter((value, item) -> value.stream().anyMatch(pool -> Util.resolveNull(item.getAgentPoolId(), id -> id.equals(pool.getAgentPoolId()), false)));

      dimensionWithFinder(INSTANCE, () -> myServiceLocator.getSingletonService(CloudInstanceFinder.class), "instances of the images").
        filter((value, item) -> value.stream().anyMatch(instance -> instance.getCloudImageId().equals(item.getId()))).
        toItems(instances -> instances.stream().map(instance -> instance.getInstance().getImage()).distinct().collect(Collectors.toList()));

      dimensionWithFinder(PROFILE, () -> myServiceLocator.getSingletonService(CloudProfileFinder.class), "profiles of the images").
        valueForDefaultFilter(item -> Collections.singleton(myCloudUtil.getProfile(item))).
        toItems(profiles -> profiles.stream().flatMap(profile -> myCloudUtil.getImages(profile).stream()).collect(Collectors.toList()));

      dimensionProjects(PROJECT, myServiceLocator).description("projects defining the cloud profiles/images").
        valueForDefaultFilter(item -> Collections.singleton(myCloudUtil.getProject(item))).
        toItems(projects -> projects.stream()
                                    .flatMap(project -> myCloudManager.listProfilesByProject(project.getProjectId(), false).stream())
                                    .flatMap(profile -> myCloudUtil.getImages(profile).stream())
                                    .collect(Collectors.toList()));

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator).description("projects where the cloud profiles/images are accessible").
        filter((projects, item) -> Util.resolveNull(myCloudUtil.getProject(item), p -> CloudUtil.containProjectOrParent(projects, p), false)).
        toItems(projects -> projects.stream()
                                    .flatMap(project -> myCloudManager.listProfilesByProject(project.getProjectId(), true).stream())
                                    .flatMap(profile -> myCloudUtil.getImages(profile).stream())
                                    .collect(Collectors.toList()));

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> {
        return processor -> myCloudManager.listAllProfiles().stream()
                                          .flatMap(p -> myCloudUtil.getImages(p).stream())
                                          .filter(i -> !processor.processItem(i)).findFirst();

      });

      locatorProvider(i -> getLocator(i, myCloudUtil));
    }
  }

}

