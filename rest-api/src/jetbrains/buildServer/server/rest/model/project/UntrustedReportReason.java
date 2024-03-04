package jetbrains.buildServer.server.rest.model.project;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya Voronin
 * @date 19.12.2023
 */
@XmlRootElement(name = "reason")
@XmlType(name = "untrustedConfigurationReason")
@SuppressWarnings("PublicField")
@ModelDescription(
  value = "Represents a single reason why build configuration was detected as untrusted."
)
public class UntrustedReportReason {
  @XmlAttribute
  public String type;
  @XmlAttribute
  private String description;

  public UntrustedReportReason() {}

  public UntrustedReportReason(@NotNull String type, @NotNull String description) {
    this.type = type;
    this.description = description;
  }
}
