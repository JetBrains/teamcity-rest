/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.controllers.fakes.FakeHttpServletRequest;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 * Date: 26/03/2019
 */
public class TestOccurrenceRequestTest extends BaseFinderTest<STestRun> {
  private TestOccurrenceRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = new TestOccurrenceRequest();
    myRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));
  }

  @Test
  public void testTestOccurrenceFields () {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa").duration(76))
                                          .withTest(BuildBuilder.TestData.test("bbb").out("std out").errorOut("str err")
                                                                         .failed("error message", "stacktrace\nline 1\r\nline2").duration(67))
                                          .finish();
    {
      TestOccurrences testOccurrences = myRequest.getTestOccurrences("build:(id:" + build10.getBuildId() + "),status:FAILURE", "**", null, null);

      assertEquals(Integer.valueOf(1), testOccurrences.count);
      assertEquals(1, testOccurrences.items.size());
      TestOccurrence testOccurrence = testOccurrences.items.get(0);
      assertEquals("bbb", testOccurrence.getName());
      assertEquals("1", testOccurrence.getRunOrder()); // "2" should actually be here, but API cannot guarantee preservation of the number when not all tests are retrieved, so documenting the current behavior.
      assertEquals(Integer.valueOf(67), testOccurrence.getDuration());
      assertEquals("FAILURE", testOccurrence.getStatus());
      assertEquals(Boolean.valueOf(false), testOccurrence.getIgnored());
      assertNull(testOccurrence.getIgnoreDetails());
      assertEquals("error message\nstacktrace\nline 1\r\nline2\n------- Stdout: -------\nstd out\n------- Stderr: -------\nstr err", testOccurrence.getDetails());
    }


    final SFinishedBuild build20 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa").duration(76))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed("error message", "stacktrace\nline 1\nline2").duration(67))
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason").out("std\r\nout").duration(67))
                                          .finish();
    {
      TestOccurrences testOccurrences = myRequest.getTestOccurrences("build:(id:" + build20.getBuildId() + "),ignored:true", "**", null, null);

      assertEquals(Integer.valueOf(1), testOccurrences.count);
      assertEquals(1, testOccurrences.items.size());
      TestOccurrence testOccurrence = testOccurrences.items.get(0);
      assertEquals("ccc", testOccurrence.getName());
      assertEquals("3", testOccurrence.getRunOrder());
      assertEquals(Integer.valueOf(0), testOccurrence.getDuration());
      assertEquals("UNKNOWN", testOccurrence.getStatus());
      assertEquals(Boolean.valueOf(true), testOccurrence.getIgnored());
      assertEquals("Ignore reason", testOccurrence.getIgnoreDetails());
      assertNull(testOccurrence.getDetails());
    }

    //checking how ignored and failed test looks like. Just asserting current behavior
    final SFinishedBuild build30 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa").duration(76))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed("error message", "stacktrace\nline 1\nline2").duration(67))
                                          .withTest(BuildBuilder.TestData.test("ccc").failed("error message", "stacktrace\nline 1\nline2").duration(67))
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason"))
                                          .finish();
    {
      TestOccurrences testOccurrences = myRequest.getTestOccurrences("build:(id:" + build30.getBuildId() + "),test:(name:ccc)", "**", null, null);

      assertEquals(Integer.valueOf(1), testOccurrences.count);
      assertEquals(1, testOccurrences.items.size());
      TestOccurrence testOccurrence = testOccurrences.items.get(0);
      assertEquals("ccc", testOccurrence.getName());
      assertEquals("3", testOccurrence.getRunOrder());
      assertEquals(Integer.valueOf(67), testOccurrence.getDuration());
      assertEquals("FAILURE", testOccurrence.getStatus());
      assertEquals(Boolean.valueOf(false), testOccurrence.getIgnored());
      assertEquals("error message\nstacktrace\nline 1\nline2", testOccurrence.getDetails());
    }
  }

  @Test
  public void testWithoutSessionUser() {
    final SFinishedBuild build = build().in(myBuildType).withTest(BuildBuilder.TestData.test("aaa").duration(76)).finish();

    FakeHttpServletRequest mockRequest = new FakeHttpServletRequest();
    mockRequest.setRequestURL("http://test/httpAuth/app/rest/testOccurrences?locator=build:" + build.getBuildId());
    TestOccurrences testOccurrences = myRequest.getTestOccurrences("build:" + build.getBuildId(),"",null, mockRequest);

    assertEquals(new Integer(1), testOccurrences.count);
  }
}
