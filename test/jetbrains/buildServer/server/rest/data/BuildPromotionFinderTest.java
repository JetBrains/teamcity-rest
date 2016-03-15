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

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.impl.*;
import jetbrains.buildServer.serverSide.impl.projects.ProjectImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2015
 */
public class BuildPromotionFinderTest extends BaseFinderTest<BuildPromotion> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setFinder(myBuildPromotionFinder);
  }


  @Test
  public void testBasic2() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();
    final RunningBuildEx runningBuild = build().in(buildConf).run();
    final SQueuedBuild queuedBuild = build().in(buildConf).addToQueue();

    checkBuilds("id:" + runningBuild.getBuildId(), runningBuild.getBuildPromotion());
    checkBuilds("id:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("id:" + runningBuild.getBuildId() + ",running:true", runningBuild.getBuildPromotion());
    checkBuilds("id:" + runningBuild.getBuildId() + ",running:false");
  }

  @Test
  public void testSateFiltering() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildConf).finish();
    final SFinishedBuild build2 = build().in(buildConf).failed().finish();
    final RunningBuildEx runningBuild = build().in(buildConf).run();
    final SQueuedBuild queuedBuild = build().in(buildConf).addToQueue();

    checkBuilds(null, build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("running:true", runningBuild.getBuildPromotion());
    checkBuilds("running:false", build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("running:any", runningBuild.getBuildPromotion(), build2.getBuildPromotion(), build1.getBuildPromotion());

    checkBuilds("state:any", queuedBuild.getBuildPromotion(), runningBuild.getBuildPromotion(), build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("state:queued", queuedBuild.getBuildPromotion());
    checkBuilds("state:running", runningBuild.getBuildPromotion());
    checkBuilds("state:finished", build2.getBuildPromotion(), build1.getBuildPromotion());

    checkBuilds("state:(queued:true,running:true,finished:true)", queuedBuild.getBuildPromotion(), runningBuild.getBuildPromotion(), build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("state:(queued:true)", queuedBuild.getBuildPromotion());
    checkBuilds("state:(queued:true,running:false)", queuedBuild.getBuildPromotion());
    checkBuilds("state:(running:true)", runningBuild.getBuildPromotion());
    checkBuilds("state:(finished:true)",build2.getBuildPromotion(), build1.getBuildPromotion());
    checkBuilds("state:(queued:true,running:true,finished:false)", queuedBuild.getBuildPromotion(), runningBuild.getBuildPromotion());
  }

  @Test
  public void testSnapshotDependencies() throws Exception {
    final BuildTypeImpl buildConf0 = registerBuildType("buildConf0", "project");
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    final BuildTypeImpl buildConf3 = registerBuildType("buildConf3", "project");
    final BuildTypeImpl buildConf4 = registerBuildType("buildConf4", "project");
    addDependency(buildConf4, buildConf3);
    addDependency(buildConf3, buildConf2);
    addDependency(buildConf2, buildConf1);
    addDependency(buildConf1, buildConf0);
    final BuildPromotion build4 = build().in(buildConf4).addToQueue().getBuildPromotion();
    final BuildPromotion build3 = build4.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build2 = build3.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build1 = build2.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build0 = build1.getDependencies().iterator().next().getDependOn();
    finishBuild(BuildBuilder.run(build1.getQueuedBuild(), myFixture), false);
    BuildBuilder.run(build2.getQueuedBuild(), myFixture);

    final String baseToLocatorStart = "snapshotDependency:(to:(id:" + build4.getId() + ")";
    checkBuilds(baseToLocatorStart + ")", build1, build0); //by default only finished builds
    checkBuilds(baseToLocatorStart + "),state:any", build3, build2, build1, build0);
    checkBuilds(baseToLocatorStart + "),state:running", build2);
    checkBuilds(baseToLocatorStart + "),state:queued", build3);
    checkBuilds(baseToLocatorStart + "),state:(queued:true)", build3);
    checkBuilds(baseToLocatorStart + "),state:(running:true,queued:true)", build3, build2);

    checkBuilds(baseToLocatorStart + ",includeInitial:true)", build1, build0); //by default only finished builds
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:any", build4, build3, build2, build1, build0);
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:running", build2);
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:queued", build4, build3);
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:(queued:true)", build4, build3);
    checkBuilds(baseToLocatorStart + ",includeInitial:true),state:(running:true,queued:true)", build4, build3, build2);

    checkBuilds(baseToLocatorStart + ",includeInitial:false)", build1, build0);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:any", build3, build2, build1, build0);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:running", build2);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:queued", build3);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:(queued:true)", build3);
    checkBuilds(baseToLocatorStart + ",includeInitial:false),state:(running:true,queued:true)", build3, build2);

    checkBuilds(baseToLocatorStart + ",recursive:true)", build1, build0);
    checkBuilds(baseToLocatorStart + ",recursive:true),state:any", build3, build2, build1, build0);
    checkBuilds(baseToLocatorStart + ",recursive:false)");
    checkBuilds(baseToLocatorStart + ",recursive:false),state:any", build3);

    checkBuilds("snapshotDependency:(to:(id:" + build3.getId() + "))", build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build3.getId() + ")),defaultFilter:false", build2, build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build3.getId() + ")),state:any", build2, build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build3.getId() + "),includeInitial:true),state:any", build3, build2, build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build2.getId() + "))", build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build2.getId() + "),recursive:false)", build1);
    checkBuilds("snapshotDependency:(to:(id:" + build2.getId() + ")),state:any", build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build1.getId() + "))", build0);
    checkBuilds("snapshotDependency:(to:(id:" + build1.getId() + ")),state:any", build0);
    checkBuilds("snapshotDependency:(to:(id:" + build1.getId() + "),includeInitial:true),state:any", build1, build0);
    checkBuilds("snapshotDependency:(to:(id:" + build0.getId() + "),includeInitial:true)", build0);
    checkBuilds("snapshotDependency:(to:(id:" + build0.getId() + ")),state:any");

    final String baseFromLocatorStart = "snapshotDependency:(from:(id:" + build0.getId() + ")";
    checkBuilds(baseFromLocatorStart + ")", build1); //by default only finished builds
    checkBuilds(baseFromLocatorStart + "),state:any", build4, build3, build2, build1);
    checkBuilds(baseFromLocatorStart + ",includeInitial:true),state:any", build4, build3, build2, build1, build0);
    checkBuilds(baseFromLocatorStart + ",includeInitial:true,recursive:false),state:any", build1, build0);
    checkBuilds(baseFromLocatorStart + "),state:running", build2);
    checkBuilds(baseFromLocatorStart + "),state:queued", build4, build3);
    checkBuilds(baseFromLocatorStart + "),state:(running:true,queued:true)", build4, build3, build2);
    checkBuilds(baseFromLocatorStart + ",includeInitial:true),state:(running:true,queued:true)", build4, build3, build2);

    checkBuilds("snapshotDependency:(from:(id:" + build1.getId() + "),to:(id:" + build3.getId() + "),includeInitial:true),state:any", build3, build2, build1);
    checkBuilds("snapshotDependency:(from:(id:" + build1.getId() + "),to:(id:" + build3.getId() + ")),state:any", build2);

    checkBuilds("snapshotDependency:(to:(id:" + (build4.getId() + 10) + ")),state:any");
  }

  @Test
  public void testSnapshotDependenciesAndBranches() throws Exception {
    final BuildTypeImpl buildConf0 = registerBuildType("buildConf0", "project");
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    addDependency(buildConf2, buildConf1);
    addDependency(buildConf1, buildConf0);

    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    final SVcsRoot vcsRoot = buildConf0.getProject().createVcsRoot("vcs", "extId", "name");

    buildConf0.addVcsRoot(vcsRoot);
    buildConf1.addVcsRoot(vcsRoot);
    buildConf2.addVcsRoot(vcsRoot);

    final VcsRootInstance vcsRootInstance = buildConf0.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState("master", map("master", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance, "+:*");

    final BuildPromotion build20 = build().in(buildConf2).finish().getBuildPromotion();
    final BuildPromotion build10 = build20.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build00 = build10.getDependencies().iterator().next().getDependOn();

    final BuildPromotion build2_20 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build2_10 = build2_20.getDependencies().iterator().next().getDependOn();
    final BuildPromotion build2_00 = build2_10.getDependencies().iterator().next().getDependOn();

    checkBuilds("snapshotDependency:(to:(id:" + build10.getId() + "))", build00);
    checkBuilds("snapshotDependency:(to:(id:" + build2_10.getId() + "))", build2_00);

    final BuildPromotion build3_20 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();
    checkBuilds("snapshotDependency:(from:(id:" + build2_00.getId() + ")),equivalent:(id:" + build3_20.getId() + ")", build2_20);
  }

  @Test
  public void testMultipleBuildTypes() throws Exception {
    final BuildTypeImpl buildConf0 = registerBuildType("buildConf0", "project");
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project1");

    final BuildPromotion build05 = build().in(buildConf0).number("10").finish().getBuildPromotion();
    final BuildPromotion build10 = build().in(buildConf1).number("10").finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf0).finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf0).number("10").finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf2).finish().getBuildPromotion();

    check(null, build40, build30, build20, build10, build05);
    check("buildType:(id:" + buildConf0.getExternalId() + ")", build30, build20, build05);
    check("buildType:(project:(name:" + "project" + "))", build30, build20, build10, build05);
    check("buildType:(project:(name:" + "project" + ")),number:10", build30, build10, build05);
    check("buildType:(item:(id:" + buildConf1.getExternalId() + "),item:(id:(" + buildConf0.getExternalId() + ")))", build30, build20, build10, build05);
  }

  @Test
  public void testQueuedBuildFinding() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SQueuedBuild queuedBuild = build().in(buildConf).addToQueue();

    checkBuilds(String.valueOf(queuedBuild.getItemId()), queuedBuild.getBuildPromotion());
    checkBuilds("id:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),id:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("taskId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("promotionId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),promotionId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
  }

  @Test
  public void testReplacementBuildFindingByMergedPromotion() throws Exception { //see also jetbrains.buildServer.server.rest.data.BuildFinderTest.testQueuedBuildByMergedPromotion()
    final BuildTypeImpl buildConf = registerBuildType("buildConf", "project");
    myFixture.registerVcsSupport("vcsSuport");
    buildConf.addVcsRoot(buildConf.getProject().createVcsRoot("vcsSuport", "extId", "name"));
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    buildConf2.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(buildConf));

    final SQueuedBuild queuedBuild1 = build().in(buildConf).addToQueue();
    final long id1 = queuedBuild1.getBuildPromotion().getId();

    final SQueuedBuild queuedBuild2 = build().in(buildConf2).addToQueue();
    final BuildPromotion queuedDependency = queuedBuild2.getBuildPromotion().getDependencies().iterator().next().getDependOn();
    final long id2 = queuedDependency.getId();

    assertEquals(3, myFixture.getBuildQueue().getNumberOfItems());
    assertNotSame(id1, id2);

    assertTrue(((BuildPromotionEx)queuedBuild1.getBuildPromotion()).getTopDependencyGraph().collectChangesForGraph(new CancelableTaskHolder()));
    assertTrue(((BuildPromotionEx)queuedBuild2.getBuildPromotion()).getTopDependencyGraph().collectChangesForGraph(new CancelableTaskHolder()));

    myFixture.getBuildQueue().setMergeBuildsInQueue(true);
    myFixture.getBuildQueue().mergeBuilds();

    assertEquals(2, myFixture.getBuildQueue().getNumberOfItems());


    checkBuilds("id:" + id2, queuedDependency);
    checkBuilds("id:" + id1, queuedDependency);
    checkBuilds(String.valueOf(id1), queuedDependency);
    checkBuilds("taskId:" + id1, queuedDependency);
  }

  @Test
  public void testEquivalentBuildFinding() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SFinishedBuild build1 = build().in(buildConf).finish();
    final SFinishedBuild build2 = build().in(buildConf).withBranch("branch1").finish();
    final SFinishedBuild build3 = build().in(buildConf).withBranch("branch1").finish();
    final SFinishedBuild build4 = build().in(buildConf).withBranch("branch2").finish();
    final SFinishedBuild build5 = build().in(buildConf).withBranch("branch1").finish();

    checkBuilds("equivalent:(id:" + build0.getBuildId() + ")", build1.getBuildPromotion());
    checkBuilds("equivalent:(id:" + build2.getBuildId() + ")", build5.getBuildPromotion(), build3.getBuildPromotion(), build1.getBuildPromotion(), build0.getBuildPromotion());
    checkBuilds("equivalent:(id:" + build4.getBuildId() + ")", build1.getBuildPromotion(), build0.getBuildPromotion());
  }


  @Test
  public void testFailedToStart() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final BuildPromotion build10 = build().in(buildConf).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf).failedToStart().finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf).failed().finish().getBuildPromotion();
    BuildPromotion build40 = build().in(buildConf).cancel(getOrCreateUser("user")).getBuildPromotion();
    BuildPromotion build50 = build().in(buildConf).failedToStart().cancel(getOrCreateUser("user")).getBuildPromotion();

    checkBuilds(null, build30, build10);
    checkBuilds("status:SUCCESS", build10);
    checkBuilds("status:FAILURE", build30);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + ")", build30, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),state:any", build30, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),canceled:any,state:any", build50, build40, build30, build10); //TeamCity API: build50 should not be here

    checkBuilds("failedToStart:true", build20);
    checkBuilds("failedToStart:any", build30, build20, build10);
    checkBuilds("failedToStart:any,canceled:true", build50, build40);
