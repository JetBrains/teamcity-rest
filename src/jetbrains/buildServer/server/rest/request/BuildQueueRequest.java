/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildFinder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.BuildCancelRequest;
import jetbrains.buildServer.server.rest.model.build.BuildQueue;
import jetbrains.buildServer.server.rest.model.build.QueuedBuild;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 03.11.13
 */
@Path(BuildQueueRequest.API_BUILD_QUEUE_URL)
public class BuildQueueRequest {
  public static final String API_BUILD_QUEUE_URL = Constants.API_URL + "/buildQueue";
  public static final String COMPATIBLE_AGENTS = "/compatibleAgents";
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private BuildFinder myBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;

  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanFactory myFactory;

  @NotNull
  public static String getHref() {
    return API_BUILD_QUEUE_URL;
  }

  @NotNull
  public static String getQueuedBuildHref(SQueuedBuild build) {
    return API_BUILD_QUEUE_URL + "/id:" + build.getItemId();
  }

  @NotNull
  public static String getCompatibleAgentsHref(SQueuedBuild build) {
    return getQueuedBuildHref(build) + COMPATIBLE_AGENTS;
  }

  /**
   * Serves build queue.
   *
   * @param locator Build locator to filter builds server
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public BuildQueue serveQueue(@QueryParam("locator") String locator, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    return new BuildQueue(myServiceLocator.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class).getItems(), null, myApiUrlBuilder, myFactory);
  }

  @GET
  @Path("/{queuedBuildLocator}")
  @Produces({"application/xml", "application/json"})
  public QueuedBuild serveQueuedBuild(@PathParam("queuedBuildLocator") String queuedBuildLocator) {
    return new QueuedBuild(myBuildFinder.getQueuedBuild(queuedBuildLocator), myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
  }

  @DELETE
  @Path("/{queuedBuildLocator}")
  public void deleteBuild(@PathParam("queuedBuildLocator") String queuedBuildLocator, @Context HttpServletRequest request) {
    SQueuedBuild build = myBuildFinder.getQueuedBuild(queuedBuildLocator);
    myServiceLocator.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class).removeItems(Collections.singleton(build.getItemId()),
                                                                                                        SessionUser.getUser(request),
                                                                                                        null);
    final SBuild associatedBuild = build.getBuildPromotion().getAssociatedBuild();
    myDataProvider.deleteBuild(associatedBuild);
  }

  @PUT
  @Path("/{queuedBuildLocator}/" + Build.CANCELED_INFO)
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Build cancelBuild(@PathParam("queuedBuildLocator") String queuedBuildLocator, BuildCancelRequest cancelRequest, @Context HttpServletRequest request) {
    SQueuedBuild build = myBuildFinder.getQueuedBuild(queuedBuildLocator);
    myServiceLocator.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class).removeItems(Collections.singleton(build.getItemId()),
                                                                                                        SessionUser.getUser(request),
                                                                                                        cancelRequest.comment);
    if (cancelRequest.readdIntoQueue) {
      throw new BadRequestException("Restore in queue is not supported for queued builds.");
    }
    final SBuild associatedBuild = build.getBuildPromotion().getAssociatedBuild();
    if (associatedBuild == null){
      return null;
    }
    return new Build(associatedBuild, myDataProvider, myApiUrlBuilder, myServiceLocator, myFactory);
  }


  @GET
  @Path("/{queuedBuildLocator}" + COMPATIBLE_AGENTS)
  @Produces({"application/xml", "application/json"})
  public Agents serveCompatibleAgents(@PathParam("queuedBuildLocator") String queuedBuildLocator) {
    return new Agents(myBuildFinder.getQueuedBuild(queuedBuildLocator).getCompatibleAgents(), myApiUrlBuilder);
  }

  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SQueuedBuild build = myBuildFinder.getQueuedBuild(buildLocator);
    return QueuedBuild.getFieldValue(build, field);
  }

}
