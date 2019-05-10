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

import io.swagger.annotations.Api;
import java.util.stream.Collectors;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.model.server.ClusterNode;
import jetbrains.buildServer.server.rest.model.server.ClusterNodes;
import jetbrains.buildServer.serverSide.TeamCityNodes;

/**
 * Api for cluster related requests.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
@Path(ClusterRequest.API_CLUSTER_URL)
@Api("Cluster")
public class ClusterRequest {
  public static final String API_CLUSTER_URL = Constants.API_URL + "/cluster";

  @Context private ServiceLocator myServiceLocator;

  @GET
  @Path("/nodes")
  @Produces({"application/xml", "application/json"})
  public ClusterNodes getNodes() {
    final TeamCityNodes teamCityNodes = getTeamCityNodes();
    return new ClusterNodes(
      teamCityNodes.getOnlineNodes().stream().map(node ->
        new ClusterNode(node.getId(), node.getUrl(), node.isOnline(), node.getDescription()
      )
    ).collect(Collectors.toList()));
  }

  private TeamCityNodes getTeamCityNodes() {
    return myServiceLocator.getSingletonService(TeamCityNodes.class);
  }
}
