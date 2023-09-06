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

package jetbrains.buildServer.server.rest.request.pages;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntriesCollector;
import jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntry;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.pages.problems.TestFailuresProblemEntries;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import org.jetbrains.annotations.NotNull;

@Path(jetbrains.buildServer.server.rest.request.pages.ProblemEntriesRequest.API_SUB_URL)
public class ProblemEntriesRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private TestFailuresProblemEntriesCollector myProblemEntriesCollector;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanFactory myFactory;

  public static final String API_SUB_URL = Constants.API_URL + "/pages/problems";

  @GET
  @Produces({"application/xml", "application/json"})
  public TestFailuresProblemEntries getEntries(@QueryParam("locator") String locatorText,
                                               @QueryParam("fields") String fieldsText,
                                               @Context UriInfo uriInfo,
                                               @Context HttpServletRequest request) {
    Locator locator = new Locator(locatorText);
    Fields fields = new Fields(fieldsText);

    PagedSearchResult<TestFailuresProblemEntry> result = myProblemEntriesCollector.getItems(locator);
    PagerDataImpl pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorText, "locator");

    return new TestFailuresProblemEntries(result.getEntries(), fields, pager, myBeanContext);
  }
}
