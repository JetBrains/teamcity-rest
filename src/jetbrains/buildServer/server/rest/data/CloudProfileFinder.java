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
import java.util.Objects;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudInstancesProvider;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Dimension;

public class CloudProfileFinder extends DelegatingFinder<CloudProfile> {
  private static final Logger LOG = Logger.getInstance(CloudProfileFinder.class.getName());

  private static final Dimension<String> ID = new Dimension<>("id");
  private static final Dimension<ValueCondition> NAME = new Dimension<>("name");
  private static final Dimension<ValueCondition> CLOUD_PROVIDER_ID = new Dimension<>("cloudProviderId");
  private static final Dimension<List<CloudInstanceData>> INSTANCE = new Dimension<>("instance");
  private static final Dimension<List<CloudImage>> IMAGE = new Dimension<>("image");
  private static final Dimension<List<SProject>> PROJECT = new Dimension<>("project");
  private static final Dimension<List<SProject>> AFFECTED_PROJECT = new Dimension<>("affectedProject");

  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final CloudManager myCloudManager;
  @NotNull private final CloudInstancesProvider myCloudInstancesProvider;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final CloudUtil myCloudUtil;

  public CloudProfileFinder(@NotNull final ServiceLocator serviceLocator,
                            @NotNull final CloudInstancesProvider cloudInstancesProvider,
                            @NotNull final ProjectManager projectManager,
                            @NotNull final CloudUtil cloudUtil) {
    myServiceLocator = serviceLocator;
    myCloudInstancesProvider = cloudInstancesProvider;
    myProjectManager = projectManager;
    myCloudUtil = cloudUtil;
    myCloudManager = myServiceLocator.getSingletonService(CloudManager.class);
    setDelegate(new Builder().build());
  }

  public static String getLocatorById(@NotNull final Long id) {
    return Locator.getStringLocator(ID.name, String.valueOf(id));
  }

  @NotNull
  public static String getLocator(@NotNull final CloudProfile item) {
    return Locator.getStringLocator(ID.name, item.getProfileId());
  }

  @NotNull
  public static String getLocator(@Nullable String baseLocator, @NotNull final SProject project) {
    if (baseLocator != null && (new Locator(baseLocator)).isSingleValue()) {
      return baseLocator;
    }
    return Locator.setDimensionIfNotPresent(baseLocator, PROJECT.name, ProjectFinder.getLocator(project));
  }

  private class Builder extends TypedFinderBuilder<CloudProfile> {
    Builder() {
      name("CloudProfileFinder");

      dimensionString(ID).description("profile id").
        filter((value, item) -> value.equals(item.getProfileId())).
        toItems(dimension -> Util.resolveNull(myCloudManager.findProfileGloballyById(dimension), Collections::singletonList, Collections.emptyList()));

      dimensionValueCondition(NAME).description("profile name").valueForDefaultFilter(CloudProfile::getProfileName);
      dimensionValueCondition(CLOUD_PROVIDER_ID).description("profile cloud provider id").valueForDefaultFilter(CloudProfile::getCloudCode);


      dimensionWithFinder(INSTANCE, () -> myServiceLocator.getSingletonService(CloudInstanceFinder.class), "instances of the profiles").
        filter((value, item) -> value.stream().anyMatch(instance -> Util.resolveNull(myCloudUtil.getProfile(instance.getInstance().getImage()), p -> p.getProfileId().equals(item.getProfileId()), false))).
        toItems(instances -> instances.stream().map(instance -> instance.getInstance().getImage()).distinct().map(image -> myCloudUtil.getProfile(image)).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

      dimensionWithFinder(IMAGE, () -> myServiceLocator.getSingletonService(CloudImageFinder.class), "images of the profiles").
        filter((value, item) -> value.stream().anyMatch(image -> Util.resolveNull(myCloudUtil.getProfile(image), p -> p.getProfileId().equals(item.getProfileId()), false))).
        toItems(images -> images.stream().map(image -> myCloudUtil.getProfile(image)).filter(Objects::nonNull).distinct().collect(Collectors.toList()));


      dimensionProjects(PROJECT, myServiceLocator).description("projects defining the cloud profiles").
        valueForDefaultFilter(item -> Util.resolveNull(myProjectManager.findProjectById(item.getProjectId()), p -> Collections.singleton(p), Collections.emptySet())).
        toItems(projects -> projects.stream().flatMap(project -> myCloudManager.listProfilesByProject(project.getProjectId(), false).stream()).collect(Collectors.toList()));

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator).description("projects where the cloud profiles are accessible").
        filter((projects, item) -> Util.resolveNull(myCloudUtil.getProject(item), p -> CloudUtil.containProjectOrParent(projects, p), false))
        .toItems(projects -> projects.stream().flatMap(project -> myCloudManager.listProfilesByProject(project.getProjectId(), true).stream()).collect(Collectors.toList()));

      multipleConvertToItemHolder(DimensionCondition.ALWAYS, dimensions -> FinderDataBinding.getItemHolder(myCloudManager.listAllProfiles()));

      filter(DimensionCondition.ALWAYS, dimensions -> {
        final PermissionChecker permissionChecker = myServiceLocator.getSingletonService(PermissionChecker.class);
        final boolean hasPermission = permissionChecker.hasGlobalPermission(Permission.VIEW_AGENT_CLOUDS);
        if (hasPermission) return null;
        return new ItemFilter<CloudProfile>() {
          @Override public boolean shouldStop(@NotNull final CloudProfile item) {return true;}
          @Override public boolean isIncluded(@NotNull final CloudProfile item) { return false;}
        };
      });

      locatorProvider(CloudProfileFinder::getLocator);
    }
  }
}

