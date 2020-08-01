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

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import jetbrains.buildServer.server.rest.data.AuditEventFinder;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.audit.AuditEvent;
import jetbrains.buildServer.server.rest.model.audit.AuditEvents;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.audit.AuditLogAction;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

@Path(AuditRequest.API_URL)
@Api("Audit")
public class AuditRequest {
  private static final Logger LOG = Logger.getInstance(AuditRequest.class.getName());
  public static final String API_URL = Constants.API_URL + "/audit";

  @Context @NotNull public AuditEventFinder myAuditEventFinder;
  @Context @NotNull public BeanContext myBeanContext;

  @GET
  @Produces({"application/xml", "application/json"})
  public AuditEvents get(@ApiParam(format = LocatorName.AUDIT) @QueryParam("locator") String locator,
                         @QueryParam("fields") String fields,
                         @Context UriInfo uriInfo,
                         @Context HttpServletRequest request) {
    PagedSearchResult<AuditLogAction> items = myAuditEventFinder.getItems(locator);
    final PagerData pagerData = new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), items, locator, "locator");
    return new AuditEvents(items.myEntries, pagerData, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{auditEventLocator}")
  @Produces({"application/xml", "application/json"})
  public AuditEvent getSingle(@ApiParam(format = LocatorName.AUDIT) @PathParam("auditEventLocator") String auditEventLocator,
                              @QueryParam("fields") String fields) {
    return new AuditEvent(myAuditEventFinder.getItem(auditEventLocator), new Fields(fields), myBeanContext);
  }
}
