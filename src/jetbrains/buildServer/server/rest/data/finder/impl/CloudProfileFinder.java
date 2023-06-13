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

import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.data.CloudUtil;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.locator.definition.FinderLocatorDefinition;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.syntax.CloudProfileDimensions.*;

@JerseyContextSingleton
@Component("restCloudProfileFinder")
public class CloudProfileFinder extends DelegatingFinder<CloudProfile> implements FinderLocatorDefinition {
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final CloudManager myCloudManager;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final CloudUtil myCloudUtil;

  public CloudProfileFinder(@NotNull final ServiceLocator serviceLocator,
                            @NotNull final ProjectManager projectManager,
                            @NotNull final CloudUtil cloudUtil) {
    myServiceLocator = serviceLocator;
    myProjectManager = projectManager;
    myCloudUtil = cloudUtil;
    myCloudManager = myServiceLocator.getSingletonService(CloudManager.class);
    setDelegate(new Builder().build());
  }

  public static String getLocatorById(@NotNull final Long id) {
    return Locator.getStringLocator(ID, String.valueOf(id));
  }

  @NotNull
  public static String getLocator(@NotNull final CloudProfile item) {
    return Locator.getStringLocator(ID, item.getProfileId());
  }

  @NotNull
  public static String getLocator(@Nullable String baseLocator, @NotNull final SProject project) {
    if (baseLocator != null && (new Locator(baseLocator)).isSingleValue()) {
      return baseLocator;
    }
    return Locator.setDimensionIfNotPresent(baseLocator, PROJECT, ProjectFinder.getLocator(project));
  }

  private class Builder extends TypedFinderBuilder<CloudProfile> {
    Builder() {
      name("CloudProfileFinder");

      dimensionString(ID)
        .filter((value, item) -> value.equals(item.getProfileId()))
        .toItems(dimension -> Util.resolveNull(myCloudUtil.findProfileGloballyById(dimension), Collections::singletonList, Collections.emptyList()));

      dimensionValueCondition(NAME).valueForDefaultFilter(CloudProfile::getProfileName);
      dimensionValueCondition(CLOUD_PROVIDER_ID).valueForDefaultFilter(CloudProfile::getCloudCode);


      dimensionWithFinder(INSTANCE, () -> myServiceLocator.getSingletonService(CloudInstanceFinder.class), "instances of the profiles")
        .filter((value, item) -> value.stream().anyMatch(instance -> Util.resolveNull(myCloudUtil.getProfile(instance.getInstance().getImage()), p -> p.getProfileId().equals(item.getProfileId()), false)))
        .toItems(instances -> instances.stream().map(instance -> instance.getInstance().getImage()).distinct().map(image -> myCloudUtil.getProfile(image)).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

      dimensionWithFinder(IMAGE, () -> myServiceLocator.getSingletonService(CloudImageFinder.class), "images of the profiles")
        .filter((value, item) -> value.stream().anyMatch(image -> Util.resolveNull(myCloudUtil.getProfile(image), p -> p.getProfileId().equals(item.getProfileId()), false)))
        .toItems(images -> images.stream().map(image -> myCloudUtil.getProfile(image)).filter(Objects::nonNull).distinct().collect(Collectors.toList()));


      dimensionProjects(PROJECT, myServiceLocator)
        .valueForDefaultFilter(item -> Util.resolveNull(myProjectManager.findProjectById(item.getProjectId()), p -> Collections.singleton(p), Collections.emptySet()))
        .toItems(projects -> projects.stream().flatMap(project -> myCloudManager.listProfilesByProject(project.getProjectId(), false).stream()).collect(Collectors.toList()));

      dimensionProjects(AFFECTED_PROJECT, myServiceLocator)
        .filter((projects, item) -> Util.resolveNull(myCloudUtil.getProject(item), p -> CloudUtil.containProjectOrParent(projects, p), false))
        .toItems(projects -> projects.stream().flatMap(project -> myCloudManager.listProfilesByProject(project.getProjectId(), true).stream()).collect(Collectors.toList()));

      fallbackItemRetriever(dimensions -> ItemHolder.of(myCloudManager.listAllProfiles()));

      locatorProvider(CloudProfileFinder::getLocator);
    }
  }
}
