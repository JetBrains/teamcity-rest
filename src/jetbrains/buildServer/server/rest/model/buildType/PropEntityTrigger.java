package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "trigger")
public class PropEntityTrigger extends PropEntity {

  public PropEntityTrigger() {
  }

  public PropEntityTrigger(final BuildTriggerDescriptor descriptor, final BuildTypeSettings buildTypeSettings) {
    super(descriptor, buildTypeSettings);
  }

  public BuildTriggerDescriptor createTrigger(final BuildTriggerDescriptorFactory factory) {
    return factory.createTriggerDescriptor(name, properties.getMap());
  }
}
