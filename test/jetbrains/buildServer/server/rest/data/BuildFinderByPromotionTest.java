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

import java.io.IOException;
import java.util.Date;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.CancelableTaskHolder;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 20/05/2015
 */
public class BuildFinderByPromotionTest extends BuildFinderTestBase {
  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setInternalProperty(BuildFinder.LEGACY_BUILDS_FILTERING, "false");  //testing BuildPromotionFinder
  }

  @Test
  public void testSingleLocators() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final BuildPromotion build0 = build().in(buildConf).finish().getBuildPromotion();
    final BuildPromotion build1 = build().in(buildConf).number("unique42").finish().getBuildPromotion();
    final BuildPromotion build2 = build().in(buildConf).failed().finish().getBuildPromotion();
    final BuildPromotion build3 = build().in(buildConf).number(String.valueOf(build2.getId() + 1000)).finish().getBuildPromotion(); //setting numeric number not clashing with any build id
    final BuildPromotion build4 = build().in(buildConf2).finish().getBuildPromotion();

    final BuildPromotion runningBuild5 = build().in(buildConf).run().getBuildPromotion();
    final BuildPromotion queuedBuild = build().in(buildConf).addToQueue().getBuildPromotion();

    @SuppressWarnings("ConstantConditions") final String build1Number = build1.getAssociatedBuild().getBuildNumber();
    @SuppressWarnings("ConstantConditions") final String runningBuild5Number = runningBuild5.getAssociatedBuild().getBuildNumber();

    checkBuild(String.valueOf(build1.getId()), build1);
    checkBuild("id:" + build1.getId(), build1);
    checkBuild("id:" + build1.getId() + ",personal:false", build1);
    checkBuild("id:" + build1.getId() + ",personal:any", build1);

    checkBuild("id:" + build2.getId(), build2);
    checkBuild("id:" + runningBuild5.getId(), runningBuild5);
    final long notExistingBuildId = runningBuild5.getId() + 10;
    checkExceptionOnBuildSearch(NotFoundException.class, "id:" + notExistingBuildId);
    checkExceptionOnBuildSearch(NotFoundException.class, String.valueOf(notExistingBuildId));
    checkBuild("number:" + build1Number, build1);
    checkExceptionOnBuildSearch(NotFoundException.class, "number:" + runningBuild5Number);
    checkBuild("number:" + runningBuild5Number + ",state:any", runningBuild5);

