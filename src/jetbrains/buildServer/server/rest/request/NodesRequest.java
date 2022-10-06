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
import io.swagger.annotations.ApiParam;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.TeamCityNodeFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.nodes.*;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.NodeResponsibility;
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
  @Context private TeamCityNodeFinder myFinder;


  @NotNull
  public static String getHref() {
    return API_NODES_URL;
  }

  @GET
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value = "Get all TeamCity nodes.", nickname = "getAllNodes")
  public Nodes getAllNodes(@ApiParam(format = LocatorName.TEAMCITY_NODE) @QueryParam("locator") String locator, @QueryParam("fields") String fields) {
    return executeSafely(() -> {
      return new Nodes(myFinder.getItems(locator).myEntries, new Fields(fields), myPermissionChecker);
    });
  }

  @GET
  @Path("/{nodeLocator}")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value = "Get a node matching the locator.", nickname = "getNode")
  public Node getNode(@ApiParam(format = LocatorName.TEAMCITY_NODE) @PathParam("nodeLocator") String locator, @QueryParam("fields") String fields) {
    return executeSafely(() -> {
      TeamCityNode node = myFinder.getItem(locator);
      return new Node(node, new Fields(fields), myPermissionChecker);
    });
  }

  @GET
  @Path("/{nodeLocator}/enabledResponsibilities")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value = "Get all enabled responsibilities for a node matching the locator.", nickname = "getEnabledResponsibilities")
  public EnabledResponsibilities getEnabledResponsibilities(@ApiParam(format = LocatorName.TEAMCITY_NODE) @PathParam("nodeLocator") String locator, @QueryParam("fields") String fields) {
    return executeSafely(() -> {
      TeamCityNode node = myFinder.getItem(locator);
      return new EnabledResponsibilities(node, new Fields(fields));
    });
  }

  @GET
  @Path("/{nodeLocator}/effectiveResponsibilities")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value = "Get all effective responsibilities for a node matching the locator.", nickname = "getEffectiveResponsibilities")
  public EffectiveResponsibilities getEffectiveResponsibilities(@ApiParam(format = LocatorName.TEAMCITY_NODE) @PathParam("nodeLocator") String locator, @QueryParam("fields") String fields) {
    return executeSafely(() -> {
      TeamCityNode node = myFinder.getItem(locator);
      return new EffectiveResponsibilities(node, new Fields(fields));
    });
  }

  @GET
  @Path("/{nodeLocator}/disabledResponsibilities")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value = "Get all effective responsibilities for a node matching the locator.", nickname = "getDisabledResponsibilities")
  public DisabledResponsibilities getDisabledResponsibilities(@ApiParam(format = LocatorName.TEAMCITY_NODE) @PathParam("nodeLocator") String locator, @QueryParam("fields") String fields) {
    return executeSafely(() -> {
      TeamCityNode node = myFinder.getItem(locator);
      return new DisabledResponsibilities(node, new Fields(fields));
    });
  }

  @PUT
  @Path("/{nodeLocator}/enabledResponsibilities/{name}")
  @Consumes({MediaType.TEXT_PLAIN})
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value="Enables or disables responsibility for a node.", nickname="changeNodeResponsibility")
  public EnabledResponsibilities changeNodeResponsibility(@ApiParam(format = LocatorName.TEAMCITY_NODE) @PathParam("nodeLocator") String nodeLocator,
                                                          @PathParam("name") String name,
                                                          String state) {
    Set<String> assignable = NodeResponsibility.assignableResponsibilities().stream().map(n -> n.name()).collect(Collectors.toSet());
    if (!assignable.contains(name)) {
      throw new BadRequestException("Cannot change node responsibility " + name + ", supported responsibilities are: " + NodeResponsibility.assignableResponsibilities());
    }

    return executeSafely(() -> {
      TeamCityNode node = myFinder.getItem(nodeLocator);
      if (node.isMainNode()) {
        throw new BadRequestException("Main node responsibilities cannot be changed");
      }

      NodeResponsibility responsibility = NodeResponsibility.valueOf(name);
      boolean respState = Boolean.parseBoolean(state);
      if (responsibility == NodeResponsibility.MAIN_NODE && respState) {
        List<TeamCityNode> mainNodes = myFinder.getItems(String.format("role:%s,state:%s", Node.NodeRole.main_node.name(), Node.NodeState.online.name())).myEntries;
        if (!mainNodes.isEmpty()) {
          throw new BadRequestException("Cannot enable main node responsibility while there is online main node, main node id: " + mainNodes.get(0).getId());
        }
      }

      TeamCityNodes nodes = myServiceLocator.getSingletonService(TeamCityNodes.class);
      nodes.setEnabled(responsibility, node, respState);

      return new EnabledResponsibilities(myFinder.getItem(nodeLocator), Fields.LONG);
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
    result.myFinder = new TeamCityNodeFinder(beanContext.getServiceLocator().getSingletonService(TeamCityNodes.class));
    result.myServiceLocator = beanContext.getServiceLocator();
    result.myPermissionChecker = beanContext.getSingletonService(PermissionChecker.class);
    result.myApiUrlBuilder = beanContext.getApiUrlBuilder();
    return result;
  }
}
