package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "step")
public class PropEntityStep extends PropEntity {
  public PropEntityStep() {
  }

  public PropEntityStep(SBuildRunnerDescriptor descriptor) {
    super(descriptor.getId(), descriptor.getName(), descriptor.getType(), descriptor.getParameters());
  }
}
