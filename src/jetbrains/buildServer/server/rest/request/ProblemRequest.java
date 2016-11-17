/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.problem.ProblemFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.Problem;
import jetbrains.buildServer.server.rest.model.problem.Problems;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.11.13
 */
@Path(ProblemRequest.API_SUB_URL)
@Api("Problem")
public class ProblemRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private ProblemFinder myProblemFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanContext myBeanContext;

  public static final String API_SUB_URL = Constants.API_URL + "/problems";

  public static String getHref() {
    return API_SUB_URL;
  }

  public static String getHref(@NotNull final ProblemWrapper problem) {
    return API_SUB_URL + "/" + ProblemFinder.getLocator(problem);
  }

  /**
   * Experimental, the requests and results returned will change in future versions!
   *
   * @param locatorText
   * @param uriInfo
   * @param request
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public Problems getProblems(@QueryParam("locator") String locatorText, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<ProblemWrapper> result = myProblemFinder.getItems(locatorText);

    return new Problems(result.myEntries,
                        new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorText, "locator"),
                        new Fields(fields),
                        myBeanContext);
  }

  @GET
  @Path("/{problemLocator}")
  @Produces({"application/xml", "application/json"})
  public Problem serveInstance(@PathParam("problemLocator") String locatorText, @QueryParam("fields") String fields) {
    return new Problem(myProblemFinder.getItem(locatorText), new Fields(fields), myBeanContext);
  }
}