//    checkBuilds("failedToStart:true,canceled:true", build50);   //TeamCity API: failed to start builds are reported to be not failed to start if canceled
    checkBuilds("status:SUCCESS,failedToStart:any", build10);
    checkBuilds("status:FAILURE,failedToStart:any", build30, build20);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),failedToStart:any", build30, build20, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),failedToStart:true", build20);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),state:any,failedToStart:any", build30, build20, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),canceled:any,state:any,failedToStart:any", build50, build40, build30, build20, build10);
    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),canceled:false,failedToStart:any", build30, build20, build10);
  }


  @Test
  @TestFor(issues = {"TW-21926"})
  public void testMultipleBuildsWithIdLocator() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final BuildPromotion build2 = build().in(buildConf).failed().finish().getBuildPromotion();

    final RunningBuildEx running3 = startBuild(myBuildType);
    running3.stop(createUser("uuser1"), "cancel comment");
    SBuild b3canceled = finishBuild(running3, true);

    final BuildPromotion build4 = build().in(buildConf2).failedToStart().finish().getBuildPromotion();
    final BuildPromotion build5 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion();

    final BuildPromotion runningBuild5 = build().in(buildConf).run().getBuildPromotion();
    final BuildPromotion queuedBuild = build().in(buildConf).addToQueue().getBuildPromotion();

    checkBuilds("taskId:" + queuedBuild.getId(), queuedBuild);
    checkBuilds("promotionId:" + queuedBuild.getId(), queuedBuild);
    checkBuilds("id:" + runningBuild5.getId(), runningBuild5);
    checkBuilds("id:" + build2.getId(), build2);
    checkBuilds("id:" + b3canceled.getBuildId(), b3canceled.getBuildPromotion());
    checkBuilds("id:" + build4.getId(), build4);
    checkBuilds("id:" + build5.getId(), build5);
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

    checkBuilds("defaultFilter:false", 8, getBuildPromotions(build90, build80, build70, build60, build40, build30, build20, build10));
    checkBuilds("state:any", 7, getBuildPromotions(build90, build80, build70, build60, build40, build20, build10));
    checkBuilds("defaultFilter:false,buildType:(id:" + buildConf1.getExternalId() + ")", 5, getBuildPromotions(build90, build80, build70, build40, build10));

    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),state:any", 7, getBuildPromotions(build90, build80, build70, build60, build40, build20));
    checkBuilds("sinceBuild:(id:" + build10.getBuildId() + "),state:any,failedToStart:any", 8, getBuildPromotions(build90, build80, build70, build60, build40, build30, build20));
    checkBuilds("sinceBuild:(id:" + build30.getBuildId() + "),state:any", 6, getBuildPromotions(build90, build80, build70, build60, build40));
    checkBuilds("sinceBuild:(id:" + build30.getBuildId() + "),state:any,failedToStart:any", 6, getBuildPromotions(build90, build80, build70, build60, build40));
    checkBuilds("sinceBuild:(id:" + build60.getBuildId() + "),state:any", 4, getBuildPromotions(build90, build80, build70));

    checkBuilds("sinceDate:(" + Util.formatTime(build60.getStartDate()) + "),state:any", 5, getBuildPromotions(build80, build70, build60));
    checkBuilds("sinceBuild:(id:" + build30.getBuildId() + "),sinceDate:(" + Util.formatTime(build60.getStartDate()) + "),state:any", 5, getBuildPromotions(build80, build70, build60));

    checkExceptionOnBuildsSearch(BadRequestException.class, "sinceBuild:(xxx)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "sinceBuild:(buildType:(" + buildConf1.getId() + "),status:FAILURE)");
  }

  @Test
  public void testTimes() {
    final MockTimeService time = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(time);
    initFinders(); //recreate finders to let time service sink in

    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SQueuedBuild queuedBuild10 = build().in(buildConf1).addToQueue();
    final SQueuedBuild queuedBuild20 = build().in(buildConf2).addToQueue();
    time.jumpTo(10);
    final SFinishedBuild build10 = myFixture.finishBuild(BuildBuilder.run(queuedBuild10, myFixture), false);
    time.jumpTo(10);
    final Date afterBuild10 = time.getNow();
    time.jumpTo(10);
    time.jumpTo(1000L - time.now() % 1000 + 100); //ensuring next jump will be within the same second
    final SFinishedBuild build20 = myFixture.finishBuild(BuildBuilder.run(queuedBuild20, myFixture), false);
    time.jumpTo(100L);
    final SFinishedBuild build23 = build().in(buildConf2).finish();
    time.jumpTo(10);

    final SFinishedBuild build25Deleted = build().in(buildConf2).failed().finish();
    final long build25DeletedId = build25Deleted.getBuildId();
    myFixture.getSingletonService(BuildHistory.class).removeEntry(build25Deleted);

    final BuildBuilder queuedBuild30 = build().in(buildConf2);
    time.jumpTo(10);
    final SFinishedBuild build30 = queuedBuild30.failedToStart().finish();
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
    final SQueuedBuild queuedBuild110 = build().in(buildConf1).addToQueue();
    time.jumpTo(10);
    final SQueuedBuild queuedBuild120 = build().in(buildConf1).parameter("a", "x").addToQueue(); //preventing build from merging in the queue
    time.jumpTo(10);
    final SQueuedBuild queuedBuild130 = build().in(buildConf1).parameter("a", "y").addToQueue(); //preventing build from merging in the queue
    //noinspection ConstantConditions
    myFixture.getBuildQueue().moveTop(queuedBuild130.getItemId());

    checkBuilds("finishDate:(build:(id:" + build10.getBuildId() + "),condition:after),state:any", getBuildPromotions(build70, build60, build40, build23, build20));
    checkBuilds("finishDate:(build:(id:" + build10.getBuildId() + "),condition:after),state:any,failedToStart:any", getBuildPromotions(build70, build60, build40, build30, build23, build20));
    checkBuilds("finishDate:(build:(id:" + build20.getBuildId() + "),condition:after),state:any", getBuildPromotions(build70, build60, build40, build23));
    checkBuilds("finishDate:(build:(id:" + build20.getBuildId() + "),condition:after,includeInitial:true),state:any", getBuildPromotions(build70, build60, build40, build23, build20));
    checkBuilds("finishDate:(build:(id:" + build20.getBuildId() + "),condition:before),state:any", getBuildPromotions(build10));
    checkBuilds("finishDate:(build:(id:" + build20.getBuildId() + "),condition:equals),state:any", getBuildPromotions(build20));
    checkBuilds("finishDate:(build:(id:" + build20.getBuildId() + ")),state:any", getBuildPromotions(build70, build60, build40, build23));
    checkExceptionOnBuildsSearch(BadRequestException.class, "finishDate:(build:(id:" + queuedBuild110.getItemId() + "))");

    checkBuilds("finishDate:(date:" + Util.formatTime(build10.getFinishDate()) + ",condition:after),state:any", getBuildPromotions(build70, build60, build40, build23, build20));
    checkBuilds("finishDate:(date:" + Util.formatTime(build10.getFinishDate()) + ",condition:after),state:any,failedToStart:any", getBuildPromotions(build70, build60, build40, build30, build23, build20));
    checkBuilds("finishDate:(date:" + Util.formatTime(build20.getFinishDate()) + ",condition:after),state:any", getBuildPromotions(build70, build60, build40));
    checkBuilds("finishDate:(date:" + Util.formatTime(build20.getFinishDate()) + ",condition:after,includeInitial:true),state:any", getBuildPromotions(build70, build60, build40, build23, build20));
    checkBuilds("finishDate:(date:" + Util.formatTime(build20.getFinishDate()) + ",condition:before),state:any", getBuildPromotions(build10));
    checkBuilds("finishDate:(date:" + Util.formatTime(build20.getFinishDate()) + ",condition:equals),state:any", getBuildPromotions(build23, build20));
    checkBuilds("finishDate:(date:" + Util.formatTime(build20.getFinishDate()) + "),state:any", getBuildPromotions(build70, build60, build40));

    checkBuilds("finishDate:(date:" + (new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSSZ", Locale.ENGLISH)).format(build20.getFinishDate()) + "),state:any",
                getBuildPromotions(build70, build60, build40, build23));
    checkBuilds("finishDate:(date:" + (new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSSX", Locale.ENGLISH)).format(build20.getFinishDate()) + "),state:any",
                getBuildPromotions(build70, build60, build40, build23));
    checkBuilds("finishDate:(date:" + (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)).format(build20.getFinishDate()) + "),state:any",
                getBuildPromotions(build70, build60, build40, build23, build20)); //new time parsing uses ms
    checkBuilds("finishDate:(date:" + (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.ENGLISH)).format(build20.getFinishDate()) + "),state:any",
                getBuildPromotions(build70, build60, build40, build23, build20));


    checkBuilds("startDate:(build:(id:" + build10.getBuildId() + "),condition:after)", getBuildPromotions(build70, build60, build40, build23, build20));
    checkBuilds("startDate:(build:(id:" + build10.getBuildId() + "),condition:after),state:any", getBuildPromotions(build80, build70, build60, build40, build23, build20));

    checkBuilds("startDate:(build:(id:" + build10.getBuildId() + "))", getBuildPromotions(build70, build60, build40, build23, build20));
    checkBuilds("startDate:(date:" + Util.formatTime(build10.getStartDate()) + ")", getBuildPromotions(build70, build60, build40, build23, build20));
    checkBuilds("startDate:(" + Util.formatTime(build10.getStartDate()) + ")", getBuildPromotions(build70, build60, build40, build23, build20));

    checkBuilds("queuedDate:(build:(id:" + build10.getBuildId() + "),condition:after)", getBuildPromotions(build70, build60, build40, build23, build20));
    checkBuilds("queuedDate:(build:(id:" + build10.getBuildId() + "),condition:after),state:any",
                getBuildPromotions(queuedBuild130, queuedBuild110, queuedBuild120, build80, build70, build60, build40, build23, build20));

    checkBuilds("startDate:(-10m),state:any", getBuildPromotions(build80, build70, build60, build40, build23, build20, build10));

    time.jumpTo(afterBuild30.getTime() + 10 * 60 * 1000 - time.getNow().getTime()); // set time to afterBuild30 + 10 minutes
    checkBuilds("startDate:(-10m),state:any", getBuildPromotions(build80, build70, build60, build40));
    time.jumpTo(9);
    checkBuilds("startDate:(-10m),state:any", getBuildPromotions(build80, build70, build60, build40));
    time.jumpTo(1);
    checkBuilds("startDate:(-10m),state:any", getBuildPromotions(build80, build70, build60));
    time.jumpTo(60*10);
    checkBuilds("startDate:(-10m),state:any");

    checkBuilds("startDate:(build:(id:" + build60.getBuildId() + "),shift:-11s),state:any", getBuildPromotions(build80, build70, build60, build40));
    checkBuilds("startDate:(build:(id:" + build60.getBuildId() + "),shift:-10s),state:any", getBuildPromotions(build80, build70, build60));
    checkBuilds("startDate:(build:(id:" + build60.getBuildId() + "),shift:+9s),state:any", getBuildPromotions(build80, build70));
    checkBuilds("startDate:(build:(id:" + build60.getBuildId() + "),shift:+10s),state:any", getBuildPromotions(build80));
    checkBuilds("startDate:(condition:after,build:(id:" + build60.getBuildId() + ")" +
                ",shift:-11s),startDate:(condition:before,build:(id:" + build60.getBuildId() + "),shift:+11s),state:any",
                getBuildPromotions(build70, build60, build40));
    checkBuilds("startDate:(condition:after,build:(id:" + build60.getBuildId() + ")" +
                ",shift:-9s),startDate:(condition:before,build:(id:" + build60.getBuildId() + "),shift:+9s),state:any",
                getBuildPromotions(build60));
    checkBuilds("startDate:(condition:after,build:(id:" + build60.getBuildId() + ")" +
                ",shift:-10s),startDate:(condition:before,build:(id:" + build60.getBuildId() + "),shift:+10s),state:any",
                getBuildPromotions(build60));

    checkExceptionOnBuildsSearch(BadRequestException.class, "finishDate:(xxx)");
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "finishDate:(time:20150101T000000+0000,build:(id:3))");
    checkExceptionOnBuildsSearch(BadRequestException.class, "finishDate:(time:20150101T000000+0000,condition:xxx)");
  }

  @Test
  public void testTimesISO() {
    final MockTimeService time = new MockTimeService(new DateTime(2016, 2, 16, 16, 47, 43, 0, DateTimeZone.forOffsetHours(1)).getMillis());
    myServer.setTimeService(time);
    initFinders(); //recreate finders to let time service sink in

    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final SFinishedBuild build10 = build().in(buildConf1).finish();  //20160216T164743.000+0100
    time.jumpTo(7L * 24 * 60 * 60 * 1000); // +1 week
    time.jumpTo(10);
    final SFinishedBuild build15 = build().in(buildConf1).finish();  //20160223T164753.000+0100
    time.jumpTo(24 * 60 * 60);  // +1 day
    final SFinishedBuild build20 = build().in(buildConf1).finish();  //20160224T164753.000+0100
    time.jumpTo(10050L);
    final SFinishedBuild build30 = build().in(buildConf1).finish();  //20160224T164803.050+0100
    time.jumpTo(100L);
    final SFinishedBuild build35 = build().in(buildConf1).finish();  //20160224T164803.150+0100
    time.jumpTo(24 * 60 * 60);  // +1 day
    time.jumpTo(10);
    final SFinishedBuild build40 = build().in(buildConf1).finish(); //20160225T164813.050+0100

    checkBuilds(null, getBuildPromotions(build40, build35, build30, build20, build15, build10));
    checkBuilds("finishDate:(date:20160224T164803.0+0100)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T164803.050+0100)", getBuildPromotions(build40, build35));
    checkBuilds("finishDate:(date:20160224T164803.049+0100)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T154803.049Z)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T154803Z)", getBuildPromotions(build40)); //this uses compatibility approach comparing the build time to be strongly > by seconds
    checkBuilds("finishDate:(date:2016-02-24T16:48:03.049+0100)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:2016-02-24T16:48:03+0100)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:2016-02-24T16:48:03.049Z)", getBuildPromotions(build40));
    checkBuilds("finishDate:(date:2016-02-24T15:48:03.049Z)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:2016-02-24T15:48:03Z)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:20160224T164803.0+01)", getBuildPromotions(build40, build35, build30));
    checkBuilds("finishDate:(date:2016-02-24)", getBuildPromotions(build40, build35, build30, build20));
    checkBuilds("finishDate:(date:2016-2-23)", getBuildPromotions(build40, build35, build30, build20, build15));
    checkBuilds("finishDate:(date:2016-02-23)", getBuildPromotions(build40, build35, build30, build20, build15));
    checkBuilds("finishDate:(date:2016-W8)", getBuildPromotions(build40, build35, build30, build20, build15));
    checkBuilds("finishDate:(shift:-1d)", getBuildPromotions(build40));
    checkBuilds("finishDate:(shift:-1d10s1ms)", getBuildPromotions(build40, build35));
    checkBuilds("finishDate:(date:00:00)", getBuildPromotions(build40));
    checkBuilds("finishDate:(date:00:00,shift:-1d)", getBuildPromotions(build40, build35, build30, build20));
    checkBuilds("finishDate:(date:00:00,shift:+1m)", getBuildPromotions(build40));
    checkBuilds("finishDate:(date:0,shift:-48h)", getBuildPromotions(build40, build35, build30, build20, build15));

    checkExceptionOnBuildsSearch(BadRequestException.class, "finishDate:(date:20160224T164803+0100xxx)");
  }

  @Test
  public void testTimesNoTimeZone() {
    //using year 2001 as with 2016 there are not actual tz issues (e.g. MSK with JDK 1.8_05)
    final MockTimeService time = new MockTimeService(new DateTime(2001, 2, 16, 16, 47, 43, 0).getMillis());

    myServer.setTimeService(time);
    initFinders(); //recreate finders to let time service sink in

    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final SFinishedBuild build10 = build().in(buildConf1).finish();
    time.jumpTo(120);
    final SFinishedBuild build15 = build().in(buildConf1).finish();
    time.jumpTo(120);
    final SFinishedBuild build20 = build().in(buildConf1).finish();

    checkBuilds("finishDate:(date:2001-02-16T16:47:44)", getBuildPromotions(build20, build15));
    checkBuilds("finishDate:(date:20010216T164744.0)", getBuildPromotions(build20, build15));
  }

  @Test
  public void testStopProcessing() {
    final MockTimeService time = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(time);

    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild finishedBuild03 = build().in(buildConf1).finish();
    time.jumpTo(10);
    final SFinishedBuild finishedBuild05 = build().in(buildConf1).finish();
    time.jumpTo(10);
    final SQueuedBuild queuedBuild10 = build().in(buildConf1).addToQueue();
    time.jumpTo(10);
    final SQueuedBuild queuedBuild20 = build().in(buildConf1).parameter("a", "x").addToQueue();
    time.jumpTo(10);
    myFixture.getBuildQueue().moveTop(queuedBuild20.getItemId());
    final RunningBuildEx runningBuild20 = BuildBuilder.run(queuedBuild20, myFixture);
    time.jumpTo(10);
    final SQueuedBuild queuedBuild30 = build().in(buildConf1).parameter("a", "y").addToQueue();
    time.jumpTo(10);
    myFixture.getBuildQueue().moveTop(queuedBuild10.getItemId());
    final MockBuildAgent agent2 = myFixture.createEnabledAgent("Ant");// adding one more agent to allow parallel builds
    final RunningBuildEx runningBuild10 = BuildBuilder.run(queuedBuild10, myFixture);
    time.jumpTo(10);
    final SFinishedBuild finishedBuild10 = myFixture.finishBuild(runningBuild10, false);
    time.jumpTo(10);
    final RunningBuildEx runningBuild30 = BuildBuilder.run(queuedBuild30, myFixture);
    agent2.setEnabled(false, null, "");
    time.jumpTo(10);
    final SFinishedBuild finishedBuild30 = myFixture.finishBuild(runningBuild30, false);
    time.jumpTo(10);
    final SFinishedBuild finishedBuild20 = myFixture.finishBuild(runningBuild20, false);
    time.jumpTo(10);
    final SQueuedBuild queuedBuild40 = build().in(buildConf1).parameter("a", "z").addToQueue();
    time.jumpTo(10);
    final SQueuedBuild queuedBuild50 = build().in(buildConf1).parameter("a", "z1").addToQueue();
    time.jumpTo(10);
    myFixture.getBuildQueue().moveTop(queuedBuild50.getItemId());
    final RunningBuildEx runningBuild50 = BuildBuilder.run(queuedBuild50, myFixture);
    time.jumpTo(10);

    checkBuilds("state:any", 7, getBuildPromotions(queuedBuild40, runningBuild50, finishedBuild30, finishedBuild10, finishedBuild20, finishedBuild05, finishedBuild03));
    checkBuilds("startDate:(build:(id:" + finishedBuild10.getBuildId() + "),condition:after),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30));
    checkBuilds("startDate:(build:(id:" + finishedBuild20.getBuildId() + "),condition:after),state:any", 6, getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));
    checkBuilds("startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:after),state:any", 4, getBuildPromotions(runningBuild50));
    checkBuilds("startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:after)", 2, new BuildPromotion[]{});
    checkBuilds("startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:after),state:finished", 2, new BuildPromotion[]{});
    checkBuilds("startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:after),state:queued", 1, new BuildPromotion[]{});
    checkBuilds("startDate:(build:(id:" + finishedBuild10.getBuildId() + "),condition:after)", 3, getBuildPromotions(finishedBuild30));
    checkBuilds("startDate:(build:(id:" + finishedBuild20.getBuildId() + "),condition:after)", 4, getBuildPromotions(finishedBuild30, finishedBuild10));

    checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after),state:any", 5, getBuildPromotions(runningBuild50, finishedBuild30));
    checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after),state:any", 6,
                getBuildPromotions(runningBuild50, finishedBuild30, finishedBuild10));
    checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after),state:any", 4, getBuildPromotions(runningBuild50));
    checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild30.getStartDate()) + ",condition:after)", 2, new BuildPromotion[]{});
    checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild10.getStartDate()) + ",condition:after)", 3, getBuildPromotions(finishedBuild30));
    checkBuilds("startDate:(date:" + Util.formatTime(finishedBuild20.getStartDate()) + ",condition:after)", 4, getBuildPromotions(finishedBuild30, finishedBuild10));

    checkBuilds(
      "startDate:(build:(id:" + finishedBuild20.getBuildId() + "),condition:after),startDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:before),state:any", 6,
      getBuildPromotions(finishedBuild10));
    checkBuilds(
      "startDate:(build:(id:" + finishedBuild05.getBuildId() + "),condition:after),finishDate:(build:(id:" + finishedBuild30.getBuildId() + "),condition:before),state:any", 7,
      getBuildPromotions(finishedBuild10));
  }

  @Test
  public void testLookupLimit() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild finishedBuild03 = build().in(buildConf1).parameter("a", "b").finish();
    final SFinishedBuild finishedBuild05 = build().in(buildConf2).finish();

    for (int i = 0; i < 100; i++) {
      build().in(buildConf1).finish();
    }

    checkBuilds("property:(name:a)", 102, getBuildPromotions(finishedBuild03));
    checkBuilds("property:(name:a),lookupLimit:20", 21, new BuildPromotion[]{});

    setInternalProperty("rest.request.builds.defaultLookupLimit", "30");
    checkBuilds("property:(name:a)", 101, new BuildPromotion[]{}); //lookupLimit should not be less than count which is 100 by defualt
    checkBuilds("property:(name:a),count:30", 31, new BuildPromotion[]{});

    checkBuilds("property:(name:a),lookupLimit:40", 41, new BuildPromotion[]{});

    checkNoBuildFound("property:(name:a)");
  }

  @Test
  public void testSinceWithQueuedBuilds() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildPromotion queuedBuild10 = build().in(buildConf).addToQueue().getBuildPromotion();
    final BuildPromotion queuedBuild20 = build().in(buildConf).parameter("a", "x").addToQueue().getBuildPromotion(); //preventing build from merging in the queue
    final BuildPromotion queuedBuild30 = build().in(buildConf).parameter("a", "y").addToQueue().getBuildPromotion(); //preventing build from merging in the queue
    //noinspection ConstantConditions
    myFixture.getBuildQueue().moveTop(queuedBuild30.getQueuedBuild().getItemId());

    checkBuilds("state:any", queuedBuild30, queuedBuild10, queuedBuild20);
    checkBuilds("state:any,sinceBuild:(id:" + queuedBuild10.getId() +")", queuedBuild30, queuedBuild20);
    checkBuilds("state:any,sinceBuild:(id:" + queuedBuild20.getId() +")", queuedBuild30);
    checkBuilds("state:any,sinceBuild:(id:" + queuedBuild30.getId() +")");
  }

  @Test
  public void testRevision() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "rev_Vcs1_1")));
    changesPolicy.setCurrentState(root2, createVersionState("master", map("master", "xxx")));
    final BuildPromotion build10 = build().in(buildConf).finish().getBuildPromotion();
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "rev_Vcs1_2")));
    final BuildPromotion build20 = build().in(buildConf).finish().getBuildPromotion();
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "xxx")));
    changesPolicy.setCurrentState(root2, createVersionState("master", map("master", "rev_Vcs2_3")));
    final BuildPromotion build30 = build().in(buildConf).finish().getBuildPromotion();
    final MockVcsModification modification = new MockVcsModification(null, "comment", new Date(), "change_v1") {
      @Override
      public String getDisplayVersion() {
        return "change_V1_display";
      }
    }.setRoot(root1);
    vcs.addChange(root1, modification);
    changesPolicy.setCurrentState(root1, createVersionState("master", map("master", "change_v1")));
    final BuildPromotion build40 = build().in(buildConf).onModifications(modification).finish().getBuildPromotion();

    checkBuilds("revision:rev_Vcs1_2", build20);
    checkBuilds("revision:(version:rev_Vcs1_2)", build20);
    checkBuilds("revision:(internalVersion:change_v1)", build40);
    checkBuilds("revision:(version:change_V1_display)", build40);
    checkBuilds("revision:(version:change_V1_display,vcsRoot:(id:" + root1.getParent().getExternalId() + "))", build40);
    checkBuilds("revision:(internalVersion:xxx)", build30, build20, build10);
    checkBuilds("revision:(internalVersion:xxx,vcsRoot:(id:" + root1.getParent().getExternalId() + "))", build30);
  }

  @Test
  public void testDefaultFiltering() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SUser user = createUser("uuser");

    SBuild build1 = build().in(buildConf).finish();
    SBuild build2failed = build().in(buildConf).failed().finish();

    SBuild build5personal = build().in(buildConf).personalForUser(user.getUsername()).finish();

    RunningBuildEx build7running = startBuild(buildConf);
    build7running.stop(user, "cancel comment");
    SBuild build7canceled = finishBuild(build7running, false);

    final RunningBuildEx build8running = startBuild(buildConf);
    build8running.addBuildProblem(createBuildProblem()); //make the build failed
    build8running.stop(user, "cancel comment");
    SBuild build8canceledFailed = finishBuild(build8running, true);

    SBuild build9failedToStart = build().in(buildConf).failedToStart().finish();
    SBuild build11inBranch = build().in(buildConf).withBranch("branch").finish();

    SBuild build13running = startBuild(buildConf);
    SQueuedBuild build14queued = addToQueue(buildConf);

    checkBuilds(null, getBuildPromotions(build2failed, build1));
    checkBuilds("defaultFilter:false", getBuildPromotions(build14queued, build13running, build11inBranch, build9failedToStart, build8canceledFailed, build7canceled, build5personal, build2failed, build1));
    checkBuilds("defaultFilter:true", getBuildPromotions(build2failed, build1));
    checkBuilds("canceled:true", getBuildPromotions(build8canceledFailed, build7canceled));
    checkBuilds("canceled:false", getBuildPromotions(build2failed, build1));
    checkBuilds("canceled:false,defaultFilter:false", getBuildPromotions(build14queued, build13running, build11inBranch, build9failedToStart, build5personal, build2failed, build1));
    checkBuilds("canceled:any", getBuildPromotions(build8canceledFailed, build7canceled, build2failed, build1));
    checkBuilds("personal:true", getBuildPromotions(build5personal));
    checkBuilds("personal:any", getBuildPromotions(build5personal, build2failed, build1));
    checkBuilds("state:any", getBuildPromotions(build14queued, build13running, build2failed, build1));
  }

  @Test
  public void testBuildNumber() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final SUser user = createUser("uuser");

    SBuild build10 = build().in(buildConf).finish();
    SBuild build20 = build().in(buildConf).number("100").finish();
    SBuild build30 = build().in(buildConf).number("100").failed().finish();
    SBuild build40 = build().in(buildConf).number("100").failedToStart().finish();
    SBuild build50 = build().in(buildConf).number("100").personalForUser(user.getUsername()).finish();
    SBuild build60 = build().in(buildConf).number("100").cancel(user);
    SBuild build70 = build().in(buildConf).finish();
    SBuild build80 = build().in(buildConf).number("100").run();

    final String buildTypeDimension = ",buildType:(id:" + buildConf.getExternalId() + ")";

    checkBuilds("number:100" + buildTypeDimension, getBuildPromotions(build30, build20));
    checkBuilds("number:100", getBuildPromotions(build30, build20));