//    checkBuild(build1Number, build1); // difference from BuildFinder
    checkExceptionOnBuildSearch(BadRequestException.class, build1Number);
    checkBuild("id:" + build1.getId() + ",number:" + build1Number, build1);

    checkBuild("buildType:(id:" + buildConf.getExternalId() + "),number:" + build1Number, build1);
    checkBuild("buildType:(id:" + buildConf.getExternalId() + "),id:" + build1.getId(), build1);
    checkBuild("buildType:(id:" + buildConf.getExternalId() + "),id:" + build1.getId() + ",status:SUCCESS", build1);

    checkBuild("taskId:" + build1.getId(), build1);
    checkBuild("taskId:" + runningBuild5.getId(), runningBuild5);
    checkBuild("taskId:" + queuedBuild.getId(), queuedBuild); // difference from 9.0 behavior in BuildFinder
    checkBuild("id:" + queuedBuild.getId(), queuedBuild); // difference from BuildFinder

    checkBuild("id:" + queuedBuild.getId() + ",state:any", queuedBuild);
    checkBuild("id:" + queuedBuild.getId() + ",state:queued", queuedBuild);
    checkExceptionOnBuildsSearch(NotFoundException.class, "id:" + queuedBuild.getId() + ",state:running");

    checkBuild("promotionId:" + build1.getId(), build1);
    checkBuild("buildType:(id:" + buildConf.getExternalId() + "),promotionId:" + build1.getId(), build1);

    long notExistentBuildId = build4.getId() + 1;
    final RunningBuildsManager runningBuildsManager = myFixture.getSingletonService(RunningBuildsManager.class);
    while (myFixture.getHistory().findEntry(notExistentBuildId) != null || runningBuildsManager.findRunningBuildById(notExistentBuildId) != null ||
           myFixture.getBuildQueue().findQueued(String.valueOf(notExistentBuildId)) != null) {
      notExistentBuildId++;
    }

    long notExistentBuildPromotionId = build4.getId() + 1;
    while (myFixture.getBuildPromotionManager().findPromotionById(notExistentBuildPromotionId) != null) {
      notExistentBuildPromotionId++;
    }

    checkNoBuildFound("id:" + notExistentBuildId);
    checkNoBuildFound("buildType:(id:" + buildConf.getExternalId() + "),id:" + notExistentBuildId);
    checkNoBuildFound("buildType:(id:" + buildConf2.getExternalId() + "),id:" + build2.getId());
    checkNoBuildFound("promotionId:" + notExistentBuildPromotionId);
    checkNoBuildFound("buildType:(id:" + buildConf.getExternalId() + "),promotionId:" + notExistentBuildPromotionId);
    checkNoBuildFound("buildType:(id:" + buildConf2.getExternalId() + "),promotionId:" + build2.getId());
  }

  @Test
  public void testOldBuildId() throws IOException {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    long build10id; //no build with such promotion exists
    long build10PromotionId; //same as build20id
    long build20id; //the same as build10PromotionId
    long build20PromotionId;
    {
      final SFinishedBuild build10 = build().in(buildConf).finish();
      build10id = build10.getBuildId() + 100;
      build10PromotionId = build10.getBuildPromotion().getId();

      final SFinishedBuild build20 = build().in(buildConf).finish();
      build20id = build10PromotionId; //to be build id
      build20PromotionId = build20.getBuildPromotion().getId();

      prepareFinishedBuildIdChange(build10.getBuildId(), build10id);
      prepareFinishedBuildIdChange(build20.getBuildId(), build20id);
      recreateBuildServer();
      init();
    }

    final SBuild build10 = myServer.findBuildInstanceById(build10id);
    final SBuild build20 = myServer.findBuildInstanceById(build20id);
    assertNotNull(build10);
    assertEquals(build10id, build10.getBuildId());
    assertEquals(build10PromotionId, build10.getBuildPromotion().getId());
    assertNotNull(build20);
    assertEquals(build20id, build20.getBuildId());
    assertEquals(build20PromotionId, build20.getBuildPromotion().getId());

    checkBuild(String.valueOf(build10id), build10);
    checkBuild(String.valueOf(build10PromotionId), build20);
    checkBuild("id:" + build10id, build10);
    checkBuild("id:" + build10PromotionId, build20);
    checkNoBuildFound("taskId:" + build10id);
    checkBuild("taskId:" + build10PromotionId, build10);
    checkNoBuildFound("promotionId:" + build10id);
    checkBuild("promotionId:" + build10PromotionId, build10);

    checkBuild("id:" + build10PromotionId + ",buildType:(" + build10.getBuildTypeId() + ")", build20);

    checkBuild(String.valueOf(build20id), build20);
    checkNoBuildFound(String.valueOf(build20PromotionId));
//    checkBuild(String.valueOf(build20PromotionId), build20); no search by number is performed
    checkBuild("id:" + build20id, build20);
    checkNoBuildFound("id:" + build20PromotionId);
    checkBuild("taskId:" + build20id, build10);
    checkBuild("taskId:" + build20PromotionId, build20);
    checkBuild("promotionId:" + build20id, build10);
    checkBuild("promotionId:" + build20PromotionId, build20);
    checkBuild("buildId:" + build20id, build20);
  }

  @Test
  public void testForcedBuildType() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SFinishedBuild build1 = build().in(buildConf).number("unique42").finish();
    final SFinishedBuild build2 = build().in(buildConf).number(String.valueOf(build0.getBuildId())).failed().finish();
    final SFinishedBuild build3 = build().in(buildConf).number(String.valueOf(build2.getBuildId() + 1000)).finish(); //setting numeric number not clashing with any build id
    final SFinishedBuild build4 = build().in(buildConf2).number(String.valueOf(build0.getBuildId())).finish();

    checkBuild(buildConf, "unique42", build1.getBuildPromotion());
    checkBuild(buildConf, build3.getBuildNumber(), build3.getBuildPromotion());
    checkBuild(buildConf, build2.getBuildNumber(), build2.getBuildPromotion());
    checkBuild(buildConf2, build4.getBuildNumber(), build4.getBuildPromotion());
    checkExceptionOnBuildSearch(NotFoundException.class, buildConf, "id:" + build4.getBuildId());
    checkExceptionOnBuildSearch(NotFoundException.class, buildConf, "10000");
  }

  @Test
  public void testNoDimensionLocatorsWithBuildType() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SFinishedBuild build1 = build().in(buildConf).number("N42").finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();
    final SFinishedBuild build4 = build().in(buildConf2).number("N42").finish();

    final String numberLocator = "number:" + build1.getBuildNumber();
    assertEquals("For locator \"" + numberLocator + "\"", build1, myBuildFinder.getBuild(build1.getBuildType(), numberLocator));
    assertEquals("For locator \"" + numberLocator + "\"", build1.getBuildPromotion(), myBuildFinder.getBuildPromotion(build1.getBuildType(), numberLocator));

    final String notExistingNumberLocator = "number:" + "notExisting";
    checkExceptionOnBuildSearch(NotFoundException.class, notExistingNumberLocator);
    checkException(NotFoundException.class, new Runnable() {
      public void run() {
        myBuildFinder.getBuild(build1.getBuildType(), notExistingNumberLocator);
      }
    }, "searching single build with locator \"" + notExistingNumberLocator + "\"");

    checkException(NotFoundException.class, new Runnable() {
      public void run() {
        myBuildFinder.getBuildPromotion(build1.getBuildType(), notExistingNumberLocator);
      }
    }, "searching single build promotion with locator \"" + notExistingNumberLocator + "\"");
  }

  @Test
  public void testWrongLocator() throws Exception {
    checkExceptionOnBuildSearch(BadRequestException.class, "");
    checkExceptionOnBuildSearch(LocatorProcessException.class, ",:,");
    checkExceptionOnBuildSearch(BadRequestException.class, "xxx");
//    checkExceptionOnBuildSearch(NotFoundException.class, "xxx"); //is not treated as build number for performance reasons
    checkExceptionOnBuildSearch(LocatorProcessException.class, "xxx:yyy");
    checkExceptionOnBuildSearch(LocatorProcessException.class, "pinned:any,xxx:yyy");
    //checkExceptionOnBuildSearch(LocatorProcessException.class, "status:");
    //checkExceptionOnBuildSearch(LocatorProcessException.class, "status:WRONG");

    checkExceptionOnBuildsSearch(LocatorProcessException.class, "");
    checkExceptionOnBuildsSearch(LocatorProcessException.class, ",:,");
//    checkBuilds("xxx"); //is treated as build number  //any search by number causes BadRequestException instead
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "xxx:yyy");
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "pinned:any,xxx:yyy");
    //checkExceptionOnBuildsSearch(LocatorProcessException.class, "status:");
    //checkExceptionOnBuildsSearch(LocatorProcessException.class, "status:WRONG");
  }

  @Test
  public void testBasic1() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();
    final SFinishedBuild build3 = build().in(buildConf).finish();
    final SFinishedBuild build4 = build().in(registerBuildType("buildConf2", "project")).finish();

    final RunningBuildEx runningBuild = startBuild(buildConf);
    final SQueuedBuild queuedBuild = addToQueue(buildConf);

    checkBuilds("buildType:(id:" + buildConf.getExternalId() + ")", build3, build2, build1);
    checkBuilds("pinned:any", build4, build3, build2, build1);

    checkBuilds("start:0", build4, build3, build2, build1);
    checkBuilds("start:1", build3, build2, build1);
    checkBuilds("count:3", build4, build3, build2);
    checkBuilds("start:1,count:1", build3);
  }

  @Test
  public void testProjectDimension() throws Exception {
    final ProjectEx parent = createProject("parent");
    final ProjectEx nested = myFixture.createProject("nested", parent);
    final BuildTypeEx buildConf1 = parent.createBuildType("buildConf1");
    final BuildTypeEx buildConf2 = nested.createBuildType("buildConf2");

    final SFinishedBuild build1 = build().in(buildConf1).finish();
    final SFinishedBuild build2 = build().in(buildConf2).finish();

    checkBuilds("pinned:any", build2, build1);

    checkBuilds("project:(id:" + parent.getExternalId() + ")", build1);
    checkBuilds("affectedProject:(id:" + parent.getExternalId() + ")", build2, build1);
    checkBuilds("project:(id:" + nested.getExternalId() + ")", build2);
  }

  @Test
  public void testBranchDimension() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).finish();
    final SFinishedBuild build2 = build().in(buildConf).withBranch("branchName").finish();

    checkBuilds(null, build1);
    //by default no branched builds should be listed
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + ")", build1);
    checkBuilds("pinned:any", build1);

    checkBuilds("branch:<default>", build1);
    checkBuilds("branch:(default:true)", build1);
    checkBuilds("branch:(default:any)", build2, build1);
    checkBuilds("branch:(branchName)", build2);
    checkBuilds("branch:(name:branchName)", build2);
    checkExceptionOnBuildSearch(LocatorProcessException.class, "branch:(::)"); //invalid branch locator
    //checkExceptionOnBuildSearch(LocatorProcessException.class, "branch:(name:branchName,aaa:bbb)");  //unused/unknown dimension
    //checkExceptionOnBuildSearch(LocatorProcessException.class, "branch:(aaa:bbb)");
  }

  @Test
  public void testAgentDimension() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).finish();

    final MockBuildAgent agent = myFixture.createEnabledAgent("smth");
    registerAndEnableAgent(agent);
    final SFinishedBuild build2 = build().in(buildConf).on(agent).failed().finish();

    final MockBuildAgent agent2 = myFixture.createEnabledAgent("smth2");
    registerAndEnableAgent(agent2);
    final SFinishedBuild build3 = build().in(buildConf).on(agent2).failed().finish();


    checkBuilds("buildType:(id:" + buildConf.getExternalId() + ")", build3, build2, build1);
    checkBuilds("pinned:any", build3, build2, build1);

    checkBuilds("agent:(name:" + build1.getAgent().getName() + ")", build1);
    checkBuilds("agentName:" + build1.getAgent().getName(), build1);
    checkBuilds("agent:(name:" + agent.getName() + ")", build2);
    checkBuilds("agentName:" + agent.getName(), build2);

    checkBuilds("agent:(connected:true)", build3, build2, build1);

    unregisterAgent(build1.getAgent().getId());
    checkBuilds("agent:(connected:true)", build3, build2);
  }


  @Test
  public void testNumberDimension() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild build0 = build().in(buildConf).number("42").finish();
    final SFinishedBuild build1 = build().in(buildConf).number("42").finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();

    checkBuilds("number:" + build0.getBuildNumber(), build1, build0);
