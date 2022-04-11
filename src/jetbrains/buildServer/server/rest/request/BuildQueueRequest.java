/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.data.build.TagFinder;
import jetbrains.buildServer.server.rest.errors.*;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.BuildCancelRequest;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.build.Tags;
import jetbrains.buildServer.server.rest.model.build.approval.ApprovalInfo;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.ApprovableBuildManager;
import jetbrains.buildServer.tags.TagsManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.util.QueueWebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 03.11.13
 */
@Path(BuildQueueRequest.API_BUILD_QUEUE_URL)
@Api("BuildQueue")
public class  BuildQueueRequest {
  private static final Logger LOG = Logger.getInstance(BuildRequest.class.getName());

  public static final String API_BUILD_QUEUE_URL = Constants.API_URL + "/buildQueue";
  public static final String COMPATIBLE_AGENTS = "/compatibleAgents";
  @Context @NotNull private QueuedBuildFinder myQueuedBuildFinder;
  @Context @NotNull private BuildPromotionFinder myBuildPromotionFinder;
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

  /**
   * Serves build queue.
   *
   * @param locator Build locator to filter builds
   * @return
   */
  @GET
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get all queued builds.",nickname="getAllQueuedBuilds")
  public Builds getBuilds(@ApiParam(format = LocatorName.BUILD_QUEUE) @QueryParam("locator") String locator,
                          @QueryParam("fields") String fields,
                          @Context UriInfo uriInfo,
                          @Context HttpServletRequest request) {
    final PagedSearchResult<SQueuedBuild> result = myQueuedBuildFinder.getItems(locator);

    final List<BuildPromotion> builds = CollectionsUtil.convertCollection(result.myEntries, new Converter<BuildPromotion, SQueuedBuild>() {
      public BuildPromotion createFrom(@NotNull final SQueuedBuild source) {
        return source.getBuildPromotion();
      }
    });
    return Builds.createFromBuildPromotions(builds,
                      new PagerData(uriInfo.getRequestUriBuilder(), request.getContextPath(), result, locator, "locator"),
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
  @ApiOperation(value="Delete all queued builds.",nickname="deleteAllQueuedBuilds")
  public void deleteBuildsExperimental(@ApiParam(format = LocatorName.BUILD_QUEUE) @QueryParam("locator") String locator,
                                       @QueryParam("fields") String fields,
                                       @Context UriInfo uriInfo,
                                       @Context HttpServletRequest request) {
    final PagedSearchResult<SQueuedBuild> result = myQueuedBuildFinder.getItems(locator);
    final List<Throwable> errors = new ArrayList<Throwable>();

    final jetbrains.buildServer.serverSide.BuildQueue buildQueue = myServiceLocator.getSingletonService(jetbrains.buildServer.serverSide.BuildQueue.class);
    final List<String> itemIds = CollectionsUtil.convertCollection(result.myEntries, new Converter<String, SQueuedBuild>() {
      public String createFrom(@NotNull final SQueuedBuild source) {
        return source.getItemId();
      }
    });
    buildQueue.removeItems(itemIds, myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser(), null);

    //TeamCity API issue: TW-34143
    for (String itemId : itemIds) {
      if (buildQueue.findQueued(itemId) != null) {
        errors.add(new AuthorizationFailedException("Build was not canceled. Probably not sufficient permissions."));
      }
    }

    //now delete the canceled builds
    for (SQueuedBuild build : result.myEntries) {
      final SBuild associatedBuild = build.getBuildPromotion().getAssociatedBuild();
      if (associatedBuild == null) {
        errors.add(new OperationException("After canceling a build with promotion id '" + build.getBuildPromotion().getId() + "' , no canceled build found to delete."));
      } else{
        DataProvider.deleteBuild(associatedBuild, myBeanContext.getSingletonService(BuildHistory.class));
      }
    }
    if (errors.size() >0){
      throw new PartialUpdateError("Some builds were not deleted", errors);
    }
  }

  @PUT
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="replaceBuilds",hidden=true)
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
    buildQueue.removeItems(queuedBuildIds, myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser(), null); //todo: consider providing comment here

    //TeamCity API issue: TW-34143
    if (!buildQueue.isQueueEmpty()) {
      throw new AuthorizationFailedException("Some builds were not canceled. Probably not sufficient permissions.");
    }

    //now delete the canceled builds
    for (BuildPromotion queuedBuildPromotion : queuedBuildPromotions) {
      final SBuild associatedBuild = queuedBuildPromotion.getAssociatedBuild();
      if (associatedBuild == null){
        throw new OperationException("After canceling a build with promotion id '" + queuedBuildPromotion.getId() + "' , no canceled build found to delete.");
      }
      DataProvider.deleteBuild(associatedBuild, myBeanContext.getSingletonService(BuildHistory.class));
    }

    // now queue

    final SUser user = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    final Map<Long, Long> buildPromotionIdReplacements = new HashMap<Long, Long>();
    List<Build> buildsToTrigger = builds.builds;
    Map<Build, Exception> buildsWithErrors;
    while (true) {
      buildsWithErrors = triggerBuilds(buildsToTrigger, user, buildPromotionIdReplacements);
      if (buildsWithErrors.isEmpty() || buildsToTrigger.size() <= buildsWithErrors.size()) {
        //no errors or no builds triggered
        break;
      }
      buildsToTrigger = new ArrayList<Build>(buildsWithErrors.keySet());
      LOG.info("There was an error triggering " + buildsToTrigger.size() + " builds, will try again." + " Affected build ids: " + listBuildIds(buildsToTrigger));
      //repeat (dependnecies order might be relevant)
    }

    if (buildsWithErrors.size() != 0) {
      final StringBuilder buildListDetails = new StringBuilder();
      for (Map.Entry<Build, Exception> buildExceptionEntry : buildsWithErrors.entrySet()) {
        final Build build = buildExceptionEntry.getKey();
        //noinspection ThrowableResultOfMethodCallIgnored
        buildListDetails.append("Not able to add build").append(build.getPromotionIdOfSubmittedBuild() != null ? " with id '" + build.getPromotionIdOfSubmittedBuild() + "'" : "")
                        .append(" to the build queue due to error: ").append(buildExceptionEntry.getValue().toString());
        buildListDetails.append("\n");
      }

      throw new BadRequestException("Error triggering " + buildsWithErrors.size() + " out of " + builds.builds.size() + " builds: \n" +
                                    buildListDetails.substring(0, buildListDetails.length() - "\n".length()));
    }
    return getBuilds(null, fields, uriInfo, request);
  }

