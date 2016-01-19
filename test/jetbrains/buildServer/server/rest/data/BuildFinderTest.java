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
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.impl.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsRootInstanceEx;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;
import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2014
 */
public class BuildFinderTest extends BuildFinderTestBase {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setInternalProperty(BuildFinder.LEGACY_BUILDS_FILTERING, "true"); //testing BuildFinder
  }

  @Test
  public void testSingleLocators() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SFinishedBuild build1 = build().in(buildConf).number("unique42").finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();
    final SFinishedBuild build3 = build().in(buildConf).number(String.valueOf(build2.getBuildId() + 1000)).finish(); //setting numeric number not clashing with any build id
    final SFinishedBuild build4 = build().in(buildConf2).finish();

    final RunningBuildEx runningBuild5 = build().in(buildConf).run();
    final SQueuedBuild queuedBuild = build().in(buildConf).addToQueue();

    checkBuild(String.valueOf(build1.getBuildId()), build1);
    checkBuild("id:" + build1.getBuildId(), build1);
    checkBuild("id:" + build2.getBuildId(), build2);
    checkBuild("id:" + runningBuild5.getBuildId(), runningBuild5);
    final long notExistingBuildId = runningBuild5.getBuildId() + 10;
    checkExceptionOnBuildSearch(NotFoundException.class, "id:" + notExistingBuildId);
    checkExceptionOnBuildSearch(NotFoundException.class, String.valueOf(notExistingBuildId));
    checkBuild("number:" + build1.getBuildNumber(), build1);
//might need to fix    checkBuild("number:" + runningBuild5.getBuildNumber(), runningBuild5);
    checkExceptionOnBuildSearch(NotFoundException.class, "number:" + runningBuild5.getBuildNumber());
//might need to fix    checkBuild(build1.getBuildNumber(), build1);
    checkExceptionOnBuildSearch(LocatorProcessException.class, build1.getBuildNumber());
