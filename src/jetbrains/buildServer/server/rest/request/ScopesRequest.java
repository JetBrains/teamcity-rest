/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.scope.Scope;
import jetbrains.buildServer.server.rest.data.problem.scope.ScopeFilter;
import jetbrains.buildServer.server.rest.data.problem.scope.ScopesCollector;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.scope.Scopes;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path(ScopesRequest.API_SUB_URL)
@Api("Scopes")
public class ScopesRequest {
  public static final String API_SUB_URL = Constants.API_URL + "/scopes";
  @Context @NotNull private BeanContext myBeanContext;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private TestOccurrenceFinder myTestOccurrenceFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;

  // Very highly experimental
  @GET
  @Path("/{scopeName}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(hidden = true, value = "highly experimental")
  public Scopes serveGroupedTestOccurrences(@QueryParam("locator") String locatorText,
                                            @PathParam("scopeName") String scopeName,
                                            @QueryParam("fields") String fields,
                                            @Context UriInfo uriInfo,
                                            @Context HttpServletRequest request) {
    Set<String> supportedGroupings = new HashSet<>(Arrays.asList("package", "suite", "class"));
    if (!supportedGroupings.contains(scopeName)) {
      throw new BadRequestException("Invalid scope. Only scopes " + String.join(",", supportedGroupings) + " are supported.");
    }

    Locator patchedLocator = new Locator(TestOccurrenceFinder.patchLocatorForPersonalBuilds(locatorText, request));
    ScopeFilter filter = new ScopeFilter(getScopeFilterDefinition(patchedLocator));
    patchedLocator.removeDimension("scope");

    final List<STestRun> items = myTestOccurrenceFinder.getItemsViaLocator(patchedLocator).myEntries;

    Stream<Scope> scopes;
    switch (scopeName) {
      case "package":
        scopes = ScopesCollector.groupByPackage(items, filter);
        break;
      case "suite":
        scopes = ScopesCollector.groupBySuite(items, filter);
        break;
      case "class":
        scopes = ScopesCollector.groupByClass(items, filter);
        break;
      default:
        throw new BadRequestException("Invalid scope. Only scopes " + String.join(",", supportedGroupings) + " are supported.");
    }

    return new Scopes(scopes.collect(Collectors.toList()), new Fields(fields), null, uriInfo, myBeanContext);
  }

  @Nullable
  private String getScopeFilterDefinition(@NotNull Locator locator) {
    if(!locator.isAnyPresent("scope")) {
      return null;
    }

    return locator.getSingleDimensionValue("scope");
  }
}
