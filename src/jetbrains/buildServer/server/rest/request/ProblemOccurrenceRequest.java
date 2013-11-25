package jetbrains.buildServer.server.rest.request;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.problem.ProblemOccurrenceFinder;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrence;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 18.11.13
 */
@Path(ProblemOccurrenceRequest.API_SUB_URL)
public class ProblemOccurrenceRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private ProblemOccurrenceFinder myProblemOccurrenceFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanFactory myFactory;

  public static final String API_SUB_URL = Constants.API_URL + "/problemOccurrences";

  public static String getHref() {
    return API_SUB_URL;
  }

  public static String getHref(@NotNull final BuildProblem problem) {
    return API_SUB_URL + "/" + ProblemOccurrenceFinder.getProblemLocator(problem);
  }

  public static String getHref(final @NotNull SBuild build) {
    return API_SUB_URL + "?locator=" + ProblemOccurrenceFinder.BUILD + ":(" + BuildRequest.getBuildLocator(build) + ")"; //todo: use location rendering here
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
  public ProblemOccurrences getProblems(@QueryParam("locator") String locatorText, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<BuildProblem> result = myProblemOccurrenceFinder.getItems(locatorText);

    return new ProblemOccurrences(result.myEntries,
                              new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result.myStart,
                                            result.myCount, result.myEntries.size(),
                                            locatorText,
                                            "locator"), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{problemLocator}")
  @Produces({"application/xml", "application/json"})
  public ProblemOccurrence serveInstance(@PathParam("problemLocator") String locatorText) {
    return new ProblemOccurrence(myProblemOccurrenceFinder.getItem(locatorText), new BeanContext(myFactory, myServiceLocator, myApiUrlBuilder), true);
  }
}