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
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.data.CloudInstanceData;
import jetbrains.buildServer.server.rest.data.CloudUtil;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.finder.syntax.CommonLocatorDimensions;
import jetbrains.buildServer.server.rest.data.locator.Dimension;
import jetbrains.buildServer.server.rest.data.locator.Syntax;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeKey;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.virtualAgent.VirtualAgentCompatibilityResult;
import jetbrains.buildServer.serverSide.impl.virtualAgent.VirtualAgentsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@LocatorResource(value = LocatorName.CLOUD_IMAGE,
  baseEntity = "CloudImage",
  examples = {
    "`name:MyImage` - find image with name `MyImage`.",
    "`profile:<profileLocator>` - find all images in cloud profile found by `profileLocator`."
  }
)
@JerseyContextSingleton
@Component("restCloudImageFinder") // Name copied from context xml file.
public class CloudImageFinder extends DelegatingFinder<CloudImage> implements FinderLocatorDefinition {
  private static final Logger LOG = Logger.getInstance(CloudImageFinder.class.getName());

  public static final Dimension ID = Dimension.ofName("id").description("Image id as provided by list images call.")
                                              .syntax(Syntax.TODO("Specially formatted text")).build();
  public static final Dimension NAME = Dimension.ofName("name").description("Image name.")
                                                .syntax(Syntax.TODO("value condition")).build();
  public static final Dimension ERROR = Dimension.ofName("errorMessage").description("Image error message.")
                                                 .hidden().build();

  public static final Dimension AGENT = Dimension.ofName("agent").description("Agent locator.")
                                                 .syntax(Syntax.forLocator(LocatorName.AGENT)).build();
  public static final Dimension AGENT_POOL = Dimension.ofName("agentPool").description("Agent pool locator.")
                                                      .syntax(Syntax.forLocator(LocatorName.AGENT_POOL)).build();
  public static final Dimension INSTANCE = Dimension.ofName("instance").description("Cloud instance locator.")
                                                    .syntax(Syntax.forLocator(LocatorName.CLOUD_INSTANCE)).build();
  public static final Dimension PROFILE = Dimension.ofName("profile").description("Cloud profile locator.")
                                                   .syntax(Syntax.forLocator(LocatorName.CLOUD_PROFILE)).build();
  public static final Dimension PROJECT = Dimension.ofName("project").description("Projects defining the cloud profiles/images.")
                                                   .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension AFFECTED_PROJECT = Dimension.ofName("affectedProject").description("Projects where the cloud profiles/images are accessible.")
                                                            .syntax(Syntax.forLocator(LocatorName.PROJECT)).build();
  public static final Dimension COMPATIBLE_BUILD_TYPE = Dimension.ofName("compatibleBuildType").description("Build type locator.")
                                                                 .syntax(Syntax.forLocator(LocatorName.BUILD_TYPE)).build();
  public static final Dimension COMPATIBLE_BUILD_PROMOTION = Dimension.ofName("compatibleBuildPromotion").description("Build promotion locator.")
                                                                      .syntax(Syntax.forLocator(LocatorName.BUILD)).build();

  public static final Dimension PROPERTY = CommonLocatorDimensions.PROPERTY;

  @NotNull
  private final ServiceLocator myServiceLocator;
  @NotNull
  private final CloudManager myCloudManager;
  @NotNull
  private final CloudUtil myCloudUtil;
  @NotNull
  private final VirtualAgentsManager myVirtualAgentsManager;

  public CloudImageFinder(@NotNull final ServiceLocator serviceLocator,
                          @NotNull final CloudUtil cloudUtil) {
    myServiceLocator = serviceLocator;
    myCloudUtil = cloudUtil;
    myCloudManager = myServiceLocator.getSingletonService(CloudManager.class);
    myVirtualAgentsManager = myServiceLocator.getSingletonService(VirtualAgentsManager.class);
    setDelegate(new Builder().build());
  }

  public static String getLocatorById(@NotNull final String id) {
    return Locator.getStringLocator(ID, id);
  }

  @NotNull
  public static String getLocator(@NotNull final CloudImage item, @NotNull final CloudUtil cloudUtil) {
    return Locator.getStringLocator(ID, cloudUtil.getId(item));
  }

  @NotNull
  public static String getLocator(@NotNull final CloudProfile item) {
    return Locator.getStringLocator(PROFILE, CloudProfileFinder.getLocator(item));
  }

  @NotNull
  public static String getLocator(@NotNull final SBuildAgent item) {
    return Locator.getStringLocator(AGENT, AgentFinder.getLocator(item));
  }

