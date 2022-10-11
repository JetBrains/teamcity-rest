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

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Dimension;

@LocatorResource(value = LocatorName.CLOUD_IMAGE,
  extraDimensions = {CommonLocatorDimensionsList.PROPERTY, AbstractFinder.DIMENSION_ITEM},
  baseEntity = "CloudImage",
  examples = {
    "`name:MyImage` - find image with name `MyImage`.",
    "`profile:<profileLocator>` - find all images in cloud profile found by `profileLocator`."
  }
)
public class CloudImageFinder extends DelegatingFinder<CloudImage> {
  private static final Logger LOG = Logger.getInstance(CloudImageFinder.class.getName());

  @LocatorDimension("id") private static final Dimension<CloudUtil.ImageIdData> ID = new Dimension<>("id");
  @LocatorDimension("name") private static final Dimension<ValueCondition> NAME = new Dimension<>("name");
  private static final Dimension<ValueCondition> ERROR = new Dimension<>("errorMessage");
  @LocatorDimension(value = "agent", format = LocatorName.AGENT, notes = "Agent locator.")
  private static final Dimension<List<SBuildAgent>> AGENT = new Dimension<>("agent");
  @LocatorDimension(value = "agentPool", format = LocatorName.AGENT_POOL, notes = "Agent pool locator.")
  private static final Dimension<List<AgentPool>> AGENT_POOL = new Dimension<>("agentPool");
  @LocatorDimension(value = "instance", format = LocatorName.CLOUD_INSTANCE, notes = "Cloud instance locator.")
  private static final Dimension<List<CloudInstanceData>> INSTANCE = new Dimension<>("instance");
  @LocatorDimension(value = "profile", format = LocatorName.CLOUD_PROFILE, notes = "Cloud profile locator.")
  private static final Dimension<List<CloudProfile>> PROFILE = new Dimension<>("profile");
  @LocatorDimension(value = "project", format = LocatorName.PROJECT, notes = "Project locator.")
  private static final Dimension<List<SProject>> PROJECT = new Dimension<>("project");
  @LocatorDimension(value = "affectedProject", format = LocatorName.PROJECT, notes = "Project (direct or indirect parent) locator.")
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

  @NotNull
  public static String getLocator(@NotNull final SBuildAgent item) {
    return Locator.getStringLocator(AGENT.name, AgentFinder.getLocator(item));
  }

  private class Builder extends TypedFinderBuilder<CloudImage> {
    Builder() {
      name("CloudImageFinder");

      dimension(ID, mapper(CloudUtil.ImageIdData::new).acceptingType("Specially formatted text"))
        .description("image id as provided by list images call")
        .filter((profileAndId, item) -> checkImageByProfileAndId(profileAndId, item))
        .toItems(profileAndId -> findImageByProfileAndId(profileAndId));

      dimensionValueCondition(NAME)
        .description("image name")
        .valueForDefaultFilter(CloudImage::getName);

      dimensionValueCondition(ERROR)
        .description("image error message")
        .valueForDefaultFilter(cloudImage -> Util.resolveNull(cloudImage.getErrorInfo(), CloudErrorInfo::getMessage));

      dimensionAgents(AGENT, myServiceLocator)
        .filter((agents, image) -> agents.stream().anyMatch(agentIsAssociatedWithCloudImage(image)));

      dimensionWithFinder(AGENT_POOL, () -> myServiceLocator.getSingletonService(AgentPoolFinder.class), "agent pools of the images")
        .filter((pools, image) -> pools.stream().anyMatch(poolIsAssociatedWithCloudImage(image)));


      dimensionWithFinder(INSTANCE, () -> myServiceLocator.getSingletonService(CloudInstanceFinder.class), "instances of the images")
        .filter((instances, image) -> instances.stream().anyMatch(instanceBelongsToImage(image)))
        .toItems(instances -> instances.stream().map(instance -> instance.getInstance().getImage()).distinct().collect(Collectors.toList()));

      dimensionWithFinder(PROFILE, () -> myServiceLocator.getSingletonService(CloudProfileFinder.class), "profiles of the images")
        .valueForDefaultFilter(item -> Collections.singleton(myCloudUtil.getProfile(item)))
        .toItems(profiles -> profiles.stream().flatMap(profile -> myCloudUtil.getImages(profile).stream()).collect(Collectors.toList()));

      dimensionProjects(PROJECT, myServiceLocator)
        .description("projects defining the cloud profiles/images")
        .valueForDefaultFilter(item -> Collections.singleton(myCloudUtil.getProject(item)))
        .toItems(projects -> projects.stream()
                                     .flatMap(project -> getAllCloudImagesInProject(project, false))
                                     .collect(Collectors.toList())
        );

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator)
        .description("projects where the cloud profiles/images are accessible")
        .filter((projects, item) -> Util.resolveNull(myCloudUtil.getProject(item), p -> CloudUtil.containProjectOrParent(projects, p), false))
        .toItems(projects -> projects.stream()
                                     .flatMap(project -> getAllCloudImagesInProject(project, true))
                                     .collect(Collectors.toList())
        );

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> getAllCloudImages());

      locatorProvider(image -> getLocator(image, myCloudUtil));
    }

    private boolean checkImageByProfileAndId(CloudUtil.ImageIdData profileAndId, CloudImage item) {
      return profileAndId.id.equals(item.getId()) && Util.resolveNull(myCloudUtil.getProfile(item), p -> profileAndId.profileId.equals(p.getProfileId()), false);
    }

    @NotNull
    private List<CloudImage> findImageByProfileAndId(@NotNull CloudUtil.ImageIdData profileAndId) {
      return Util.resolveNull(myCloudUtil.getImage(profileAndId.profileId, profileAndId.id), Collections::singletonList, Collections.emptyList());
    }

    @NotNull
    private Stream<CloudImage> getAllCloudImagesInProject(@NotNull SProject project, boolean includeImagesInSubprojects) {
      return myCloudManager.listProfilesByProject(project.getProjectId(), includeImagesInSubprojects)
                           .stream()
                           .flatMap(profile -> myCloudUtil.getImages(profile).stream());
    }

    @NotNull
    private Predicate<CloudInstanceData> instanceBelongsToImage(@NotNull CloudImage image) {
      return instance -> instance.getCloudImageId().equals(image.getId());
    }

    @NotNull
    private FinderDataBinding.ItemHolder<CloudImage> getAllCloudImages() {
      return itemProcessor -> {
        Stream<CloudImage> allImages = myCloudManager.listAllProfiles().stream()
                                                     .flatMap(p -> myCloudUtil.getImages(p).stream());

        // this is equialent of takeWhile
        allImages.filter(image -> !itemProcessor.processItem(image))
                 .findFirst();
      };
    }

    @NotNull
    private Predicate<SBuildAgent> agentIsAssociatedWithCloudImage(@NotNull CloudImage image) {
      return agent -> {
        CloudProfile profile = myCloudUtil.getProfile(image);
        if(profile == null) {
          return false;
        }

        SAgentType imageDescription = myCloudManager.getDescriptionFor(profile, image.getId());
        if(imageDescription == null) {
          return false;
        }

        return agent.getAgentTypeId() == imageDescription.getAgentTypeId();
      };
    }

    @NotNull
    private Predicate<AgentPool> poolIsAssociatedWithCloudImage(@NotNull CloudImage cloudImage) {
      return pool -> Util.resolveNull(cloudImage.getAgentPoolId(), id -> id.equals(pool.getAgentPoolId()),false);
    }
  }

}

