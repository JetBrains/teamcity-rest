package jetbrains.buildServer.server.rest.model.project;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.impl.untrustedBuilds.UntrustedBuildsSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya Voronin
 * @date 19.12.2023
 */
@XmlRootElement(name = "untrustedReport")
@XmlType(name = "untrustedReport")
@SuppressWarnings("PublicField")
@ModelDescription(
  value = "Represents an untrusted report for a single build configuration."
)
public class UntrustedReport {

  @XmlElement(name = "reasons")
  public List<UntrustedReportReason> untrustedReportReasons;
  @XmlAttribute
  public String untrustedBuildsAction;
  @XmlAttribute
  public String buildTypeId;

  public UntrustedReport() {}

  public UntrustedReport(@NotNull SBuildType buildType, @NotNull List<UntrustedReportReason> reasons, @NotNull UntrustedBuildsSettings.DefaultAction defaultAction) {
    buildTypeId = buildType.getExternalId();
    untrustedReportReasons = reasons;
    untrustedBuildsAction = defaultAction.name().toLowerCase();
  }

}

