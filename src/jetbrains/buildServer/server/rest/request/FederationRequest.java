/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.federation.ConnectedServers;
import jetbrains.buildServer.federation.TeamCityServer;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.federation.FederationServer;
import jetbrains.buildServer.server.rest.model.federation.FederationServers;
import jetbrains.buildServer.util.StringUtil;

import static java.util.stream.Collectors.toList;

@Path(FederationRequest.API_FEDERATION_URL)
@Api
public class FederationRequest {
  public static final String API_FEDERATION_URL = Constants.API_URL + "/federation";
  @Context private ServiceLocator myServiceLocator;
  @Context private ApiUrlBuilder myApiUrlBuilder;

  @GET
  @Path("/servers")
  @Produces({"application/xml", "application/json"})
  public FederationServers servers(@QueryParam("fields") String fields) {
    ConnectedServers connectedServers = myServiceLocator.getSingletonService(ConnectedServers.class);
    return new FederationServers(connectedServers.getAttachedServers(), new Fields(fields));
  }

  @PUT
  @Path("/servers")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public FederationServers addServer(FederationServers servers) {
    if (servers.servers.stream().map(FederationServer::getUrl).anyMatch(StringUtil::isEmpty)) {
      throw new BadRequestException("Server url cannot be empty.");
    }

    myServiceLocator.getSingletonService(ConnectedServers.class)
                    .setAttachedServer(servers.servers.stream().map(server -> new TeamCityServer(server.getUrl(), server.getName())).collect(toList()));

    return servers;
  }
}
