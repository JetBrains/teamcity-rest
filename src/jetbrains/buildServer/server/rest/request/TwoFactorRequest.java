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
import java.util.Set;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.server.rest.data.TwoFactorSecretKeysUpdater;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.model.user.TwoFactorCredentials;
import jetbrains.buildServer.server.rest.model.user.TwoFactorRecoveryKeys;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

@Path(Constants.API_URL + "/2FA")
public class TwoFactorRequest {
  @Context @NotNull private UserFinder myUserFinder;
  @Context @NotNull private TwoFactorSecretKeysUpdater myKeysUpdater;

  @POST
  @Path("/setup")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Setup 2FA for current user, create secret key and recovery keys", nickname = "setup2FA")
  public TwoFactorCredentials setupTwoFactor() {
    // TODO: confirmation of secret key (react?)
    final SUser user = myUserFinder.getCurrentUser();
    final Set<String> recovery = myKeysUpdater.generateAndSetRecoveryKeys(user);
    final String secret = myKeysUpdater.generateAndSetSecretKey(user);
    return new TwoFactorCredentials(secret, new TwoFactorRecoveryKeys(recovery));
  }

  @DELETE
  @Path("/{userLocator}/disable")
  @ApiOperation(value = "Delete secret key and recovery keys of 2FA for user", nickname = "disable2FA")
  public void deleteTwoFactor(@ApiParam(format = LocatorName.USER) @PathParam("userLocator") String userLocator) {
    final SUser targetUser = myUserFinder.getItem(userLocator, true);
    myKeysUpdater.delete2FA(targetUser);
  }

  @POST
  @Path("/newRecoveryKeys")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Generate and set new recovery keys for user", nickname = "newRecoveryKeys")
  public TwoFactorRecoveryKeys serveRecoveryKeys() {
    final SUser user = myUserFinder.getCurrentUser();
    if (!myKeysUpdater.hasSetUp2FA(user)) {
      throw new AccessDeniedException(user, "You need to set up 2FA to generate recovery keys");
    }
    return new TwoFactorRecoveryKeys(myKeysUpdater.generateAndSetRecoveryKeys(user));
  }


  public void initForTests(@NotNull final BeanContext beanContext) {
    myUserFinder = beanContext.getSingletonService(UserFinder.class);
    myKeysUpdater = beanContext.getSingletonService(TwoFactorSecretKeysUpdater.class);
  }

}