  private class Builder extends TypedFinderBuilder<CloudImage> {
    Builder() {
      name("CloudImageFinder");

      dimension(ID, mapper(CloudUtil.ImageIdData::new).acceptingType("Specially formatted text"))
        .filter((profileAndId, item) -> checkImageByProfileAndId(profileAndId, item))
        .toItems(profileAndId -> findImageByProfileAndId(profileAndId));

      dimensionValueCondition(NAME).valueForDefaultFilter(CloudImage::getName);

      dimensionValueCondition(ERROR)
        .valueForDefaultFilter(cloudImage -> Util.resolveNull(cloudImage.getErrorInfo(), CloudErrorInfo::getMessage));

      dimensionAgents(AGENT, myServiceLocator)
        .filter((agents, image) -> agents.stream().anyMatch(agentIsAssociatedWithCloudImage(image)));

      dimensionWithFinder(AGENT_POOL, () -> myServiceLocator.getSingletonService(AgentPoolFinder.class), "agent pools of the images")
        .filter((pools, image) -> pools.stream().anyMatch(poolIsAssociatedWithCloudImage(image)));

      dimensionWithFinder(INSTANCE, () -> myServiceLocator.getSingletonService(CloudInstanceFinder.class), "instances of the images")
        .filter((instances, image) -> instances.stream().anyMatch(instanceBelongsToImage(image)))
        .toItems(instances -> instances.stream().map(instance -> instance.getInstance().getImage()).distinct().collect(Collectors.toList()));

      dimensionBuildTypes(COMPATIBLE_BUILD_TYPE, myServiceLocator)
        .filter((buildTypes, cloudImage) -> findCompatibleCloudImages(buildTypes).anyMatch(cloudImage::equals))
        .toItems(buildTypes -> findCompatibleCloudImages(buildTypes).collect(Collectors.toList()));

      dimensionBuildPromotions(COMPATIBLE_BUILD_PROMOTION, myServiceLocator)
        .toItems(buildPromotions -> findCompatibleCloudImagesForBuildPromotions(buildPromotions).collect(Collectors.toList()));

      dimensionWithFinder(PROFILE, () -> myServiceLocator.getSingletonService(CloudProfileFinder.class), "profiles of the images")
        .valueForDefaultFilter(item -> Collections.singleton(myCloudUtil.getProfile(item)))
        .toItems(profiles -> profiles.stream().flatMap(profile -> myCloudUtil.getImages(profile).stream()).collect(Collectors.toList()));

      dimensionProjects(PROJECT, myServiceLocator)
        .valueForDefaultFilter(item -> Collections.singleton(myCloudUtil.getProject(item)))
        .toItems(projects -> projects.stream()
                                     .flatMap(project -> getAllCloudImagesInProject(project, false))
                                     .collect(Collectors.toList())
        );

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator)
        .filter((projects, item) -> Util.resolveNull(myCloudUtil.getProject(item), p -> CloudUtil.containProjectOrParent(projects, p), false))
        .toItems(projects -> projects
          .stream()
          .flatMap(project -> getAllCloudImagesInProject(project, true))
          .collect(Collectors.toList())
        );

      fallbackItemRetriever(dimensions -> getAllCloudImages());

      locatorProvider(image -> getLocator(image, myCloudUtil));
    }

    private Stream<CloudImage> findCompatibleCloudImages(List<BuildTypeOrTemplate> buildTypeOrTemplateList) {
      return buildTypeOrTemplateList
        .stream()
        .flatMap(it -> findCompatibleCloudImages(it));
    }

    private Stream<CloudImage> findCompatibleCloudImagesForBuildPromotions(List<BuildPromotion> buildPromotions) {
      return buildPromotions
        .stream()
        .flatMap(it -> findCompatibleCloudImages(it));
    }

    @NotNull
    private Stream<CloudImage> findCompatibleCloudImages(BuildTypeOrTemplate buildTypeOrTemplate) {
      Map<SAgentType, VirtualAgentCompatibilityResult> availableAgentTypes;
      if (buildTypeOrTemplate.isBuildType()) {
        availableAgentTypes = myVirtualAgentsManager.getAvailableAgentTypes(Objects.requireNonNull(buildTypeOrTemplate.getBuildType()));
      } else {
        availableAgentTypes = myVirtualAgentsManager.getAvailableAgentTypes(Objects.requireNonNull(buildTypeOrTemplate.getTemplate()));
      }
      return getCompatibleCloudImages(availableAgentTypes, buildTypeOrTemplate.getProject());
    }

    @NotNull
    private Stream<CloudImage> findCompatibleCloudImages(BuildPromotion build) {
      Map<SAgentType, VirtualAgentCompatibilityResult> availableAgentTypes;
      availableAgentTypes = myVirtualAgentsManager.getAvailableAgentTypes(Objects.requireNonNull(build));
      SBuildType buildType = build.getBuildType();
      if (buildType == null) {
        return Stream.empty();
      }
      return getCompatibleCloudImages(availableAgentTypes, buildType.getProject());
    }

    @NotNull
    private Stream<CloudImage> getCompatibleCloudImages(Map<SAgentType, VirtualAgentCompatibilityResult> availableAgentTypes, SProject project) {
      return availableAgentTypes.entrySet().stream()
                                .filter(it -> it.getValue().getResult().isCompatible())
                                .filter(it -> it.getKey().getPolicy().getAllowedProjects().contains(project.getProjectId()))
                                .map(it -> it.getKey())
                                .map(it -> findRespectiveCloudImage(it.getAgentTypeKey()))
                                .filter(it -> it != null);
    }

    @Nullable
    private CloudImage findRespectiveCloudImage(@NotNull AgentTypeKey AgentTypeKey) {
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

        return respectiveImage;
      } catch (AccessDeniedException ade) {
        LOG.debug(ade);
        return null;
      }
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
    private ItemHolder<CloudImage> getAllCloudImages() {
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
      return pool -> Util.resolveNull(cloudImage.getAgentPoolId(), id -> id.equals(pool.getAgentPoolId()), false);
    }
  }

  @NotNull
  public static String getCompatibleBuildTypeLocator(final BuildTypeOrTemplate buildType) {
    if (buildType.isBuildType()) {
      return Locator.getStringLocator(COMPATIBLE_BUILD_TYPE, BuildTypeFinder.getLocator(Objects.requireNonNull(buildType.getBuildType())));
    } else {
      return Locator.getStringLocator(COMPATIBLE_BUILD_TYPE, BuildTypeFinder.getLocator(Objects.requireNonNull(buildType.getTemplate())));
    }
  }

  @NotNull
  public static String getCompatibleBuildPromotionLocator(final BuildPromotion buildType) {
      return Locator.getStringLocator(COMPATIBLE_BUILD_PROMOTION, BuildPromotionFinder.getLocator(Objects.requireNonNull(buildType)));
  }
}

