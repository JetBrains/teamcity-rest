package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildRunnerDescriptor;
import jetbrains.buildServer.serverSide.BuildRunnerDescriptorFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
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

  public PropEntityStep(SBuildRunnerDescriptor descriptor, final BuildTypeSettings buildType) {
    super(descriptor.getId(), descriptor.getName(), descriptor.getType(), buildType.isEnabled(descriptor.getId()),
          descriptor.getParameters());
  }

  public SBuildRunnerDescriptor addStep(final BuildTypeSettings buildType, final BuildRunnerDescriptorFactory factory) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Created step cannot have empty 'type'.");
    }

    @SuppressWarnings("ConstantConditions")
    final SBuildRunnerDescriptor runnerToCreate =
      factory.createNewBuildRunner(StringUtil.isEmpty(name) ? "" : name, type, properties.getMap());
    buildType.addBuildRunner(runnerToCreate);
    if (disabled != null) {
      buildType.setEnabled(runnerToCreate.getId(), !disabled);
    }
    return buildType.findBuildRunnerById(runnerToCreate.getId());
  }


  public static String getSetting(final BuildTypeSettings buildType, final BuildRunnerDescriptor step, final String name) {
    if ("name".equals(name)) {
      return step.getName();
    }
    if ("disabled".equals(name)) {
      return String.valueOf(!buildType.isEnabled(step.getId()));
    }
    throw new BadRequestException("Only 'name'and 'disabled' setting names are supported. '" + name + "' unknown.");
  }

  public static void setSetting(final BuildTypeSettings buildType,
                                final BuildRunnerDescriptor step,
                                final String name,
                                final String value) {
    if ("name".equals(name)) {
      buildType.updateBuildRunner(step.getId(), value, step.getType(), step.getParameters());
    } else if ("disabled".equals(name)) {
      buildType.setEnabled(step.getId(), !Boolean.parseBoolean(value));
    } else {
      throw new BadRequestException("Only 'name'and 'disabled' setting names are supported. '" + name + "' unknown.");
    }
  }
}