//fix    checkExceptionOnBuildSearch(LocatorProcessException.class, "id:" + build1.getBuildId() + ",number:" + build1.getBuildNumber());
    checkBuild("id:" + build1.getBuildId() + ",number:" + build1.getBuildNumber(), build1);

    checkBuild("buildType:(id:" + buildConf.getExternalId() + "),number:" + build1.getBuildNumber(), build1);
    checkBuild("buildType:(id:" + buildConf.getExternalId() + "),id:" + build1.getBuildId(), build1);

    checkBuild("taskId:" + build1.getBuildPromotion().getId(), build1);
    checkBuild("taskId:" + runningBuild5.getBuildPromotion().getId(), runningBuild5);
    checkBuild("taskId:" + queuedBuild.getBuildPromotion().getId(), queuedBuild.getBuildPromotion());  //change of behavior comparing to 9.0 (used to get NotFoundException)
    checkExceptionOnBuildSearch(NotFoundException.class, "id:" + queuedBuild.getItemId());
    checkBuild("promotionId:" + build1.getBuildPromotion().getId(), build1);
    checkBuild("buildType:(id:" + buildConf.getExternalId() + "),promotionId:" + build1.getBuildPromotion().getId(), build1);

    long notExistentBuildId = build4.getBuildId() + 1;
    final RunningBuildsManager runningBuildsManager = myFixture.getSingletonService(RunningBuildsManager.class);
    while (myFixture.getHistory().findEntry(notExistentBuildId) != null || runningBuildsManager.findRunningBuildById(notExistentBuildId) != null ||
           myFixture.getBuildQueue().findQueued(String.valueOf(notExistentBuildId)) != null) {
      notExistentBuildId++;
    }

    long notExistentBuildPromotionId = build4.getBuildPromotion().getId() + 1;
    while (myFixture.getBuildPromotionManager().findPromotionById(notExistentBuildPromotionId) != null) {
      notExistentBuildPromotionId++;
    }

    checkNoBuildFound("id:" + notExistentBuildId);
    checkNoBuildFound("buildType:(id:" + buildConf.getExternalId() + "),id:" + notExistentBuildId);
    checkNoBuildFound("buildType:(id:" + buildConf2.getExternalId() + "),id:" + build2.getBuildId());
    checkNoBuildFound("promotionId:" + notExistentBuildPromotionId);
    checkNoBuildFound("buildType:(id:" + buildConf.getExternalId() + "),promotionId:" + notExistentBuildPromotionId);
    checkNoBuildFound("buildType:(id:" + buildConf2.getExternalId() + "),promotionId:" + build2.getBuildPromotion().getId());
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

    checkBuild(String.valueOf(build20id), build20);
    checkNoBuildFound(String.valueOf(build20PromotionId));
    checkBuild("id:" + build20id, build20);
    checkNoBuildFound("id:" + build20PromotionId);
    checkBuild("taskId:" + build20id, build10);
    checkBuild("taskId:" + build20PromotionId, build20);
    checkBuild("promotionId:" + build20id, build10);
    checkBuild("promotionId:" + build20PromotionId, build20);
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
    checkExceptionOnBuildSearch(LocatorProcessException.class, "xxx");
    checkExceptionOnBuildSearch(LocatorProcessException.class, "xxx:yyy");
    checkExceptionOnBuildSearch(LocatorProcessException.class, "pinned:any,xxx:yyy");
    /*
    checkExceptionOnBuildSearch(LocatorProcessException.class, "status:");
    checkExceptionOnBuildSearch(LocatorProcessException.class, "status:WRONG");
    */
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

    checkBuilds("project:(id:" + parent.getExternalId() + ")", build2, build1);
    checkBuilds("project:(id:" + nested.getExternalId() + ")", build2);
    checkBuilds("project:(id:" + parent.getExternalId() + "),byPromotion:false", build2, build1);
    checkBuilds("project:(id:" + parent.getExternalId() + "),byPromotion:true", build1);
  }

  @Test
  public void testBranchDimension() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).withDefaultBranch().finish();
    final SFinishedBuild build2 = build().in(buildConf).withBranch("branchName").finish();

    checkBuilds(null, build1);
    //by default no branched builds should be listed
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + ")", build1);
    checkBuilds("pinned:any", build1);

    //checkBuilds("branch:<default>", build1); this works only if the build was forced to run on the branch "<default>". Normally, however, branch is set to null
    checkBuilds("branch:(default:true)", build1);
    checkBuilds("branch:(default:any)", build2, build1);
    checkBuilds("branch:(branchName)", build2);
    checkBuilds("branch:(name:branchName)", build2);
    checkExceptionOnBuildSearch(LocatorProcessException.class, "branch:(::)"); //invalid branch locator
  }

  @Test
  public void testBranchDimension2() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final SFinishedBuild build5 = build().in(buildConf).finish(); //not branched build

    //settings to make display name for branch != branch name
    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstanceEx root1 = (VcsRootInstanceEx)buildConf.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstanceEx root2 = (VcsRootInstanceEx)buildConf.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;
    setBranchSpec(root1, "+:b1");
    setBranchSpec(root2, "+:b2");

    final MockCollectRepositoryChangesPolicy changesPolicy = new MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "rev1", "b1", "revB1")));
    changesPolicy.setCurrentState(root2, createVersionState("master", map("master", "rev1", "b2", "revB2")));


    final SFinishedBuild build10 = build().in(buildConf).finish();

    final SFinishedBuild build20 = build().in(buildConf).withBranch(Branch.DEFAULT_BRANCH_NAME).finish();
    final SFinishedBuild build30 = build().in(buildConf).withBranch("master").finish();
    final SFinishedBuild build40 = build().in(buildConf).withBranch("b1").finish();

    final Branch branch = build20.getBranch();
    assert branch != null;
    assertEquals("<default>", branch.getName());
    assertEquals("master", branch.getDisplayName());

    //by default no branched builds should be listed
    checkBuilds(null, build20, build10, build5);
    final String btLocator = "buildType:(id:" + buildConf.getExternalId() + ")";
    checkBuilds(btLocator, build20, build10, build5);
    checkBuilds("pinned:any", build20, build10, build5);

    checkBuilds("branch:<default>", build20, build10);
    checkBuilds("branch:(default:true)", build20, build10, build5);
    checkBuilds("branch:(default:any)", build40, build30, build20, build10, build5);
    checkBuilds("branch:<any>", build40, build30, build20, build10, build5);
    checkBuilds("branch:(default:false)", build40, build30);
    checkBuilds("branch:(b1)", build40);
    checkBuilds("branch:(name:b1)", build40);
    checkBuilds("branch:(name:branchName)");
    checkBuilds("branch:(name:master)", build30, build20, build10);
    checkBuilds("branch:(name:master,default:true)", build20, build10);
    checkBuilds("branch:(branched:true)", build40, build30, build20, build10);
    checkBuilds("branch:(branched:false)", build5);
    checkBuilds("branch:(name:master,branched:true)", build30, build20, build10);

    checkBuilds(btLocator + ",branch:<default>", build20, build10);
    checkBuilds(btLocator + ",branch:(default:true)", build20, build10, build5);
    checkBuilds(btLocator + ",branch:(default:any)", build40, build30, build20, build10, build5);
    checkBuilds(btLocator + ",branch:<any>", build40, build30, build20, build10, build5);
    checkBuilds(btLocator + ",branch:(default:false)", build40, build30);
    checkBuilds(btLocator + ",branch:(b1)", build40);
    checkBuilds(btLocator + ",branch:(name:b1)", build40);
    checkBuilds(btLocator + ",branch:(name:branchName)");
    checkBuilds(btLocator + ",branch:(name:master)", build30, build20, build10);
    checkBuilds(btLocator + ",branch:(name:master,default:true)", build20, build10);
    checkBuilds(btLocator + ",branch:(branched:true)", build40, build30, build20, build10);
    checkBuilds(btLocator + ",branch:(branched:false)", build5);
    checkBuilds(btLocator + ",branch:(name:master,branched:true)", build30, build20, build10);

    checkExceptionOnBuildSearch(LocatorProcessException.class, "branch:(::)"); //invalid branch locator
    checkExceptionOnBuildSearch(LocatorProcessException.class, "branch:(name:branchName,aaa:bbb)");  //unused/unknown dimension
    checkExceptionOnBuildSearch(LocatorProcessException.class, "branch:(aaa:bbb)");
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
    checkBuilds("pinned:any", build60, build50, build40, build30, build25, build20, build10); //fix: failed to start

    checkBuilds("tag:a,buildType:(id:" + myBuildType.getExternalId() + ")", build30, build20);
    checkBuilds("tag:a", build40, build30, build25, build20);
    checkBuilds("tag:aa", build50);
    checkBuilds("tag:(a:b)", build40);
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "tag:a,tag:b");

    checkBuilds("tags:a,buildType:(id:" + myBuildType.getExternalId() + ")", build30, build20);
    checkBuilds("tags:a", build40, build30, build25, build20);
    checkBuilds("tags:aa", build50);
    checkBuilds("tags:(a,b)", build40, build30); //???
    checkBuilds("tags:(a:b)", build40);
    checkBuilds("tags:(b,a:b)", build40);
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "tags:a,tags:b"); //"documenting" existing exception types
    checkExceptionOnBuildsSearch(BadRequestException.class, "tag:a,tags:b"); //"documenting" existing exception types

    checkBuilds("tag:(format:extended,present:any,regexp:aaa)", build60, build50, build40, build30, build25, build20, build10);
    checkBuilds("tag:(format:extended,present:true)", build50, build40, build30, build25, build20);
    checkBuilds("tag:(format:extended,present:false)", build60, build10);
    checkBuilds("tag:(format:extended,present:true,regexp:a.)", build50);