//    checkBuilds("buildType:(" + buildConf.getExternalId() + "),number:" + build0.getBuildNumber(), build1, build0);
    checkBuilds("buildType:(" + buildConf.getExternalId() + "),number:" + build0.getBuildNumber(), build1); //this finds only the most recent build for performance reasons
  }

  @Test
  public void testTagDimension() throws Exception {
    final SFinishedBuild build10 = build().in(myBuildType).finish();
    final SFinishedBuild build20 = build().in(myBuildType).tag("a").failed().finish();
    final SFinishedBuild build25 = build().in(myBuildType).tag("a").failedToStart().finish();
    final SFinishedBuild build30 = build().in(myBuildType).tag("a").tag("b").finish();
    final SFinishedBuild build40 = build().in(registerBuildType("buildConf1", "project")).tag("a").tag("b").tag("a:b").finish();
    final SFinishedBuild build50 = build().in(myBuildType).tag("aa").finish();
    final SFinishedBuild build60 = build().in(myBuildType).finish();
    final SRunningBuild build70 = build().in(myBuildType).tag("a").run();
    final SQueuedBuild build80 = build().in(myBuildType).tag("a").addToQueue();

    checkBuilds("buildType:(id:" + myBuildType.getExternalId() + ")", build60, build50, build30, build20, build10);
    checkBuilds("pinned:any", build60, build50, build40, build30, build20, build10); //fix: failed to start
    checkBuilds("pinned:any,failedToStart:any", build60, build50, build40, build30, build25, build20, build10); //fix: failed to start

    checkBuilds("tag:a,buildType:(id:" + myBuildType.getExternalId() + ")", build30, build20);
    checkBuilds("tag:a", build40, build30, build20);
    checkBuilds("tag:a,failedToStart:any", build40, build30, build25, build20);
    checkBuilds("tag:aa", build50);
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "tag:(a:b)");
    checkBuilds("tag:(name:(a:b))", build40);
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "tag:a,tag:b");

    checkBuilds("tags:(a,b)", build40, build30); //???
    checkBuilds("tags:(a:b)", build40);
    checkBuilds("tags:(b,a:b)", build40);
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "tags:a,tags:b"); //"documenting" existing exception types
    checkBuilds("tag:a,tags:b", build40, build30);

    checkBuilds("tag:(format:extended,present:any,regexp:aaa)", build60, build50, build40, build30, build20, build10);
    checkBuilds("tag:(format:extended,present:any,regexp:aaa),failedToStart:any", build60, build50, build40, build30, build25, build20, build10);
    checkBuilds("tag:(format:extended,present:true)", build50, build40, build30, build20);
    checkBuilds("tag:(format:extended,present:true),failedToStart:any", build50, build40, build30, build25, build20);
    checkBuilds("tag:(format:extended,present:false)", build60, build10);
    checkBuilds("tag:(format:extended,present:true,regexp:a.)", build50);
    //checkBuilds("tag:(present:true,regexp:a.,format:extended)", build50);
    checkBuilds("tag:(format:extended,present:false,regexp:a.)", build60, build40, build30, build20, build10);
    checkBuilds("tag:(format:extended,present:false,regexp:a.),failedToStart:any", build60, build40, build30, build25, build20, build10);
    //checkExceptionOnBuildsSearch(BadRequestException.class, "tag:(format:notExtended,present:true)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "tag:(format:extended,present:true,regexp:)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "tag:(format:extended,present:true,regexp:*)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "tag:(format:extended,present:true,regexp:*,a:b)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "tag:(format:extended,present:true,regexp:())");
  }

  @Test
  public void testPropertyDimension() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).parameter("a", "x").finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();
    final SFinishedBuild build3 = build().in(buildConf).parameter("a", "y").finish();
    final SFinishedBuild build4 = build().in(buildConf).parameter("b", "x").finish();

    checkBuilds("buildType:(id:" + buildConf.getExternalId() + ")", build4, build3, build2, build1);
    checkBuilds("pinned:any", build4, build3, build2, build1);

    checkBuilds("property:(name:a)", build3, build1);
    checkBuilds("property:(name:a,value:y)", build3);
    checkBuilds("property:(value:x)", build4, build1);
  }

  @Test
  public void testLookupLimitDimension() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).parameter("a", "x").finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().parameter("a", "x").finish();
    final SFinishedBuild build3 = build().in(buildConf).parameter("a", "x").finish();
    final SFinishedBuild build4 = build().in(buildConf).failed().finish();
    final SFinishedBuild build5 = build().in(buildConf).parameter("a", "x").finish();
    final SFinishedBuild build6 = build().in(buildConf).finish();

    final String baseLocator = "status:SUCCESS,property:(name:a)";
    checkBuilds(baseLocator, build5, build3, build1);
    checkBuilds(baseLocator + ",lookupLimit:1");
    checkBuilds(baseLocator + ",lookupLimit:2", build5);
    checkBuilds(baseLocator + ",lookupLimit:3", build5);
    checkBuilds(baseLocator + ",lookupLimit:4", build5, build3);
  }

  @Test
  public void testDefaultCountDimension() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    SBuild[] otherBuilds = new SBuild[20];
    for (int i = 0; i < 20; i++) {
      otherBuilds[19 - i] = build().in(buildConf).finish();
    }
    SBuild[] builds = new SBuild[100];
    for (int i = 0; i < 100; i++) {
      builds[99 - i] = build().in(buildConf).finish();
    }
    checkBuilds("pinned:any", builds); //by default should return only 100 builds
    checkBuilds("count:100", builds);
    checkBuilds("count:2,start:99", builds[99], otherBuilds[0]);
  }

  @Test
  public void testQueuedBuildByMergedPromotion() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf", "project");
    myFixture.registerVcsSupport("vcsSuport");
    buildConf.addVcsRoot(buildConf.getProject().createVcsRoot("vcsSuport", "extId", "name"));
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    buildConf2.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(buildConf));

    final SQueuedBuild queuedBuild1 = build().in(buildConf).addToQueue();
    final long id1 = queuedBuild1.getBuildPromotion().getId();
    final SQueuedBuild queuedBuild2 = build().in(buildConf2).addToQueue();
    final long id2 = queuedBuild2.getBuildPromotion().getDependencies().iterator().next().getDependOn().getId();

    assertEquals(3, myFixture.getBuildQueue().getNumberOfItems());
    assertNotSame(id1, id2);

    assertTrue(((BuildPromotionEx)queuedBuild1.getBuildPromotion()).getTopDependencyGraph().collectChangesForGraph(new CancelableTaskHolder()));
    assertTrue(((BuildPromotionEx)queuedBuild2.getBuildPromotion()).getTopDependencyGraph().collectChangesForGraph(new CancelableTaskHolder()));

    myFixture.getBuildQueue().setMergeBuildsInQueue(true);
    myFixture.getBuildQueue().mergeBuilds();

    assertEquals(2, myFixture.getBuildQueue().getNumberOfItems());


    PagedSearchResult<SQueuedBuild> result = myQueuedBuildFinder.getItems("id:" + id2);

    assertEquals(1, result.myEntries.size());
    assertEquals(id2, result.myEntries.get(0).getBuildPromotion().getId());

    result = myQueuedBuildFinder.getItems("id:" + id1);

    assertEquals(1, result.myEntries.size());
    assertEquals(id2, result.myEntries.get(0).getBuildPromotion().getId());
  }

  @Test
  void testCanceledBuilds() {
    final SFinishedBuild b1 = build().in(myBuildType).finish();

    final RunningBuildEx running2 = startBuild(myBuildType);
    running2.stop(createUser("uuser1"), "cancel comment");
    SBuild b2canceled = finishBuild(running2, true);

    final RunningBuildEx running3 = startBuild(myBuildType);
    running3.addBuildProblem(createBuildProblem()); //make the build failed
    running3.stop(createUser("uuser2"), "cancel comment");
    SBuild b3canceledFailed = finishBuild(running3, true);

    final SFinishedBuild b4 = build().in(myBuildType).failed().finish();
    final SFinishedBuild b5_failedToStart = build().in(myBuildType).failedToStart().finish();

    //by default no canceled builds should be listed
    checkBuilds("buildType:(id:" + myBuildType.getExternalId() + ")", b4, b1);
    checkBuilds("pinned:any", b4, b1);
    checkBuilds("pinned:any,failedToStart:any", b5_failedToStart, b4, b1);

    checkBuilds("canceled:true", b3canceledFailed, b2canceled);
    checkBuilds("canceled:true,status:UNKNOWN", b3canceledFailed, b2canceled);
    checkBuilds("canceled:false", b4, b1);
    checkBuilds("canceled:false,failedToStart:any", b5_failedToStart, b4, b1);
    checkBuilds("canceled:any,status:FAILURE", b4);
    checkBuilds("canceled:any,status:FAILURE,failedToStart:any", b5_failedToStart, b4);
    checkBuilds("canceled:any,status:SUCCESS", b1);
    //due status processing?
    //checkBuilds("canceled:true,status:SUCCESS", b2canceled);
    //checkBuilds("buildType:(id:" + myBuildType.getExternalId() + "),canceled:any,status:FAILED", b3canceledFailed, b4);
   }

  @Test
  void testPersonalBuilds() {
    final SUser user1 = createUser("uuser1");
    final SFinishedBuild b10 = build().in(myBuildType).finish();
    final SFinishedBuild b20personal = build().in(myBuildType).personalForUser(user1.getUsername()).finish();

    //TW-23299
    checkBuilds("pinned:any", b10);
    checkBuilds("status:SUCCESS", b10);

    final RunningBuildEx running30 = build().in(myBuildType).run();
    running30.stop(user1, "cancel comment");
    SBuild b30canceled = finishBuild(running30, true);

    final BuildTypeImpl buildType2 = registerBuildType("buildConf2", "project");
    final RunningBuildEx running40 = build().in(buildType2).personalForUser(user1.getUsername()).run();
    running40.stop(user1, "cancel comment");
    SBuild b40personalCanceled = finishBuild(running40, true);

    final SUser user2 = createUser("uuser2");
    final RunningBuildEx running50 = build().in(myBuildType).run();
    running50.addBuildProblem(createBuildProblem()); //make the build failed
    running50.stop(user2, "cancel comment");
    SBuild b50canceledFailed = finishBuild(running50, true);

    final RunningBuildEx running60 = build().in(myBuildType).personalForUser(user1.getUsername()).run();
    running60.addBuildProblem(createBuildProblem()); //make the build failed
    running60.stop(user2, "cancel comment");
    SBuild b60personalCanceledFailed = finishBuild(running60, true);

    final SFinishedBuild b70 = build().in(buildType2).failed().finish();
    final SFinishedBuild b80personal = build().in(buildType2).personalForUser(user1.getUsername()).failed().finish();
    final SFinishedBuild b90FailedToStart = build().in(myBuildType).failedToStart().finish();
    final SFinishedBuild b100personalFailedToStart = build().in(myBuildType).personalForUser(user2.getUsername()).failedToStart().finish();

     //by default no personal builds should be listed
    checkBuilds("buildType:(id:" + myBuildType.getExternalId() + ")", b10);
    checkBuilds("pinned:any", b70, b10);
    checkBuilds("pinned:any,failedToStart:any", b90FailedToStart, b70, b10);

    checkBuilds("personal:true", b80personal, b20personal);
    checkBuilds("personal:true,failedToStart:any", b100personalFailedToStart, b80personal, b20personal);
    //checkBuilds("personal:true,status:UNKNOWN", b60personalCanceledFailed, b40personalCanceled);
    checkBuilds("personal:true,canceled:true", b60personalCanceledFailed, b40personalCanceled);
    checkBuilds("personal:false", b70, b10);
    checkBuilds("personal:false,failedToStart:any", b90FailedToStart, b70, b10);
    checkBuilds("personal:any,status:FAILURE", b80personal, b70);
    checkBuilds("personal:any,status:FAILURE,failedToStart:any", b100personalFailedToStart, b90FailedToStart, b80personal, b70);
    checkBuilds("personal:any,status:SUCCESS", b20personal, b10);
    checkBuilds("personal:true,status:SUCCESS", b20personal);
    //checkBuilds("buildType:(id:" + myBuildType.getExternalId() + "),personal:any,status:FAILURE", b100personalFailedToStart, b90FailedToStart);
    checkNoBuildsFound("user:(id:" + user1.getId() + ")");
    checkNoBuildsFound("user:(id:" + user1.getId() + "),personal:true");
    checkNoBuildsFound("user:(id:" + user1.getId() + "),personal:any");
   }

  @Test
  public void testSinceUntil() {
    final MockTimeService time = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(time);

    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild build10 = build().in(buildConf1).finish();
    time.jumpTo(10);
    final Date afterBuild10 = time.getNow();
    time.jumpTo(10);
    final SFinishedBuild build20 = build().in(buildConf2).failed().finish();
    time.jumpTo(10);

    final SFinishedBuild build25Deleted = build().in(buildConf2).failed().finish();
    final long build25DeletedId = build25Deleted.getBuildId();
    myFixture.getSingletonService(BuildHistory.class).removeEntry(build25Deleted);

    final SFinishedBuild build30 = build().in(buildConf2).failedToStart().finish();
    time.jumpTo(10);
    final Date afterBuild30 = time.getNow();
    time.jumpTo(10);

    final SFinishedBuild build40 = build().in(buildConf1).finish();
    time.jumpTo(10);

    final SFinishedBuild build50Deleted = build().in(buildConf2).failed().finish();
    final long build50DeletedId = build50Deleted.getBuildId();
    myFixture.getSingletonService(BuildHistory.class).removeEntry(build50Deleted);

    final SFinishedBuild build60 = build().in(buildConf2).finish();
    time.jumpTo(10);
    final Date afterBuild60 = time.getNow();

    final SFinishedBuild build70 = build().in(buildConf1).finish();

    time.jumpTo(10);
    final SRunningBuild build80 = build().in(buildConf1).run();
    time.jumpTo(10);
    final SQueuedBuild build90 = build().in(buildConf1).addToQueue();

    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + ")", build70, build60, build40, build20);
    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),failedToStart:any", build70, build60, build40, build30, build20);
