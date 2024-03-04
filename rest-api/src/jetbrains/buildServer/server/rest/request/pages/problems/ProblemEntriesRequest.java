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

package jetbrains.buildServer.server.rest.request.pages.problems;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.pages.problems.*;
import jetbrains.buildServer.server.rest.data.util.tree.Node;
import jetbrains.buildServer.server.rest.data.util.tree.ScopeTree;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerDataImpl;
import jetbrains.buildServer.server.rest.model.pages.problems.BuildProblemEntries;
import jetbrains.buildServer.server.rest.model.pages.problems.BuildProblemEntriesTree;
import jetbrains.buildServer.server.rest.model.pages.problems.TestFailuresProblemEntries;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.jetbrains.annotations.NotNull;

@Path(ProblemEntriesRequest.API_SUB_URL)
public class ProblemEntriesRequest {
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private TestFailuresProblemEntriesCollector myProblemEntriesCollector;
  @Context @NotNull private BuildProblemEntriesFinder myBuildProblemEntriesFinder;
  @Context @NotNull private BuildProblemEntriesTreeCollector myBuildProblemEntriesTreeCollector;

  public static final String API_SUB_URL = Constants.API_URL + "/pages/problems";

  @GET
  @Path(("/testFailures"))
  @Produces({"application/xml", "application/json"})
  public TestFailuresProblemEntries getTestEntries(@QueryParam("locator") String locatorText,
                                                   @QueryParam("fields") String fieldsText,
                                                   @Context UriInfo uriInfo,
                                                   @Context HttpServletRequest request) {
    Locator locator = new Locator(locatorText);
    Fields fields = new Fields(fieldsText);

    PagedSearchResult<TestFailuresProblemEntry> result = myProblemEntriesCollector.getItems(locator);
    PagerDataImpl pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorText, "locator");

    return new TestFailuresProblemEntries(result.getEntries(), fields, pager, myBeanContext);
  }


  @GET
  @Path("/buildProblems")
  @Produces({"application/xml", "application/json"})
  public BuildProblemEntries getBuildEntries(@QueryParam("locator") String locatorText,
                                             @QueryParam("fields") String fieldsText,
                                             @Context UriInfo uriInfo,
                                             @Context HttpServletRequest request) {
    PagedSearchResult<BuildProblemEntry> result = myBuildProblemEntriesFinder.getItems(locatorText);

    Fields fields = new Fields(fieldsText);
    PagerDataImpl pager = new PagerDataImpl(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorText, "locator");

    return new BuildProblemEntries(result.getEntries(), fields, pager, myBeanContext);
  }

  @GET
  @Path("/buildProblems/tree")
  @Produces({"application/xml", "application/json"})
  public BuildProblemEntriesTree getBuildEntriesTree(@QueryParam("locator") String locatorText,
                                                     @QueryParam("fields") String fieldsText) {

    List<Node<BuildProblemEntry, BuildProblemEntriesTreeCollector.Counters>> nodes =
      myBuildProblemEntriesTreeCollector.getTree(new Locator(locatorText));

    Fields fields = new Fields(fieldsText);

    return new BuildProblemEntriesTree(nodes, fields, myBeanContext);
  }
}