//fix    checkBuilds("tag:(present:true,regexp:a.,format:extended)", build50);
    checkBuilds("tag:(format:extended,present:false,regexp:a.)", build60, build40, build30, build25, build20, build10);
//fix    checkExceptionOnBuildsSearch(BadRequestException.class, "tag:(format:notExtended,present:true)");
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
    checkBuilds("pinned:any", b5_failedToStart, b4, b1); //b5_failedToStart should not be here

    checkBuilds("canceled:true", b3canceledFailed, b2canceled);
    checkBuilds("canceled:true,status:UNKNOWN", b3canceledFailed, b2canceled);
    checkBuilds("canceled:false", b5_failedToStart, b4, b1); //b5_failedToStart should not be here
    checkBuilds("canceled:any,status:FAILURE", b5_failedToStart, b4);
    checkBuilds("canceled:any,status:SUCCESS", b1);
    //due status processing?
    //checkBuilds("canceled:true,status:SUCCESS", b2canceled);
    //checkBuilds("buildType:(id:" + myBuildType.getExternalId() + "),canceled:any,status:FAILED", b3canceledFailed, b4);

    checkBuild("canceled:true", b3canceledFailed);
    checkBuild("canceled:true,status:UNKNOWN", b3canceledFailed);
    checkBuild("canceled:false", b5_failedToStart);
    checkBuild("canceled:any,status:FAILURE", b5_failedToStart);
    checkBuild("canceled:any,status:SUCCESS", b1);
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
    checkBuilds("pinned:any", b90FailedToStart, b70, b10);

    checkBuilds("personal:true", b100personalFailedToStart, b80personal, b20personal);