//    see also jetbrains.buildServer.server.rest.data.BuildPromotionFinderTest.testSinceUntil()
    checkBuilds("sinceBuild:(id:" + build25DeletedId + ")", build70, build60, build40);
    checkBuilds("sinceBuild:(id:" + build25DeletedId + "),failedToStart:any", build70, build60, build40, build30);

    checkBuilds("untilBuild:(id:" + build60.getBuildId() + ")", build60, build40, build20, build10);
    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),failedToStart:any", build60, build40, build30, build20, build10);
    checkBuilds("untilBuild:(id:" + build50DeletedId + "),state:any", build40, build20, build10);
    checkBuilds("untilBuild:(id:" + build50DeletedId + "),state:any,failedToStart:any", build40, build30, build20, build10);

    checkBuilds("sinceDate:" + fDate(build20.getStartDate()) + ")", build70, build60, build40, build20);
    checkBuilds("sinceDate:" + fDate(build20.getStartDate()) + "),failedToStart:any", build70, build60, build40, build30, build20);
    checkBuilds("sinceDate:" + fDate(afterBuild30) + ")", build70, build60, build40);
    checkBuilds("untilDate:" + fDate(build60.getStartDate()) + ")", build40, build20, build10);
    checkBuilds("untilDate:" + fDate(build60.getStartDate()) + "),failedToStart:any", build40, build30, build20, build10);
    checkBuilds("untilDate:" + fDate(afterBuild30) + ")", build20, build10);
    checkBuilds("untilDate:" + fDate(afterBuild30) + "),failedToStart:any", build30, build20, build10);

    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),sinceDate:" + fDate(build10.getStartDate()), build70, build60, build40, build20);
    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),sinceDate:" + fDate(build10.getStartDate()) + ",failedToStart:any", build70, build60, build40, build30, build20);
    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),sinceDate:" + fDate(afterBuild30), build70, build60, build40);
    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + fDate(build60.getStartDate()), build40, build20, build10);
    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + fDate(build60.getStartDate()) + ",failedToStart:any", build40, build30, build20, build10);
    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + fDate(afterBuild30), build20, build10);
    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + fDate(afterBuild30) + ",failedToStart:any", build30, build20, build10);

    checkBuilds("sinceBuild:(id:" + build20.getBuildId() + "),untilBuild:" + build60.getBuildId(), build60, build40);
    checkBuilds("sinceBuild:(id:" + build20.getBuildId() + "),untilBuild:" + build60.getBuildId() + ",failedToStart:any", build60, build40, build30);
    checkBuilds("sinceBuild:(id:" + build20.getBuildId() + "),untilDate:" + fDate(afterBuild30));
    checkBuilds("sinceBuild:(id:" + build20.getBuildId() + "),untilDate:" + fDate(afterBuild30) + ",failedToStart:any", build30);
    checkBuilds("sinceDate:(" + fDate(afterBuild10) + "),untilDate:" + fDate(afterBuild30), build20);
    checkBuilds("sinceDate:(" + fDate(afterBuild10) + "),untilDate:" + fDate(afterBuild30) + ",failedToStart:any", build30, build20);
  }

  @Test
  public void testSinceWithOldBuildId() throws IOException {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    long initialId;
    long build40Id;
    {
      //increment promotion id and build id
      final SFinishedBuild build = build().in(buildConf).finish();
      initialId = build.getBuildId();
      myFixture.getSingletonService(BuildHistory.class).removeEntry(build);
      for (int i = 0; i < 10; i++) {
        myFixture.getSingletonService(BuildHistory.class).removeEntry(build().in(buildConf).finish());
      }

      final SFinishedBuild build10 = build().in(buildConf).finish();
      final SFinishedBuild build20 = build().in(buildConf).finish();
      final SFinishedBuild build30 = build().in(buildConf).finish();
      final SFinishedBuild build40 = build().in(buildConf).finish();
      build40Id = build40.getBuildId();

      prepareFinishedBuildIdChange(build10.getBuildId(), initialId);
      prepareFinishedBuildIdChange(build20.getBuildId(), initialId + 1);
      prepareFinishedBuildIdChange(build30.getBuildId(), initialId + 2);
      recreateBuildServer();
      init();
    }

    final SBuild build10 = myServer.findBuildInstanceById(initialId);
    final SBuild build20 = myServer.findBuildInstanceById(initialId + 1);
    final SBuild build30 = myServer.findBuildInstanceById(initialId + 2);
    final SBuild build40 = myServer.findBuildInstanceById(build40Id);

    checkBuilds("sinceBuild:(id:" + (initialId + 2) +")", build40);
    checkBuilds("sinceBuild:(id:" + (initialId + 1) +")", build40, build30);
  }

  @Test
  public void testSinceWithReorderedBuilds() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SQueuedBuild queuedBuild10 = build().in(buildConf).addToQueue();
    final SQueuedBuild queuedBuild20 = build().in(buildConf).parameter("a", "x").addToQueue(); //preventing build from merging in the queue
    final SQueuedBuild queuedBuild30 = build().in(buildConf).parameter("a", "y").addToQueue(); //preventing build from merging in the queue
    myFixture.getBuildQueue().moveTop(queuedBuild30.getItemId());
    final SFinishedBuild build30 = myFixture.finishBuild(BuildBuilder.run(queuedBuild30, myFixture), false);
    final SFinishedBuild build10 = myFixture.finishBuild(BuildBuilder.run(queuedBuild10, myFixture), false);
    final SFinishedBuild build20 = myFixture.finishBuild(BuildBuilder.run(queuedBuild20, myFixture), false);

    checkBuilds(null, build20, build10, build30);
    checkBuilds("sinceBuild:(id:" + build20.getBuildId() +")");
    checkBuilds("sinceBuild:(id:" + build10.getBuildId() +")", build20);
    checkBuilds("sinceBuild:(id:" + build30.getBuildId() +")", build20, build10);
  }

}
