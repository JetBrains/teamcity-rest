package jetbrains.buildServer.server.rest.model.server;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.serverSide.auth.AuthModuleType;
import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "serverAuthModules")
public class AuthModules {
  private List<AuthModule> modules;

  public AuthModules() {
  }

  public AuthModules(@NotNull List<jetbrains.buildServer.serverSide.auth.AuthModule<AuthModuleType>> authModules,
                     @NotNull final ServiceLocator serviceLocator) {
    modules = CollectionsUtil.convertCollection(
      authModules,
      source -> new AuthModule(source, serviceLocator)
    );
  }

  @XmlElement(name = "module")
  public List<AuthModule> getModules() {
    return modules;
  }

  public void setModules(List<AuthModule> modules) {
    this.modules = modules;
  }
}
