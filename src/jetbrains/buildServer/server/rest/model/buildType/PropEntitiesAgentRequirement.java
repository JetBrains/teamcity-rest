package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="agent-requirements")
@SuppressWarnings("PublicField")
public class PropEntitiesAgentRequirement {
  @XmlElement(name = "agent-requirement")
  public List<PropEntity> propEntities;

  public PropEntitiesAgentRequirement() {
  }

  public PropEntitiesAgentRequirement(final SBuildType buildType) {
    propEntities = CollectionsUtil.convertCollection(buildType.getRequirements(), new Converter<PropEntity, Requirement>() {
          public PropEntity createFrom(@NotNull final Requirement source) {
            return new PropEntityAgentRequirement(source);
          }
        });
  }
}
