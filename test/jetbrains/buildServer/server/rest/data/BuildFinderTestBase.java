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

import com.intellij.openapi.diagnostic.Logger;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import jetbrains.buildServer.log.Loggable;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2015
 */
public class BuildFinderTestBase extends BaseServerTestCase {
  private static Logger LOG = Logger.getInstance(BuildFinderTestBase.class.getName());

  protected BuildFinder myBuildFinder;
  protected QueuedBuildFinder myQueuedBuildFinder;
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
    myBuildFinder = new BuildFinder(myServer, buildTypeFinder, projectFinder, userFinder, myBuildPromotionFinder, agentFinder);
    myQueuedBuildFinder = new QueuedBuildFinder(myServer.getQueue(), projectFinder, buildTypeFinder, userFinder, agentFinder, myFixture.getBuildPromotionManager(), myServer);
  }

  public void checkBuilds(@Nullable final String locator, SBuild... builds) {
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

  public void checkMultipleBuilds(final @Nullable String locator, final SBuild... builds) {
    final List<BuildPromotion> result = myBuildFinder.getBuilds(null, locator).myEntries;
    final String expected = getPromotionsDescription(getPromotions(builds));
    final String actual = getPromotionsDescription(result);
    assertEquals("For builds locator \"" + locator + "\"\n" +
                 "Expected:\n" + expected + "\n\n" +
                 "Actual:\n" + actual, builds.length, result.size());

    for (int i = 0; i < builds.length; i++) {
      if (!builds[i].getBuildPromotion().equals(result.get(i))) {
        fail("Wrong build found for locator \"" + locator + "\" at position " + (i + 1) + "/" + builds.length + "\n" +
             "Expected:\n" + expected + "\n" +
             "\nActual:\n" + actual);
      }
    }
  }

  protected void checkBuild(final String locator, @NotNull SBuild build) {
    checkBuild(null, locator, build.getBuildPromotion());
  }

  protected void checkBuild(final String locator, @NotNull BuildPromotion buildPromotion) {
    checkBuild(null, locator, buildPromotion);
  }

  protected void checkBuild(final SBuildType buildType, final String locator, @NotNull BuildPromotion buildPromotion) {
    SBuild build = buildPromotion.getAssociatedBuild();
    if (build != null) {
      //checking for build
      SBuild result = myBuildFinder.getBuild(buildType, locator);

      if (!build.equals(result)) {
        fail("While searching for single build with locator \"" + locator + "\"\n" +
             "Expected: " + LogUtil.describeInDetail(build) + "\n" +
             "Actual: " + LogUtil.describeInDetail(result));
      }
    }

    //checking for build promotion
    BuildPromotion result1 = myBuildFinder.getBuildPromotion(buildType, locator);

    if (!buildPromotion.equals(result1)) {
      fail("While searching for single build promotion with locator \"" + locator + "\"\n" +
           "Expected: " + LogUtil.describeInDetail(buildPromotion) + "\n" +
           "Actual: " + LogUtil.describeInDetail(result1));
    }
  }

  private List<BuildPromotion> getPromotions(final SBuild[] builds) {
    return CollectionsUtil.convertCollection(Arrays.asList(builds), new Converter<BuildPromotion, SBuild>() {
      public BuildPromotion createFrom(@NotNull final SBuild source) {
        return source.getBuildPromotion();
      }
    });
  }

  private List<BuildPromotion> getPromotions(final Iterable<SBuild> builds) {
    return CollectionsUtil.convertCollection(builds, new Converter<BuildPromotion, SBuild>() {
      public BuildPromotion createFrom(@NotNull final SBuild source) {
        return source.getBuildPromotion();
      }
    });
  }

  protected void checkNoBuildsFound(@Nullable final String locator) {
    final List<BuildPromotion> result = myBuildFinder.getBuilds(null, locator).myEntries;
    if (!result.isEmpty()) {
      fail("For builds locator \"" + locator + "\" expected NotFoundException but found " + LogUtil.describe(result) + "");
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
      LOG.debug(e);
      fail("Wrong exception type is thrown" + details + ".\n" +
           "Expected: " + exception.getName() + "\n" +
           "Actual  : " + e.toString());
    }
    fail("No exception is thrown" + details +
         ". Expected: " + exception.getName());
  }

  public <E extends Throwable> void checkExceptionOnBuildSearch(final Class<E> exception, final String singleBuildLocator) {
    checkExceptionOnBuildSearch(exception, null, singleBuildLocator);
  }

  public <E extends Throwable> void checkExceptionOnBuildSearch(final Class<E> exception, final SBuildType buildType, final String singleBuildLocator) {
    checkException(exception, new Runnable() {
      public void run() {
        myBuildFinder.getBuild(buildType, singleBuildLocator);
      }
    }, "searching single build with locator \"" + singleBuildLocator + "\"");

    checkException(exception, new Runnable() {
      public void run() {
        myBuildFinder.getBuildPromotion(buildType, singleBuildLocator);
      }
    }, "searching single build promotion with locator \"" + singleBuildLocator + "\"");
  }

  public <E extends Throwable> void checkExceptionOnBuildsSearch(final Class<E> exception, @Nullable final String multipleBuildsLocator) {
    checkException(exception, new Runnable() {
      public void run() {
        myBuildFinder.getBuilds(null, multipleBuildsLocator);
      }
    }, "searching builds with locator \"" + multipleBuildsLocator + "\"");
  }

  @NotNull
  public static String fDate(final Date date) {
    return new SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.ENGLISH).format(date);
  }

}