//getBuildPromotions(build80, build60, build50, build40, build30, build20, build10)
    checkBuilds("number:100,defaultFilter:false", getBuildPromotions(build80, build60, build50, build40, build30, build20));
    checkBuilds("number:100,defaultFilter:false" + buildTypeDimension,                getBuildPromotions(build80, build60, build50, build40, build30, build20));
    checkBuilds("number:100,canceled:any", getBuildPromotions(build60, build30, build20));
    checkBuilds("number:100,canceled:any" + buildTypeDimension, getBuildPromotions(build60, build30, build20));
    checkBuilds("number:100,personal:true", getBuildPromotions(build50));
    checkBuilds("number:100,personal:true" + buildTypeDimension, getBuildPromotions(build50));
    checkBuilds("number:100,personal:any", getBuildPromotions(build50, build30, build20));
    checkBuilds("number:100,personal:any" + buildTypeDimension, getBuildPromotions(build50, build30, build20));
    checkBuilds("number:100,state:any", getBuildPromotions(build80, build30, build20));
    checkBuilds("number:100,state:any" + buildTypeDimension, getBuildPromotions(build80, build30, build20));
    checkBuilds("number:100,running:true", getBuildPromotions(build80));
    checkBuilds("number:100,running:true" + buildTypeDimension, getBuildPromotions(build80));
    checkBuilds("number:100,running:false", getBuildPromotions(build30, build20));
    checkBuilds("number:100,running:false" + buildTypeDimension, getBuildPromotions(build30, build20));

    checkBuilds("count:1,number:378" + buildTypeDimension);
  }

  @Test
  public void testWithProject() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    SBuild build10 = build().in(buildConf).finish();
    SBuild build20 = build().in(buildConf).number("100").finish();
    SBuild build30 = build().in(buildConf).number("100").failed().finish();
    SBuild build40 = build().in(buildConf).number("100").failedToStart().finish();

    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    SBuild build100 = build().in(buildConf2).failed().finish();

    PagedSearchResult<BuildPromotion> promotions = myBuildPromotionFinder.getBuildPromotions(buildConf, null);
    assertEquals(3, promotions.myEntries.size());

    promotions = myBuildPromotionFinder.getBuildPromotions(buildConf, "buildType:$any,status:FAILURE");
    assertEquals(2, promotions.myEntries.size());
  }

  @Test
  public void testParametersCondition() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final SFinishedBuild finishedBuild05 = build().in(buildConf1).finish();
    final SFinishedBuild finishedBuild10 = build().in(buildConf1).parameter("a", "10").parameter("b", "10").parameter("aa", "15").finish();
    final SFinishedBuild finishedBuild20 = build().in(buildConf1).parameter("b", "20").parameter("myProp1", "randomValue#mPWh1dEHNVHVPhE17nwzYJng").finish();
    final SFinishedBuild finishedBuild30 = build().in(buildConf1).parameter("c", "20").parameter("myProp2", "randomValue#mPWh1dEHNVHVPhE17nwzYJng").finish();
    final SFinishedBuild finishedBuild35 = build().in(buildConf1).parameter("c", "10").finish();
    final SFinishedBuild finishedBuild40 = build().in(buildConf1).parameter("zzz", "30").finish();
    final SFinishedBuild finishedBuild50 = build().in(buildConf1).parameter("aa", "10").parameter("aaa", "10").finish();

    checkBuilds("property:(name:a)", getBuildPromotions(finishedBuild10));
    checkBuilds("property:(name:b)", getBuildPromotions(finishedBuild20, finishedBuild10));
    checkBuilds("property:(name:b,value:20)", getBuildPromotions(finishedBuild20));
    checkBuilds("property:(value:randomValue#mPWh1dEHNVHVPhE17nwzYJng)", getBuildPromotions(finishedBuild30, finishedBuild20));

    //"contains" by default
    checkBuilds("property:(value:0)", getBuildPromotions(finishedBuild50, finishedBuild40, finishedBuild35, finishedBuild30, finishedBuild20, finishedBuild10, finishedBuild05));

    //finds all as entire build params map is used for the search and build has a parameter with build conf id
    checkBuilds("property:(value:" + buildConf1.getExternalId() + ")",
                getBuildPromotions(finishedBuild50, finishedBuild40, finishedBuild35, finishedBuild30, finishedBuild20, finishedBuild10, finishedBuild05));

    checkBuilds("property:(value:randomValue#mPWh1dEHNVHVPhE17nwzYJng,matchType:equals)", getBuildPromotions(finishedBuild30, finishedBuild20));
    checkBuilds("property:(value:15,matchType:more-than)", getBuildPromotions(finishedBuild40, finishedBuild30, finishedBuild20));
    checkBuilds("property:(name:b,value:15,matchType:more-than)", getBuildPromotions(finishedBuild20));
