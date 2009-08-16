/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.sun.jersey.spi.resource.Singleton;
import javax.ws.rs.*;
import jetbrains.buildServer.server.rest.AgentsSearchFields;
import jetbrains.buildServer.server.rest.DataProvider;
import jetbrains.buildServer.server.rest.data.agent.Agent;
import jetbrains.buildServer.server.rest.data.agent.Agents;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@Path("/httpAuth/api/agents")
@Singleton
public class AgentRequest {
  private final DataProvider myDataProvider;

  public AgentRequest(final DataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public static String getAgentHref(@NotNull final SBuildAgent agent) {
    return "/httpAuth/api/agents/id:" + agent.getId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  /*
  @XmlElementWrapper(name = "agents") //todo: investigate why this is ignored...
  @XmlElement(name = "agent") //todo: investigate why this is ignored...
  public List<AgentRef> serveAgents(@QueryParam("includeDisconnected") @DefaultValue("true") boolean includeDisconnected,
  */
  public Agents serveAgents(@QueryParam("includeDisconnected") @DefaultValue("true") boolean includeDisconnected,
                            @QueryParam("includeUnauthorized") @DefaultValue("true") boolean includeUnauthorized) {
    return new Agents(myDataProvider.getAllAgents(new AgentsSearchFields(includeDisconnected, includeUnauthorized)));
  }

  @GET
  @Path("/{agentLocator}")
  @Produces({"application/xml", "application/json"})
  public Agent serveBuild(@PathParam("agentLocator") String agentLocator) {
    return new Agent(myDataProvider.getAgent(agentLocator));
  }
}
