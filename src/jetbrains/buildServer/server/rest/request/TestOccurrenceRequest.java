package jetbrains.buildServer.server.rest.request;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.13
 */
@Path(TestOccurrenceRequest.API_SUB_URL)
public class TestOccurrenceRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private TestOccurrenceFinder myTestOccurrenceFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanFactory myBeanFactory;

  public static final String API_SUB_URL = Constants.API_URL + "/testOccurrences";

  public static String getHref() {
    return API_SUB_URL;
  }

  public static String getHref(final @NotNull SBuild build) {
    return API_SUB_URL + "?locator=" + TestOccurrenceFinder.BUILD + ":(" + BuildRequest.getBuildLocator(build) + ")"; //todo: use location rendering here
  }

  public static String getHref(final @NotNull STestRun testRun) {
    return API_SUB_URL + "/" + getTestRunLocator(testRun);
  }

  public static String getTestRunLocator(final @NotNull STestRun testRun) {
    return TestOccurrenceFinder.TEST_NAME_ID + ":" + testRun.getTest().getTestNameId() + "," + TestOccurrenceFinder.BUILD + ":(" + BuildRequest.getBuildLocator(testRun.getBuild()) + ")";
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
  public TestOccurrences getTestOccurrences(@QueryParam("locator") String locatorText, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<STestRun> result = myTestOccurrenceFinder.getItems(locatorText);

    return new TestOccurrences(result.myEntries,
                               new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result.myStart,
                                             result.myCount, result.myEntries.size(),
                                             locatorText,
                                             "locator"), new BeanContext(myBeanFactory, myServiceLocator, myApiUrlBuilder));
  }

  @GET
  @Path("/{testLocator}")
  @Produces({"application/xml", "application/json"})
  public TestOccurrence serveInstance(@PathParam("testLocator") String locatorText) {
    return new TestOccurrence(myTestOccurrenceFinder.getItem(locatorText), new BeanContext(myBeanFactory, myServiceLocator, myApiUrlBuilder), true);
  }
}