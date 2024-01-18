package jetbrains.buildServer.server.rest.model.server;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.buildTriggers.scheduler.CronField;
import jetbrains.buildServer.buildTriggers.scheduler.CronScheduler;

@XmlRootElement(name = "daily")
@XmlType(name = "daily")
public class CleanupDaily {
  @XmlAttribute
  public Integer hour;
  @XmlAttribute
  public Integer minute;

  public CleanupDaily() {
  }

  public CleanupDaily(CronScheduler cronScheduler) {
    hour = Integer.valueOf(cronScheduler.getFieldToValue().get(CronField.HOUR));
    minute = Integer.valueOf(cronScheduler.getFieldToValue().get(CronField.MIN));
  }
}
