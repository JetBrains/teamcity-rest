package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "features")
@SuppressWarnings("PublicField")
public class PropEntitiesFeature {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "feature")
  public List<PropEntityFeature> propEntities;

  public PropEntitiesFeature() {
  }

  public PropEntitiesFeature(final BuildTypeSettings buildType) {
    propEntities =
      CollectionsUtil.convertCollection(buildType.getBuildFeatures(), new Converter<PropEntityFeature, SBuildFeatureDescriptor>() {
        public PropEntityFeature createFrom(@NotNull final SBuildFeatureDescriptor source) {
          return new PropEntityFeature(source, buildType);
        }
      });
    count = ValueWithDefault.decideDefault(null, propEntities.size());
  }
}