  @NotNull
  private String listBuildIds(@NotNull final Collection<Build> buildsToTrigger) {
    return StringUtil.join(buildsToTrigger, new Function<Build, String>() {
      public String fun(final Build build) {
        return String.valueOf(build.getPromotionIdOfSubmittedBuild());
      }
    }, ", ");
  }

  @NotNull
  private Map<Build, Exception> triggerBuilds(@NotNull final List<Build> builds, @Nullable final SUser user, @NotNull final Map<Long, Long> buildPromotionIdReplacements) {
    final Map<Build, Exception> buildsWithErrors = new LinkedHashMap<Build, Exception>();
    for (Build build : builds) {
      try {
        final SQueuedBuild queuedBuild = build.triggerBuild(user, myServiceLocator, buildPromotionIdReplacements);
        if (build.getPromotionIdOfSubmittedBuild() != null) {
          buildPromotionIdReplacements.put(build.getPromotionIdOfSubmittedBuild(), queuedBuild.getBuildPromotion().getId());
        }
      } catch (Exception e) {
        //noinspection ThrowableResultOfMethodCallIgnored
        buildsWithErrors.put(build, e);
        LOG.debug("Got error trying to add build" + (build.getPromotionIdOfSubmittedBuild() != null ? " with id '" + build.getPromotionIdOfSubmittedBuild() + "'" : "") +
                  " to the build queue. Details: " + e.toString(), e);
      }
    }
    return buildsWithErrors;
  }

