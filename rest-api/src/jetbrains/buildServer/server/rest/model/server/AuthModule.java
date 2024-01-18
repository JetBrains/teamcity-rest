package jetbrains.buildServer.server.rest.model.server;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.parameters.EntityWithParameters;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.serverSide.auth.AuthModuleType;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "serverAuthModule")
public class AuthModule {
  private String name;
  private Properties properties;

  public AuthModule() {
  }

  public AuthModule(@NotNull jetbrains.buildServer.serverSide.auth.AuthModule<AuthModuleType> module,
                    @NotNull final ServiceLocator serviceLocator) {
    name = module.getType().getName();
    EntityWithParameters entity = Properties.createEntity(module.getProperties(), null);
    properties = new Properties(entity, false, null, null, Fields.ALL, serviceLocator, null);
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @XmlElement(name = "properties")
  public Properties getProperties() {
    return properties;
  }

  public void setProperties(Properties properties) {
    this.properties = properties;
  }
}
