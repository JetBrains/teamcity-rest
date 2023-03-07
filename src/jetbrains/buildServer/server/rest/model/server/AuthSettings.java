package jetbrains.buildServer.server.rest.model.server;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.serverSide.impl.auth.LoginConfigurationEx;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "serverAuthSettings")
@XmlType(name = "serverAuthSettings")
@ModelDescription("Authentication Settings")
public class AuthSettings {
  private Boolean allowGuest;
  private String guestUsername;
  private String welcomeText;
  private Boolean collapseLoginForm;
  private Boolean perProjectPermissions;
  private Boolean emailVerification;

  private AuthModules modules;

  public AuthSettings() {
  }

  public AuthSettings(@NotNull final LoginConfigurationEx config,
                      @NotNull final ServerSettings server,
                      @NotNull final ServiceLocator serviceLocator) {
    allowGuest = config.isGuestLoginAllowed();
    guestUsername = config.getGuestUsername();
    welcomeText = config.getTextForLoginPage();
    collapseLoginForm = config.isLoginFormCollapsed();
    perProjectPermissions = server.isPerProjectPermissionsEnabled();
    emailVerification = server.isEmailVerificationEnabled();
    modules = new AuthModules(config.getConfiguredAuthModules(null), serviceLocator);
  }

  @XmlAttribute(name = "allowGuest")
  public Boolean getAllowGuest() {
    return allowGuest;
  }

  public void setAllowGuest(Boolean allowGuest) {
    this.allowGuest = allowGuest;
  }

  @XmlAttribute(name = "guestUsername")
  public String getGuestUsername() {
    return guestUsername;
  }

  public void setGuestUsername(String guestUsername) {
    this.guestUsername = guestUsername;
  }

  @XmlAttribute(name = "welcomeText")
  public String getWelcomeText() {
    return welcomeText;
  }

  public void setWelcomeText(String welcomeText) {
    this.welcomeText = welcomeText;
  }

  @XmlAttribute(name = "collapseLoginForm")
  public Boolean getCollapseLoginForm() {
    return collapseLoginForm;
  }

  public void setCollapseLoginForm(Boolean collapseLoginForm) {
    this.collapseLoginForm = collapseLoginForm;
  }

  @XmlAttribute(name = "perProjectPermissions")
  public Boolean getPerProjectPermissions() {
    return perProjectPermissions;
  }

  public void setPerProjectPermissions(Boolean perProjectPermissions) {
    this.perProjectPermissions = perProjectPermissions;
  }

  @XmlAttribute(name = "emailVerification")
  public Boolean getEmailVerification() {
    return emailVerification;
  }

  public void setEmailVerification(Boolean emailVerification) {
    this.emailVerification = emailVerification;
  }

  @XmlElement(name = "modules")
  public AuthModules getModules() {
    return modules;
  }

  public void setModules(AuthModules modules) {
    this.modules = modules;
  }
}