  @GET
  @Path("/{queuedBuildLocator}")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get a queued matching build.",nickname="getQueuedBuild")
  public Build getBuild(@ApiParam(format = LocatorName.BUILD_QUEUE) @PathParam("queuedBuildLocator") String queuedBuildLocator,
                        @QueryParam("fields") String fields,
                        @Context UriInfo uriInfo,
                        @Context HttpServletResponse response) {
    //also find already started builds
    BuildPromotion buildPromotion = myQueuedBuildFinder.getBuildPromotionByBuildQueueLocator(queuedBuildLocator);
    //todo: handle build merges in the queue (TW-33260)
    return new Build(buildPromotion,  new Fields(fields), myBeanContext);
  }

  @DELETE
  @Path("/{queuedBuildLocator}")
  @ApiOperation(value="Delete a queued matching build.",nickname="deleteQueuedBuild")
  public void deleteQueuedBuild(@ApiParam(format = LocatorName.BUILD_QUEUE) @PathParam("queuedBuildLocator") String queuedBuildLocator) {
    SQueuedBuild build = myQueuedBuildFinder.getItem(queuedBuildLocator);
    cancelQueuedBuild(build, null);

    //now delete the canceled build
    final SBuild associatedBuild = build.getBuildPromotion().getAssociatedBuild();
    if (associatedBuild == null){
      throw new OperationException("After canceling a build with promotion id '" + build.getBuildPromotion().getId() + "' , no canceled build found to delete.");
    }
    DataProvider.deleteBuild(associatedBuild, myBeanContext.getSingletonService(BuildHistory.class));
  }

  @GET
  @ApiOperation(value = "getBuildCancelRequestQueue", hidden = true)
  @Path("/{buildLocator}/example/buildCancelRequest")
  @Produces({"application/xml", "application/json"})
  public BuildCancelRequest getBuildCancelRequestQueue(@ApiParam(format = LocatorName.BUILD_QUEUE) @PathParam("buildLocator") String buildLocator) {
    return new BuildCancelRequest("example build cancel comment", false);
  }

  @POST
  @Path("/{queuedBuildLocator}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Cancel a queued matching build.",nickname="cancelQueuedBuild")
  public Build cancelBuild(@ApiParam(format = LocatorName.BUILD_QUEUE) @PathParam("queuedBuildLocator") String queuedBuildLocator,
                           BuildCancelRequest cancelRequest) {
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
    buildQueue.removeItems(Collections.singleton(itemId), myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser(), comment);

    //TeamCity API issue: TW-34143
    if (buildQueue.findQueued(itemId) != null) {
      throw new AuthorizationFailedException("Build was not canceled. Probably not sufficient permissions.");
    }
  }

  @GET
  @Path("/{queuedBuildLocator}" + COMPATIBLE_AGENTS)
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get compatible agents for a queued matching build.",nickname="getCompatibleAgentsForBuild")
  public Agents serveCompatibleAgents(@ApiParam(format = LocatorName.BUILD_QUEUE) @PathParam("queuedBuildLocator") String queuedBuildLocator,
                                      @QueryParam("fields") String fields) {
    return new Agents(AgentFinder.getCompatibleAgentsLocator(myQueuedBuildFinder.getItem(queuedBuildLocator).getBuildPromotion()), null,  new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}/tags/")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get tags of the queued matching build.",nickname="getQueuedBuildTags")
  public Tags serveTags(@ApiParam(format = LocatorName.BUILD_QUEUE) @PathParam("buildLocator") String buildLocator,
                        @QueryParam("locator") String tagLocator,
                        @QueryParam("fields") String fields) {
    BuildPromotion buildPromotion = myBuildPromotionFinder.getItem(Locator.createLocator(buildLocator, getBuildPromotionLocatorDefaults(), null).getStringRepresentation());
    return new Tags(new TagFinder(myBeanContext.getSingletonService(UserFinder.class), buildPromotion).getItems(tagLocator, TagFinder.getDefaultLocator()).myEntries,
                    new Fields(fields), myBeanContext);
  }

