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

import java.util.*;
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
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
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
    return API_BUILD_QUEUE_URL + "/" + QueuedBuildFinder.getLocator(build);
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
                       new Fields(fields),
                      myBeanContext);
  }

  /**
   * Experimental! Deletes the set of builds filtered
   *
   * @param locator Build locator to filter builds to delete
   * @return
   */
  @DELETE
  public void deleteBuildsExperimental(@QueryParam("locator") String locator, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletRequest request) {
    final PagedSearchResult<SQueuedBuild> result = myQueuedBuildFinder.getItems(locator);
    final List<Throwable> errors = new ArrayList<Throwable>();

    final jetbrains.buildServer.serverSide.BuildQueue buildQueue = myServiceLocator.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class);
    final List<String> itemIds = CollectionsUtil.convertCollection(result.myEntries, new Converter<String, SQueuedBuild>() {
      public String createFrom(@NotNull final SQueuedBuild source) {
        return source.getItemId();
      }
    });
    buildQueue.removeItems(itemIds, myDataProvider.getCurrentUser(), null);

    //TeamCity API issue: TW-34143
    for (String itemId : itemIds) {
      if (buildQueue.findQueued(itemId) != null) {
        errors.add(new AuthorizationFailedException("Build was not canceled. Probably not sufficient permisisons."));
      }
    }

    //now delete the canceled builds
    for (SQueuedBuild build : result.myEntries) {
      final SBuild associatedBuild = build.getBuildPromotion().getAssociatedBuild();
      if (associatedBuild == null) {
        errors.add(new OperationException("After canceling a build with promotion id '" + build.getBuildPromotion().getId() + "' , no canceled build found to delete."));
      } else{
        myDataProvider.deleteBuild(associatedBuild);
      }
    }
    if (errors.size() >0){
      throw new PartialUpdateError("Some builds were not deleted", errors);
    }
  }

  @PUT
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Builds replaceBuilds(Builds builds, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletRequest request){
    if (builds == null){
      throw new BadRequestException("List of builds should be posted.");
    }
    if (builds.builds == null){
      throw new BadRequestException("Posted element should contain 'builds' sub-element.");
    }

    final jetbrains.buildServer.serverSide.BuildQueue buildQueue = myServiceLocator.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class);
    final List<BuildPromotion> queuedBuildPromotions = CollectionsUtil.convertCollection(buildQueue.getItems(), new Converter<BuildPromotion, SQueuedBuild>() {
      public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
        return source.getBuildPromotion();
      }
    });
    final List<String> queuedBuildIds = CollectionsUtil.convertCollection(buildQueue.getItems(), new Converter<String, SQueuedBuild>() {
      public String createFrom(@NotNull final SQueuedBuild source) {
        return source.getItemId();
      }
    });
    buildQueue.removeItems(queuedBuildIds, myDataProvider.getCurrentUser(), null); //todo: consider providing comment here

    //TeamCity API issue: TW-34143
    if (!buildQueue.isQueueEmpty()) {
      throw new AuthorizationFailedException("Some builds were not canceled. Probably not sufficient permisisons.");
    }

    //now delete the canceled builds
    for (BuildPromotion queuedBuildPromotion : queuedBuildPromotions) {
      final SBuild associatedBuild = queuedBuildPromotion.getAssociatedBuild();
      if (associatedBuild == null){
        throw new OperationException("After canceling a build with promotion id '" + queuedBuildPromotion.getId() + "' , no canceled build found to delete.");
      }
      myDataProvider.deleteBuild(associatedBuild);
    }

    // now queue

    final SUser user = myDataProvider.getCurrentUser();
    final Map<Long, Long> buildPromotionIdReplacements = new HashMap<Long, Long>();
    for (Build build : builds.builds) {
      final SQueuedBuild queuedBuild = build.triggerBuild(user, myServiceLocator, buildPromotionIdReplacements);
      if (build.getSubmittedPromotionId() != null){
        buildPromotionIdReplacements.put(build.getSubmittedPromotionId(), queuedBuild.getBuildPromotion().getId());
      }
    }
    return getBuilds(null, fields, uriInfo, request);
  }

  @GET
  @Path("/{queuedBuildLocator}")
  @Produces({"application/xml", "application/json"})
  public Build getBuild(@PathParam("queuedBuildLocator") String queuedBuildLocator, @QueryParam("fields") String fields, @Context UriInfo uriInfo, @Context HttpServletResponse response) {
    //also find already started builds
    BuildPromotion buildPromotion = myQueuedBuildFinder.getBuildPromotionByBuildQueueLocator(queuedBuildLocator);
    //todo: handle build merges in the queue (TW-33260)
    return new Build(buildPromotion,  new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{queuedBuildLocator}")
  public void deleteBuild(@PathParam("queuedBuildLocator") String queuedBuildLocator) {
    SQueuedBuild build = myQueuedBuildFinder.getItem(queuedBuildLocator);
    cancelQueuedBuild(build, null);

    //now delete the canceled build
    final SBuild associatedBuild = build.getBuildPromotion().getAssociatedBuild();
    if (associatedBuild == null){
      throw new OperationException("After canceling a build with promotion id '" + build.getBuildPromotion().getId() + "' , no canceled build found to delete.");
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

    //TeamCity API issue: TW-34143
    if (buildQueue.findQueued(itemId) != null) {
      throw new AuthorizationFailedException("Build was not canceled. Probably not sufficient permisisons.");
    }
  }

  @GET
  @Path("/{queuedBuildLocator}" + COMPATIBLE_AGENTS)
  @Produces({"application/xml", "application/json"})
  public Agents serveCompatibleAgents(@PathParam("queuedBuildLocator") String queuedBuildLocator, @QueryParam("fields") String fields) {
    return new Agents(myQueuedBuildFinder.getItem(queuedBuildLocator).getCompatibleAgents(), null,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  public String serveBuildFieldByBuildOnly(@PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    final BuildPromotion buildPromotion = myQueuedBuildFinder.getBuildPromotionByBuildQueueLocator(buildLocator);
    return Build.getFieldValue(buildPromotion, field, myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  public Build queueNewBuild(Build build, @Context HttpServletRequest request){
    final SUser user = myDataProvider.getCurrentUser();
    SQueuedBuild queuedBuild = build.triggerBuild(user, myServiceLocator, new HashMap<Long, Long>());
    return new Build(queuedBuild.getBuildPromotion(), Fields.LONG, myBeanContext);
  }

}
