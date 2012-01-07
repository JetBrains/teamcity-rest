package jetbrains.buildServer.server.rest.model.buildType;

import java.util.HashMap;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildRunnerDescriptorFactory;
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor;
import jetbrains.buildServer.util.StringUtil;

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

  public SBuildRunnerDescriptor createRunner(final BuildRunnerDescriptorFactory factory) {
    if (StringUtil.isEmpty(type)){
      throw new BadRequestException("Created step cannot have empty 'type'.");
    }

    return factory.createNewBuildRunner(StringUtil.isEmpty(name)?"" :name, type, properties == null? new HashMap<String, String>() : properties.getMap());
  }
}
