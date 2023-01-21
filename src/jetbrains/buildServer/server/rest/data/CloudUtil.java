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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.server.CloudInstancesProviderExtendedCallback;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * @author Yegor.Yarko
 * @since 13.09.2019
 */
@Component("restCloudUtil") // Name copied from context xml.
public class CloudUtil {
  @NotNull
  private final ServiceLocator myServiceLocator;

  @NotNull
  private final CloudManager myCloudManager;

  @NotNull
  private final ProjectManager myProjectManager;

  public CloudUtil(
    @NotNull ServiceLocator serviceLocator,
    @NotNull CloudManager cloudManager,
    @NotNull ProjectManager projectManager
  ) {
    myServiceLocator = serviceLocator;
    myCloudManager = cloudManager;
    myProjectManager = projectManager;
  }

  public static boolean containProjectOrParent(@NotNull final List<SProject> projects, @NotNull SProject project) {
    while (project != null) {
      if (projects.contains(project)) {
        return true;
      }
      project = project.getParentProject();
    }
    return false;
  }

  @Nullable
  public SProject getProject(@NotNull final CloudProfile profile) {
    return myProjectManager.findProjectById(profile.getProjectId());
  }

  @Nullable
  public SProject getProject(@NotNull final CloudImage image) {
    return Util.resolveNull(getProfile(image), this::getProject);
  }

  @NotNull
  public Collection<? extends CloudImage> getImages(@NotNull final CloudProfile profile) {
    return getClient(profile).getImages();
  }

  @NotNull
  public CloudClientEx getClient(@NotNull final CloudProfile profile) {
    return myCloudManager.getClient(profile.getProjectId(), profile.getProfileId());
  }

  @Nullable
  public CloudProfile getProfile(@NotNull final CloudImage image) {
    String profileId = image.getProfileId();
    if (profileId != null) return myCloudManager.findProfileGloballyById(profileId);

    return myCloudManager.findProfileByImageId(image.getId());
  }

  @Nullable
  public String getProfileId(@NotNull final CloudImage image) {
    String profileId = image.getProfileId();
    if (profileId != null) return profileId;

    CloudProfile profile = myCloudManager.findProfileByImageId(image.getId());
    return profile == null ? null : profile.getProfileId();
  }

  @Nullable
  public SProject getInstanceProject(@NotNull final CloudInstanceData instance) {
    CloudProfile profile = getProfile(instance.getInstance().getImage());
    return profile == null ? null : myProjectManager.findProjectById(profile.getProjectId());
  }


  @NotNull
  public Stream<CloudInstanceData> getInstancesByProfile(@NotNull CloudProfile profile) {
    ArrayList<CloudInstanceData> result = new ArrayList<>();
    myCloudManager.iterateProfileInstances(profile, callback(item -> {result.add(item); return true;}));
    return result.stream();
  }

  @Nullable
  public CloudProfile findProfileGloballyById(@NotNull String profileId) {
    try {
      return myCloudManager.findProfileGloballyById(profileId);
    } catch (AccessDeniedException e ) {
      return null;
    }
  }

  public ItemHolder<CloudInstanceData> getAllInstancesProcessor() {
    return processor -> myCloudManager.iterateInstances(callback(processor));
  }

  @NotNull
  private CloudInstancesProviderExtendedCallback callback(@NotNull final ItemProcessor<CloudInstanceData> processor) {
    return new CloudInstancesProviderExtendedCallback() {
      @Override public void processNotReady(@NotNull final CloudProfile profile) {}
      @Override public void processClientError(@NotNull final CloudProfile profile, @NotNull CloudErrorInfo errorInfo) {}
      @Override public void processImageError(@NotNull final CloudProfile profile, @NotNull final CloudImage image) {}
      @Override public void processInstanceError(@NotNull final CloudProfile profile, @NotNull final CloudInstance instance) {}
      @Override public void processInstanceExpired(@NotNull final CloudProfile profile, @NotNull final CloudClientEx client, @NotNull final CloudInstance instance) {}
      @Override public boolean processInstance(@NotNull final CloudProfile profile, @NotNull final CloudInstance instance) {
        return processor.processItem(new CloudInstanceData(instance, profile.getProfileId(), myServiceLocator));
      }
    };
  }

  @NotNull
  public String getId(@NotNull final CloudInstance instance) {
    String profileId = getProfileId(instance.getImage());
    if (profileId == null) profileId = "<missing>";
    return Locator.getStringLocator("profileId", profileId, "imageId", instance.getImageId(), "id", instance.getInstanceId());
  }

  @NotNull
  public String getId(@NotNull final CloudImage image) {
    String profileId = getProfileId(image);
    if (profileId == null) profileId = "<missing>";
    return Locator.getStringLocator("profileId", profileId, "id", image.getId());
  }

  @Nullable
  public CloudImage getImage(@NotNull final String profileId, @NotNull final String id) {
    CloudProfile profile = findProfileGloballyById(profileId);
    if (profile == null) return null;
    return getClient(profile).findImageById(id);
  }

  @Nullable
  public CloudInstance getInstance(@NotNull final String profileId, @NotNull final String imageId, @NotNull final String id) {
    CloudProfile profile = findProfileGloballyById(profileId);
    if (profile == null) return null;
    return myCloudManager.findInstanceById(profile.getProjectId(), profile.getProfileId(), id);
  }

  public static class ImageIdData {
    public String profileId;
    public String id;

    public ImageIdData(@NotNull final String value) {
      Locator locator = new Locator(value, "profileId", "id");
      profileId = locator.getSingleDimensionValue("profileId");
      id = locator.getSingleDimensionValue("id");
      if (StringUtil.isEmpty(profileId) || StringUtil.isEmpty(id)) {
        throw new LocatorProcessException("Invalid cloud image id \"" + value + "\": should be in the form \"profileId:<profileId>,id:<imageId>\"");
      }
      locator.checkLocatorFullyProcessed();
    }
  }

  public static class InstanceIdData {
    public String profileId;
    public String imageId;
    public String id;

    public InstanceIdData(@NotNull final String value) {
      Locator locator = new Locator(value, "profileId", "id");
      profileId = locator.getSingleDimensionValue("profileId");
      imageId = locator.getSingleDimensionValue("imageId");
      id = locator.getSingleDimensionValue("id");
      if (StringUtil.isEmpty(profileId) || StringUtil.isEmpty(imageId) || StringUtil.isEmpty(id)) {
        throw new LocatorProcessException("Invalid cloud instance id \"" + value + "\": should be in the form \"profileId:<profileId>,imageId:<imageId>,id:<instanceId>\"");
      }
      locator.checkLocatorFullyProcessed();
    }
  }
}