//this is not reported anyhow from the core (Requirement)    checkExceptionOnBuildsSearch(BadRequestException.class, "property:(value:[,matchType:matches)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "property:(value:10,matchType:Equals)");
    checkExceptionOnBuildsSearch(BadRequestException.class, "property:(value:10,matchType:AAA)");

    checkBuilds("property:(name:([b,c]),nameMatchType:matches)", getBuildPromotions(finishedBuild35, finishedBuild30, finishedBuild20, finishedBuild10));
    checkBuilds("property:(name:.,nameMatchType:matches,value:15,matchType:more-than)", getBuildPromotions(finishedBuild30, finishedBuild20));
    checkBuilds("property:(matchScope:any,value:10,matchType:equals)", getBuildPromotions(finishedBuild50, finishedBuild35, finishedBuild10));
//this does not work as all build params are checked, not only custom    checkBuilds("property:(matchScope:all,value:10,matchType:equals)", getBuildPromotions(finishedBuild50, finishedBuild35));
    checkBuilds("property:(name:(a*),nameMatchType:matches,matchScope:all,value:10,matchType:equals)", getBuildPromotions(finishedBuild50));
    checkBuilds("property:(name:(.),nameMatchType:matches,matchScope:any,value:10,matchType:equals)", getBuildPromotions(finishedBuild35, finishedBuild10));
    checkBuilds("property:(name:zzz)", getBuildPromotions(finishedBuild40));
    checkBuilds("property:(name:z.z)");

    //builds with at least one param not equal to 10
    checkBuilds("property:(value:10,matchType:does-not-match)",
                getBuildPromotions(finishedBuild50, finishedBuild40, finishedBuild35, finishedBuild30, finishedBuild20, finishedBuild10, finishedBuild05));
    //builds with no param equal to 10
    checkBuilds("property:(nameMatchType:exists,matchScope:all,value:10,matchType:does-not-equal)",
                getBuildPromotions(finishedBuild40, finishedBuild30, finishedBuild20, finishedBuild05));
  }

  @Test
  public void testBranchDimension() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).withBranch("branch").finish().getBuildPromotion();

    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    buildConf1.addVcsRoot(buildConf1.getProject().createVcsRoot("vcs", "extId", "name"));

    final VcsRootInstance vcsRootInstance = buildConf1.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState("master", map("master", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance, "+:*");
    buildConf1.forceCheckingForChanges();
    myFixture.getVcsModificationChecker().ensureModificationChecksComplete();

    final BuildPromotion build30 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf1).withDefaultBranch().finish().getBuildPromotion();
    final BuildPromotion build50 = build().in(buildConf1).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build60 = build().in(buildConf1).withBranch("branch1").finish().getBuildPromotion();

    final BuildPromotion build65 = build().in(buildConf1).withBranch(Branch.UNSPECIFIED_BRANCH_NAME).finish().getBuildPromotion(); //right way to run unspecified?

    final RunningBuildEx running70 = build().withBranch("branch1").in(buildConf1).run();
    running70.stop(getOrCreateUser("user1"), "cancel comment");
    final BuildPromotion build70 = finishBuild(running70, true).getBuildPromotion();

    final BuildPromotion build80 = build().in(buildConf1).withBranch("branch1").run().getBuildPromotion();
    final BuildPromotion build90 = build().in(buildConf1).withBranch("branch1").addToQueue().getBuildPromotion();

    checkBuilds("defaultFilter:false", build90, build80, build70, build65, build60, build50, build40, build30, build20, build10);
    checkBuilds(null, build40, build30, build10);
    checkBuilds("branch:(default:any)", build65, build60, build50, build40, build30, build20, build10);
    checkBuilds("branch:(default:true)", build40, build30, build10);
    checkBuilds("branch:(default:false)", build65, build60, build50, build20);
    checkBuilds("branch:(branched:true)", build65, build60, build50, build40, build30, build20);
    checkBuilds("branch:(branched:false)",build10);
    checkBuilds("branch:(unspecified:true)", build65);
    checkBuilds("branch:(name:<unspecified>)", build65);
    checkBuilds("branch:(unspecified:false)", build60, build50, build40, build30, build20, build10);
    checkBuilds("branch:(name:branch1)", build60);
    checkBuilds("branch:branch1", build60);
    checkBuilds("branch:(name:master)", build40, build30);

    checkBuilds("branch:(name:<default>)", build40, build30);
    checkBuilds("branch:<default>", build40, build30);
    checkBuilds("branch:(name:<any>)", build65, build60, build50, build40, build30, build20, build10);
    checkBuilds("branch:<any>", build65, build60, build50, build40, build30, build20, build10);

    checkBuilds("branch:(buildType:(id:" + buildConf1.getExternalId() + "),policy:ALL_BRANCHES)", build65, build60, build50, build40, build30, build20);
    checkBuilds("branch:(buildType:(id:" + buildConf1.getExternalId() + "),policy:VCS_BRANCHES)", build60, build40, build30);

    checkBuilds("buildType:(id:" + buildConf1.getExternalId() + "),branch:(policy:ALL_BRANCHES)", build65, build60, build50, build40, build30, build20);
    checkBuilds("buildType:(id:" + buildConf1.getExternalId() + "),branch:(policy:VCS_BRANCHES)", build60, build40, build30);
    checkBuilds("buildType:(id:" + buildConf1.getExternalId() + "),branch:(default:true)", build40, build30, build10);

    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(policy:ALL_BRANCHES)"); //policy is not supported for the build's branch locator
    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(aaa:bbb)");

    // check that no filtering is done when not necessary
    assertEquals(0, ((MultiCheckerFilter)myBranchFinder.getFilter(new Locator("<any>"))).getSubFiltersCount());
    assertEquals(0, ((MultiCheckerFilter)myBranchFinder.getFilter(new Locator("default:any"))).getSubFiltersCount());
  }

  @Test
  public void testOrderDimension() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    final BuildPromotion build01 = build().in(buildConf).finish().getBuildPromotion();
    final BuildPromotion build1 = build().in(buildConf2).finish().getBuildPromotion();
    final BuildPromotion build02 = build().in(buildConf).failed().finish().getBuildPromotion();
    final BuildPromotion build2 = build().in(buildConf2).failed().finish().getBuildPromotion();

    final RunningBuildEx running3 = build().in(buildConf2).run();
    running3.stop(getOrCreateUser("user1"), "cancel comment");
    final BuildPromotion build3 = finishBuild(running3, true).getBuildPromotion();

    final BuildPromotion build4 = build().in(buildConf2).failedToStart().finish().getBuildPromotion();
    final BuildPromotion build5 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build6 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build7 = build().in(buildConf2).withBranch("branch2").finish().getBuildPromotion();
    final BuildPromotion build8 = build().in(buildConf2).finish().getBuildPromotion();
    final BuildPromotion build9 = build().in(buildConf2).withBranch("branch2").finish().getBuildPromotion();
    final BuildPromotion build10 = build().in(buildConf2).withBranch("branch").finish().getBuildPromotion();

    final RunningBuildEx running11 = build().in(buildConf2).withBranch("branch").run();
    running11.stop(getOrCreateUser("user1"), "cancel comment");
    final BuildPromotion build11 = finishBuild(running11, true).getBuildPromotion();

    final BuildPromotion runningBuild5 = build().in(buildConf2).withBranch("branch").run().getBuildPromotion();
    final BuildPromotion build15 = build().in(buildConf2).withBranch("branch").addToQueue().getBuildPromotion();

    checkBuilds("ordered:(from:(id:" + build4.getId() + "))", build8);
    checkBuilds("ordered:(to:(id:" + build4.getId() + "))", build2, build1);
    checkBuilds("ordered:(to:(id:" + build4.getId() + ")),defaultFilter:false", build3, build2, build1);
    checkBuilds("ordered:(from:(id:" + build6.getId() + ")),defaultFilter:false", build10, build11, runningBuild5);
    checkBuilds("ordered:(to:(id:" + build6.getId() + "))", build5, build2, build1);
    checkBuilds("ordered:(to:(id:" + build6.getId() + ")),defaultFilter:false", build5, build4, build3, build2, build1);
    checkBuilds("ordered:(to:(id:" + runningBuild5.getId() + "))", build10, build6, build5, build2, build1);
    checkBuilds("ordered:(to:(id:" + build7.getId() + ")),equivalent:(id:" + build5.getId() + ")", build2, build1);
    checkBuilds("ordered:(to:(id:" + build10.getId() + "))", build6, build5, build2, build1);
    checkBuilds("ordered:(to:(id:" + build10.getId() + ")),equivalent:(id:" + build5.getId() + ")", build6, build2, build1);
  }

  @Test
  public void testItemDimension() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).parameter("a", "10").parameter("b", "10").parameter("aa", "15").finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).failed().parameter("b", "20").finish().getBuildPromotion();

    checkBuilds("item:(id:" + build10.getId() + ")", build10);
    checkBuilds("item:(id:" + build10.getId() + "),item:(id:" + build10.getId() + ")", build10, build10);
    checkBuilds("item:(id:" + build10.getId() + "),item:(id:" + build10.getId() + "),unique:true", build10);
    checkBuilds("item:(id:" + build10.getId() + "),item:(id:" + build20.getId() + ")", build10, build20);
    checkBuilds("item:(id:" + build20.getId() + "),item:(id:" + build10.getId() + ")", build20, build10);
    checkBuilds("item:(id:" + build20.getId() + "),item:(id:" + build10.getId() + "),count:1", build20);
    checkBuilds("item:(id:" + build20.getId() + "),item:(id:" + build10.getId() + "),start:1", build10);
    checkBuilds("item:(status:SUCCESS),item:(status:FAILURE)", build20, build10, build30);
    checkBuilds("item:(status:SUCCESS),item:(status:FAILURE),item:(id:" + build10.getId() + "),unique:true", build20, build10, build30);
  }

  @Test
  public void testStrobDimension() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");
    final BuildTypeEx buildConf2 = (BuildTypeEx)project.createBuildType("buildConf2", "buildConf2");
    project.createBuildTypeTemplate("template1", "template1");

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build15 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf2).failed().finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).failed().finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf2).finish().getBuildPromotion();

    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")))", build30, build40);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),locator:(count:10))", build30, build15, build10, build40, build20);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),locator:(status:SUCCESS))", build15, build40);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),locator:(status:SUCCESS,count:10))", build15, build10, build40);

    ((ProjectImpl)project).setOwnBuildTypesOrder(Arrays.asList(buildConf2.getId(), buildConf1.getId()));
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")))", build40, build30);
  }

  @Test
  public void testStrobBranchedDimension() {
    final SProject project = createProject("prj", "project");
    final BuildTypeEx buildConf1 = (BuildTypeEx)project.createBuildType("buildConf1", "buildConf1");
    final BuildTypeEx buildConf2 = (BuildTypeEx)project.createBuildType("buildConf2", "buildConf2");

    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    buildConf1.addVcsRoot(buildConf1.getProject().createVcsRoot("vcs", "extId", "name"));

    final VcsRootInstance vcsRootInstance = buildConf1.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState("master", map("master", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance, "+:*");
    buildConf1.forceCheckingForChanges();
    myFixture.getVcsModificationChecker().ensureModificationChecksComplete();

    final BuildPromotion build10 = build().in(buildConf1).finish().getBuildPromotion();
    final BuildPromotion build15 = build().in(buildConf1).withDefaultBranch().finish().getBuildPromotion();
    final BuildPromotion build20 = build().in(buildConf1).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build25 = build().in(buildConf1).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build30 = build().in(buildConf1).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build35 = build().in(buildConf1).withBranch("branch").finish().getBuildPromotion();
    final BuildPromotion build40 = build().in(buildConf1).finish().getBuildPromotion();

    final BuildPromotion build50 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();
    final BuildPromotion build60 = build().in(buildConf2).withBranch("branch1").finish().getBuildPromotion();

    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")))", build40);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),branch:(policy:ALL_BRANCHES))", build40, build35, build25, build60);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),branch:(policy:VCS_BRANCHES))", build40, build25);
    checkBuilds("strob:(buildType:(project:(id:" + project.getExternalId() + ")),branch:(default:any))", build40, build35, build25, build60);
    checkBuilds("strob:(branch:(default:any,buildType:(id:" + buildConf1.getExternalId() + ")),locator:(buildType:(id:" + buildConf1.getExternalId() + ")))", build40, build35, build25);
    checkExceptionOnBuildsSearch(BadRequestException.class, "strob:(branch:(default:any),locator:(buildType:(id:" + buildConf1.getExternalId() + ")))");

    checkBuilds("strob:(branch:(default:any,buildType:(id:" + buildConf2.getExternalId() + ")),locator:(buildType:(id:" + buildConf1.getExternalId() + ")))", build40, build25);
  }

  //==================================================

  public void checkBuilds(final String locator, BuildPromotion... builds) {
    checkMultipleBuilds(locator, builds);

    //check single build retrieve
    if (locator != null) {
      if (builds.length == 0) {
        checkNoBuildFound(locator);
      } else {
        checkBuild(locator, builds[0]);
      }
    }
  }

  private void checkBuilds(final String locator, final long actuallyProcessedLimit, final BuildPromotion[] builds) {
    final PagedSearchResult<BuildPromotion> result = checkMultipleBuilds(locator, builds);
    //noinspection ConstantConditions
    assertTrue("Actually processed count (" + result.myActuallyProcessedCount + ") is more than expected limit " + actuallyProcessedLimit,
                 (long)result.myActuallyProcessedCount <= actuallyProcessedLimit);

  }

  private PagedSearchResult<BuildPromotion> checkMultipleBuilds(final String locator, final BuildPromotion[] builds) {
    final PagedSearchResult<BuildPromotion> searchResult = myBuildPromotionFinder.getItems(locator);
    final List<BuildPromotion> result = searchResult.myEntries;
    final String expected = getPromotionsDescription(Arrays.asList(builds));
    final String actual = getPromotionsDescription(result);
    assertEquals("For locator \"" + locator + "\"\n" +
                 "Expected:\n" + expected + "\n\n" +
                 "Actual:\n" + actual, builds.length, result.size());
    for (int i = 0; i < builds.length; i++) {
      if (!builds[i].equals(result.get(i))) {
        fail("Wrong build found for locator \"" + locator + "\" at position " + (i + 1) + "/" + builds.length + "\n" +
             "Expected:\n" + expected + "\n" +
             "\nActual:\n" + actual);
      }
    }
    return searchResult;
  }

  protected void checkBuild(final String locator, @NotNull BuildPromotion build) {
    BuildPromotion result = myBuildPromotionFinder.getItem(locator);

    if (!build.equals(result)) {
      fail("For single build locator \"" + locator + "\"\n" +
           "Expected: " + LogUtil.describeInDetail(build) + "\n" +
           "Actual: " + LogUtil.describeInDetail(result));
    }
  }

  protected void checkNoBuildsFound(final String locator) {
    final List<BuildPromotion> result = myBuildPromotionFinder.getItems(locator).myEntries;
    if (!result.isEmpty()) {
      fail("For locator \"" + locator + "\" expected NotFoundException but found " + LogUtil.describe(result) + "");
    }
  }

  protected void checkNoBuildFound(final String singleBuildLocator) {
    checkExceptionOnBuildSearch(NotFoundException.class, singleBuildLocator);
  }

  public static String getPromotionsDescription(final List<BuildPromotion> result) {
    return LogUtil.describe(CollectionsUtil.convertCollection(result, new Converter<Loggable, BuildPromotion>() {
      public Loggable createFrom(@NotNull final BuildPromotion source) {
        return new Loggable() {
          @NotNull
          public String describe(final boolean verbose) {
            return LogUtil.describeInDetail(source);
          }
        };
      }
    }), false, "\n", "", "");
  }

  public static <E extends Throwable> void checkException(final Class<E> exception, final Runnable runnnable, final String operationDescription) {
    final String details = operationDescription != null ? " while " + operationDescription : "";
    try {
      runnnable.run();
    } catch (Throwable e) {
      if (exception.isAssignableFrom(e.getClass())) {
        return;
      }
      final StringBuilder exceptionDetails = new StringBuilder();
      ExceptionUtil.dumpStacktrace(exceptionDetails, e);
      fail("Wrong exception type is thrown" + details + ".\n" +
           "Expected: " + exception.getName() + "\n" +
           "Actual  : " + exceptionDetails.toString());
    }
    fail("No exception is thrown" + details +
         ". Expected: " + exception.getName());
  }

  public <E extends Throwable> void checkExceptionOnBuildSearch(final Class<E> exception, final String singleBuildLocator) {
    checkException(exception, new Runnable() {
      public void run() {
        myBuildPromotionFinder.getItem(singleBuildLocator);
      }
    }, "searching single build with locator \"" + singleBuildLocator + "\"");
  }

  public <E extends Throwable> void checkExceptionOnBuildsSearch(final Class<E> exception, final String multipleBuildsLocator) {
    checkException(exception, new Runnable() {
      public void run() {
        myBuildPromotionFinder.getItems(multipleBuildsLocator);
      }
    }, "searching builds with locator \"" + multipleBuildsLocator + "\"");
  }

  @NotNull
  public static BuildPromotion[] getBuildPromotions(final BuildPromotionOwner... builds) {
    final BuildPromotion[] buildPromotions = new BuildPromotion[builds.length];
    for (int i = 0; i < builds.length; i++) {
      buildPromotions[i] = builds[i].getBuildPromotion();
    }
    return buildPromotions;
  }

}
