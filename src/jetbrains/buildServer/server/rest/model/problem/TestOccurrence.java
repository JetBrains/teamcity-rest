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

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import io.swagger.annotations.ApiModelProperty;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.server.rest.data.FilterItemProcessor;
import jetbrains.buildServer.server.rest.data.PagingItemFilter;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.request.TestOccurrenceRequest;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldStrategy;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldRule;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldStrategySupported;
import jetbrains.buildServer.server.rest.util.fieldInclusion.FieldInclusionChecker;
import jetbrains.buildServer.serverSide.MultiTestRun;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.TestRunEx;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.serverSide.BuildStatisticsOptions.ALL_TESTS_NO_DETAILS;

/**
 * @author Yegor.Yarko
 */
@SuppressWarnings({"WeakerAccess"})
@XmlRootElement(name = "testOccurrence")
@XmlType(name = "testOccurrence", propOrder = {"id", "name", "status", "ignored", "duration", "runOrder"/*experimental*/, "newFailure"/*experimental*/, "muted", "currentlyMuted", "currentlyInvestigated",
  "href",
  "ignoreDetails", "details", "test", "mute", "build", "firstFailed", "nextFixed", "invocations", "metadata"})
@ModelDescription("Represents a relation between a test and the specific build.")
@FieldStrategySupported
public class TestOccurrence {
  private static final Logger LOG = Logger.getInstance(TestOccurrence.class.getName());

  @NotNull private BeanContext myBeanContext;
  @NotNull private Fields myFields;
  @NotNull private STestRun myTestRun;
  private final FieldInclusionChecker myChecker = FieldInclusionChecker.getForClass(TestOccurrence.class);

  private TestOccurrenceFinder myTestOccurrenceFinder;

  public TestOccurrence() { }

  public TestOccurrence(final @NotNull STestRun testRun, final @NotNull BeanContext beanContext, @NotNull final Fields fields) {
    myTestRun = testRun;
    myBeanContext = beanContext;
    myFields = fields;

    myTestOccurrenceFinder = myBeanContext.getSingletonService(TestOccurrenceFinder.class);
  }

  @XmlAttribute
  @FieldStrategy(name = "id")
  public String getId() {
    //STestRun.getTestRunId() can be the same between different builds
    return ValueWithDefault.decideDefault(myChecker.isIncluded("id", myFields), TestOccurrenceFinder.getTestRunLocator(myTestRun));
  }

