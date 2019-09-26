/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.util.Arrays;
import java.util.Date;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 20/05/2015
 */
public class BuildFinderFixedBuildSequenceByPromotionTest extends BuildFinderTestBase {
  private MockTimeService myTimeService;
  private SUser myUser;
  private BuildTypeImpl myBuildConf2;
  private BuildTypeImpl myBuildConf;
  private SFinishedBuild myBuild1;
  private SFinishedBuild myBuild2failed;
  private SFinishedBuild myDeleted;
  private SFinishedBuild myBuild3tagged;
  private SFinishedBuild myBuild4conf2FailedPinned;
  private SFinishedBuild myBuild5personal;
  private SFinishedBuild myBuild6personalFailed;
  private SBuild myBuild7canceled;
  private SBuild myBuild8canceledFailed;
  private SFinishedBuild myBuild9failedToStart;
  private SFinishedBuild myBuild10byUser;
  private SFinishedBuild myBuild11inBranch;
  private SFinishedBuild myBuild12;
  private RunningBuildEx myBuild13running;
  private SQueuedBuild myBuild14queued;
  private Date myTimeAfterBuild4;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setInternalProperty(BuildFinder.LEGACY_BUILDS_FILTERING, "false");  //testing BuildPromotionFinder

    myTimeService = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(myTimeService);
//  do not need this in finally, do we?     myServer.setTimeService(SystemTimeService.getInstance());

    if (time.now() % 1000 == 0){ //disable checking 0ms case
      time.jumpTo(10L);
    }

    myUser = createUser("uuser");
    myBuildConf = registerBuildType("buildConf1", "project");
    myBuildConf2 = registerBuildType("buildConf2", "project");


    myBuild1 = build().in(myBuildConf).finish();
    myBuild2failed = build().in(myBuildConf).failed().finish();
    myDeleted = build().in(myBuildConf).failed().finish();
    myFixture.getHistory().removeEntry(myDeleted);

    myTimeService.jumpTo(10);

    myBuild3tagged = build().in(myBuildConf).finish();
    myBuild3tagged.setTags(Arrays.asList("tag1", "tag2"));

    myTimeService.jumpTo(10);

    myBuild4conf2FailedPinned = build().in(myBuildConf2).failed().finish();
    myBuild4conf2FailedPinned.setPinned(true, myUser, "pin comment");
    myTimeService.jumpTo(10);
    myTimeAfterBuild4 = myTimeService.getNow();
    myTimeService.jumpTo(10);

    myBuild5personal = build().in(myBuildConf).personalForUser(myUser.getUsername()).finish();
    myBuild6personalFailed = build().in(myBuildConf2).personalForUser(myUser.getUsername()).failed().finish();

    RunningBuildEx build7running = startBuild(myBuildConf);
    build7running.stop(myUser, "cancel comment");
    myBuild7canceled = finishBuild(build7running, false);

    final RunningBuildEx build8running = startBuild(myBuildConf);
    build8running.addBuildProblem(createBuildProblem()); //make the build failed
    build8running.stop(myUser, "cancel comment");
    myBuild8canceledFailed = finishBuild(build8running, true);

    myBuild9failedToStart = build().in(myBuildConf).failedToStart().finish();
    myTimeService.jumpTo(10);
    myBuild10byUser = build().in(myBuildConf).by(myUser).finish();
    myTimeService.jumpTo(10);
    myBuild11inBranch = build().in(myBuildConf).withBranch("branch").finish();
    myTimeService.jumpTo(10);
    myBuild12 = build().in(myBuildConf).finish();

