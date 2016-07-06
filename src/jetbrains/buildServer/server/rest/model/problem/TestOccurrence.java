/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.problem;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.request.TestOccurrenceRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.CompositeTestRun;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.serverSide.BuildStatisticsOptions.ALL_TESTS_NO_DETAILS;

/**
 * @author Yegor.Yarko
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "testOccurrence")
@XmlType(name = "testOccurrence", propOrder = {"id", "name", "status", "ignored", "duration", "runOrder"/*experimental*/, "muted", "currentlyMuted", "currentlyInvestigated", "href",
  "ignoreDetails", "details", "test", "mute", "build", "firstFailed", "nextFixed", "invocations"})
public class TestOccurrence {
  @XmlAttribute public String id;
  @XmlAttribute public String name;
  @XmlAttribute public String runOrder; /*experimental*/
  @XmlAttribute public String status;
  @XmlAttribute public Boolean ignored;
  @XmlAttribute public String href;
  @XmlAttribute public Integer duration;//test run duration in milliseconds

  /**
   * Experimental! "true" is the test occurrence was muted, not present otherwise
   */
  @XmlAttribute public Boolean muted;
  /**
   * Experimental! "true" is the test has investigation at the moment of request, not present otherwise
   */
  @XmlAttribute public Boolean currentlyInvestigated;
  /**
   * Experimental! "true" is the test is muted at the moment of request, not present otherwise
   */
  @XmlAttribute public Boolean currentlyMuted;

  @XmlElement public String ignoreDetails;
  @XmlElement public String details; //todo: consider using CDATA output here

  @XmlElement public Test test;
  @XmlElement public Mute mute;

  @XmlElement public Build build;
  @XmlElement public TestOccurrence firstFailed;
  @XmlElement public TestOccurrence nextFixed;
  @XmlElement public TestOccurrences invocations;

  public TestOccurrence() {
  }

  public TestOccurrence(final @NotNull STestRun testRun, final @NotNull BeanContext beanContext, @NotNull final Fields fields) {
    final STest sTest = testRun.getTest();
    //STestRun.getTestRunId() can be the same between different builds
    id = ValueWithDefault.decideDefault(fields.isIncluded("id"), TestOccurrenceFinder.getTestRunLocator(testRun));

    name = ValueWithDefault.decideDefault(fields.isIncluded("name"), sTest.getName().getAsString());

    status = ValueWithDefault.decideDefault(fields.isIncluded("status"), testRun.getStatus().getText());

    href = ValueWithDefault.decideDefault(fields.isIncluded("href"), beanContext.getApiUrlBuilder().transformRelativePath(TestOccurrenceRequest.getHref(testRun)));

    duration = ValueWithDefault.decideDefault(fields.isIncluded("duration"), testRun.getDuration());

    runOrder = ValueWithDefault.decideDefault(fields.isIncluded("runOrder", false, false), String.valueOf(testRun.getOrderId()));

    ignored = ValueWithDefault.decideDefault(fields.isIncluded("ignored"), testRun.isIgnored());

    final MuteInfo muteInfo = testRun.getMuteInfo();
    muted = ValueWithDefault.decideDefault(fields.isIncluded("muted"), muteInfo != null);

    final TestOccurrenceFinder testOccurrenceFinder = beanContext.getSingletonService(TestOccurrenceFinder.class);
    currentlyInvestigated = ValueWithDefault.decideDefault(fields.isIncluded("currentlyInvestigated"), new ValueWithDefault.Value<Boolean>() {
      public Boolean get() {
        return testOccurrenceFinder.isCurrentlyInvestigated(testRun);
      }
    });
    currentlyMuted = ValueWithDefault.decideDefault(fields.isIncluded("currentlyMuted"), new ValueWithDefault.Value<Boolean>() {
      public Boolean get() {
        return testOccurrenceFinder.isCurrentlyMuted(testRun);
      }
    });

    details = ValueWithDefault.decideDefault(fields.isIncluded("details", false), new ValueWithDefault.Value<String>() {
      public String get() {
        return testRun.getFullText();
      }
    });
    //consider providing separate stacktrace, stdout and stderr, see implementation of jetbrains.buildServer.serverSide.stat.TestFullTextBuilderImpl.getFullText()
    ignoreDetails = ValueWithDefault.decideDefault(fields.isIncluded("ignoreDetails", false), new ValueWithDefault.Value<String>() {
      public String get() {
        return testRun.getIgnoreComment();
      }
    });

    test = ValueWithDefault.decideDefault(fields.isIncluded("test", false), new ValueWithDefault.Value<Test>() {
      public Test get() {
        return new Test(sTest, beanContext, fields.getNestedField("test"));
      }
    });

    mute = muteInfo == null ? null : ValueWithDefault.decideDefault(fields.isIncluded("mute", false), new ValueWithDefault.Value<Mute>() {
      public Mute get() {
        return new Mute(muteInfo, fields.getNestedField("mute", Fields.NONE, Fields.LONG), beanContext);
      }
    });

    build = ValueWithDefault.decideDefault(fields.isIncluded("build", false), new ValueWithDefault.Value<Build>() {
      public Build get() {
        return new Build(testRun.getBuild(), fields.getNestedField("build"), beanContext);
      }
    });

    firstFailed = ValueWithDefault.decideDefault(fields.isIncluded("firstFailed", false), new ValueWithDefault.Value<TestOccurrence>() {
      public TestOccurrence get() {
        final SBuild firstFailedInBuild = testRun.getFirstFailed();
        return firstFailedInBuild == null ? null : new TestOccurrence(getTestRun(firstFailedInBuild, testRun), beanContext, fields.getNestedField("firstFailed"));
      }
    });

    nextFixed = ValueWithDefault.decideDefault(fields.isIncluded("nextFixed", false), new ValueWithDefault.Value<TestOccurrence>() {
      public TestOccurrence get() {
        final SBuild fixedInBuild = testRun.getFixedIn();
        return fixedInBuild == null ? null : new TestOccurrence(getTestRun(fixedInBuild, testRun), beanContext, fields.getNestedField("firstFailed"));
      }
    });

    invocations = ValueWithDefault.decideDefault(fields.isIncluded("invocations", false, false), new ValueWithDefault.Value<TestOccurrences>() {
      public TestOccurrences get() {
        if (!(testRun instanceof CompositeTestRun)) return null;
        Fields nestedField = fields.getNestedField("invocations");
        String invocationsLocator = Locator.merge(TestOccurrenceFinder.getTestInvocationsLocator(testRun), nestedField.getLocator());
        return new TestOccurrences(testOccurrenceFinder.getItems(invocationsLocator).myEntries, testRun.getInvocationCount(), null, testRun.getFailedInvocationCount(),
                                   null, null, null, null, null, nestedField, beanContext);
      }
    });
  }

  @NotNull
  private STestRun getTestRun(@NotNull final SBuild build, @NotNull final STestRun sampleTestRun) {
    final STestRun testRun = build.getBuildStatistics(ALL_TESTS_NO_DETAILS).findTestByTestNameId(sampleTestRun.getTest().getTestNameId());
    if (testRun == null) {
      throw new IllegalArgumentException("Cannot find test with name " + sampleTestRun.getFullText() + " in build " + build);
    }
    return testRun;
  }
}
