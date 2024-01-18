package jetbrains.buildServer.server.rest.model.server;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.buildTriggers.scheduler.CronField;
import jetbrains.buildServer.buildTriggers.scheduler.CronScheduler;

@XmlRootElement(name = "cron")
@XmlType(name = "cron")
public class CleanupCron {
  @XmlAttribute
  public String minute;
  @XmlAttribute
  public String hour;
  @XmlAttribute
  public String day;
  @XmlAttribute
  public String month;
  @XmlAttribute
  public String dayWeek;

  public CleanupCron() {
  }

  public CleanupCron(CronScheduler cronScheduler) {
    minute = cronScheduler.getFieldToValue().get(CronField.MIN);
    hour = cronScheduler.getFieldToValue().get(CronField.HOUR);
    day = cronScheduler.getFieldToValue().get(CronField.DM);
    month = cronScheduler.getFieldToValue().get(CronField.MONTH);
    dayWeek = cronScheduler.getFieldToValue().get(CronField.DW);
  }
}
