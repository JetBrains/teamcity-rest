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
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
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
  public void testQueuedBuildFinding() throws Exception {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    final SFinishedBuild build0 = build().in(buildConf).finish();
    final SQueuedBuild queuedBuild = build().in(buildConf).addToQueue();

    checkBuilds(String.valueOf(queuedBuild.getItemId()), queuedBuild.getBuildPromotion());
    checkBuilds("id:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
//fix    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),id:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("taskId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
    checkBuilds("promotionId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
//    checkBuilds("buildType:(id:" + buildConf.getExternalId() + "),promotionId:" + queuedBuild.getItemId(), queuedBuild.getBuildPromotion());
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
    if (builds.length == 0) {
      checkNoBuildFound(locator);
    } else {
      checkBuild(locator, builds[0]);
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
