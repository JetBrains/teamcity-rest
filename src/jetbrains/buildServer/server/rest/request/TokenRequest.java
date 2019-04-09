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

package jetbrains.buildServer.server.rest.request;

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.auth.TokenAuthenticationModel;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.server.rest.data.UserFinder;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.impl.DebugLogUtils;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitrii Bogdanov
 */
@Path(TokenRequest.API_TOKENS_URL)
@Api("Token")
public class TokenRequest {
  private static final Logger LOG = Loggers.SERVER;
  private static final String TOKENS_ROOT_REQUEST_PATH = "/tokens";
  static final String API_TOKENS_URL = Constants.API_URL + TOKENS_ROOT_REQUEST_PATH;
  @Context
  private ServiceLocator myServiceLocator;
  @Context
  private BeanContext myBeanContext;

  @POST
  @Path("/{name}")
  @Produces({"application/xml", "application/json"})
  public String createToken(@PathParam("name") @NotNull final String name,
                            @Context @NotNull final BeanContext beanContext) {
    final TokenAuthenticationModel tokenAuthenticationModel = myServiceLocator.getSingletonService(TokenAuthenticationModel.class);
    final SUser currentUser = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    DebugLogUtils.debug(LOG, "Creating token with name '", name, "' for user '", currentUser.getId(), "'");
    return tokenAuthenticationModel.createToken(currentUser.getId(), name);
  }

  @GET
  @Path("/")
  @Produces({"application/xml", "application/json"})
  public List<String> listUserTokens(@Context @NotNull final BeanContext beanContext) {
    final TokenAuthenticationModel tokenAuthenticationModel = myServiceLocator.getSingletonService(TokenAuthenticationModel.class);
    final SUser currentUser = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    DebugLogUtils.debug(LOG, "Listing tokens for user '", currentUser.getId(), "'");
    return tokenAuthenticationModel.getUserTokenNames(currentUser.getId());
  }

  @DELETE
  @Path("/{name}")
  @Produces({"application/xml", "application/json"})
  public void deleteToken(@PathParam("name") @NotNull final String name,
                          @Context @NotNull final BeanContext beanContext) {
    final TokenAuthenticationModel tokenAuthenticationModel = myServiceLocator.getSingletonService(TokenAuthenticationModel.class);
    final SUser currentUser = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    DebugLogUtils.debug(LOG, "Deleting token with name '", name, "' for user '", currentUser.getId(), "'");
    tokenAuthenticationModel.deleteToken(currentUser.getId(), name);
  }
}
