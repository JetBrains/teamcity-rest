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
import java.util.function.Supplier;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.nodes.Node;
import jetbrains.buildServer.server.rest.model.nodes.Nodes;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.TeamCityNode;
import jetbrains.buildServer.serverSide.TeamCityNodes;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import org.jetbrains.annotations.NotNull;

@Path(NodesRequest.API_NODES_URL)
@Api("Node")
public class NodesRequest {
  public static final String NODES_PATH = ServerRequest.SERVER_REQUEST_PATH + "/nodes";
  public static final String API_NODES_URL = Constants.API_URL + NODES_PATH;

  @Context private ServiceLocator myServiceLocator;
  @Context private ApiUrlBuilder myApiUrlBuilder;
  @Context private PermissionChecker myPermissionChecker;

  @NotNull
  public static String getHref() {
    return API_NODES_URL;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Nodes nodes(@QueryParam("fields") String fields) {
    return executeSafely(() -> {
      TeamCityNodes teamCityNodes = myServiceLocator.getSingletonService(TeamCityNodes.class);
      return new Nodes(teamCityNodes.getNodes(), new Fields(fields), myPermissionChecker);
    });
  }

  @GET
  @Path("/{nodeId}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get a node with specified id.", nickname = "getNode")
  public Node getNode(@PathParam("nodeId") final String nodeId, @QueryParam("fields") String fields) {
    return executeSafely(() -> {
      TeamCityNodes teamCityNodes = myServiceLocator.getSingletonService(TeamCityNodes.class);
      TeamCityNode found = null;
      for (TeamCityNode n: teamCityNodes.getNodes()) {
        if (n.getId().equals(nodeId)) {
          found = n;
          break;
        }
      }

      if (found == null) {
        throw new NotFoundException("Node with id '" + nodeId + "' does not exist.");
      }

      return new Node(found, new Fields(fields), myPermissionChecker);
    });
  }

  private <T> T executeSafely(@NotNull Supplier<T> action) {
    SecurityContextEx securityContext = myServiceLocator.getSingletonService(SecurityContextEx.class);
    boolean notAuthorizedRequest = securityContext.isSystemAccess();
    if (notAuthorizedRequest) {
      return securityContext.runAsUnchecked(SecurityContextImpl.NO_PERMISSIONS, new SecurityContextEx.RunAsActionWithResult<T>() {
        @Override
        public T run() throws Throwable {
          return action.get();
        }
      });
    }

    return action.get();
  }

  @NotNull
  public static NodesRequest createForTests(@NotNull final BeanContext beanContext) {
    NodesRequest result = new NodesRequest();
    result.myServiceLocator = beanContext.getServiceLocator();
    result.myPermissionChecker = beanContext.getSingletonService(PermissionChecker.class);
    result.myApiUrlBuilder = beanContext.getApiUrlBuilder();
    return result;
  }
}
