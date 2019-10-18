/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.request.TestOccurrenceRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.MultiTestRun;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.TestRunEx;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.serverSide.BuildStatisticsOptions.ALL_TESTS_NO_DETAILS;

/**
 * @author Yegor.Yarko
 */
@SuppressWarnings({"WeakerAccess"})
@XmlRootElement(name = "testOccurrence")
@XmlType(name = "testOccurrence", propOrder = {"id", "name", "status", "ignored", "duration", "runOrder"/*experimental*/, "muted", "currentlyMuted", "currentlyInvestigated",
  "href",
  "ignoreDetails", "details", "test", "mute", "build", "firstFailed", "nextFixed", "invocations", "metadata"})
public class TestOccurrence {
  @NotNull private BeanContext myBeanContext;
  @NotNull private Fields myFields;
  @NotNull private STestRun myTestRun;
  private TestOccurrenceFinder myTestOccurrenceFinder;

  public TestOccurrence() {
  }

  public TestOccurrence(final @NotNull STestRun testRun, final @NotNull BeanContext beanContext, @NotNull final Fields fields) {
    myTestRun = testRun;
    myBeanContext = beanContext;
    myFields = fields;

    myTestOccurrenceFinder = myBeanContext.getSingletonService(TestOccurrenceFinder.class);
  }

  @XmlAttribute
  public String getId() {
    //STestRun.getTestRunId() can be the same between different builds
    return ValueWithDefault.decideDefault(myFields.isIncluded("id"), TestOccurrenceFinder.getTestRunLocator(myTestRun));
  }

