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
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Null;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.StandardProperties;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.omg.PortableInterceptor.LOCATION_FORWARD;

/**
 * @author Yegor.Yarko
 * Date: 16.11.13
 */
@Path(TestOccurrenceRequest.API_SUB_URL)
@Api("TestOccurrence")
public class TestOccurrenceRequest {
  public static final String API_SUB_URL = Constants.API_URL + "/testOccurrences";
  @Context @NotNull public BeanContext myBeanContext;
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
    String locator = patchLocatorForPersonalBuilds(locatorText, request);
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
    String locator = patchLocatorForPersonalBuilds(locatorText, request);

    return new TestOccurrence(myTestOccurrenceFinder.getItem(locator), myBeanContext, new Fields(fields));
  }

  /** Ensures we don't include personal builds by default (except when build locator is provided) and sets an internal dimension with user id. */
  @Nullable
  private String patchLocatorForPersonalBuilds(@Nullable String locator, @Nullable HttpServletRequest request) {
    if(locator == null || request == null) {
      return locator;
    }

    Locator patchedLocator = new Locator(locator);
    if(patchedLocator.isAnyPresent(TestOccurrenceFinder.INCLUDE_ALL_PERSONAL)) {
      // We do not want somebody to set this dimension explicitely.
      throw new BadRequestException(String.format("%s dimension is not supported.", TestOccurrenceFinder.INCLUDE_ALL_PERSONAL));
    }

    patchedLocator.setDimensionIfNotPresent(TestOccurrenceFinder.INCLUDE_PERSONAL, Locator.BOOLEAN_FALSE);

    SUser user = SessionUser.getUser(request);
    if(user != null) {
      patchedLocator.setDimension(TestOccurrenceFinder.PERSONAL_FOR_USER, Long.toString(user.getId()));
    }

    return patchedLocator.getStringRepresentation();
  }

  void initForTests(@NotNull final BeanContext beanContext) {
    myServiceLocator = beanContext.getSingletonService(ServiceLocator.class);
    myTestOccurrenceFinder = beanContext.getSingletonService(TestOccurrenceFinder.class);
    myApiUrlBuilder = beanContext.getApiUrlBuilder();
    myBeanContext = beanContext;
  }

  @GET
  @Path("/groupBy/{fieldName}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(hidden = true, value = "highly experimental")
  public GroupedTestOccurrences serveGroupedTestOccurrences(@QueryParam("locator") String locatorText,
                                                            @PathParam("fieldName") String fieldName,
                                                            @QueryParam("fields") String fields,
                                                            @QueryParam("depth")
                                                            @NotNull
                                                            @DefaultValue("3")
                                                            @Min(value = 1, message = "The value must be > 0")
                                                            @Max(value = 16, message = "The value must <= 16") final Integer depth,
                                                            @Context UriInfo uriInfo,
                                                            @Context HttpServletRequest request) {
    if (depth < 1 || depth > 16) {
      throw new BadRequestException("depth should be between 0 and 17");
    }
    if (!"package".equals(fieldName)) {
      throw new BadRequestException("Only grouping by 'package' is currently supported");
    }

    String patchedLocator = patchLocatorForPersonalBuilds(locatorText, request);
    final List<STestRun> items = myTestOccurrenceFinder.getItems(patchedLocator).myEntries;
    return new GroupedTestOccurrences(items, new Fields(fields), myBeanContext, depth);
  }
}