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
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.problem.ProblemOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.ProblemWrapper;
import jetbrains.buildServer.server.rest.data.problem.scope.ProblemOccurrencesTreeCollector;
import jetbrains.buildServer.server.rest.data.problem.tree.ScopeTree;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrence;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrences;
import jetbrains.buildServer.server.rest.model.problem.scope.ProblemOccurrencesTree;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
@Path(ProblemOccurrenceRequest.API_SUB_URL)
@Api("ProblemOccurrence")
public class ProblemOccurrenceRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private ProblemOccurrenceFinder myProblemOccurrenceFinder;
  @Context @NotNull private ProblemOccurrencesTreeCollector myProblemOccurrencesTreeCollector;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanFactory myFactory;

  public static final String API_SUB_URL = Constants.API_URL + "/problemOccurrences";

  public static String getHref() {
    return API_SUB_URL;
  }

  public static String getHref(@NotNull final BuildProblem problem) {
    return API_SUB_URL + "/" + ProblemOccurrenceFinder.getProblemOccurrenceLocator(problem);
  }

  public static String getHref(@NotNull final ProblemWrapper problem) {
    return API_SUB_URL + "?locator=" + ProblemOccurrenceFinder.getProblemOccurrenceLocator(problem);
  }

  public static String getHref(final @NotNull SBuild build) {
    return API_SUB_URL + "?locator=" + ProblemOccurrenceFinder.getProblemOccurrenceLocator(build);
  }

  public static String getHref(final @NotNull BuildPromotion buildPromotion) {
    return API_SUB_URL + "?locator=" + ProblemOccurrenceFinder.getProblemOccurrenceLocator(buildPromotion);
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
  @ApiOperation(value="Get all build problem occurrences.", nickname="getAllBuildProblemOccurrences")
  public ProblemOccurrences getProblems(@ApiParam(format = LocatorName.PROBLEM_OCCURRENCE) @QueryParam("locator") String locatorText,
                                        @QueryParam("fields") String fields,
                                        @Context UriInfo uriInfo,
                                        @Context HttpServletRequest request) {
    final PagedSearchResult<BuildProblem> result = myProblemOccurrenceFinder.getItems(locatorText);

    return new ProblemOccurrences(result.getEntries(),
                                  uriInfo.getRequestUri().toString(),
                                  new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorText, "locator"),
                                  new Fields(fields),
                                  new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder)
    );
  }

  /** Experimental **/
  @GET
  @Path("/tree")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get tree of build problem occurrences.", nickname="getProblemsTree", hidden = true)
  public ProblemOccurrencesTree getProblemsTree(@QueryParam("locator") String locatorText,
                                                @QueryParam("fields") String fields,
                                                @Context UriInfo uriInfo,
                                                @Context HttpServletRequest request) {
    List<ScopeTree.Node<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters>> tree = myProblemOccurrencesTreeCollector.getTree(new Locator(locatorText));

    return new jetbrains.buildServer.server.rest.model.problem.scope.ProblemOccurrencesTree(tree, new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{problemLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a matching build problem occurrence.", nickname="getBuildProblemOccurrence")
  public ProblemOccurrence serveInstance(@ApiParam(format = LocatorName.PROBLEM_OCCURRENCE) @PathParam("problemLocator") String locatorText,
                                         @QueryParam("fields") String fields) {
    return new ProblemOccurrence(myProblemOccurrenceFinder.getItem(locatorText), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder),
                                  new Fields(fields));
  }
}