  /**
   * Replaces build's tags.
   *
   * @param buildLocator build locator
   */
  @PUT
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="replaceTags",hidden=true)
  public Tags replaceTags(@ApiParam(format = LocatorName.BUILD_QUEUE) @PathParam("buildLocator") String buildLocator,
                          @ApiParam(format = LocatorName.TAG) @QueryParam("locator") String tagLocator,
                          Tags tags,
                          @QueryParam("fields") String fields, @Context HttpServletRequest request) {
    BuildPromotion buildPromotion = myBuildPromotionFinder.getItem(Locator.createLocator(buildLocator, getBuildPromotionLocatorDefaults(), null).getStringRepresentation());
    final TagFinder tagFinder = new TagFinder(myBeanContext.getSingletonService(UserFinder.class), buildPromotion);
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);

    tagsManager.removeTagDatas(buildPromotion, tagFinder.getItems(tagLocator, TagFinder.getDefaultLocator()).myEntries);
    tagsManager.addTagDatas(buildPromotion, tags.getFromPosted(myBeanContext.getSingletonService(UserFinder.class)));

    return new Tags(tagFinder.getItems(null, TagFinder.getDefaultLocator()).myEntries, new Fields(fields), myBeanContext);
  }

  /**
   * Adds a set of tags to a build
   *
   * @param buildLocator build locator
   */
  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"application/xml", "application/json"})
  @ApiOperation(value="Add tags to the matching build.",nickname="addTagsToBuild")
  public void addTags(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                      Tags tags,
                      @Context HttpServletRequest request) {
    BuildPromotion buildPromotion = myBuildPromotionFinder.getItem(Locator.createLocator(buildLocator, getBuildPromotionLocatorDefaults(), null).getStringRepresentation());
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);

    tagsManager.addTagDatas(buildPromotion, tags.getFromPosted(myBeanContext.getSingletonService(UserFinder.class)));
  }

  /**
   * Adds a single tag to a build
   *
   * @param buildLocator build locator
   * @param tagName      name of a tag to add
   */
  @POST
  @Path("/{buildLocator}/tags/")
  @Consumes({"text/plain"})
  @Produces({"text/plain"})
  @ApiOperation(hidden = true, value = "Use addTags instead")
  public String addTag(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                       String tagName,
                       @Context HttpServletRequest request) {
    if (StringUtil.isEmpty(tagName)) { //check for empty tags: http://youtrack.jetbrains.com/issue/TW-34426
      throw new BadRequestException("Cannot apply empty tag, should have non empty request body");
    }
    BuildPromotion buildPromotion = myBuildPromotionFinder.getItem(Locator.createLocator(buildLocator, getBuildPromotionLocatorDefaults(), null).getStringRepresentation());
    final TagsManager tagsManager = myBeanContext.getSingletonService(TagsManager.class);

    tagsManager.addTagDatas(buildPromotion, Collections.singleton(TagData.createPublicTag(tagName)));
    return tagName;
  }

  @NotNull
  private Locator getBuildPromotionLocatorDefaults() {
    Locator defaultLocator = Locator.createEmptyLocator();
    defaultLocator.setDimension(BuildPromotionFinder.STATE, BuildPromotionFinder.STATE_QUEUED);
    defaultLocator.addIgnoreUnusedDimensions(BuildPromotionFinder.STATE);
    return defaultLocator;
  }


  @GET
  @Path("/{buildLocator}/{field}")
  @Produces("text/plain")
  @ApiOperation(value="serveBuildFieldByBuildOnly",hidden=true)
  public String serveBuildFieldByBuildOnly(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                           @PathParam("field") String field) {
    final BuildPromotion buildPromotion = myQueuedBuildFinder.getBuildPromotionByBuildQueueLocator(buildLocator);
    return Build.getFieldValue(buildPromotion, field, myBeanContext);
  }

  @POST
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Add a new build to the queue.",nickname="addBuildToQueue")
  public Build queueNewBuild(Build build, @QueryParam("moveToTop") Boolean moveToTop,
                             @Context HttpServletRequest request,
                             @Context HttpServletResponse response){

    if (QueueWebUtil.processLargeQueueCase(request, response, myServiceLocator.getSingletonService(BuildQueue.class))) {
      return null;
    }

    final SUser user = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    SQueuedBuild queuedBuild = build.triggerBuild(user, myServiceLocator, new HashMap<Long, Long>());
    if (moveToTop != null && moveToTop){
      final BuildQueue buildQueue = myServiceLocator.getSingletonService(BuildQueue.class);
      buildQueue.moveTop(queuedBuild.getItemId());
    }
    return new Build(queuedBuild.getBuildPromotion(), Fields.LONG, myBeanContext);
  }

  /**
   * Experimental ability to reorder the queue
   */
  @PUT
  @Path("/order")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update the build queue order.",nickname="setQueuedBuildsOrder")
  public Builds setBuildQueueOrder(Builds builds, @QueryParam("fields") String fields) {
    if (builds.builds == null){
      throw new BadRequestException("No new builds order specified. Should post a collection of builds, each with id or locator");
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (Build build : builds.builds) {
      try {
        List<BuildPromotion> items = myBuildPromotionFinder.getItems(build.getLocatorFromPosted(Collections.emptyMap()), new Locator(getBuildPromotionLocatorDefaults())).myEntries;
        for (BuildPromotion buildPromotion : items) {
          SQueuedBuild queuedBuild = buildPromotion.getQueuedBuild();
          if (queuedBuild == null) continue;
          ids.add(String.valueOf(queuedBuild.getItemId()));
        }
      } catch (NotFoundException e) {
        //ignore
      }
    }
    final BuildQueue buildQueue = myServiceLocator.getSingletonService(BuildQueue.class);
    buildQueue.applyOrder(CollectionsUtil.toArray(ids, String.class));

    //see getBuilds()
    return Builds.createFromBuildPromotions(myBuildPromotionFinder.getItems(getBuildPromotionLocatorDefaults().getStringRepresentation()).myEntries,
                                            null, new Fields(fields), myBeanContext);
  }

  /**
   * Experimental ability to get a build at specific queue position
   */
  @GET
  @Path("/order/{queuePosition}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get the queue position of a queued matching build.",nickname="getQueuedBuildPosition")
  public Build getBuildQueuePosition(@PathParam("queuePosition") String queuePosition, @QueryParam("fields") String fields) {
    int queuePositionNumber;
    queuePositionNumber = getQueuePositionNumber(queuePosition);
    if (queuePositionNumber < 1) {
      throw new BadRequestException("Unsupported value of queuePosition \"" + queuePosition + "\": should be greater than 0");
    }
    int actualPosition;
    if (queuePositionNumber == Integer.MAX_VALUE) {
      final BuildQueue buildQueue = myServiceLocator.getSingletonService(BuildQueue.class);
      actualPosition = buildQueue.getNumberOfItems() - 1;
    } else {
      actualPosition = queuePositionNumber - 1;
    }
    Locator locator = getBuildPromotionLocatorDefaults().setDimension(PagerData.START, String.valueOf(actualPosition));
    return new Build(myBuildPromotionFinder.getItem(locator.getStringRepresentation()), new Fields(fields), myBeanContext);
  }

  private int getQueuePositionNumber(final @PathParam("queuePosition") String queuePosition) {
    try {
      if ("first".equals(queuePosition)) return 1;
      if ("last".equals(queuePosition)) return Integer.MAX_VALUE;
      return Integer.parseInt(queuePosition);
    } catch (NumberFormatException e) {
      throw new BadRequestException("Error parsing queuePosition \"" + queuePosition + "\": should be a number, \"first\" or \"last\"");
    }
  }

  /**
   * Experimental ability to move to top
   */
  @PUT
  @Path("/order/{queuePosition}")
  @Consumes({"application/xml", "application/json"})
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Update the queue position of a queued matching build.",nickname="setQueuedBuildPosition")
  public Build setBuildQueuePosition(Build build, @PathParam("queuePosition") String queuePosition, @QueryParam("fields") String fields) {
    BuildPromotion buildToMove = build.getFromPosted(myBuildPromotionFinder, Collections.emptyMap());
    SQueuedBuild queuedBuild = buildToMove.getQueuedBuild();
    if (queuedBuild == null) {
      throw new BadRequestException("Cannot move build which is not queued");
    }

    int queuePositionNumber;
    queuePositionNumber = getQueuePositionNumber(queuePosition);
    if (queuePositionNumber == 1) {
      final BuildQueue buildQueue = myServiceLocator.getSingletonService(BuildQueue.class);
      buildQueue.moveTop(queuedBuild.getItemId());
    } else if (queuePositionNumber == Integer.MAX_VALUE) {
      final BuildQueue buildQueue = myServiceLocator.getSingletonService(BuildQueue.class);
      buildQueue.moveBottom(queuedBuild.getItemId());
    } else {
      throw new BadRequestException("Unsupported value of queuePosition \"" + queuePosition + "\": only \"1\", \"first\" and \"last\" are supported");
    }


    return new Build(queuedBuild.getBuildPromotion(), new Fields(fields), myBeanContext);
  }

  @GET
  @Path("/{buildLocator}/approvalInfo")
  @Produces({"application/xml", "application/json"})
  @ApiOperation(value="Get approval info of a queued matching build.",nickname="getApprovalInfo")
  public ApprovalInfo getApprovalInfo(
    @ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
    @QueryParam("fields") String fields
  ) {
    ApprovableBuildManager approvableBuildManager = myBeanContext.getSingletonService(ApprovableBuildManager.class);
    BuildPromotionEx buildPromotionEx = (BuildPromotionEx)myBuildPromotionFinder.getBuildPromotion(null, buildLocator);

    if (approvableBuildManager.getApprovalFeature(buildPromotionEx).isPresent()) {
      return new ApprovalInfo(buildPromotionEx, new Fields(fields), myBeanContext);
    } else {
      throw new BadRequestException(
         "Trying to access approval status for a queued build that does not have approval feature enabled"
      );
    }
  }

  @POST
  @Path("/{buildLocator}/approve")
  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @ApiOperation(value = "Approve queued build with approval feature enabled.", nickname = "approveQueuedBuild")
  public ApprovalInfo approveQueuedBuild(@ApiParam(format = LocatorName.BUILD) @PathParam("buildLocator") String buildLocator,
                                  String requestor,
                                  @QueryParam("fields") String fields) {
    final ApprovableBuildManager approvableBuildManager = myServiceLocator.getSingletonService(ApprovableBuildManager.class);
    BuildPromotionEx buildPromotionEx = (BuildPromotionEx)myBuildPromotionFinder.getBuildPromotion(null, buildLocator);
    if (approvableBuildManager.hasTimedOut(buildPromotionEx)) {
      throw new BadRequestException("This build has timed out and cannot be approved");
    }
    SUser user = myServiceLocator.getSingletonService(UserFinder.class).getCurrentUser();
    try {
      approvableBuildManager.addApprovedByUser(buildPromotionEx, user);
    } catch (IllegalStateException | AccessDeniedException e) {
      throw new BadRequestException(e.getMessage());
    }

    return new ApprovalInfo(buildPromotionEx, new Fields(fields), myBeanContext);
  }
}