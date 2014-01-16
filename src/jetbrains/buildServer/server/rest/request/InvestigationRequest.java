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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.buildType.Investigation;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.STest;
import org.jetbrains.annotations.NotNull;

/**
 *  Experimental, the requests and results returned will change in future versions!
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
@Path(InvestigationRequest.API_SUB_URL)
public class InvestigationRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private InvestigationFinder myInvestigationFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanFactory myBeanFactory;

  public static final String API_SUB_URL = Constants.API_URL + "/investigations";

  public static String getHref() {
    return API_SUB_URL;
  }

  public static String getHref(@NotNull final InvestigationWrapper investigation) {
    return API_SUB_URL + "/" + InvestigationFinder.getLocator(investigation);
  }

  public static String getHref(@NotNull final ProblemWrapper problem) {
    return API_SUB_URL + "?locator=" + InvestigationFinder.getLocator(problem);
  }

  public static String getHref(@NotNull final STest test) {
    return API_SUB_URL + "?locator=" + InvestigationFinder.getLocator(test);
  }

  public static String getHref(@NotNull final SBuildType buildType) {
    return API_SUB_URL + "?locator=" + InvestigationFinder.getLocator(buildType);
  }

  /*
  public static String getInvestigationHref(@NotNull final InvestigationWrapper investigation) {
    return API_SUB_URL + "?locator=" + VcsRootFinder.VCS_ROOT_DIMENSION + ":(id:" + vcsRoot.getExternalId() + ")";
  }
  */

  /**
   * Experimental, the requests and results returned will change in future versions!
   * @param locatorText
   * @param uriInfo
   * @param request
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public Investigations getInvestigations(@QueryParam("locator") String locatorText, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<InvestigationWrapper> result = myInvestigationFinder.getItems(locatorText);

    final PagerData pager = new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result.myStart,
                                            result.myCount, result.myEntries.size(),
                                            locatorText,
                                            "locator");
    return new Investigations(result.myEntries,
                              new Href(pager.getHref(), myApiUrlBuilder),
                              new Fields(fields, Fields.LONG),
                              pager,
                              new BeanContext(myBeanFactory, myServiceLocator, myApiUrlBuilder)
    );
  }

  @GET
  @Path("/{investigationLocator}")
  @Produces({"application/xml", "application/json"})
  public Investigation serveInstance(@PathParam("investigationLocator") String locatorText, @QueryParam("fields") String fields) {
    return new Investigation(myInvestigationFinder.getItem(locatorText), new Fields(fields, Fields.LONG),
                             new BeanContext(myBeanFactory, myServiceLocator, myApiUrlBuilder));
  }

  /*
  @GET
  @Path("/{investigationLocator}/{field}")
  @Produces("text/plain")
  public String serveInstanceField(@PathParam("investigationLocator") String locatorText, @PathParam("field") String fieldName) {
    InvestigationWrapper investigation = myInvestigationFinder.getItem(locatorText);
    return Investigation.getFieldValue(investigation, fieldName, myDataProvider);
  }

  @PUT
  @Path("/{investigationLocator}/{field}")
  @Consumes("text/plain")
  @Produces("text/plain")
  public String setInstanceField(@PathParam("investigationLocator") String locatorText,
                                 @PathParam("field") String fieldName, String newValue) {
    InvestigationWrapper investigation = myInvestigationFinder.getItem(locatorText);
    Investigation.setFieldValue(investigation, fieldName, newValue, myDataProvider);
    investigation.persist();
    return Investigation.getFieldValue(investigation, fieldName, myDataProvider);
  }
  */
}