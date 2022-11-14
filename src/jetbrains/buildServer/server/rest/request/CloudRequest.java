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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.clouds.server.TerminateInstanceReason;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.cloud.*;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.CachingValue;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@Path(CloudRequest.API_CLOUD_URL)
@Api("CloudInstance")
public class CloudRequest {
  @Context private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private CloudInstanceFinder myCloudInstanceFinder;
  @Context @NotNull private CloudImageFinder myCloudImageFinder;
  @Context @NotNull private CloudProfileFinder myCloudProfileFinder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;

  public static final String API_CLOUD_URL = Constants.API_URL + "/cloud";

  public static String getHref() {
    return API_CLOUD_URL;
  }

  @NotNull
  public static String getHref(@NotNull CloudInstanceData item) {
    return API_CLOUD_URL + "/instances" + "/" + CloudInstanceFinder.getLocator(item);
  }

  @NotNull
  public static String getHref(@NotNull final jetbrains.buildServer.clouds.CloudImage item, @NotNull final CloudUtil cloudUtil) {
    return getItemHref("/images", CloudImageFinder.getLocator(item, cloudUtil));
  }

  @NotNull
  public static String getHref(@NotNull final jetbrains.buildServer.clouds.CloudProfile item) {
    return getItemHref("/profiles", CloudProfileFinder.getLocator(item));
  }

  @NotNull
  public static String getItemHref(@NotNull final String pathSuffix, @NotNull final String locatorText) {
    return API_CLOUD_URL + pathSuffix + "/" + locatorText;
  }

  @NotNull
  public static String getItemsHref(@NotNull final String pathSuffix, @NotNull final String locatorText) {
    return API_CLOUD_URL + pathSuffix + "?locator=" + locatorText;
  }

  @NotNull
  public static String getInstancesHref(@NotNull final jetbrains.buildServer.clouds.CloudImage item, @NotNull final CloudUtil cloudUtil) {
    return getItemsHref("/instances", CloudInstanceFinder.getLocator(item, cloudUtil));
  }

  @NotNull
  public static String getImagesHref(@NotNull final jetbrains.buildServer.clouds.CloudProfile profile) {
    return getItemsHref("/images", CloudImageFinder.getLocator(profile));
  }

  @NotNull
  public static String getProfilesHref(@Nullable String baseLocator, @NotNull final SProject project) {
    return getItemsHref("/profiles", CloudProfileFinder.getLocator(baseLocator, project));
  }


  /**
   * Returns list of currently known cloud instances
   * @param locator
   */
  @GET
  @Path("/instances")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all cloud instances.",nickname="getAllCloudInstances")
  public CloudInstances serveInstances(@QueryParam("locator") String locator,
                                       @QueryParam("fields") String fields,
                                       @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<CloudInstanceData> result = myCloudInstanceFinder.getItems(locator);

    final PagerData pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator");
    return new CloudInstances(CachingValue.simple(() -> result.myEntries), pager,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/instances/{instanceLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get cloud instance matching the locator.",nickname="getCloudInstance")
  public CloudInstance serveInstance(@PathParam("instanceLocator") String instanceLocator, @QueryParam("fields") String fields) {
    return new CloudInstance(myCloudInstanceFinder.getItem(instanceLocator), new Fields(fields), myBeanContext);
  }

  /**
   * Starts a new instance
   */
  @POST
  @Path("/instances")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Start a new cloud instance.",nickname="startInstance")
  public void startInstance(CloudInstance cloudInstance, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final SUser user = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    jetbrains.buildServer.clouds.CloudInstance startedInstance = cloudInstance.startInstance(user, myServiceLocator);
  }

  @DELETE
  @Path("/instances/{instanceLocator}")
  @ApiOperation(value="Stop cloud instance matching the locator.",nickname="stopInstance")
  public void stopInstance(@PathParam("instanceLocator") String instanceLocator) {
    jetbrains.buildServer.clouds.CloudInstance instance = myCloudInstanceFinder.getItem(instanceLocator).getInstance();
    final SUser user = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    CloudUtil cloudUtil = myBeanContext.getSingletonService(CloudUtil.class);
    String profileId = cloudUtil.getProfileId(instance.getImage());
    if (profileId == null) {
      throw new InvalidStateException("Cannot find profile for the cloud image");
    }
    myBeanContext.getSingletonService(CloudManager.class).terminateInstance(profileId, instance.getImageId(), instance.getInstanceId(), TerminateInstanceReason.userAction(user));
  }

  /**
   * Returns list of currently known cloud images
   * @param locator
   */
  @GET
  @Path("/images")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all cloud images.",nickname="getAllCloudImages")
  public CloudImages serveImages(@QueryParam("locator") String locator,
                                 @QueryParam("fields") String fields,
                                 @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<jetbrains.buildServer.clouds.CloudImage> result = myCloudImageFinder.getItems(locator);

    final PagerData pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator");
    return new CloudImages(CachingValue.simple(() -> result.myEntries), pager, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/images/{imageLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get cloud image matching the locator.",nickname="getCloudImage")
  public CloudImage serveImage(@PathParam("imageLocator") String imageLocator, @QueryParam("fields") String fields) {
    return new CloudImage(myCloudImageFinder.getItem(imageLocator), new Fields(fields), myBeanContext);
  }

  /**
   * Returns list of currently known cloud profiles
   * @param locator
   */
  @GET
  @Path("/profiles")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all cloud profiles.",nickname="getAllCloudProfiles")
  public CloudProfiles serveProfiles(@QueryParam("locator") String locator,
                                     @QueryParam("fields") String fields,
                                     @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<jetbrains.buildServer.clouds.CloudProfile> result = myCloudProfileFinder.getItems(locator);

    final PagerData pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator");
    return new CloudProfiles(result.myEntries, pager,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/profiles/{profileLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get cloud profile matching the locator.",nickname="getCloudProfile")
  public CloudProfile serveProfile(@PathParam("profileLocator") String profileLocator, @QueryParam("fields") String fields) {
    return new CloudProfile(myCloudProfileFinder.getItem(profileLocator), new Fields(fields), myBeanContext);
  }
}
