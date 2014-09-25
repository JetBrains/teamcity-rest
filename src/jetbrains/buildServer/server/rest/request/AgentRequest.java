/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.SBuildAgent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 01.08.2009
 */
@Path(AgentRequest.API_AGENTS_URL)
public class AgentRequest {
  @Context private DataProvider myDataProvider;
  @Context private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private AgentPoolsFinder myAgentPoolsFinder;
  @Context @NotNull private AgentFinder myAgentFinder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;

  public static final String API_AGENTS_URL = Constants.API_URL + "/agents";

  public static String getHref() {
    return API_AGENTS_URL;
  }

  public static String getAgentHref(@NotNull final SBuildAgent agent) {
    return API_AGENTS_URL + "/id:" + agent.getId();
  }

  /**
   * Returns list of agents
   * @param includeDisconnected Deprecated, use "locator" parameter instead
   * @param includeUnauthorized Deprecated, use "locator" parameter instead
   * @param locator
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public Agents serveAgents(@QueryParam("includeDisconnected") Boolean includeDisconnected,
                            @QueryParam("includeUnauthorized") Boolean includeUnauthorized,
                            @QueryParam("locator") String locator,
                            @QueryParam("fields") String fields,
                            @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    //pre-8.1 compatibility:
    String locatorToUse = locator;
    if (includeDisconnected != null && !includeDisconnected){
      final Locator parsedLocator = StringUtil.isEmpty(locatorToUse) ? Locator.createEmptyLocator() : new Locator(locatorToUse);
      final String dimension = parsedLocator.getSingleDimensionValue(AgentFinder.CONNECTED);
      if (dimension != null && !"true".equals(dimension)){
        throw new BadRequestException("Both 'includeDisconnected' URL parameter and '" + AgentFinder.CONNECTED + "' locator dimension are specified. Please use locator only.");
      }
      locatorToUse = parsedLocator.setDimensionIfNotPresent(AgentFinder.CONNECTED, "true").getStringRepresentation();
    }
    if (includeUnauthorized != null && !includeUnauthorized){
      final Locator parsedLocator = StringUtil.isEmpty(locatorToUse) ? Locator.createEmptyLocator() : new Locator(locatorToUse);
      final String dimension = parsedLocator.getSingleDimensionValue(AgentFinder.AUTHORIZED);
      if (dimension != null && !"true".equals(dimension)){
        throw new BadRequestException("Both 'includeUnauthorized' URL parameter and '" + AgentFinder.AUTHORIZED + "' locator dimension are specified. Please use locator only.");
      }
      locatorToUse = parsedLocator.setDimensionIfNotPresent(AgentFinder.AUTHORIZED, "true").getStringRepresentation();
    }

    final PagedSearchResult<SBuildAgent> result = myAgentFinder.getItems(locatorToUse);

    final PagerData pager = new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result.myStart,
                                          result.myCount, result.myEntries.size(),
                                          locatorToUse,
                                          "locator");
    return new Agents(result.myEntries, pager,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{agentLocator}")
  @Produces({"application/xml", "application/json"})
  public Agent serveAgent(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    return new Agent(myAgentFinder.getItem(agentLocator), myAgentPoolsFinder, new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{agentLocator}")
  public void deleteAgent(@PathParam("agentLocator") String agentLocator) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    myServiceLocator.getSingletonService(BuildAgentManager.class).removeAgent(agent, myDataProvider.getCurrentUser());
  }

  @GET
  @Path("/{agentLocator}/pool")
  @Produces({"application/xml", "application/json"})
  public AgentPool getAgentPool(@PathParam("agentLocator") String agentLocator, @QueryParam("fields") String fields) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    return new AgentPool(myAgentPoolsFinder.getAgentPool(agent),  new Fields(fields),myBeanContext);
  }

  @PUT
  @Path("/{agentLocator}/pool")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public AgentPool setAgentPool(@PathParam("agentLocator") String agentLocator, AgentPool agentPool) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    myDataProvider.addAgentToPool(agentPool.getAgentPoolFromPosted(myAgentPoolsFinder), agent);
    return new AgentPool(myAgentPoolsFinder.getAgentPool(agent), Fields.LONG, myBeanContext);
  }

  @GET
  @Path("/{agentLocator}/{field}")
  @Produces("text/plain")
  public String serveAgentField(@PathParam("agentLocator") String agentLocator, @PathParam("field") String fieldName) {
    return Agent.getFieldValue(myAgentFinder.getItem(agentLocator), fieldName);
  }

  @PUT
  @Path("/{agentLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setAgentField(@PathParam("agentLocator") String agentLocator, @PathParam("field") String fieldName, String value) {
    final SBuildAgent agent = myAgentFinder.getItem(agentLocator);
    Agent.setFieldValue(agent, fieldName, value, myDataProvider);
    return Agent.getFieldValue(agent, fieldName);
  }
}
