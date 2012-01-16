package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildFeatureDescriptorFactory;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.ParametersDescriptor;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "feature")
public class PropEntityFeature extends PropEntity{
  public PropEntityFeature() {
  }
  public PropEntityFeature(@NotNull ParametersDescriptor descriptor, @NotNull final BuildTypeSettings buildType) {
    super(descriptor, buildType);
  }

  public SBuildFeatureDescriptor addFeature(final BuildTypeSettings buildType, final BuildFeatureDescriptorFactory factory) {
    if (StringUtil.isEmpty(type)){
      throw new BadRequestException("Created build feature cannot have empty 'type'.");
    }
    final SBuildFeatureDescriptor newBuildFeature = factory.createNewBuildFeature(type, properties.getMap());
    //todo: refuse to add if such feature already exists
    buildType.addBuildFeature(newBuildFeature);
    if (disabled != null){
      buildType.setEnabled(newBuildFeature.getId(), !disabled);
    }
    return BuildTypeUtil.getBuildTypeFeature(buildType, newBuildFeature.getId());
  }
}
