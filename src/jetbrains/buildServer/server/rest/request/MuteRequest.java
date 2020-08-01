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
import io.swagger.annotations.ApiParam;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.mutes.MuteFinder;
import jetbrains.buildServer.server.rest.data.problem.MuteData;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.Mute;
import jetbrains.buildServer.server.rest.model.problem.Mutes;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  Experimental, the requests and results returned will change in future versions!
 * @author Yegor.Yarko
 *         Date: 09.08.17
 */
@Path(MuteRequest.API_SUB_URL)
@Api("Mute")
public class MuteRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private MuteFinder myMuteFinder;
  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private BeanContext myBeanContext;

  public static final String API_SUB_URL = Constants.API_URL + "/mutes";

  public static String getHref() {
    return API_SUB_URL;
  }

  public static String getHref(@NotNull final MuteInfo mute) {
    return API_SUB_URL + "/" + MuteFinder.getLocator(mute);
  }

  /*
  public static String getHref(@NotNull final ProblemWrapper problem) {
    return getHref(MuteFinder.getLocator(problem));
  }

  public static String getHref(@NotNull final STest test) {
    return getHref(MuteFinder.getLocator(test));
  }

  public static String getHref(@NotNull final SBuildType buildType) {
    return getHref(MuteFinder.getLocator(buildType));
  }
  */

  @NotNull
  public static String getHref(@NotNull final String locator) {
    return API_SUB_URL + "?locator=" + locator;
  }

  /**
   * Experimental, the requests and results returned will change in future versions!
   * @param locatorText
   * @param uriInfo
   * @param request
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public Mutes getMutes(@QueryParam("locator") String locatorText, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<MuteInfo> result = myMuteFinder.getItems(locatorText);

    return new Mutes(result.myEntries,
                     uriInfo == null ? null : new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorText, "locator"),
                     new Fields(fields),
                     myBeanContext
    );
  }

  @GET
  @Path("/{muteLocator}")
  @Produces({"application/xml", "application/json"})
  public Mute serveInstance(@ApiParam(format = LocatorName.MUTE) @PathParam("muteLocator") String locatorText,
                            @QueryParam("fields") String fields) {
    return new Mute(myMuteFinder.getItem(locatorText), new Fields(fields), myBeanContext);
  }

  /**
   * Comment is read from the body as an experimental approach
   */
  @DELETE
  @Path("/{muteLocator}")
  @Produces({"application/xml", "application/json"})
  public void deleteInstance(@ApiParam(format = LocatorName.MUTE) @PathParam("muteLocator") String locatorText,
                             String comment) {
    MuteInfo item = myMuteFinder.getItem(locatorText);
    MuteData muteData = new MuteData(item.getScope(), StringUtil.isEmpty(comment) ? null : comment, item.getTests(),
                                     item.getBuildProblemIds().stream().map(i -> i.longValue()).collect(Collectors.toList()), myServiceLocator);
    muteData.unmute();
  }

  /* this is not exactly PUT as it creates a new instance (with new id), so it is better not to have PUT at all
  @PUT
  @Path("/{muteLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Mute replaceInstance(@PathParam("muteLocator") String locatorText, Mute mute, @QueryParam("fields") String fields) {
    deleteInstance(locatorText);
    return createInstance(mute, fields);
  }
  */

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Mute createInstance(Mute mute, @QueryParam("fields") String fields) {
    return new Mute(mute.getFromPosted(myServiceLocator).mute(), new Fields(fields), myBeanContext);
  }

  /**
   * Experimental use only!
   */
  @POST
  @Path("/multiple")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Mutes createInstances(Mutes mutes, @QueryParam("fields") String fields) {
    List<MuteData> postedEntities = mutes.getFromPosted(myServiceLocator);
    List<MuteInfo> results = postedEntities.stream().map(muteData -> muteData.mute()).collect(Collectors.toList()); //muting after getting objects to report any deserialize errors before
    return new Mutes(results, null, new Fields(fields), myBeanContext);
  }

  public void initForTests(@NotNull final BeanContext beanContext) {
    myServiceLocator = beanContext.getServiceLocator();
    myMuteFinder = beanContext.getSingletonService(MuteFinder.class);
    myApiUrlBuilder = beanContext.getApiUrlBuilder();
    myBeanContext = beanContext;
  }
}