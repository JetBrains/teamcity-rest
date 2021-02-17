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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.messages.TestMetadata;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityFacade;
import jetbrains.buildServer.responsibility.impl.TestNameResponsibilityEntryImpl;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.model.problem.TypedValue;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.StandardProperties;
import jetbrains.buildServer.users.User;
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
  public void testByTestName() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("bbb", true)
                                          .withTest("(bbb)", true)
                                          .withTest("((bbb))", true)
                                          .withTest("ccc(ddd)", true)
                                          .withTest("ccc(ddd)", true)
                                          .withTest("(aaa(bbb))", true)
                                          .withTest("(aaa)(bbb)", true)
                                          .withTest("aaa:bbb", true)
                                          .withTest("(aaa:bbb)", true)
                                          .withTest("((::,,", true)
                                          .withTest("::,,", true)
                                          .withTest("(::,,)", true)
                                          .withTest("((::,,))", true)
                                          .finish();

    int idx = 1;
    check("build:(id:" + build10.getBuildId() + "),expandInvocations:true", TEST_MATCHER,
          t("bbb", Status.NORMAL, idx++),
          t("(bbb)", Status.NORMAL, idx++),
          t("((bbb))", Status.NORMAL, idx++),
          t("ccc(ddd)", Status.NORMAL, idx++),
          t("ccc(ddd)", Status.NORMAL, idx++),
          t("(aaa(bbb))", Status.NORMAL, idx++),
          t("(aaa)(bbb)", Status.NORMAL, idx++),
          t("aaa:bbb", Status.NORMAL, idx++),
          t("(aaa:bbb)", Status.NORMAL, idx++),
          t("((::,,", Status.NORMAL, idx++),
          t("::,,", Status.NORMAL, idx++),
          t("(::,,)", Status.NORMAL, idx++),
          t("((::,,))", Status.NORMAL, idx++)
    );

    Checker test = new Checker<TestRunData, String>(nameDimension -> "build:(id:" + build10.getBuildId() + "),expandInvocations:true,test:(name:" + nameDimension + ")",
                                                    testName -> t(testName, Status.NORMAL, null),
                                                    TEST_MATCHER);

    test.check("bbb", "bbb");
    test.check("(bbb)", "bbb");
    test.check("((bbb))", "(bbb)");
    test.check("(((bbb)))", "((bbb))");
    check("build:(id:" + build10.getBuildId() + "),expandInvocations:true,test:(name:" + "ccc(ddd)" + ")", TEST_MATCHER,
          t("ccc(ddd)", Status.NORMAL, null), t("ccc(ddd)", Status.NORMAL, null));
    check("build:(id:" + build10.getBuildId() + "),expandInvocations:true,test:(name:" + "(ccc(ddd))" + ")", TEST_MATCHER,
          t("ccc(ddd)", Status.NORMAL, null), t("ccc(ddd)", Status.NORMAL, null));
    test.check("((ccc(ddd)))");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "build:(id:" + build10.getBuildId() + "),expandInvocations:true,test:(name:" + "(aaa)(bbb)" + ")");
    test.check("((aaa)(bbb))", "(aaa)(bbb)");
    test.check("((aaa)(bbb))", "(aaa)(bbb)");
    test.check("aaa:bbb", "aaa:bbb");
    test.check("(aaa:bbb)", "aaa:bbb");
    test.check("((aaa:bbb))", "(aaa:bbb)");
    test.check("(((aaa:bbb)))");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "build:(id:" + build10.getBuildId() + "),expandInvocations:true,test:(name:" + "((::,," + ")");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "build:(id:" + build10.getBuildId() + "),expandInvocations:true,test:(name:" + "::,," + ")");
    test.check("(::,,)", "::,,");
    test.check("((::,,))", "(::,,)");
    test.check("(((::,,)))", "((::,,))");

    test.check("($base64:" + new String(Base64.getUrlEncoder().encode("::,,".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8) + ")", "::,,");
    test.check("($base64:" + new String(Base64.getUrlEncoder().encode("((::,,".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8) + ")", "((::,,");
  }

  @Test
  public void testByTestNameCondition() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("aaabbb", true)
                                          .withTest("aaa", true)
                                          .withTest("AAA", true)
                                          .withTest("bbb", true)
                                          .withTest("(aaabbb)", true)
                                          .finish();

    int idx = 1;
    check("build:(id:" + build10.getBuildId() + "),expandInvocations:true", TEST_MATCHER,
          t("aaabbb", Status.NORMAL, idx++),
          t("aaa", Status.NORMAL, idx++),
          t("AAA", Status.NORMAL, idx++),
          t("bbb", Status.NORMAL, idx++),
          t("(aaabbb)", Status.NORMAL, idx++)
    );

    Checker test = new Checker<TestRunData, String>(nameDimension -> "build:(id:" + build10.getBuildId() + "),test:(build:(id:" + build10.getBuildId() + "),name:" + nameDimension + ")",
                                                    testName -> t(testName, Status.NORMAL, null),
                                                    TEST_MATCHER);

    test.check("aaa", "aaa");
    test.check("aa");
    test.check("(value:aaa)", "aaa");
    test.check("(value:aaa,ignoreCase:true)", "aaa", "AAA");
    test.check("(value:aaa,matchType:starts-with)", "aaabbb", "aaa");
    test.check("(value:ab,matchType:contains)", "aaabbb", "(aaabbb)");
  }

  @Test
  public void testByTestOccurrenceNameCondition() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("aaabbb", true)
                                          .withTest("aaa", true)
                                          .withTest("AAA", true)
                                          .withTest("bbb", true)
                                          .withTest("(aaabbb)", true)
                                          .finish();

    int idx = 1;
    check("build:(id:" + build10.getBuildId() + "),expandInvocations:true", TEST_MATCHER,
          t("aaabbb", Status.NORMAL, idx++),
          t("aaa", Status.NORMAL, idx++),
          t("AAA", Status.NORMAL, idx++),
          t("bbb", Status.NORMAL, idx++),
          t("(aaabbb)", Status.NORMAL, idx++)
    );

    Checker test = new Checker<TestRunData, String>(nameDimension -> "build:(id:" + build10.getBuildId() + "),name:" + nameDimension,
                                                    testName -> t(testName, Status.NORMAL, null),
                                                    TEST_MATCHER);

    test.check("aaa", "aaa");
    test.check("aa");
    test.check("(value:aaa)", "aaa");
    test.check("(value:aaa,ignoreCase:true)", "aaa", "AAA");
    test.check("(value:aaa,matchType:starts-with)", "aaabbb", "aaa");
    test.check("(value:ab,matchType:contains)", "aaabbb", "(aaabbb)");
    test.check("(value:(aa),matchType:contains)", "aaabbb", "aaa", "(aaabbb)");
  }

  @Test
  public void testTestNameDetails() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("com.jetbrains.teamcity.MyClass.method1", false)
                                          .withTest("com.jetbrains.teamcity.MyClass.method2", true)
                                          .finish();

    check("build:(id:" + build10.getBuildId() + ")", TEST_MATCHER,
          t("com.jetbrains.teamcity.MyClass.method1", Status.FAILURE, 1),
          t("com.jetbrains.teamcity.MyClass.method2", Status.NORMAL, 2));

    STestRun method1 = build10.getFullStatistics().getAllTests().get(0);
    STestRun method2 = build10.getFullStatistics().getAllTests().get(1);

    {
      TestOccurrence testOccurrence = new TestOccurrence(method1, getBeanContext(myServer), new Fields("test(id,name)"));
      assertNull( testOccurrence.getTest().getParsedTestName());  //if we didn't ask for parsed test name - the object will be not filled
    }

    {
      TestOccurrence testOccurrence = new TestOccurrence(method1, getBeanContext(myServer), new Fields("test(id,name,parsedTestName)"));
      assertNotNull( testOccurrence.getTest().getParsedTestName());
      assertEquals("MyClass", testOccurrence.getTest().getParsedTestName().testClass);
      assertEquals("method1", testOccurrence.getTest().getParsedTestName().testMethodName);
    }


    {
      TestOccurrence testOccurrence = new TestOccurrence(method2, getBeanContext(myServer), new Fields("test(id,name,parsedTestName)"));
      assertNotNull( testOccurrence.getTest().getParsedTestName());
      assertEquals("MyClass", testOccurrence.getTest().getParsedTestName().testClass);
      assertEquals("method2", testOccurrence.getTest().getParsedTestName().testMethodName);
    }
  }

  @Test
  public void testBranchFiltering() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    BuildBuilder.TestData test10, test20, test30, test40, test50, test60;
    final SFinishedBuild build10 = build().in(buildType).withTest(test10 = BuildBuilder.TestData.test("test").duration(10)).finish();
    final SFinishedBuild build20 = build().in(buildType).withBranch("branch1").withTest(test20 = BuildBuilder.TestData.test("test").duration(20)).finish();
    final SFinishedBuild build30 = build().in(buildType).withDefaultBranch().withTest(test30 = BuildBuilder.TestData.test("test").duration(30)).finish();
    final SFinishedBuild build40 = build().in(buildType).personalForUser("user").withTest(test40 = BuildBuilder.TestData.test("test").duration(40)).finish();
    final SFinishedBuild build50 = build().in(buildType).withTest(test50 = BuildBuilder.TestData.test("test").duration(50)).cancel(null);
    final SFinishedBuild build60 = build().in(buildType).withBranch("branch3:(a)").withTest(test60 = BuildBuilder.TestData.test("test").duration(60)).finish();

    Matcher<BuildBuilder.TestData, STestRun> matcher = (testData, sTestRun) -> testData.duration == sTestRun.getDuration();
    check("buildType:(id:" + buildType.getExternalId() + "),test:(name:test)", matcher, test60, test50, test30, test20, test10);

    //comparing the branch filtering with behavior while filtering builds
    String locatorPartTests__ = "buildType:(id:" + buildType.getExternalId() + "),test:(name:test),";
    String locatorPartBuilds = "buildType:(id:" + buildType.getExternalId() + "),build:(defaultFilter:false,personal:false,buildType:(id:" + buildType.getExternalId() + "),";

    check(locatorPartTests__ + "branch:(default:true)", matcher, test50, test30, test10);
    check(locatorPartBuilds + "branch:(default:true))", matcher, test50, test30, test10);

    check(locatorPartTests__ + "branch:(<default>)", matcher); //the build is not branched in this test
    check(locatorPartBuilds + "branch:(<default>))", matcher);

    check(locatorPartTests__ + "branch:(branch1)", matcher, test20);
    check(locatorPartBuilds + "branch:(branch1))", matcher, test20);

    check(locatorPartTests__ + "branch:(Branch1)", matcher, test20);
    check(locatorPartBuilds + "branch:(Branch1))", matcher, test20);

    check(locatorPartTests__ + "branch:(name:(branch1))", matcher, test20);
    check(locatorPartBuilds + "branch:(name:(branch1)))", matcher, test20);

    check(locatorPartTests__ + "branch:(name:Branch1,unknown:false)", matcher);
    checkExceptionOnItemsSearch(jetbrains.buildServer.server.rest.errors.BadRequestException.class, "buildType:(id:" + buildType.getExternalId() + "),build:(buildType:(id:" + buildType.getExternalId() + "),branch:(name:Branch1,unknown:false))");

    check(locatorPartTests__ + "branch:(branch3:(a))", matcher, test60);
    checkExceptionOnItemsSearch(jetbrains.buildServer.server.rest.errors.BadRequestException.class,"buildType:(id:" + buildType.getExternalId() + "),build:(buildType:(id:" + buildType.getExternalId() + "),branch:(branch3:(a)))");

    check(locatorPartTests__ + "branch:missing", matcher);
    check("buildType:(id:" + buildType.getExternalId() + "),build:(buildType:(id:" + buildType.getExternalId() + "),branch:missing)", matcher);
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

    STestRun testRun = getFinder().getItem("build:(id:" + build10.getBuildId() + ")");
    check("build:(id:" + build10.getBuildId() + "),id:"+ testRun.getTestRunId() + "", TEST_MATCHER,
          t(testRun.getTest().getName().toString(), testRun.getStatus(), testRun.getOrderId()));

    check("id:404,build:(id:" + build10.getBuildId() + ")", TEST_MATCHER);
    check("id:404,build:(id:" + build10.getBuildId() + "),expandInvocations:true", TEST_MATCHER);
    check("id:404,build:(id:404)", TEST_MATCHER); //documenting current behavior: no error, can be useful for "get test runs from such builds
    check("id:404,build:(id:404),expandInvocations:true", TEST_MATCHER);

    check("build:(id:" + build10.getBuildId() + "),test:(name:missing)", TEST_MATCHER); //documenting current behavior: no error, can be useful for "get test runs for the currently investigated tests, or alike
    check("test:(name:missing)", TEST_MATCHER); //documenting current behavior: no error
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
    checkExceptionOnItemsSearch(BadRequestException.class, "build:(id:" + bId + "),status:AAA");

    check("build:(id:" + bId + "),ignored:true", TEST_WITH_BUILD_MATCHER,
          ccc);
    check("build:(id:" + bId + "),ignored:false", TEST_WITH_BUILD_MATCHER,
          aaa,
          bbb);

    check("build:(id:" + bId + "),ignored:any", TEST_WITH_BUILD_MATCHER,
          aaa,
          bbb,
          ccc);
  }

  @Test
  public void testTestRunFromPersonalBuild() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    SUser user = myFixture.createUserAccount("andrey");

    final SFinishedBuild personalBuild = build().in(buildType)
                                          .personalForUser(user.getUsername())
                                          .withTest(BuildBuilder.TestData.test("aaa"))
                                          .finish();

    final SFinishedBuild regularBuild = build().in(buildType)
                                                .withTest(BuildBuilder.TestData.test("aaa"))
                                                .finish();


    TestRunDataWithBuild personalRun = t("aaa", Status.NORMAL, 1, personalBuild.getBuildId());
    TestRunDataWithBuild regularRun  = t("aaa", Status.NORMAL, 2, regularBuild.getBuildId());

    check("test:(name:aaa)", TEST_MATCHER, regularRun);
    check("test:(name:aaa),includePersonal:true,personalForUser:" + user.getId(), TEST_MATCHER, personalRun, regularRun);
    check("test:(name:aaa),includePersonal:false", TEST_MATCHER, regularRun);
  }

  @Test
  public void testNoTestRunsFromOthersPersonalBuild() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    SUser user = myFixture.createUserAccount("andrey");

    final SFinishedBuild personalBuild = build().in(buildType)
                                                .personalForUser(user.getUsername())
                                                .withTest(BuildBuilder.TestData.test("aaa"))
                                                .finish();

    final SFinishedBuild regularBuild = build().in(buildType)
                                               .withTest(BuildBuilder.TestData.test("aaa"))
                                               .finish();

    TestRunDataWithBuild regularRun  = t("aaa", Status.NORMAL, 2, regularBuild.getBuildId());

    int anotherUserId = 999;
    check("test:(name:aaa)", TEST_MATCHER, regularRun);
    check("test:(name:aaa),includePersonal:true,personalForUser:" + anotherUserId, TEST_MATCHER, regularRun);
    check("test:(name:aaa),includePersonal:false", TEST_MATCHER, regularRun);
  }

  @Test
  public void testTestFromOtherUsersPersonalBuildByBuildLocator() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    SUser user = myFixture.createUserAccount("andrey");

    final SFinishedBuild personalBuild = build().in(buildType)
                                                .personalForUser(user.getUsername())
                                                .withTest(BuildBuilder.TestData.test("aaa"))
                                                .finish();


    TestRunDataWithBuild personalRun = t("aaa", Status.NORMAL, 1, personalBuild.getBuildId());

    check(String.format("build:(id:%d)", personalBuild.getBuildId()), TEST_MATCHER, personalRun);
  }

  @Test
  public void testTestFromOtherUsersPersonalBuildTestRunId() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    SUser user = myFixture.createUserAccount("andrey");

    final SFinishedBuild personalBuild = build().in(buildType)
                                                .personalForUser(user.getUsername())
                                                .withTest(BuildBuilder.TestData.test("aaa"))
                                                .finish();

    int testRunId = personalBuild.getBuildStatistics(BuildStatisticsOptions.ALL_TESTS_NO_DETAILS).getAllTests().get(0).getTestRunId();

    TestRunDataWithBuild personalRun = t("aaa", Status.NORMAL, 1, personalBuild.getBuildId());

    check(String.format("id:%d,build:%d", testRunId, personalBuild.getBuildId()), TEST_MATCHER, personalRun);
  }

  @Test
  public void testDoesNotReturnOtherUsersPersonalBuilds() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    SUser user = myFixture.createUserAccount("andrey");

    final SFinishedBuild personalBuild = build().in(buildType)
                                                .personalForUser(user.getUsername())
                                                .withTest(BuildBuilder.TestData.test("aaa"))
                                                .finish();

    STestRun testInPersonalBuild = personalBuild.getFullStatistics().getAllTests().get(0);

    TestRunDataWithBuild personalRun = t("aaa", Status.NORMAL, 1, personalBuild.getBuildId());

    check(String.format("id:%d,build:%d", testInPersonalBuild.getTestRunId(), personalBuild.getBuildId()), TEST_MATCHER, personalRun);
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
      assertEquals(Integer.valueOf(4), testOccurrence.getInvocations().getCount());
      assertEquals(Integer.valueOf(1), testOccurrence.getInvocations().getFailed());
      assertNotNull(testOccurrence.getInvocations().items);
      assertEquals(4, testOccurrence.getInvocations().items.size());
      assertEquals("SUCCESS", testOccurrence.getInvocations().items.get(0).getStatus());
      assertEquals("FAILURE", testOccurrence.getInvocations().items.get(2).getStatus());
    }
    {
      TestOccurrence testOccurrence = new TestOccurrence(testRunAAA, getBeanContext(myServer), new Fields("invocations($long,$locator(status:FAILURE))"));
      assertEquals(Integer.valueOf(1), testOccurrence.getInvocations().getCount());
      assertNotNull(testOccurrence.getInvocations().items);
      assertEquals(1, testOccurrence.getInvocations().items.size());
      assertEquals("FAILURE", testOccurrence.getInvocations().items.get(0).getStatus());
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
    assertEquals(Integer.valueOf(2), testOccurrence.getMetadata().count);
    final List<TypedValue> items = testOccurrence.getMetadata().typedValues;
    assertEquals(items.size(), 2);
    System.out.println("items = " + StringUtil.join("\n", items));
    assertTrue(items.contains(new TypedValue("some key", "link", "value", Fields.LONG)));
    assertTrue(items.contains(new TypedValue("some key3", "number", String.valueOf(44f), Fields.LONG)));
  }

  @Test
  public void testShortStatisticsIsEnoughWhenOnlyLegacyCountersRequested() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa"))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed())
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason"))
                                          .finish();

    assertNotNull(
      "Retrieving counters should be done via short statistics.",
      myTestOccurrenceFinder.getShortStatisticsIfEnough("build:" + build.getBuildId(), "count,passed,failed,newFailed,ignored,muted")
    );
  }

  @Test
  public void testShortStatisticsIsNotEnoughWhenNoBuildInRequest() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa"))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed())
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason"))
                                          .finish();

    assertNull(
      "ShortStatistics can be used only if the build is specified.",
      myTestOccurrenceFinder.getShortStatisticsIfEnough("test:(status:failed,affectedProject:project)", "count,passed,failed,newFailed,ignored,muted")
    );
  }

  @Test
  public void testShortStatisticsIsNotEnoughWhenThereIsMoreThanOneBuildInRequest() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build1 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa"))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed())
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason"))
                                          .finish();

    final SFinishedBuild build2 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa"))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed())
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason"))
                                          .finish();


    assertNull(
      "ShortStatistics can be used only if there is a single build.",
      myTestOccurrenceFinder.getShortStatisticsIfEnough("build:(status:failed)", "count,passed,failed,newFailed,ignored,muted")
    );
  }

  @Test
  public void testShortStatisticsIsEnoughWhenTestCountersRequested() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa"))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed())
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason"))
                                          .finish();

    assertNotNull(
      "Retrieving counters should be done via short statistics.",
      myTestOccurrenceFinder.getShortStatisticsIfEnough("build:" + build.getBuildId(), "testCounters(all,success,failed,newFailed,ignored,muted)")
    );
  }

  @Test
  public void testShortStatisticsIsNotEnoughWhenTestCountersWithDurationRequested() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa"))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed())
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason"))
                                          .finish();

    assertNull(
      "Can't retrieve duration without detching all tests.",
      myTestOccurrenceFinder.getShortStatisticsIfEnough("build:" + build.getBuildId(), "testCounters(all,duration)")
    );
  }

  private static final Matcher<TestRunData, STestRun> TEST_MATCHER = new Matcher<TestRunData, STestRun>() {
    @Override
    public boolean matches(@NotNull final TestRunData data, @NotNull final STestRun sTestRun) {
      return data.testName.equals(sTestRun.getTest().getName().getAsString()) &&
             (data.status == null || data.status.equals(sTestRun.getStatus())) &&
             //data.orderId == sTestRun.getOrderId() && //https://youtrack.jetbrains.com/issue/TW-62277 currently orderId depends on the way the tests are provided and the cache state, so it should not be relied upon
             (Status.UNKNOWN.equals(data.status) == sTestRun.isIgnored());
    }
  };

  private static TestRunData t(final String testName, @Nullable final Status status, @Nullable final Integer orderId) {
    return new TestRunData(testName, status, orderId);
  }

  private static class TestRunData {
    protected final String testName;
    protected final Status status;
    protected final Integer orderId;

    private TestRunData(final String testName, final Status status, final Integer orderId) {
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
      return "{" + testName + ", " + status.getText() + "(" + status.getPriority() + "), order: " + orderId + ", buildId: " + buildId + "}";
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
