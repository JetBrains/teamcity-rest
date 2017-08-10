/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.mutes.MuteFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.problem.Mute;
import jetbrains.buildServer.server.rest.model.problem.Mutes;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

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
                              new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locatorText, "locator"),
                              new Fields(fields),
                              myBeanContext
    );
  }

  @GET
  @Path("/{muteLocator}")
  @Produces({"application/xml", "application/json"})
  public Mute serveInstance(@PathParam("muteLocator") String locatorText, @QueryParam("fields") String fields) {
    return new Mute(myMuteFinder.getItem(locatorText), new Fields(fields),
                             myBeanContext);
  }

 /*
  @DELETE
  @Path("/{muteLocator}")
  @Produces({"application/xml", "application/json"})
  public void deleteInstance(@PathParam("muteLocator") String locatorText) {
    MuteInfo item = myMuteFinder.getItem(locatorText);
    item.remove(myServiceLocator);
  }

  @PUT
  @Path("/{muteLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Mute replaceInstance(@PathParam("muteLocator") String locatorText, Mute mute, @QueryParam("fields") String fields) {
    MuteInfo item = myMuteFinder.getItem(locatorText);
    item.remove(myServiceLocator);
    return createInstance(mute, fields);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Mute createInstance(Mute mute, @QueryParam("fields") String fields) {
    MuteInfo muteInfo = null;
    try {
      muteInfo = mute.getFromPostedAndApply(myServiceLocator, false).get(0);
    } catch (Mute.OnlySingleEntitySupportedException e) {
      throw new BadRequestException(e.getMessage() + ". Use \"" + ".../multiple" + "\" request to post multiple entities");
    }
    return new Mute(muteInfo, new Fields(fields), myBeanContext);
  }

  *//**
   * Experimental use only!
   *//*
  @POST
  @Path("/multiple")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Mutes createInstances(Mutes mutes, @QueryParam("fields") String fields) {
    List<MuteInfo> muteInfos = mutes.getFromPostedAndApply(myServiceLocator);
    return new Mutes(muteInfos, null, new Fields(fields), myBeanContext);
  }
*/
  public void initForTests(@NotNull final BeanContext beanContext) {
    myServiceLocator = beanContext.getServiceLocator();
    myMuteFinder = beanContext.getSingletonService(MuteFinder.class);
    myApiUrlBuilder = beanContext.getApiUrlBuilder();
    myBeanContext = beanContext;
  }
}