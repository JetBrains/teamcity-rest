/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.AgentsSearchFields;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@Path(AgentRequest.API_AGENTS_URL)
public class AgentRequest {
  @Context
  private DataProvider myDataProvider;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;
  public static final String API_AGENTS_URL = Constants.API_URL + "/agents";

  public static String getAgentHref(@NotNull final SBuildAgent agent) {
    return API_AGENTS_URL + "/id:" + agent.getId();
  }

  @GET
  @Produces({"application/xml", "application/json"})
  public Agents serveAgents(@QueryParam("includeDisconnected") @DefaultValue("true") boolean includeDisconnected,
                            @QueryParam("includeUnauthorized") @DefaultValue("true") boolean includeUnauthorized) {
    return new Agents(myDataProvider.getAllAgents(new AgentsSearchFields(includeDisconnected, includeUnauthorized)), myApiUrlBuilder);
  }

  @GET
  @Path("/{agentLocator}")
  @Produces({"application/xml", "application/json"})
  public Agent serveAgent(@PathParam("agentLocator") String agentLocator) {
    return new Agent(myDataProvider.getAgent(agentLocator), myApiUrlBuilder);
  }

  @GET
  @Path("/{agentLocator}/{field}")
  @Produces("text/plain")
  public String serveAgentField(@PathParam("agentLocator") String agentLocator, @PathParam("field") String fieldName) {
    return Agent.getFieldValue(myDataProvider.getAgent(agentLocator), fieldName);
  }

  @PUT
  @Path("/{agentLocator}/{field}")
  @Consumes("text/plain")
  public void setAgentField(@PathParam("agentLocator") String agentLocator, @PathParam("field") String fieldName, String value) {
    Agent.setFieldValue(myDataProvider.getAgent(agentLocator), fieldName, value, myDataProvider);
  }
}
