package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

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

  public BuildTriggerDescriptor addTrigger(final BuildTypeSettings buildType, final BuildTriggerDescriptorFactory descriptorFactory) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Build trigger cannot have empty 'type'.");
    }
    final BuildTriggerDescriptor triggerToAdd = descriptorFactory.createTriggerDescriptor(type, properties.getMap());

    if (!buildType.addBuildTrigger(triggerToAdd)) {
      String additionalMessage = getDetails(buildType, triggerToAdd);
      throw new OperationException("Build trigger addition failed." + (additionalMessage != null ? " " + additionalMessage : ""));
    }
    if (disabled != null) {
      buildType.setEnabled(triggerToAdd.getId(), !disabled);
    }
    return buildType.findTriggerById(triggerToAdd.getId());
  }

  public BuildTriggerDescriptor updateTrigger(@NotNull final BuildTypeSettings buildType, @NotNull final BuildTriggerDescriptor trigger) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Build trigger cannot have empty 'type'.");
    }
    if (!type.equals(trigger.getType())) {
      throw new BadRequestException("Cannot change type of existing trigger.");
    }
    if (!buildType.updateBuildTrigger(trigger.getId(), type, properties.getMap())) {
      throw new OperationException("Update failed");
    }
    if (disabled != null) {
      buildType.setEnabled(trigger.getId(), !disabled);
    }
    return buildType.findTriggerById(trigger.getId());
  }

  private String getDetails(final BuildTypeSettings buildType, final BuildTriggerDescriptor triggerToAdd) {
    final BuildTriggerDescriptor foundTriggerWithSameId = buildType.findTriggerById(triggerToAdd.getId());
    if (foundTriggerWithSameId != null) {
      return "Trigger with id '" + triggerToAdd.getId() + "'already exists.";
    }
    return null;
  }
}
