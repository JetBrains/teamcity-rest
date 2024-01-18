package jetbrains.buildServer.server.rest.model.server;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.buildTriggers.scheduler.CronScheduler;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.serverSide.cleanup.ServerCleanupManager;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"PublicField", "WeakerAccess"})
@XmlRootElement(name = "cleanup")
@XmlType(name = "cleanup")
@ModelDescription("Clean-up Settings")
public class CleanupSettings {
  @XmlAttribute
  public Boolean enabled;
  @XmlAttribute
  public Integer maxCleanupDuration;

  @XmlElement(name = "daily")
  public CleanupDaily daily;

  @XmlElement(name = "cron")
  public CleanupCron cron;

  public CleanupSettings() {
  }

  public CleanupSettings(final @NotNull ServerCleanupManager serverCleanupManager) {
    enabled = serverCleanupManager.isCleanupEnabled();
    maxCleanupDuration = serverCleanupManager.getMaxCleanupDuration();

    CronScheduler schedule = serverCleanupManager.getCleanupStartCron();
    if(schedule.isDaily()) {
      daily = new CleanupDaily(schedule);
    } else {
      cron = new CleanupCron(schedule);
    }
  }
}
