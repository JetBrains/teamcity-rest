package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildRunnerDescriptor;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "steps")
@SuppressWarnings("PublicField")
public class PropEntitiesStep {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "step")
  public List<PropEntityStep> propEntities;

  public PropEntitiesStep() {
  }

  public PropEntitiesStep(final BuildTypeSettings buildType) {
    propEntities = CollectionsUtil.convertCollection(buildType.getBuildRunners(),
                                                     new Converter<PropEntityStep, SBuildRunnerDescriptor>() {
                                                       public PropEntityStep createFrom(@NotNull final SBuildRunnerDescriptor source) {
                                                         return new PropEntityStep(source, buildType);
                                                       }
                                                     });
    count = ValueWithDefault.decideDefault(null, propEntities.size());
  }

}
