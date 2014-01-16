/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.*;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.BuildCancelRequest;
import jetbrains.buildServer.server.rest.model.build.BuildTask;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 03.11.13
 */
@Path(BuildQueueRequest.API_BUILD_QUEUE_URL)
public class BuildQueueRequest {
  public static final String API_BUILD_QUEUE_URL = Constants.API_URL + "/buildQueue";
  public static final String COMPATIBLE_AGENTS = "/compatibleAgents";
  @Context @NotNull private DataProvider myDataProvider;
  @Context @NotNull private QueuedBuildFinder myQueuedBuildFinder;
  @Context @NotNull private BuildTypeFinder myBuildTypeFinder;
  @Context @NotNull private AgentFinder myAgentFinder;

  @Context @NotNull private ApiUrlBuilder myApiUrlBuilder;
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BeanContext myBeanContext;

  @NotNull
  public static String getHref() {
    return API_BUILD_QUEUE_URL;
  }

  @NotNull
  public static String getQueuedBuildHref(SQueuedBuild build) {
    return API_BUILD_QUEUE_URL + "/id:" + build.getBuildPromotion().getId();
  }

  @NotNull
  public static String getCompatibleAgentsHref(SQueuedBuild build) {
    return getQueuedBuildHref(build) + COMPATIBLE_AGENTS;
  }

  /**
   * Serves build queue.
   *
   * @param locator Build locator to filter builds
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  public Builds getBuilds(@QueryParam("locator") String locator, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<SQueuedBuild> result = myQueuedBuildFinder.getItems(locator);

    final List<BuildPromotion> builds = CollectionsUtil.convertCollection(result.myEntries, new Converter<BuildPromotion, SQueuedBuild>() {
      public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
        return source.getBuildPromotion();
      }
    });
    return new Builds(builds,
                      new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result.myStart,
                                    result.myCount, builds.size(),
                                    locator,
                                    "locator"),
                      new Fields(fields,Fields.LONG),
                      myBeanContext);
  }

  @GET
  @Path("/{queuedBuildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build getBuild(@PathParam("queuedBuildLocator") String queuedBuildLocator, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletResponse response) {
    //redirect if the build was already started
    final BuildPromotion buildPromotion = myQueuedBuildFinder.getBuildPromotionByBuildQueueLocator(queuedBuildLocator);  //todo: support full locators here (locators for build promotion)
    if (buildPromotion == null){
      throw new NotFoundException("No builds are fouind by the locator specified");
    }
    //todo: handle build merges in the queue
    return new Build(buildPromotion, new Fields(fields, Fields.LONG), myBeanContext);
  }

  @DELETE
  @Path("/{queuedBuildLocator}")
  public void deleteBuild(@PathParam("queuedBuildLocator") String queuedBuildLocator) {
    SQueuedBuild build = myQueuedBuildFinder.getItem(queuedBuildLocator);
    cancelQueuedBuild(build, null);

    //now delete the canceled build
    final SBuild associatedBuild = build.getBuildPromotion().getAssociatedBuild();
    if (associatedBuild == null){
      throw new OperationException("After canceling a build, no canceled build found to delete.");
    }
    myDataProvider.deleteBuild(associatedBuild);
  }

  @GET
  @Path("/{buildLocator}/example/buildCancelRequest")
  @Produces({"application/xml", "application/json"})
  public BuildCancelRequest cancelBuild(@PathParam("buildLocator") String buildLocator) {
    return new BuildCancelRequest("example build cancel comment", false);
  }

  @POST
  @Path("/{queuedBuildLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Build cancelBuild(@PathParam("queuedBuildLocator") String queuedBuildLocator, BuildCancelRequest cancelRequest) {
    if (cancelRequest.readdIntoQueue) {
      throw new BadRequestException("Restore in queue is not supported for queued builds.");
    }
    SQueuedBuild build = myQueuedBuildFinder.getItem(queuedBuildLocator);
    cancelQueuedBuild(build, cancelRequest.comment);

    return new Build(build.getBuildPromotion(), Fields.LONG, myBeanContext);
  }

  private void cancelQueuedBuild(@NotNull final SQueuedBuild build, @Nullable final String comment) {
    final jetbrains.buildServer.serverSide.BuildQueue buildQueue = myServiceLocator.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class);
    final String itemId = build.getItemId();
    buildQueue.removeItems(Collections.singleton(itemId), myDataProvider.getCurrentUser(), comment);

    //todo: TeamCity API issue: TW-34143
    if (buildQueue.findQueued(itemId) != null) {
      throw new AuthorizationFailedException("Build was not canceled. Probably not sufficient permisisons.");
    }
  }

  @GET
  @Path("/{queuedBuildLocator}" + COMPATIBLE_AGENTS)
  @Produces({"application/xml", "application/json"})
  public Agents serveCompatibleAgents(@PathParam("queuedBuildLocator") String queuedBuildLocator) {
    return new Agents(myQueuedBuildFinder.getItem(queuedBuildLocator).getCompatibleAgents(), null, null, myApiUrlBuilder);
  }

  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    SQueuedBuild build = myQueuedBuildFinder.getItem(buildLocator);
    return Build.getFieldValue(build.getBuildPromotion(), field, myBeanContext);
  }

  @POST
  @Produces({"application/xml", "application/json"})
  public Build queueNewBuild(BuildTask buildTask, @Context HttpServletRequest request){
    final SUser user = myDataProvider.getCurrentUser();
    BuildPromotion buildToTrigger = buildTask.getBuildToTrigger(user, myBuildTypeFinder, myServiceLocator);
    TriggeredByBuilder triggeredByBulder = new TriggeredByBuilder();
    if (user != null){
      triggeredByBulder = new TriggeredByBuilder(user);
    }
    final SBuildAgent agent = buildTask.getAgent(myAgentFinder);
    SQueuedBuild queuedBuild;
    if (agent != null){
      queuedBuild = buildToTrigger.addToQueue(agent, triggeredByBulder.toString());
    }else{
      queuedBuild = buildToTrigger.addToQueue(triggeredByBulder.toString());
    }
    if (queuedBuild == null){
      throw new InvalidStateException("Failed to add build into the queue for unknown reason.");
    }
    if (buildTask.queueAtTop != null && buildTask.queueAtTop ){
      myServiceLocator.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class).moveTop(queuedBuild.getItemId());
    }
    return new Build(queuedBuild.getBuildPromotion(), Fields.LONG, myBeanContext);
  }
}
