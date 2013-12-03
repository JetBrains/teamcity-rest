package jetbrains.buildServer.server.rest.model.problem;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.BuildRef;
import jetbrains.buildServer.server.rest.request.TestOccurrenceRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildStatisticsOptions;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "testOccurrence")
@XmlType(name = "testOccurrence", propOrder = {"id", "name", "status", "ignored", "duration", "muted", "currentlyMuted", "currentlyInvestigated", "href",
  "ignoreDetails", "details", "test", "mute", "build", "firstFailed", "nextFixed"})
public class TestOccurrence {
  @XmlAttribute public String id;
  @XmlAttribute public String name;
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

  @XmlElement public BuildRef build;
  @XmlElement public TestOccurrence firstFailed;
  @XmlElement public TestOccurrence nextFixed;

  public TestOccurrence() {
  }

  public TestOccurrence(final @NotNull STestRun testRun, final @NotNull BeanContext beanContext, @NotNull final Fields fields) {
    final STest sTest = testRun.getTest();
    id = TestOccurrenceFinder.getTestRunLocator(testRun); //STestRun.getTestRunId() can be the same between different builds

    name = sTest.getName().getAsString();

    status = testRun.getStatus().getText();

    href = beanContext.getApiUrlBuilder().transformRelativePath(TestOccurrenceRequest.getHref(testRun));

    duration = testRun.getDuration();
    //testRun.getOrderId();

    ignored = testRun.isIgnored();

    final MuteInfo muteInfo = testRun.getMuteInfo();
    if (muteInfo != null) muted = true;

    if (beanContext.getSingletonService(TestOccurrenceFinder.class).isCurrentlyInvestigated(testRun)) {
      currentlyInvestigated = true;
    }
    if (beanContext.getSingletonService(TestOccurrenceFinder.class).isCurrentlyMuted(testRun)) {
      currentlyMuted = true;
    }

    if (fields.isAllFieldsIncluded()) {
    /*
    final TestFailureInfo failureInfo = testRun.getFailureInfo();
    if (failureInfo != null){
      details = failureInfo.getShortStacktrace();
    }
    */
      details = testRun.getFullText();

      ignoreDetails = testRun.getIgnoreComment();

      //todo: add links to the test failure (or build) it fixed in and the build if first failed in (if not the same)
      //testRun.isNewFailure();
      //testRun.isFixed();

      test = new Test(sTest, beanContext, fields.getNestedField("test"));

      if (muteInfo != null) {
        mute = new Mute(muteInfo, beanContext);
      }

      build = new BuildRef(testRun.getBuild(), beanContext.getServiceLocator(), beanContext.getApiUrlBuilder());

      final SBuild firstFailedInBuild = testRun.getFirstFailed();
      if (firstFailedInBuild != null) {
        //noinspection ConstantConditions
        firstFailed = new TestOccurrence(getTestRun(firstFailedInBuild, testRun), beanContext, fields.getNestedField("firstFailed"));
      }
      final SBuild fixedInBuild = testRun.getFixedIn();
      if (fixedInBuild != null) {
        //noinspection ConstantConditions
        nextFixed = new TestOccurrence(getTestRun(fixedInBuild, testRun), beanContext, fields.getNestedField("firstFailed"));
      }
    }
  }

  @Nullable
  private STestRun getTestRun(@NotNull final SBuild build, @NotNull final STestRun sampleTestRun) {
    //todo: TeamCity API (MP): is there a dedicated API for this?
    //todo: handle several returned test runs
    final List<STestRun> testRuns = build.getBuildStatistics(new BuildStatisticsOptions(BuildStatisticsOptions.PASSED_TESTS | BuildStatisticsOptions.IGNORED_TESTS, 0))
      .findTestsBy(sampleTestRun.getTest().getName());
    return testRuns.iterator().next();
  }
}
