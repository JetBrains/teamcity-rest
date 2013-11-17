package jetbrains.buildServer.server.rest.model.problem;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.build.BuildRef;
import jetbrains.buildServer.server.rest.request.TestOccurrenceRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 11.02.12
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "testOccurrence")
@XmlType(name = "testOccurrence", propOrder = {"id", "name", "status", "ignored", "href",
  "duration", "ignoreDetails", "details", "test", "mute", "build"})
public class TestOccurrence {
  @XmlAttribute public String id;
  @XmlAttribute public String name;
  @XmlAttribute public String status;
  @XmlAttribute public Boolean ignored;
  @XmlAttribute public String href;

  //test run duration in milliseconds
  @XmlElement public Integer duration;
  @XmlElement public String ignoreDetails;
  @XmlElement public String details;

  @XmlElement public Test test;
  @XmlElement public Mute mute;

  @XmlElement public BuildRef build;

  public TestOccurrence() {
  }

  public TestOccurrence(final @NotNull STestRun testRun, final @NotNull BeanContext beanContext, final boolean fullDetails) {
    final STest sTest = testRun.getTest();
    id = String.valueOf(testRun.getTestRunId());
//    id = getId(testRun);
    name = sTest.getName().getAsString();

    status = testRun.getStatus().getText();

    href = beanContext.getApiUrlBuilder().transformRelativePath(TestOccurrenceRequest.getHref(testRun));

    duration = testRun.getDuration();
    //testRun.getOrderId();

    ignored = testRun.isIgnored();
    ignoreDetails = testRun.getIgnoreComment();

    if (fullDetails) {
    /*
    final TestFailureInfo failureInfo = testRun.getFailureInfo();
    if (failureInfo != null){
      details = failureInfo.getShortStacktrace();
    }
    */
      details = testRun.getFullText();


      //todo: add links to the test failure (or build) it fixed in and the build if first failed in (if not the same)
      //testRun.isNewFailure();
      //testRun.isFixed();

      test = new Test(sTest, beanContext, false);
      final MuteInfo muteInfo = testRun.getMuteInfo();
      if (muteInfo != null) {
        mute = new Mute(muteInfo, beanContext);
      }

      build = new BuildRef(testRun.getBuild(), beanContext.getServiceLocator(), beanContext.getApiUrlBuilder());
    }
  }
}