  @XmlAttribute
  @FieldStrategy(name = "name")
  public String getName() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("name", myFields), myTestRun.getTest().getName().getAsString());
  }

  /**
   * Experimental and to be dropped as the number is not stable: it actually depends on the set of queried tests
   */
  @XmlAttribute
  @FieldStrategy(name = "runOrder", defaultForShort = FieldRule.EXCLUDE, defaultForLong = FieldRule.EXCLUDE)
  public String getRunOrder() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("runOrder", myFields), String.valueOf(myTestRun.getOrderId()));
  }

  /**
   * Experimental. Present only for failed tests. Indicates if this test was not failing in the previous build.
   * This can be more effective than getting "firstFailed" details
   */
  @XmlAttribute
  @FieldStrategy(name = "newFailure", defaultForShort = FieldRule.EXCLUDE, defaultForLong = FieldRule.EXCLUDE)
  public Boolean isNewFailure() {
    if (!myTestRun.getStatus().isFailed()) {
      return null;
    }
    return ValueWithDefault.decideDefault(myChecker.isIncluded("newFailure", myFields), () -> myTestRun.isNewFailure());
  }

  @XmlAttribute
  @ApiModelProperty(allowableValues = "UNKNOWN, NORMAL, WARNING, FAILURE, ERROR")
  @FieldStrategy(name = "status")
  public String getStatus() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("status", myFields), myTestRun.getStatus().getText());
  }

  @XmlAttribute
  @FieldStrategy(name = "ignored")
  public Boolean getIgnored() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("ignored", myFields), myTestRun.isIgnored());
  }

  @XmlAttribute
  @FieldStrategy(name = "href")
  public String getHref() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("href", myFields), myBeanContext.getApiUrlBuilder().transformRelativePath(TestOccurrenceRequest.getHref(myTestRun)));
  }

  @XmlAttribute
  @FieldStrategy(name = "duration")
  public Integer getDuration() { //test run duration in milliseconds
    return ValueWithDefault.decideDefault(myChecker.isIncluded("duration", myFields), myTestRun.getDuration());
  }

  /**
   * Experimental! "true" is the test occurrence was muted, not present otherwise
   */
  @XmlAttribute
  @FieldStrategy(name = "muted")
  public Boolean getMuted() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("muted", myFields), myTestRun.getMuteInfo() != null);
  }

  /**
   * Experimental! "true" is the test has investigation at the moment of request, not present otherwise
   */
  @XmlAttribute
  @FieldStrategy(name = "currentlyInvestigated")
  public Boolean getCurrentlyInvestigated() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("currentlyInvestigated", myFields), () -> myTestOccurrenceFinder.isCurrentlyInvestigated(myTestRun));
  }

  /**
   * Experimental! "true" is the test is muted at the moment of request, not present otherwise
   */
  @XmlAttribute
  @FieldStrategy(name = "currentlyMuted")
  public Boolean getCurrentlyMuted() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("currentlyMuted", myFields), () -> myTestOccurrenceFinder.isCurrentlyMuted(myTestRun));
  }

  /**
   * Experimental
   */
  @XmlAttribute
  @FieldStrategy(name = "logAnchor", defaultForShort = FieldRule.EXCLUDE, defaultForLong = FieldRule.EXCLUDE)
  public String getLogAnchor() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("logAnchor", myFields), () -> String.valueOf(myTestRun.getTestRunId()));
  }

  @XmlElement
  @FieldStrategy(name = "ignoreDetails", defaultForShort = FieldRule.EXCLUDE)
  public String getIgnoreDetails() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("ignoreDetails", myFields), () -> myTestRun.getIgnoreComment());
  }

  @XmlElement
  @FieldStrategy(name = "details", defaultForShort = FieldRule.EXCLUDE)
  public String getDetails() { //todo: consider using CDATA output her
    //consider providing separate stacktrace, stdout and stderr, see implementation of jetbrains.buildServer.serverSide.stat.TestFullTextBuilderImpl.getFullText()
    return ValueWithDefault.decideDefault(myChecker.isIncluded("details", myFields), () -> myTestRun.getFullText());
  }

  @XmlElement
  @FieldStrategy(name = "test", defaultForShort = FieldRule.EXCLUDE)
  public Test getTest() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("test", myFields), () -> new Test(myTestRun.getTest(), myBeanContext, myFields.getNestedField("test")));
  }

  @XmlElement
  @FieldStrategy(name = "mute", defaultForShort = FieldRule.EXCLUDE)
  public Mute getMute() {
    return Util.resolveNull(myTestRun.getMuteInfo(),
                            (mi) -> ValueWithDefault.decideDefault(myChecker.isIncluded("mute", myFields),
                                                                   () -> new Mute(mi, myFields.getNestedField("mute", Fields.NONE, Fields.LONG), myBeanContext)));
  }

  @XmlElement
  @FieldStrategy(name = "build", defaultForShort = FieldRule.EXCLUDE)
  public Build getBuild() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("build", myFields), () -> new Build(myTestRun.getBuild(), myFields.getNestedField("build"), myBeanContext));
  }

  @Nullable
  @XmlElement
  @FieldStrategy(name = "firstFailed", defaultForShort = FieldRule.EXCLUDE)
  public TestOccurrence getFirstFailed() {
    if (BooleanUtils.isNotTrue(myChecker.isIncluded("firstFailed", myFields))) {
      return null;
    }

    try {
      if (myTestRun.isNewFailure()) {
        return null;
      }

      return ValueWithDefault.decideDefault(myChecker.isIncluded("firstFailed", myFields),
                                            () -> Util.resolveNull(myTestRun.getFirstFailed(),
                                                                   (ff) -> new TestOccurrence(getFailedTestRun(ff, myTestRun), myBeanContext,
                                                                                              myFields.getNestedField("firstFailed"))));
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      // can be thrown by getFailedTestRun
      LOG.warnAndDebugDetails("Returning empty firstFailed as there was an error while getting firstFailed for test occurrence \"" + TestOccurrenceFinder.getTestRunLocator(myTestRun) + "\"", e);
      return null;
    }
  }

  @XmlElement
  @FieldStrategy(name = "nextFixed", defaultForShort = FieldRule.EXCLUDE)
  public TestOccurrence getNextFixed() {
    if (BooleanUtils.isNotTrue(myChecker.isIncluded("nextFixed", myFields))) {
      return null;
    }

    try {
      if (!myTestRun.isFixed()) {
        return null;
      }
      return ValueWithDefault.decideDefault(myChecker.isIncluded("nextFixed", myFields),
                                            () -> Util.resolveNull(myTestRun.getFixedIn(),
                                                                   (fi) -> new TestOccurrence(getSuccessfulTestRun(fi, myTestRun), myBeanContext, myFields.getNestedField("nextFixed"))));
    } catch (IllegalArgumentException | UnsupportedOperationException e) {
      LOG.warnAndDebugDetails("Returning empty nextFixed as there was an error while getting nextFixed for test occurrence \"" + TestOccurrenceFinder.getTestRunLocator(myTestRun) + "\"", e);
      return null;
    }
  }

  @XmlElement
  @FieldStrategy(name = "invocations", defaultForShort = FieldRule.EXCLUDE, defaultForLong = FieldRule.EXCLUDE)
  public TestOccurrences getInvocations() {
    return ValueWithDefault.decideDefault(myChecker.isIncluded("invocations", myFields), () -> {
      if (!(myTestRun instanceof MultiTestRun)) return null;
      MultiTestRun multiTestRun = (MultiTestRun) myTestRun;
      Fields nestedField = myFields.getNestedField("invocations");

      PagingItemFilter<STestRun> pagingFilter = myTestOccurrenceFinder.getPagingInvocationsFilter(nestedField);
      FilterItemProcessor<STestRun> processor = new FilterItemProcessor<>(pagingFilter);

      multiTestRun.getTestRuns().forEach(processor::processItem);

      ArrayList<STestRun> filtered = processor.getResult();

      return new TestOccurrences(filtered, null, null, null, nestedField, myBeanContext);
    });
  }

  /**
   * Experimental! Exposes test run metadata
   */
  @XmlElement
  @FieldStrategy(name = "metadata", defaultForShort = FieldRule.EXCLUDE, defaultForLong = FieldRule.EXCLUDE)
  public TestRunMetadata getMetadata() {
    return ValueWithDefault.decideDefaultIgnoringAccessDenied(myChecker.isIncluded("metadata", myFields),
                                                              () -> new TestRunMetadata(((TestRunEx)myTestRun).getMetadata(),
                                                                                        myFields.getNestedField("metadata", Fields.SHORT, Fields.LONG)));
  }

  @NotNull
  private STestRun getFailedTestRun(@NotNull final SBuild build, @NotNull final STestRun sampleTestRun) {
    //this is different from getSuccessfulTestRun to be more performant
    long testNameId = sampleTestRun.getTest().getTestNameId();
    return build.getShortStatistics().getFailedTestsIncludingMuted().stream().filter(t -> t.getTest().getTestNameId() == testNameId).findFirst()
                          .orElseThrow(() -> new IllegalArgumentException("Cannot find test with name \"" + sampleTestRun.getTest().getName() + "\" (test name id " + testNameId + ") in build with id " + build.getBuildId()));
  }

  /**
   * For not successful test use getFailedTestRun for performance reasons.
   */
  @NotNull
  private STestRun getSuccessfulTestRun(@NotNull final SBuild build, @NotNull final STestRun sampleTestRun) {
    final STestRun testRun = build.getBuildStatistics(ALL_TESTS_NO_DETAILS).findTestByTestNameId(sampleTestRun.getTest().getTestNameId());
    if (testRun == null) {
      throw new IllegalArgumentException("Cannot find test with name \"" + sampleTestRun.getTest().getName() + "\" in build with id " + build.getBuildId());
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
