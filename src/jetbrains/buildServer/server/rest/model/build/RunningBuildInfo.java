package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 13.09.2010
 */
@XmlType(propOrder = {"probablyHanging", "outdated", "currentStageText", "estimatedDuration", "elapsedTime", "progress"})
@XmlRootElement(name = "progress-info")
public class RunningBuildInfo {
  @NotNull
  private SRunningBuild myBuild;

  public RunningBuildInfo() {
  }

  public RunningBuildInfo(@NotNull final SRunningBuild build) {
    myBuild = build;
  }

  @XmlAttribute(name = "percentageComplete")
  public Integer getProgress() {
    if (myBuild.getDurationEstimate() == -1){
      return null;
    }
    return myBuild.getCompletedPercent();
  }

  @XmlAttribute(name = "elapsedSeconds")
  public long getElapsedTime() {
    return myBuild.getElapsedTime();
  }

  @XmlAttribute(name = "estimatedTotalSeconds")
  public Long getEstimatedDuration() {
    final long durationEstimate = myBuild.getDurationEstimate();
    if (durationEstimate == -1) {
      return null;
    } else {
      return durationEstimate;
    }
  }

  @XmlAttribute
  public boolean isOutdated() {
    return myBuild.isOutdated();
  }

  @XmlAttribute
  public boolean isProbablyHanging() {
    return myBuild.isProbablyHanging();
  }

  @XmlAttribute
  public String getCurrentStageText() {
    return myBuild.getCurrentPath();
  }
}
