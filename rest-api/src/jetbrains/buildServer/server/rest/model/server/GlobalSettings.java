package jetbrains.buildServer.server.rest.model.server;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.serverSide.crypt.CustomKeyEncryptionStrategy;
import jetbrains.buildServer.serverSide.impl.ServerSettingsImpl;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "serverGlobalSettings")
@XmlType(name = "serverGlobalSettings")
@ModelDescription("Global Server Settings")
public class GlobalSettings {
  private String myArtifactDirectories;
  private String myRootUrl;
  private Long myMaxArtifactSize;
  private Long myMaxArtifactsNumber;
  private Integer myDefaultExecutionTimeout;
  private Integer myDefaultVCSCheckInterval;
  private Boolean myEnforceDefaultVCSCheckInterval;
  private Integer myDefaultQuietPeriod;
  private Boolean myUseEncryption;
  private String myEncryptionKey;
  private Boolean myArtifactsDomainIsolation;
  private String myArtifactsUrl;

  public GlobalSettings() {
  }

  public GlobalSettings(@NotNull final ServerSettingsImpl serverSettings) {
    myArtifactDirectories = serverSettings.getArtifactDirectoriesPaths();
    myRootUrl = serverSettings.getRootUrl();
    myMaxArtifactSize = serverSettings.getMaximumAllowedArtifactSize();
    myMaxArtifactsNumber = serverSettings.getMaximumAllowedArtifactsNumber();
    myDefaultExecutionTimeout = serverSettings.getDefaultExecutionTimeout();
    myDefaultVCSCheckInterval = serverSettings.getDefaultModificationCheckInterval();
    myEnforceDefaultVCSCheckInterval = serverSettings.isMinimumCheckIntervalEnforced();
    myDefaultQuietPeriod = serverSettings.getDefaultQuietPeriod();
    myUseEncryption = serverSettings.getEncryptionStrategy().equals(CustomKeyEncryptionStrategy.STRATEGY_NAME);
    myArtifactsDomainIsolation = serverSettings.isDomainIsolationProtectionEnabled();
    myArtifactsUrl = serverSettings.getArtifactsRootUrl();
  }

  @XmlAttribute(name = "artifactDirectories")
  public String getArtifactDirectories() {
    return myArtifactDirectories;
  }

  public void setArtifactDirectories(String artifactDirectories) {
    myArtifactDirectories = artifactDirectories;
  }

  @XmlAttribute(name = "rootUrl")
  public String getRootUrl() {
    return myRootUrl;
  }

  public void setRootUrl(String rootUrl) {
    myRootUrl = rootUrl;
  }

  @XmlAttribute(name = "maxArtifactSize")
  public Long getMaxArtifactSize() {
    return myMaxArtifactSize;
  }

  public void setMaxArtifactSize(Long maxArtifactSize) {
    myMaxArtifactSize = maxArtifactSize;
  }

  @XmlAttribute(name = "maxArtifactsNumber")
  public Long getMaxArtifactsNumber() {
    return myMaxArtifactsNumber;
  }

  public void setMaxArtifactsNumber(Long maxArtifactsNumber) {
    myMaxArtifactsNumber = maxArtifactsNumber;
  }

  @XmlAttribute(name = "defaultExecutionTimeout")
  public Integer getDefaultExecutionTimeout() {
    return myDefaultExecutionTimeout;
  }

  public void setDefaultExecutionTimeout(Integer defaultExecutionTimeout) {
    myDefaultExecutionTimeout = defaultExecutionTimeout;
  }

  @XmlAttribute(name = "defaultVCSCheckInterval")
  public Integer getDefaultVCSCheckInterval() {
    return myDefaultVCSCheckInterval;
  }

  public void setDefaultVCSCheckInterval(Integer defaultVCSCheckInterval) {
    myDefaultVCSCheckInterval = defaultVCSCheckInterval;
  }

  @XmlAttribute(name = "enforceDefaultVCSCheckInterval")
  public Boolean getEnforceDefaultVCSCheckInterval() {
    return myEnforceDefaultVCSCheckInterval;
  }

  public void setEnforceDefaultVCSCheckInterval(Boolean enforceDefaultVCSCheckInterval) {
    myEnforceDefaultVCSCheckInterval = enforceDefaultVCSCheckInterval;
  }

  @XmlAttribute(name = "defaultQuietPeriod")
  public Integer getDefaultQuietPeriod() {
    return myDefaultQuietPeriod;
  }

  public void setDefaultQuietPeriod(Integer defaultQuietPeriod) {
    myDefaultQuietPeriod = defaultQuietPeriod;
  }

  @XmlAttribute(name = "artifactsDomainIsolation")
  public Boolean getArtifactsDomainIsolation() {
    return myArtifactsDomainIsolation;
  }

  public void setArtifactsDomainIsolation(Boolean artifactsDomainIsolation) {
    myArtifactsDomainIsolation = artifactsDomainIsolation;
  }

  @XmlAttribute(name = "artifactsUrl")
  public String getArtifactsUrl() {
    return myArtifactsUrl;
  }

  public void setArtifactsUrl(String artifactsUrl) {
    myArtifactsUrl = artifactsUrl;
  }

  @XmlAttribute(name = "useEncryption")
  public Boolean getUseEncryption() {
    return myUseEncryption;
  }

  public void setUseEncryption(Boolean useEncryption) {
    myUseEncryption = useEncryption;
  }

  @XmlAttribute(name = "encryptionKey")
  public String getEncryptionKey() {
    return myEncryptionKey;
  }

  public void setEncryptionKey(String encryptionKey) {
    myEncryptionKey = encryptionKey;
  }
}
