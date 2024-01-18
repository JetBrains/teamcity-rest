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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import jetbrains.buildServer.server.rest.model.server.GlobalSettings;
import jetbrains.buildServer.server.rest.service.rest.ServerGlobalSettingsRestService;
import org.jetbrains.annotations.NotNull;

@Path(ServerGlobalSettingsRequest.API_SUB_URL)
@Api("Global Server Settings")
public class ServerGlobalSettingsRequest {
  public static final String API_SUB_URL = Constants.API_URL + ServerRequest.SERVER_REQUEST_PATH + "/globalSettings";

  @Context private ServerGlobalSettingsRestService myServerGlobalSettingsRestService;

  @GET
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value = "Get global settings.", nickname = "getGlobalSettings")
  public GlobalSettings getGlobalSettings() {
    return myServerGlobalSettingsRestService.getGlobalSettings();
  }

  @PUT
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value = "Set global settings.", nickname = "setGlobalSettings")
  public GlobalSettings setGlobalSettings(GlobalSettings settings) {
    return myServerGlobalSettingsRestService.setGlobalSettings(settings);
  }

  public void initForTests(
    @NotNull ServerGlobalSettingsRestService serverGlobalSettingsRestService) {
    myServerGlobalSettingsRestService = serverGlobalSettingsRestService;
  }
}