    myBuild13running = startBuild(myBuildConf);
    myBuild14queued = addToQueue(myBuildConf);
  }

  @Test
  public void testSingleDimensions() {
    checkBuilds("buildType:(id:" + myBuildConf.getExternalId() + ")", myBuild12, myBuild10byUser, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("project:(id:" + myBuildConf.getProjectExternalId() + ")", myBuild12, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("project:(id:" + myBuildConf.getProjectExternalId() + "),failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned,
                myBuild3tagged,
                myBuild2failed, myBuild1);

    checkBuilds("user:(username:" + myUser.getUsername() + ")", myBuild10byUser);
    checkBuilds("tags:tag1", myBuild3tagged);
    checkBuilds("tag:tag1", myBuild3tagged);
    checkNoBuildsFound("tag:bla");

    checkBuilds("status:failure", myBuild4conf2FailedPinned, myBuild2failed);
    checkBuilds("status:failure,failedToStart:any", myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild2failed);
    checkBuilds("status:SUCCESS", myBuild12, myBuild10byUser, myBuild3tagged, myBuild1);

    checkBuilds("buildType:(id:" + myBuildConf2.getExternalId() + "),number:" + myBuild4conf2FailedPinned.getBuildNumber(), myBuild4conf2FailedPinned);

    checkBuilds("personal:true", myBuild6personalFailed, myBuild5personal);
    checkBuilds("personal:false", myBuild12, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("personal:false,failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("personal:any", myBuild12, myBuild10byUser, myBuild6personalFailed, myBuild5personal, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("personal:any,failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild6personalFailed, myBuild5personal, myBuild4conf2FailedPinned,
                myBuild3tagged, myBuild2failed, myBuild1);

    checkBuilds("canceled:true", myBuild8canceledFailed, myBuild7canceled);
    checkBuilds("canceled:false", myBuild12, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("canceled:false,failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("canceled:any", myBuild12, myBuild10byUser, myBuild8canceledFailed, myBuild7canceled, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("canceled:any,failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild8canceledFailed, myBuild7canceled, myBuild4conf2FailedPinned,
                myBuild3tagged, myBuild2failed, myBuild1);

    checkBuilds("running:true", myBuild13running);
    checkBuilds("running:false", myBuild12, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("running:false,failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("running:any", myBuild13running, myBuild12, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("running:any,failedToStart:any", myBuild13running, myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);

    checkBuilds("pinned:true", myBuild4conf2FailedPinned);
    checkBuilds("pinned:false", myBuild12, myBuild10byUser, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("pinned:false,failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("pinned:any", myBuild12, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("pinned:any,failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);

    checkBuilds("branch:branch", myBuild11inBranch);
    checkBuilds("branch:(default:true)", myBuild12, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("branch:(default:true),failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("branch:(default:false)", myBuild11inBranch);
    checkBuilds("branch:(default:any)", myBuild12, myBuild11inBranch, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("branch:(default:any),failedToStart:any", myBuild12, myBuild11inBranch, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
  }

  @Test
  public void testSinceUntilBuildSearch() {
    checkBuilds("sinceBuild:(id:" + myBuild3tagged.getBuildId() + ")", myBuild12, myBuild10byUser, myBuild4conf2FailedPinned);
    checkBuilds("sinceBuild:(id:" + myBuild3tagged.getBuildId() + "),failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned);
//    checkBuilds("sinceBuild:(id:" + deleted.getBuildId() + ")", build12, build10byUser, build4conf2FailedPinned, build3tagged); //todo: should handle this

    checkBuilds("untilBuild:(id:" + myBuild10byUser.getBuildId() + ")", myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("untilBuild:(id:" + myBuild10byUser.getBuildId() + "),failedToStart:any", myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned, myBuild3tagged,
                myBuild2failed, myBuild1);
//    checkBuilds("untilBuild:(id:" + deleted.getBuildId() + ")", build2failed, build1); //todo: should handle this

    final String startDate = fDate(myBuild4conf2FailedPinned.getStartDate());
    checkBuilds("sinceDate:" + startDate + ")", myBuild12, myBuild10byUser, myBuild4conf2FailedPinned);
    checkBuilds("sinceDate:" + startDate + "),failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned);
    checkBuilds("sinceDate:" + fDate(myTimeAfterBuild4), myBuild12, myBuild10byUser);
    checkBuilds("sinceDate:" + fDate(myTimeAfterBuild4) + ",failedToStart:any", myBuild12, myBuild10byUser, myBuild9failedToStart);
    checkBuilds("untilDate:" + startDate + ")", myBuild3tagged, myBuild2failed, myBuild1);
    checkBuilds("untilDate:" + fDate(myTimeAfterBuild4) + ")", myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
    checkExceptionOnBuildsSearch(BadRequestException.class,
                                 "sinceBuild:(id:" + myBuild3tagged.getBuildId() + "),sinceDate:" + startDate + ",byPromotion:false"); //this is for buildFinder

    //todo: these are for buildPromotionFinder
//    checkBuilds("sinceBuild:(id:" + myBuild3tagged.getBuildId() + "),sinceDate:" + startDate, myBuild12, myBuild10byUser, myBuild9failedToStart, myBuild4conf2FailedPinned);
//    checkBuilds("sinceBuild:(id:" + myBuild3tagged.getBuildId() + "),sinceDate:" + fDate(myTimeAfterBuild4), myBuild12, myBuild10byUser, myBuild9failedToStart);
    checkBuilds("sinceBuild:(id:" + myBuild3tagged.getBuildId() + "),untilDate:" + fDate(myTimeAfterBuild4), myBuild4conf2FailedPinned);
  }

  @Test
  public void testSingleBuildSearch() { //todo: remove the tests as they are checked in multiple builds search
    checkBuild("buildType:(id:" + myBuildConf2.getExternalId() + ")", myBuild4conf2FailedPinned);
    checkBuild("project:(id:" + myBuildConf.getProjectExternalId() + ")", myBuild12);

    checkBuild("user:(username:" + myUser.getUsername() + ")", myBuild10byUser);
    checkBuild("tags:tag1", myBuild3tagged);
    checkBuild("tag:tag1", myBuild3tagged);
    checkNoBuildFound("tag:bla");

    checkBuild("status:failure", myBuild4conf2FailedPinned);
    checkBuild("status:failure,failedToStart:any", myBuild9failedToStart);
    checkBuild("status:SUCCESS", myBuild12);

    checkBuild("buildType:(id:" + myBuildConf2.getExternalId() + "),number:" + myBuild4conf2FailedPinned.getBuildNumber(), myBuild4conf2FailedPinned);

    checkBuild("personal:true", myBuild6personalFailed);
    checkBuild("personal:false", myBuild12);
    checkBuild("personal:any", myBuild12); //todo: check what if the first one if failed to start, etc.

    checkBuild("canceled:true", myBuild8canceledFailed);
    checkBuild("canceled:false", myBuild12);  //todo: failed to start
    checkBuild("canceled:any", myBuild12);

    checkBuild("running:true", myBuild13running);
    checkBuild("running:false", myBuild12);
    checkBuild("running:any", myBuild13running);

    checkBuild("pinned:true", myBuild4conf2FailedPinned);
    checkBuild("pinned:false", myBuild12);
    checkBuild("pinned:any", myBuild12);

    checkBuild("branch:branch", myBuild11inBranch);
    checkBuild("branch:(default:true)", myBuild12);
    checkBuild("branch:(default:false)", myBuild11inBranch);
    checkBuild("branch:(default:any)", myBuild12);

    checkBuild("sinceBuild:(id:" + myBuild3tagged.getBuildId() + ")",
               myBuild12);

    checkBuild("untilBuild:(id:" + myBuild10byUser.getBuildId() + ")",
               myBuild10byUser);

    checkBuild("sinceDate:" + fDate(myBuild4conf2FailedPinned.getStartDate()) + ")", myBuild12);
    checkBuild("untilDate:" + fDate(myBuild4conf2FailedPinned.getStartDate()) + ")", myBuild3tagged);
  }

  @Test
  public void testMultipleDimensions1() {
    final String btId = myBuildConf.getExternalId();
    final long b2Id = myBuild2failed.getBuildId();
    final long b9id = myBuild9failedToStart.getBuildId();
    checkBuilds("buildType:(id:" + btId + "),sinceBuild:(id:" + b2Id + "),untilBuild:(id:" + b9id + "),status:SUCCESS",
                myBuild3tagged);
    //checkBuilds("buildType:(id:"+myBuildConf2.getExternalId()+"),user:(id:"+myUser.getId() +"),status:FAILURE,personal:true",
    //            myBuild6personalFailed);
    checkBuilds("buildType:(id:" + myBuildConf.getExternalId() + "),branch:(name:branch),status:SUCCESS,personal:false", myBuild11inBranch);
  }

  @Test
  public void testEmptyLocator() {
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "");
    checkMultipleBuilds(null, myBuild12, myBuild10byUser, myBuild4conf2FailedPinned, myBuild3tagged, myBuild2failed, myBuild1);
  }

}