  @XmlAttribute
  public String getName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("name"), myTestRun.getTest().getName().getAsString());
  }

  /**
   * Experimental and to be dropped as the number is not stable: it actually depends on the set of queried tests
   */
  @XmlAttribute
  public String getRunOrder() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("runOrder", false, false), String.valueOf(myTestRun.getOrderId()));
  }

  @XmlAttribute
  public String getStatus() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("status"), myTestRun.getStatus().getText());
  }

  @XmlAttribute
  public Boolean getIgnored() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("ignored"), myTestRun.isIgnored());
  }

  @XmlAttribute
  public String getHref() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("href"), myBeanContext.getApiUrlBuilder().transformRelativePath(TestOccurrenceRequest.getHref(myTestRun)));
  }

  @XmlAttribute
  public Integer getDuration() { //test run duration in milliseconds
    return ValueWithDefault.decideDefault(myFields.isIncluded("duration"), myTestRun.getDuration());
  }


  /**
   * Experimental! "true" is the test occurrence was muted, not present otherwise
   */
  @XmlAttribute
  public Boolean getMuted() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("muted"), myTestRun.getMuteInfo() != null);
  }

  /**
   * Experimental! "true" is the test has investigation at the moment of request, not present otherwise
   */
  @XmlAttribute
  public Boolean getCurrentlyInvestigated() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("currentlyInvestigated"), () -> myTestOccurrenceFinder.isCurrentlyInvestigated(myTestRun));
  }

  /**
   * Experimental! "true" is the test is muted at the moment of request, not present otherwise
   */
  @XmlAttribute
  public Boolean getCurrentlyMuted() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("currentlyMuted"), () -> myTestOccurrenceFinder.isCurrentlyMuted(myTestRun));
  }

  /**
   * Experimental
   */
  @XmlAttribute
  public String getLogAnchor() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("logAnchor", false, false), () -> String.valueOf(myTestRun.getTestRunId()));
  }

  @XmlElement
  public String getIgnoreDetails() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("ignoreDetails", false), () -> myTestRun.getIgnoreComment());
  }

  @XmlElement
  public String getDetails() { //todo: consider using CDATA output her
    //consider providing separate stacktrace, stdout and stderr, see implementation of jetbrains.buildServer.serverSide.stat.TestFullTextBuilderImpl.getFullText()
    return ValueWithDefault.decideDefault(myFields.isIncluded("details", false), () -> myTestRun.getFullText());
  }

  @XmlElement
  public Test getTest() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("test", false), () -> new Test(myTestRun.getTest(), myBeanContext, myFields.getNestedField("test")));
  }

  @XmlElement
  public Mute getMute() {
    return Util.resolveNull(myTestRun.getMuteInfo(),
                            (mi) -> ValueWithDefault.decideDefault(myFields.isIncluded("mute", false),
                                                                   () -> new Mute(mi, myFields.getNestedField("mute", Fields.NONE, Fields.LONG), myBeanContext)));
  }

  @XmlElement
  public Build getBuild() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("build", false), () -> new Build(myTestRun.getBuild(), myFields.getNestedField("build"), myBeanContext));
  }

  @XmlElement
  public TestOccurrence getFirstFailed() {
    //todo: use FirstFailedInFixedInCalculator#calculateFFIData instead???
    return ValueWithDefault.decideDefault(myFields.isIncluded("firstFailed", false),
                                          () -> Util.resolveNull(myTestRun.getFirstFailed(),
                                                                 (ff) -> new TestOccurrence(getFailedTestRun(ff, myTestRun), myBeanContext, myFields.getNestedField("firstFailed"))));
  }

  @XmlElement
  public TestOccurrence getNextFixed() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("nextFixed", false),
                                          () -> Util.resolveNull(myTestRun.getFixedIn(),
                                                                 (fi) -> new TestOccurrence(getSuccessfulTestRun(fi, myTestRun), myBeanContext, myFields.getNestedField("firstFailed"))));
  }

  @XmlElement
  public TestOccurrences getInvocations() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("invocations", false, false), () -> {
      if (!(myTestRun instanceof MultiTestRun)) return null;
      Fields nestedField = myFields.getNestedField("invocations");
      String invocationsLocator = Locator.merge(TestOccurrenceFinder.getTestInvocationsLocator(myTestRun), nestedField.getLocator());
      return new TestOccurrences(myTestOccurrenceFinder.getItems(invocationsLocator).myEntries, myTestRun.getInvocationCount(), null, myTestRun.getFailedInvocationCount(), null,
                                 null, null, null, null, nestedField, myBeanContext);
    });
  }

  /**
   * Experimental! Exposes test run metadata
   */
  @XmlElement
  public TestRunMetadata getMetadata() {
    return ValueWithDefault.decideDefaultIgnoringAccessDenied(myFields.isIncluded("metadata", false, false),
                                                              () -> new TestRunMetadata(((TestRunEx)myTestRun).getMetadata(),
                                                                                        myFields.getNestedField("metadata", Fields.SHORT, Fields.LONG)));
  }

  @NotNull
  private STestRun getFailedTestRun(@NotNull final SBuild build, @NotNull final STestRun sampleTestRun) {
    //this is different from getSuccessfulTestRun to be more performant
    long testNameId = sampleTestRun.getTest().getTestNameId();
    return build.getShortStatistics().getFailedTests().stream().filter(t -> t.getTest().getTestNameId() == testNameId).findFirst()
                          .orElseThrow(() -> new IllegalArgumentException("Cannot find test with name \"" + sampleTestRun.getFullText() + "\" in build with id " + build.getBuildId()));
  }

  /**
   * For not successful test use getFailedTestRun for performance reasons.
   */
  @NotNull
  private STestRun getSuccessfulTestRun(@NotNull final SBuild build, @NotNull final STestRun sampleTestRun) {
    final STestRun testRun = build.getBuildStatistics(ALL_TESTS_NO_DETAILS).findTestByTestNameId(sampleTestRun.getTest().getTestNameId());
    if (testRun == null) {
      throw new IllegalArgumentException("Cannot find test with name \"" + sampleTestRun.getFullText() + "\" in build with id " + build.getBuildId());
    }
    return testRun;
  }

  @NotNull
  public static Status getStatusFromPosted(@NotNull String statusText) {
    Status result = Status.getStatus(statusText.toUpperCase());
    if (result == null) {
      throw new BadRequestException("Unsupported value '" + statusText + "'. Supported values are: " + Status.NORMAL.getText().toLowerCase() + ", " + Status.FAILURE.getText().toLowerCase());
    }
    return result;
  }
}
