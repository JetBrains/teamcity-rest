package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildFeatureDescriptorFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.ParametersDescriptor;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.DuplicateIdException;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "feature")
public class PropEntityFeature extends PropEntity {
  public PropEntityFeature() {
  }

  public PropEntityFeature(@NotNull ParametersDescriptor descriptor, @NotNull final BuildTypeSettings buildType) {
    super(descriptor, buildType);
  }

  public SBuildFeatureDescriptor addFeature(final BuildTypeSettings buildType, final BuildFeatureDescriptorFactory factory) {
    if (StringUtil.isEmpty(type)) {
      throw new BadRequestException("Created build feature cannot have empty 'type'.");
    }
    final SBuildFeatureDescriptor newBuildFeature = factory.createNewBuildFeature(type, properties.getMap());
    try {
      buildType.addBuildFeature(newBuildFeature);
    } catch (DuplicateIdException e) {
      final String details = getDetails(buildType, newBuildFeature, e);
      throw new BadRequestException("Error adding feature." + (details != null ? " " + details : ""));
    }
    if (disabled != null) {
      buildType.setEnabled(newBuildFeature.getId(), !disabled);
    }
    return BuildTypeUtil.getBuildTypeFeatureOrNull(buildType, newBuildFeature.getId());
  }

  private String getDetails(final BuildTypeSettings buildType, final SBuildFeatureDescriptor newBuildFeature, final Exception e) {
    final SBuildFeatureDescriptor existingFeature = BuildTypeUtil.getBuildTypeFeatureOrNull(buildType, newBuildFeature.getId());
    if (existingFeature != null) {
      return "Feature with id '" + newBuildFeature.getId() + "' already exists.";
    }
    return e.getClass().getName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
  }
}
