package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.serverSide.ParametersDescriptor;

/**
 * Author: Yegor.Yarko
 */
@XmlType(propOrder = {"type", "name", "id",
  "properties"})
//@XmlRootElement(name = "property-described-entity")
public class PropEntity {
  @SuppressWarnings("PublicField")
  @XmlAttribute
  public String id;

  @SuppressWarnings("PublicField")
  @XmlAttribute
  public String name;

  @SuppressWarnings("PublicField")
  @XmlAttribute
  public String type;

  @SuppressWarnings("PublicField")
  @XmlElement
  public Properties properties;

  public PropEntity() {
  }

  public PropEntity(ParametersDescriptor descriptor) {
    id = descriptor.getId();
    type = descriptor.getType();
    properties = new Properties(descriptor.getParameters());
  }

  public PropEntity(final String idP, final String typeP, final Map<String, String> propertiesP) {
    this.id = idP;
    this.type = typeP;
    this.properties = new Properties(propertiesP);
  }

  public PropEntity(final String idP, final String name, final String typeP, final Map<String, String> propertiesP) {
    this.id = idP;
    this.name = name;
    this.type = typeP;
    this.properties = new Properties(propertiesP);
  }
}
