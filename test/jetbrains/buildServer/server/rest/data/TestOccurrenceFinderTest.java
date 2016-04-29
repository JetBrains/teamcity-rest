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

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 16/12/2015
 */
public class TestOccurrenceFinderTest extends BaseFinderTest<STestRun> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myProject.remove();

    setFinder(myTestOccurrenceFinder);
  }

  @Test
  public void testBasic() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType).withTest("aaa", true).finish();

    checkExceptionOnItemSearch(BadRequestException.class, "No_match");
    checkExceptionOnItemsSearch(BadRequestException.class, "No_match");
    check("build:(id:" + build10.getBuildId() + ")", build10.getFullStatistics().getAllTests().get(0));
    check("build:(id:" + build10.getBuildId() + ")", TEST_MATCHER, t("aaa", Status.NORMAL, 1));

    int testRunId = build10.getFullStatistics().getAllTests().get(0).getTestRunId();
    check("build:(id:" + build10.getBuildId() + "),id:" + testRunId, TEST_MATCHER, t("aaa", Status.NORMAL, 1));
    check("build:(id:" + build10.getBuildId() + "),id:" + testRunId + 1);

    check("build:(id:" + build10.getBuildId() + "),test:(name:aaa)", TEST_MATCHER, t("aaa", Status.NORMAL, 1));
    check("build:(id:" + build10.getBuildId() + "),test:(name:bbb)");
  }

  @Test
  public void testByTest() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("aaa", false)
                                          .withTest("bbb", true)
                                          .withTest("ccc", false)
                                          .finish();

    check("build:(id:" + build10.getBuildId() + ")", TEST_MATCHER, t("aaa", Status.FAILURE, 1), t("bbb", Status.NORMAL, 2), t("ccc", Status.FAILURE, 3));
    check("build:(id:" + build10.getBuildId() + "),test:(name:missingTest)", TEST_MATCHER);
    check("build:(id:" + build10.getBuildId() + "),test:(name:bbb)", TEST_MATCHER, t("bbb", Status.NORMAL, 2));
    check("build:(id:" + build10.getBuildId() + "),test:(currentlyFailing:true)", TEST_MATCHER, t("aaa", Status.FAILURE, 1), t("ccc", Status.FAILURE, 3));
  }

  @Test
  public void testSeveralInvocations() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("aaa", true)
                                          .withTest("aaa", true)
                                          .withTest("bbb", true)
                                          .withTest("ccc", false)
                                          .withTest("bbb", true)
                                          .withTest("aaa", false)
                                          .withTest("aaa", true)
                                          .withTest("ddd", true)
                                          .finish();

    check("build:(id:" + build10.getBuildId() + ")", TEST_MATCHER,
          t("aaa", Status.FAILURE, 1),
          t("bbb", Status.NORMAL, 3),
          t("ccc", Status.FAILURE, 4),
          t("ddd", Status.NORMAL, 8));

    {
      int testRunId = myTestOccurrenceFinder.getItems("build:(id:" + build10.getBuildId() + ")").myEntries.get(0).getTestRunId();
      check("build:(id:" + build10.getBuildId() + "),id:(" + testRunId + ")", TEST_MATCHER, t("aaa", Status.FAILURE, 1));
      assertEquals(testRunId, myTestOccurrenceFinder.getItems("build:(id:" + build10.getBuildId() + "),id:(" + testRunId + ")").myEntries.get(0).getTestRunId());
      assertEquals(testRunId, myTestOccurrenceFinder.getItem("build:(id:" + build10.getBuildId() + "),id:(" + testRunId + ")").getTestRunId());
    }

    check("build:(id:" + build10.getBuildId() + "),expandInvocations:true", TEST_MATCHER,
          t("aaa", Status.NORMAL, 1),
          t("aaa", Status.NORMAL, 2),
          t("aaa", Status.FAILURE, 6), //ordering so far is grouped by test
          t("aaa", Status.NORMAL, 7),
          t("bbb", Status.NORMAL, 3),
          t("bbb", Status.NORMAL, 5),
          t("ccc", Status.FAILURE, 4),
          t("ddd", Status.NORMAL, 8));

    {
      int testRunId = myTestOccurrenceFinder.getItems("build:(id:" + build10.getBuildId() + "),expandInvocations:true").myEntries.get(2).getTestRunId();
      check("build:(id:" + build10.getBuildId() + "),id:(" + testRunId + ")", TEST_MATCHER, t("aaa", Status.FAILURE, 6));
      assertEquals(testRunId, myTestOccurrenceFinder.getItems("build:(id:" + build10.getBuildId() + "),id:(" + testRunId + ")").myEntries.get(0).getTestRunId());
      assertEquals(testRunId, myTestOccurrenceFinder.getItem("build:(id:" + build10.getBuildId() + "),id:(" + testRunId + ")").getTestRunId());
    }

    check("build:(id:" + build10.getBuildId() + "),test:(name:aaa)", TEST_MATCHER,
          t("aaa", Status.FAILURE, 1));

    check("build:(id:" + build10.getBuildId() + "),test:(name:aaa),expandInvocations:true", TEST_MATCHER,
          t("aaa", Status.NORMAL, 1),
          t("aaa", Status.NORMAL, 2),
          t("aaa", Status.FAILURE, 6),
          t("aaa", Status.NORMAL, 7));
  }

  @Test
  public void testBuildDimension() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("aaa", true)
                                          .withTest("aaa", true)
                                          .withTest("bbb", true)
                                          .withTest("ccc", false)
                                          .withTest("bbb", true)
                                          .withTest("aaa", false)
                                          .withTest("aaa", true)
                                          .withTest("ddd", true)
                                          .finish();

    final SFinishedBuild build20 = build().in(buildType)
                                          .withTest("xxx", true)
                                          .withTest("aaa", true)
                                          .finish();

    check("build:(id:" + build10.getBuildId() + ")", TEST_WITH_BUILD_MATCHER,
          t("aaa", Status.FAILURE, 1, build10.getBuildId()),
          t("bbb", Status.NORMAL, 3, build10.getBuildId()),
          t("ccc", Status.FAILURE, 4, build10.getBuildId()),
          t("ddd", Status.NORMAL, 8, build10.getBuildId()));

    check("build:(item:(id:" + build10.getBuildId() + "),item:(id:" + build20.getBuildId() + "))", TEST_WITH_BUILD_MATCHER,
          t("aaa", Status.FAILURE, 1, build10.getBuildId()),
          t("bbb", Status.NORMAL, 3, build10.getBuildId()),
          t("ccc", Status.FAILURE, 4, build10.getBuildId()),
          t("ddd", Status.NORMAL, 8, build10.getBuildId()),
          t("xxx", Status.NORMAL, 1, build20.getBuildId()),
          t("aaa", Status.NORMAL, 2, build20.getBuildId()));
  }

  private static final Matcher<TestRunData, STestRun> TEST_MATCHER = new Matcher<TestRunData, STestRun>() {
    @Override
    public boolean matches(@NotNull final TestRunData data, @NotNull final STestRun sTestRun) {
      return data.testName.equals(sTestRun.getTest().getName().getAsString()) &&
             data.status.equals(sTestRun.getStatus()) &&
             data.orderId == sTestRun.getOrderId();
    }
  };

  private static TestRunData t(final String testName, final Status status, final int orderId) {
    return new TestRunData(testName, status, orderId);
  }

  private static class TestRunData {
    protected final String testName;
    protected final Status status;
    protected final int orderId;

    private TestRunData(final String testName, final Status status, final int orderId) {
      this.testName = testName;
      this.status = status;
      this.orderId = orderId;
    }

    @Override
    public String toString() {
      return "{" + testName + ", " + status.getText() + ", " + orderId + "}";
    }
  }

  private static final Matcher<TestRunDataWithBuild, STestRun> TEST_WITH_BUILD_MATCHER = new Matcher<TestRunDataWithBuild, STestRun>() {
    @Override
    public boolean matches(@NotNull final TestRunDataWithBuild data, @NotNull final STestRun sTestRun) {
      return TEST_MATCHER.matches(data, sTestRun) && sTestRun.getBuildId() == data.buildId;
    }
  };

  private static TestRunDataWithBuild t(final String testName, final Status status, final int orderId, final long buildId) {
    return new TestRunDataWithBuild(testName, status, orderId, buildId);
  }

  private static class TestRunDataWithBuild extends TestRunData {
    private final long buildId;

    private TestRunDataWithBuild(final String testName, final Status status, final int orderId, final long buildId) {
      super(testName, status, orderId);
      this.buildId = buildId;
    }

    @Override
    public String toString() {
      return "{" + testName + ", " + status.getText() + ", " + orderId + ", " + buildId + "}";
    }
  }
}
