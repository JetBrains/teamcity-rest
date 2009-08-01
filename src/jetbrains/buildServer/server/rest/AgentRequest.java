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

package jetbrains.buildServer.server.rest;

import com.sun.jersey.spi.resource.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.ws.rs.*;
import javax.xml.bind.annotation.XmlElementWrapper;
import jetbrains.buildServer.server.rest.data.agent.Agent;
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
  @XmlElementWrapper(name = "agents")
  public List<Agent> serveAgents(@QueryParam("includeDisconnected") @DefaultValue("true") boolean includeDisconnected,
                                 @QueryParam("includeUnauthorized") @DefaultValue("true") boolean includeUnauthorized) {
    final ArrayList<Agent> result = new ArrayList<Agent>();
    final Collection<SBuildAgent> agents = myDataProvider.getAllAgents(new AgentsSearchFields(includeDisconnected, includeUnauthorized));
    for (SBuildAgent agent : agents) {
      result.add(new Agent(agent));
    }
    return result;
  }

  @GET
  @Path("/{agentLocator}")
  @Produces({"application/xml", "application/json"})
  public Agent serveBuild(@PathParam("agentLocator") String agentLocator) {
    return new Agent(myDataProvider.getAgent(agentLocator));
  }
}
