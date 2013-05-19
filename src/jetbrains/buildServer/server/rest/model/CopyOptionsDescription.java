package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.serverSide.CopyOptions;

@SuppressWarnings("PublicField")
public class CopyOptionsDescription {
  @XmlAttribute public Boolean copyAllAssociatedSettings;

  public CopyOptions getCopyOptions() {
    final CopyOptions result = new CopyOptions();
    if (toBoolean(copyAllAssociatedSettings)) {
      //todo: need to use some API to set all necessary options. e.g. see TW-16948, TW-16934
      result.addOption(CopyOptions.Option.COPY_AGENT_POOL_ASSOCIATIONS);
      result.addOption(CopyOptions.Option.COPY_AGENT_RESTRICTIONS);
      result.addOption(CopyOptions.Option.COPY_MUTED_TESTS);
      result.addOption(CopyOptions.Option.COPY_USER_NOTIFICATION_RULES);
      result.addOption(CopyOptions.Option.COPY_USER_ROLES);
    }
    result.addOption(CopyOptions.Option.COPY_VCS_ROOTS); //todo: TeamCity API: this option seems unnecessary and is always implied
    return result;
  }

  private static boolean toBoolean(final Boolean value) {
    return (value == null) ? false: value;
  }
}