//    checkBuilds("personal:true,status:UNKNOWN", b60personalCanceledFailed, b40personalCanceled);
    checkBuilds("personal:true,canceled:true", b60personalCanceledFailed, b40personalCanceled);
    checkBuilds("personal:false", b90FailedToStart, b70, b10); //b90FailedToStart should not be here
    checkBuilds("personal:any,status:FAILURE", b100personalFailedToStart, b90FailedToStart, b80personal, b70);
    checkBuilds("personal:any,status:SUCCESS", b20personal, b10);
    checkBuilds("personal:true,status:SUCCESS", b20personal);
//    checkBuilds("buildType:(id:" + myBuildType.getExternalId() + "),personal:any,status:FAILURE", b100personalFailedToStart, b90FailedToStart);
    checkNoBuildsFound("user:(id:" + user1.getId() + ")");
    checkNoBuildsFound("user:(id:" + user1.getId() + "),personal:true");
    checkNoBuildsFound("user:(id:" + user1.getId() + "),personal:any");

    checkBuild("personal:true", b100personalFailedToStart);
    checkBuild("personal:false", b90FailedToStart);
    checkBuild("personal:any,status:FAILURE", b100personalFailedToStart);
    checkBuild("personal:any,status:SUCCESS", b20personal);
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

    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + ")", build70, build60, build40, build30, build20);
//    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),state:any", build90, build80, build70, build60, build40, build30, build20);
//    checkBuilds("sinceBuild:(id:" + build25DeletedId + ")", build70, build60, build40,build30);

    checkBuilds("untilBuild:(id:" + build60.getBuildId() + ")", build60, build40, build30, build20, build10);
//    checkBuilds("untilBuild:(id:" + build50DeletedId + "),state:any", build40, build30, build20, build10);

    checkBuilds("sinceDate:" + fDate(build20.getStartDate()) + ")", build70, build60, build40, build30, build20);
    checkBuilds("sinceDate:" + fDate(afterBuild30) + ")", build70, build60, build40);
    checkBuilds("untilDate:" + fDate(build60.getStartDate()) + ")", build40, build30, build20, build10);
    checkBuilds("untilDate:" + fDate(afterBuild30) + ")", build30, build20, build10);

//    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),sinceDate:" + fDate(build10.getStartDate()), build70, build60, build40, build30, build20);
//    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),sinceDate:" + fDate(afterBuild30), build70, build60, build40);
//    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + fDate(build60.getStartDate()), build60, build40, build30, build20, build10);
//    checkBuilds("untilBuild:(id:" + build60.getBuildId() + "),untilDate:" + fDate(afterBuild30), build10, build20, build30);

    checkBuilds("sinceBuild:(id:" + build20.getBuildId() + "),untilBuild:" + build60.getBuildId(), build60, build40, build30);
    checkBuilds("sinceBuild:(id:" + build20.getBuildId() + "),untilDate:" + fDate(afterBuild30), build30);
    checkBuilds("sinceDate:(" + fDate(afterBuild10) + "),untilDate:" + fDate(afterBuild30), build30, build20);
  }

  @Test
  public void testBuildsOrder() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final SVcsRootImpl vcsRoot = myFixture.addVcsRoot("mock", "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(vcsRoot);
    assert root1 != null;

    final SFinishedBuild build10 = build().in(buildConf).finish();

    myFixture.addModification(modification().in(root1).version("1"));
    final SVcsModification change20 = myFixture.addModification(modification().in(root1).version("2"));
    myFixture.addModification(modification().in(root1).version("3"));
    assertEquals(3, buildConf.getPendingChanges().size());

    final SFinishedBuild build20 = build().in(buildConf).finish();
    final SFinishedBuild build30 = build().in(buildConf).onModifications(change20).finish();


    checkBuilds(null, build30, build20, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() +")", build30, build20, build10);
    checkBuilds("sinceBuild:(id:" + build20.getBuildId() +")", build30);
  }
}
