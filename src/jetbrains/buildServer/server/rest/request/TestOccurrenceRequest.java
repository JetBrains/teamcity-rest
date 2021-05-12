/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrencesCachedInfo;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 * Date: 16.11.13
 */
@Path(TestOccurrenceRequest.API_SUB_URL)
@Api("TestOccurrence")
public class TestOccurrenceRequest {
  public static final String API_SUB_URL = Constants.API_URL + "/testOccurrences";
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private TestOccurrenceFinder myTestOccurrenceFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;

  public static String getHref() {
    return API_SUB_URL;
  }

  public static String getHref(final @NotNull SBuild build) {
    return API_SUB_URL + "?locator=" + TestOccurrenceFinder.getTestRunLocator(build);
  }

  public static String getHref(final @NotNull STest test) {
    return API_SUB_URL + "?locator=" + TestOccurrenceFinder.getTestRunLocator(test);
  }

  public static String getHref(final @NotNull STestRun testRun) {
    return API_SUB_URL + "/" + TestOccurrenceFinder.getTestRunLocator(testRun);
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
  @ApiOperation(value="Get all test occurrences.",nickname="getAllTestOccurrences")
  public TestOccurrences getTestOccurrences(@ApiParam(format = LocatorName.TEST_OCCURRENCE) @QueryParam("locator") String locatorText,
                                            @QueryParam("fields") String fields,
                                            @Context UriInfo uriInfo,
                                            @Context HttpServletRequest request) {
    String locator = TestOccurrenceFinder.patchLocatorForPersonalBuilds(locatorText, request);
    TestOccurrencesCachedInfo info = myTestOccurrenceFinder.tryGetCachedInfo(locator, fields);
    if(info.getShortStatistics() != null) {
      // Short href and pager data are meaningless in a case when we need only some counters.

      if(info.filteringRequired()) {
        // We need a locator as getLocator(String) calls locator.isFullyProcessed() which breaks everything
        Locator locator1 = Locator.createPotentiallyEmptyLocator(locator);

        // Due to reasons, in composite builds MultiTestRun.getBuild() will return different build than specified in the locator.
        // At the time of writing this, the returned build will be one of the non-composite snapshot dependencies.
        // We are okay with it as we account for a BUILD dimension when retrieving short statistics in the first place.
        // However, let's skip filtering as it will filter out legitimate results.
        locator1.markUsed(Collections.singleton(TestOccurrenceFinder.BUILD));
        ItemFilter<STestRun> filter = myTestOccurrenceFinder.getFilter(locator1);
        PagingItemFilter<STestRun> pagingFilter = myTestOccurrenceFinder.getPagingFilter(locator1, filter);
        FilterItemProcessor<STestRun> processor = new FilterItemProcessor<>(pagingFilter);

        info.getShortStatistics().getFailedTestsIncludingMuted().forEach(processor::processItem);

        PagedSearchResult<STestRun> pagedResult = new PagedSearchResult<>(processor.getResult(),
                                                                          pagingFilter.getStart(), pagingFilter.getCount(), processor.getProcessedItemsCount(),
                                                                          pagingFilter.getLookupLimit(), pagingFilter.isLookupLimitReached(), pagingFilter.getLastProcessedItem());

        return new TestOccurrences(pagedResult.myEntries, null,
                                   uriInfo == null ? null : uriInfo.getRequestUri().toString(),
                                   uriInfo == null ? null : new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), pagedResult, locatorText, "locator"),
                                   new Fields(fields), myBeanContext);
      }

      return new TestOccurrences(null, info.getShortStatistics(), null, null, new Fields(fields), myBeanContext);
    }

    final PagedSearchResult<STestRun> result = myTestOccurrenceFinder.getItems(locator);

    return new TestOccurrences(result.myEntries,
                               null,
                               uriInfo == null ? null : uriInfo.getRequestUri().toString(),
                               uriInfo == null ? null : new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorText, "locator"),
                               new Fields(fields),
                               myBeanContext
    );
  }

  @GET
  @Path("/{testLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a matching test occurrence.",nickname="getTestOccurrence")
  public TestOccurrence serveInstance(@ApiParam(format = LocatorName.TEST_OCCURRENCE) @PathParam("testLocator") String locatorText,
                                      @QueryParam("fields") String fields,
                                      @Context HttpServletRequest request) {
    String locator = TestOccurrenceFinder.patchLocatorForPersonalBuilds(locatorText, request);

    return new TestOccurrence(myTestOccurrenceFinder.getItem(locator), myBeanContext, new Fields(fields));
  }

  void initForTests(
    @NotNull ServiceLocator serviceLocator,
    @NotNull TestOccurrenceFinder testOccurrenceFinder,
    @NotNull ApiUrlBuilder apiUrlBuilder,
    @NotNull final BeanContext beanContext) {
    myServiceLocator = serviceLocator;
    myTestOccurrenceFinder = testOccurrenceFinder;
    myApiUrlBuilder = apiUrlBuilder;
    myBeanContext = beanContext;
  }
}