/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
import io.swagger.annotations.ApiParam;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserAvatarsManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.DefaultMultipartHttpServletRequest;

import static jetbrains.buildServer.server.rest.request.AvatarRequest.API_AVATARS_URL;

@Api("Avatar")
@Path(API_AVATARS_URL)
public class AvatarRequest {

  public static final String API_AVATARS_URL = Constants.API_URL + "/avatars";
  public static final String AVATAR_MAX_SIZE = "teamcity.user.avatar.maxSize";  // in bytes
  public static final String AVATAR_CACHE_LIFETIME = "teamcity.user.avatar.cacheLifetime";  // in seconds

  @Context @NotNull private UserFinder myUserFinder;
  @Context @NotNull private UserAvatarsManager myUserAvatarsManager;

  @GET
  @Produces(MediaType.IMAGE_PNG_VALUE)
  @Path("/{userLocator}/{size}/avatar.png")
  public void getAvatar(
    @Context HttpServletResponse response,
    @ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator,
    @PathParam("size") Integer size
  ) throws IOException {
    if (size <= 0 || size > 300) throw new BadRequestException("\"size\" must be bigger than 0 and lower or equal than 300");

    final SUser user = myUserFinder.getItem(userLocator);

    final BufferedImage image = myUserAvatarsManager.getAvatar(user, size);
    if (image == null) {
      throw new NotFoundException("avatar (username: " + user.getUsername() + ") not found");
    }

    final int avatarCacheLifeTime = TeamCityProperties.getInteger(AVATAR_CACHE_LIFETIME, 86400);
    response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + avatarCacheLifeTime);

    ImageIO.write(image, "png", response.getOutputStream());
  }

  @PUT
  @Consumes(MediaType.MULTIPART_FORM_DATA_VALUE)
  @Path("/{userLocator}")
  public void putAvatar(
    @Context HttpServletRequest request,
    @ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator
  ) throws IOException {
    final SUser currentUser = myUserFinder.getCurrentUser();
    if (currentUser == null) throw new AccessDeniedException(null, "Log in to your account");

    final SUser targetUser = myUserFinder.getItem(userLocator);
    ServerAuthUtil.canEditUser(currentUser, targetUser);

    // get avatar file
    final MultipartFile avatar;
    if (request instanceof DefaultMultipartHttpServletRequest) {
      final DefaultMultipartHttpServletRequest multipartRequest = (DefaultMultipartHttpServletRequest)request;
      final Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
      avatar = fileMap.get("avatar");
    } else {
      throw new BadRequestException("You must specify the \"avatar\" field");
    }

    // check avatar file size
    final long avatarMaxSize = TeamCityProperties.getLong(AVATAR_MAX_SIZE, 10_000_000);
    if (avatar.getSize() >= avatarMaxSize) {
      throw new BadRequestException(String.format("The size of the avatar must be less than or equal to %d kilobytes (%d bytes)", avatarMaxSize / 1024, avatarMaxSize));
    }

    final BufferedImage image = ImageIO.read(avatar.getInputStream());

    myUserAvatarsManager.saveAvatar(targetUser, image);
  }

  @DELETE
  @Path("/{userLocator}")
  public void deleteAvatar(
    @ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator
  ) throws IOException {
    final SUser currentUser = myUserFinder.getCurrentUser();
    if (currentUser == null) throw new AccessDeniedException(null, "Log in to your account");

    final SUser targetUser = myUserFinder.getItem(userLocator);
    ServerAuthUtil.canEditUser(currentUser, targetUser);

    myUserAvatarsManager.deleteAvatar(targetUser);
  }
}
