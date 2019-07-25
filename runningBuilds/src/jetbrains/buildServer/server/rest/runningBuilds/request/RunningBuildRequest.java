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

package jetbrains.buildServer.server.rest.runningBuilds.request;

import java.util.Collections;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.server.rest.data.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.TimeWithPrecision;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.request.Constants;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.QueuedBuildEx;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.auth.AuthorityHolder;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.BuildAgentMessagesQueue;
import jetbrains.buildServer.serverSide.impl.auth.ServerAuthUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TimeService;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 */
@Path(Constants.API_URL + "/runningBuilds")
//@Api("RunningBuilds") //todo Swagger
public class RunningBuildRequest {
  @Context @NotNull private ServiceLocator myServiceLocator;
  @Context @NotNull private BuildPromotionFinder myBuildPromotionFinder;
  @Context @NotNull private BeanFactory myFactory;
  @Context @NotNull private PermissionChecker myPermissionChecker;
  @Context @NotNull private BeanContext myBeanContext;

  //drop
  @GET
  @Path("/test")
  @Produces({MediaType.TEXT_PLAIN})
  public String test() {
    return "OK";
  }

  //drop
  @POST
  @Path("/test")
  @Produces({MediaType.TEXT_PLAIN})
  public String testPost() {
    return "OK";
  }

    //todo: add GET .../runningData for consistency
    @PUT
    @Path("/{buildLocator}/runningData")
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Build markBuildAsRunning(@PathParam("buildLocator") String buildLocator,
                                    String requestor,
                                    @QueryParam("fields") String fields,
                                    @Context HttpServletRequest request) {
      BuildPromotion buildPromotion = myBuildPromotionFinder.getBuildPromotion(null, buildLocator);
      checkBuildOperationPermission(buildPromotion);
      QueuedBuildEx build = (QueuedBuildEx)buildPromotion.getQueuedBuild();
      if (build != null) {
        try {
          build.startBuild(requestor);
        } catch (IllegalStateException e) {
          throw new BadRequestException("Error on attempt to mark the build as running: " + e.getMessage());
        }
      } else {
        throw new BadRequestException("Build with promotion id " + buildPromotion.getId() + " is not a queued build");
      }
      return new Build(buildPromotion, new Fields(fields), myBeanContext);
    }

  @POST
  @Path("/{buildLocator}/log")
  @Consumes({MediaType.TEXT_PLAIN})
  public void addLogMessage(@PathParam("buildLocator") String buildLocator, //todo: return next command to the client
                            String logLines,
                            @QueryParam("fields") String fields,
                            @Context HttpServletRequest request) {
    BuildPromotion buildPromotion = myBuildPromotionFinder.getBuildPromotion(null, buildLocator);
    checkBuildOperationPermission(buildPromotion);
    // check for running?
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null) {
      throw new NotFoundException("Build with id " + buildPromotion.getId() + " is not in the runing or finished state");
    }
    logMessage(build, logLines);
    //return next command?
  }

  //todo: ideally, should put all the data from the same client into the same flow in the build
  //can also try to put it into a dedicated block...
  private void logMessage(final SBuild build, final String lines) {
//    build.getBuildLog().message(lines, Status.NORMAL, MessageAttrs.attrs());
    if (build.isFinished() || !(build instanceof RunningBuildEx)) {
      throw new NotFoundException("Build with id " + build.getBuildId() + " is already finished");
    }
    RunningBuildEx runningBuild = (RunningBuildEx)build;
    try {
      myServiceLocator.getSingletonService(BuildAgentMessagesQueue.class).processMessages(runningBuild, Collections.singletonList(DefaultMessagesInfo.createTextMessage(lines)));
    } catch (InterruptedException e) {
      throw new OperationException("Got interrupted", e); //todo
    } catch (BuildAgentMessagesQueue.BuildMessagesQueueFullException e) {
      throw new OperationException("Failed to add messages as the queue is full", e); //todo
    }
  }

  @PUT
  @Path("/{buildLocator}/finishDate") //add GET
  @Consumes({MediaType.TEXT_PLAIN})
  @Produces({MediaType.TEXT_PLAIN})
  public String setFinishedTime(@PathParam("buildLocator") String buildLocator,
                            String date,
                            @QueryParam("fields") String fields, //drop
                            @Context HttpServletRequest request) {
    BuildPromotion buildPromotion = myBuildPromotionFinder.getBuildPromotion(null, buildLocator);
    checkBuildOperationPermission(buildPromotion);
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build == null) {
      throw new NotFoundException("Build with id " + buildPromotion.getId() + " is not in the runing or finished state");
    }
    if (build.isFinished() || !(build instanceof RunningBuildEx)) {
      throw new NotFoundException("Build with id " + buildPromotion.getId() + " is already finished");
    }
    RunningBuildEx runningBuild = (RunningBuildEx)build;
    TimeService timeService = myServiceLocator.findSingletonService(TimeService.class);
    Date finishTime = !StringUtil.isEmpty(date) ? TimeWithPrecision.parse(date, timeService).getTime() : new Date(timeService.now());
    logMessage(build, "Build finish request received via REST endpoint");
    runningBuild.markAsFinished(finishTime, false); //todo: should add a message into the build log
    return Util.formatTime(finishTime);
    //add javadoc and note that it's async
  }

  private void checkBuildOperationPermission(@NotNull final BuildPromotion buildPromotion) {
    AuthorityHolder user = myPermissionChecker.getCurrent();
    Long buildId = ServerAuthUtil.getBuildIdIfBuildAuthority(user);
    if (buildId != null) {
      SBuild build = buildPromotion.getAssociatedBuild();
      if (build != null && buildId == build.getBuildId()) {
        return;
      }
    }
    try {
      myPermissionChecker.checkPermission(Permission.EDIT_PROJECT, buildPromotion);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedException("Should use build authentication or have \"" + Permission.EDIT_PROJECT + "\" permission to do the build operation", e);
    }
  }
}