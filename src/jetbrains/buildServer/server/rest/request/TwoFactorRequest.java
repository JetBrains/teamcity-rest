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

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.server.rest.data.TwoFactorSecretKeysUpdater;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.user.TwoFactorCredentials;
import jetbrains.buildServer.server.rest.model.user.TwoFactorRecoveryKeys;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.impl.TwoFactorConfirmationException;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.web.util.TwoFactorAuthUtil;
import org.jetbrains.annotations.NotNull;

@Path(Constants.API_URL + "/2FA")
public class TwoFactorRequest {
  @Context @NotNull private UserFinder myUserFinder;
  @Context @NotNull private TwoFactorSecretKeysUpdater myKeysUpdater;

  @POST
  @Path("/setup")
  @Produces({"application/json"})
  @ApiOperation(value = "Begin setup 2FA for current user, create secret key and recovery keys", nickname = "setup2FA")
  public TwoFactorCredentials setupTwoFactor() {
    final SUser user = myUserFinder.getCurrentUser();
    if (myKeysUpdater.hasEnabled2FA(user)) {
      throw new AccessDeniedException(user, "You already have enabled 2FA");
    }
    return myKeysUpdater.generateAndSetDraftCredentials(user);
  }

  @POST
  @Path("/confirm")
  @ApiOperation(value = "Confirm 2FA secret key", nickname = "confirm2FA")
  public void confirmTwoFactor(@QueryParam("uuid") String uuid, @QueryParam("password") int password, @Context HttpServletRequest request) {
    if (uuid == null){
      throw new BadRequestException("Missing parameter 'uuid'");
    }
    try {
      myKeysUpdater.confirmCredentials(myUserFinder.getCurrentUser(), UUID.fromString(uuid), password);
      TwoFactorAuthUtil.setTwoFactorCompletion(request);  // TODO: attempt to prevent instant kick after enabled 2FA without context request
    } catch (TwoFactorConfirmationException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @DELETE
  @Path("/{userLocator}/disable")
  @ApiOperation(value = "Delete secret key and recovery keys of 2FA for user", nickname = "disable2FA")
  public void deleteTwoFactor(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator) {
    final SUser targetUser = myUserFinder.getItem(userLocator, true);
    myKeysUpdater.disable2FA(targetUser);
  }

  @POST
  @Path("/newRecoveryKeys")
  @Produces({"application/json"})
  @ApiOperation(value = "Generate and set new recovery keys for user", nickname = "newRecoveryKeys")
  public TwoFactorRecoveryKeys serveRecoveryKeys() {
    final SUser user = myUserFinder.getCurrentUser();
    if (!myKeysUpdater.hasEnabled2FA(user)) {
      throw new AccessDeniedException(user, "You need to set up 2FA to generate recovery keys");
    }
    return new TwoFactorRecoveryKeys(myKeysUpdater.generateAndSetRecoveryKeys(user));
  }

  @POST
  @Path("/{userLocator}/refreshGracePeriod")
  @ApiOperation(value = "Refresh 2FA grace period for user", nickname = "refreshGracePeriod")
  public void refreshGracePeriod(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator) {
    final SUser targetUser = myUserFinder.getItem(userLocator, true);
    try {
      myKeysUpdater.refreshGracePeriod(targetUser);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Refresh of grace period is not applicable - user has 2FA or mandatory mode is turned off");
    }
  }

  public void initForTests(@NotNull final BeanContext beanContext) {
    myUserFinder = beanContext.getSingletonService(UserFinder.class);
    myKeysUpdater = beanContext.getSingletonService(TwoFactorSecretKeysUpdater.class);
  }

}
