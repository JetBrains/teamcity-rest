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
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2015
 */
public class BuildFinderTestBase extends BaseServerTestCase {
  protected BuildFinder myBuildFinder;
  protected QueuedBuildFinder myQueuedBuildFinder;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager);
    final AgentFinder agentFinder = new AgentFinder(myAgentManager);
    final BuildTypeFinder buildTypeFinder = new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, myServer);
    final UserFinder userFinder = new UserFinder(myFixture);
    final BuildPromotionFinder buildPromotionFinder = new BuildPromotionFinder(myFixture.getBuildPromotionManager(), myFixture.getBuildQueue(), myServer, myFixture.getHistory(),
                                                                               projectFinder, buildTypeFinder, userFinder, agentFinder);
    myBuildFinder = new BuildFinder(myServer, buildTypeFinder, projectFinder, userFinder, buildPromotionFinder, agentFinder);
    myQueuedBuildFinder = new QueuedBuildFinder(myServer.getQueue(), projectFinder, buildTypeFinder, userFinder, agentFinder, myFixture.getBuildPromotionManager(), myServer);
  }

  public void checkBuilds(final String locator, SBuild... builds) {
    final List<SBuild> result = myBuildFinder.getBuilds(myBuildFinder.getBuildsFilter(null, locator));
//    checkOrderedCollection(result, builds);
    assertEquals("For locator \"" + locator + "\"\n" +
                 "Expected:\n" + getBuildsList(Arrays.asList(builds)) + "\n\n" +
                 "Actual:\n" + getBuildsList(result), builds.length, result.size());
    for (int i = 0; i < builds.length; i++) {
      if (!builds[i].equals(result.get(i))) {
        fail("Wrong build found for locator \"" + locator + "\" at position " + (i + 1) + "/" + builds.length + "\n" +
             "Expected:\n" + getBuildsList(Arrays.asList(builds)) + "\n" +
             "\nActual:\n" + getBuildsList(result));
      }
    }
  }

  protected void checkNoBuildsFound(final String locator) {
    final List<SBuild> result = myBuildFinder.getBuilds(myBuildFinder.getBuildsFilter(null, locator));
    if (!result.isEmpty()){
      fail("For locator \"" + locator + "\" expected NotFoundException but found " + LogUtil.describe(result) + "");
    }
  }

  protected void checkBuild(final String locator, Object build) {
    SBuild result = myBuildFinder.getBuild(null, locator);

    assertEquals("For locator \"" + locator + "\"", build, result);
  }

  protected void checkBuildNotFound(final String locator) {
    SBuild result = null;
    try {
      result = myBuildFinder.getBuild(null, locator);
    } catch (NotFoundException e) {
      return;
    }
    fail("For locator \"" + locator + "\" expected NotFoundException but found " + LogUtil.describe(result) + "");
  }

  public static String getBuildsList(final List<SBuild> result) {
    return LogUtil.describe(CollectionsUtil.convertCollection(result, new Converter<Loggable, SBuild>() {
      public Loggable createFrom(@NotNull final SBuild source) {
        return new Loggable() {
          @NotNull
          public String describe(final boolean verbose) {
            return LogUtil.describeInDetail(source);
          }
        };
      }
    }), false, "\n", "", "");
  }

  public static <E extends Throwable> void checkException(final Class<E> exception, final Runnable runnnable) {
    try {
      runnnable.run();
    } catch (Throwable e) {
      if (exception.isAssignableFrom(e.getClass())) {
        return;
      }
      fail("Wrong exception is thrown.\n" +
           "Expected: " + exception.getName() + "\n" +
           "Actual  : " + e.getClass().getName());
    }
    fail("No exception is thrown. Expected: " + exception.getName());
  }
}
