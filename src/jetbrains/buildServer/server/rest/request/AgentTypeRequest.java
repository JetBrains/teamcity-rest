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
import io.swagger.annotations.ApiParam;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.finder.impl.AgentTypeFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.agent.AgentType;
import jetbrains.buildServer.server.rest.model.agent.AgentTypes;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;

@Path(AgentTypeRequest.API_URL)
@Api("AgentType")
public class AgentTypeRequest {
  @Context
  private DataProvider myDataProvider;
  @Context
  private ApiUrlBuilder myApiUrlBuilder;
  @Context
  @NotNull
  private AgentTypeFinder myAgentTypeFinder;
  @Context
  @NotNull
  private ServiceLocator myServiceLocator;
  @Context
  @NotNull
  private BeanContext myBeanContext;

  public static final String API_URL = Constants.API_URL + "/agentTypes";

  public static String getHref() {
    return API_URL;
  }

  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get all known agent types.", nickname = "getAllAgentTypes", hidden = true)
  public AgentTypes serveAgentTypes(@ApiParam(format = LocatorName.AGENT_TYPE) @QueryParam("locator") String locator,
                                    @QueryParam("fields") String fields,
                                    @Context UriInfo uriInfo, @Context HttpServletRequest request) {

    PagedSearchResult<SAgentType> result = myAgentTypeFinder.getItems(locator);

    final PagerData pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator");
    return new AgentTypes(result.getEntries(), new Fields(fields), pager, myBeanContext);
  }

  @GET
  @Path("/{agentTypeLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value = "Get agent type matching the locator.", nickname = "getAgentType")
  public AgentType serveAgentType(@ApiParam(format = LocatorName.AGENT) @PathParam("agentTypeLocator") String agentTypeLocator,
                                  @QueryParam("fields") String fields) {
    return new AgentType(myAgentTypeFinder.getItem(agentTypeLocator), new Fields(fields), myServiceLocator, myApiUrlBuilder);
  }
}
