package jetbrains.buildServer.server.rest.model.buildType;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.ParametersDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Author: Yegor.Yarko
 */
@XmlType(propOrder = {"id", "name", "type",
  "properties"})
//@XmlRootElement(name = "property-described-entity")
@SuppressWarnings("PublicField")
public class PropEntity {
  @XmlAttribute
  @NotNull
  public String id;

  @XmlAttribute
  @Nullable
  public String name;

  @XmlAttribute
  @NotNull
  public String type;

  @XmlAttribute
  @Nullable
  public Boolean disabled;

  @XmlElement
  @NotNull
  public Properties properties;

  public PropEntity() {
  }

  public PropEntity(@NotNull ParametersDescriptor descriptor, @NotNull BuildTypeSettings buildType) {
    init(descriptor.getId(), null, descriptor.getType(), buildType.isEnabled(descriptor.getId()), descriptor.getParameters());
  }

  public PropEntity(@NotNull final String id,
                    @Nullable final String name,
                    @NotNull final String type,
                    @Nullable final Boolean enabled,
                    @NotNull final Map<String, String> properties) {
    init(id, name, type, enabled, properties);
  }

  private void init(@NotNull final String id,
                    @Nullable final String name,
                    @NotNull final String type,
                    @Nullable final Boolean enabled,
                    @NotNull final Map<String, String> properties) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.properties = new Properties(properties);
    disabled = enabled ? null : true;
  }

  public static String getSetting(final BuildTypeSettings buildType, final ParametersDescriptor descriptor, final String name) {
    if ("disabled".equals(name)) {
      return String.valueOf(!buildType.isEnabled(descriptor.getId()));
    }
    throw new BadRequestException("Only 'disabled' setting names is supported. '" + name + "' unknown.");
  }

  public static void setSetting(final BuildTypeSettings buildType, final ParametersDescriptor descriptor, final String name, final String value) {
    if ("disabled".equals(name)) {
      buildType.setEnabled(descriptor.getId(), !Boolean.parseBoolean(value));
    } else {
      throw new BadRequestException("Only 'disabled' setting names is supported. '" + name + "' unknown.");
    }
  }
}
