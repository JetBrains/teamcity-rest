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

import com.google.common.base.Objects;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.messages.TestMetadata;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityFacade;
import jetbrains.buildServer.responsibility.impl.TestNameResponsibilityEntryImpl;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.model.problem.TypedValue;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.TestName2Index;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    checkExceptionOnItemSearch(BadRequestException.class, "id:" + testRunId);
    checkExceptionOnItemsSearch(BadRequestException.class, "id:" + testRunId);
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
  public void testSameTestInDifferentBuilds() throws Exception {
    final BuildTypeImpl buildType1 = registerBuildType("buildConf1", "project1");
    final BuildTypeImpl buildType2 = registerBuildType("buildConf2", "project2");
    final SFinishedBuild build10 = build().in(buildType1).withTest("aaa", false).finish();
    final SFinishedBuild build20 = build().in(buildType2).withTest("aaa", false).finish();

    check("currentlyFailing:true", TEST_MATCHER, t("aaa", Status.FAILURE, 1), t("aaa", Status.FAILURE, 1));
    check("currentlyFailing:true,buildType:(id:" + buildType1.getExternalId() + ")", TEST_MATCHER, t("aaa", Status.FAILURE, 1));

    STestRun testRun1 = getFinder().getItems("currentlyFailing:true").myEntries.get(0);
    STestRun testRun2 = getFinder().getItems("currentlyFailing:true").myEntries.get(1);
    assertEquals(testRun1.getBuildId(), build10.getBuildId());
    assertEquals(testRun2.getBuildId(), build20.getBuildId());

    check("currentlyFailing:true,currentlyInvestigated:false", TEST_MATCHER, t("aaa", Status.FAILURE, 1), t("aaa", Status.FAILURE, 1));


    long nameId = myFixture.getSingletonService(TestName2Index.class).getOrSaveTestNameId("aaa");
    SUser user = createUser("user");
    ProjectEx project = myServer.getProjectManager().getRootProject().findProjectByName("project1");
    TestNameResponsibilityEntryImpl testNameResponsibilityEntry = new TestNameResponsibilityEntryImpl(new TestName("aaa"), nameId, ResponsibilityEntry.State.TAKEN, user, user,
                                                                                                      new Date(), "Please, fix",
                                                                                                      project,
                                                                                                      ResponsibilityEntry.RemoveMethod.MANUALLY);

    myFixture.getSingletonService(TestNameResponsibilityFacade.class).setTestNameResponsibility(new TestName("aaa"), project.getProjectId(), testNameResponsibilityEntry);

    check("currentlyFailing:true,currentlyInvestigated:false", TEST_MATCHER, t("aaa", Status.FAILURE, 1));
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

    check("build:(id:" + build10.getBuildId() + "),invocations:(search:(status:FAILURE))", TEST_MATCHER,
          t("aaa", Status.FAILURE, 1),
          t("ccc", Status.FAILURE, 4));
    check("build:(id:" + build10.getBuildId() + "),invocations:(search:(count:100),match:(status:SUCCESS))", TEST_MATCHER,
          t("bbb", Status.NORMAL, 3),
          t("ddd", Status.NORMAL, 8));
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

  @Test
  public void testTestStatus() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa"))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed())
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason"))
                                          .finish();

    long bId = build10.getBuildId();
    TestRunDataWithBuild aaa = t("aaa", Status.NORMAL, 1, bId);
    TestRunDataWithBuild bbb = t("bbb", Status.FAILURE, 2, bId);
    TestRunDataWithBuild ccc = t("ccc", Status.UNKNOWN, 3, bId);

    check("build:(id:" + bId + ")", TEST_WITH_BUILD_MATCHER,
          aaa,
          bbb,
          ccc);

    check("build:(id:" + bId + "),status:SUCCESS", TEST_WITH_BUILD_MATCHER,
          aaa);
    check("build:(id:" + bId + "),status:FAILURE", TEST_WITH_BUILD_MATCHER,
          bbb);
    check("build:(id:" + bId + "),status:failure", TEST_WITH_BUILD_MATCHER,
          bbb);
    check("build:(id:" + bId + "),status:unknown", TEST_WITH_BUILD_MATCHER,
          ccc);
    check("build:(id:" + bId + "),ignored:true", TEST_WITH_BUILD_MATCHER,
          ccc);
    check("build:(id:" + bId + "),ignored:false", TEST_WITH_BUILD_MATCHER,
          aaa,
          bbb);
  }

  @Test
  public void testTestOccurrenceEntityInvocations() throws Exception {
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

    STestRun testRunAAA = build10.getFullStatistics().getAllTests().get(0);

    {
      TestOccurrence testOccurrence = new TestOccurrence(testRunAAA, getBeanContext(myServer), new Fields("invocations($long)"));
      assertEquals(Integer.valueOf(4), testOccurrence.invocations.count);
      assertEquals(Integer.valueOf(1), testOccurrence.invocations.failed);
      assertNotNull(testOccurrence.invocations.items);
      assertEquals(4, testOccurrence.invocations.items.size());
      assertEquals("SUCCESS", testOccurrence.invocations.items.get(0).status);
      assertEquals("FAILURE", testOccurrence.invocations.items.get(2).status);
    }
    {
      TestOccurrence testOccurrence = new TestOccurrence(testRunAAA, getBeanContext(myServer), new Fields("invocations($long,$locator(status:FAILURE))"));
      assertEquals(Integer.valueOf(1), testOccurrence.invocations.count);
      assertNotNull(testOccurrence.invocations.items);
      assertEquals(1, testOccurrence.invocations.items.size());
      assertEquals("FAILURE", testOccurrence.invocations.items.get(0).status);
    }
  }

  @Test
  public void testTestOccurrenceEntity_Metadata() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final RunningBuildEx build = startBuild(buildType);
    myFixture.doTestPassed(build, "testName");
    myFixture.doTestMetadata(build,new TestMetadata("testName", "some key", "link", "value"));
    myFixture.doTestMetadata(build,new TestMetadata("testName", "some key3", "number", new BigDecimal("44")));

    STestRun testRun = finishBuild().getFullStatistics().getAllTests().get(0);

    TestOccurrence testOccurrence = new TestOccurrence(testRun, getBeanContext(myServer), new Fields("metadata"));
    assertEquals(Integer.valueOf(2), testOccurrence.metadata.count);
    final List<TypedValue> items = testOccurrence.metadata.typedValues;
    assertEquals(items.size(), 2);
    System.out.println("items = " + StringUtil.join("\n", items));
    assertTrue(items.contains(new TypedValue("some key", "link", "value", Fields.LONG)));
    assertTrue(items.contains(new TypedValue("some key3", "number", String.valueOf(44f), Fields.LONG)));
  }

  private static final Matcher<TestRunData, STestRun> TEST_MATCHER = new Matcher<TestRunData, STestRun>() {
    @Override
    public boolean matches(@NotNull final TestRunData data, @NotNull final STestRun sTestRun) {
      return data.testName.equals(sTestRun.getTest().getName().getAsString()) &&
             data.status.equals(sTestRun.getStatus()) &&
             data.orderId == sTestRun.getOrderId() &&
             (Status.UNKNOWN.equals(data.status) == sTestRun.isIgnored());
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

  public void check(@Nullable final String locator, STestRun... items) {
    //using getEqualsMatcher() sometimes fails
    check(locator, new Matcher<STestRun, STestRun>() {
      @Override
      public boolean matches(@NotNull final STestRun o, @NotNull final STestRun o2) {
          if (o == o2) return true;
          if (o.getClass() != o2.getClass()) return false;
          return o.getOrderId() == o2.getOrderId() &&
                 o.getTestRunId() == o2.getTestRunId() &&
                 o.getDuration() == o2.getDuration() &&
                 o.getStatus() == o2.getStatus() &&
                 Objects.equal(o.getTest(), o2.getTest()) &&
                 Objects.equal(o.getBuild(),o2.getBuild()) &&
//                 Objects.equal(o.getTestOutputInfo(), o2.getTestOutputInfo()) &&
                 Objects.equal(o.getMuteInfo(), o2.getMuteInfo());
      }
    }, getFinder(), items);
  }
}
