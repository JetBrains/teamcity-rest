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
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.CancelableTaskHolder;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2015
 */
public class BuildPromotionFinderTest extends BaseServerTestCase {
  private BuildPromotionFinder myBuildPromotionFinder;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager);
    final AgentFinder agentFinder = new AgentFinder(myAgentManager);
    final BuildTypeFinder buildTypeFinder = new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, myServer);
    final UserFinder userFinder = new UserFinder(myFixture);
    myBuildPromotionFinder = new BuildPromotionFinder(myFixture.getBuildPromotionManager(), myFixture.getBuildQueue(), myServer, myFixture.getHistory(),
                                                                               projectFinder, buildTypeFinder, userFinder, agentFinder);
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
    checkExceptionOnBuildsSearch(LocatorProcessException.class, "id:" + runningBuild.getBuildId() + ",running:true");
//consider fixing    checkBuilds("id:" + runningBuild.getBuildId() + ",running:true", runningBuild.getBuildPromotion());
//consider fixing    checkExceptionOnBuildsSearch(NotFoundException.class, "id:" + runningBuild.getBuildId() + ",running:false");
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

    checkExceptionOnBuildsSearch(NotFoundException.class, "snapshotDependency:(to:(id:" + (build4.getId() + 10) + ")),state:any");
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
  }

  @Test
  public void testEquivalentBuildFinding() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SFinishedBuild build1 = build().in(buildConf).finish();

    checkBuilds("equivalent:(id:" + build0.getBuildId() + ")", build1.getBuildPromotion());

    //todo: add test for snapshot +  equivalent filtering
  }

//==================================================

  public void checkBuilds(final String locator, BuildPromotion... builds) {
    final List<BuildPromotion> result = myBuildPromotionFinder.getItems(locator).myEntries;
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

    //check single build retrieve
    if (locator != null) {
      if (builds.length == 0) {
        checkNoBuildFound(locator);
      } else {
        checkBuild(locator, builds[0]);
      }
    }
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
//    final List<BuildPromotion> result = myBuildPromotionFinder.getItems(locator).myEntries;
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
      fail("Wrong exception type is thrown" + details + ".\n" +
           "Expected: " + exception.getName() + "\n" +
           "Actual  : " + e.toString());
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
}
