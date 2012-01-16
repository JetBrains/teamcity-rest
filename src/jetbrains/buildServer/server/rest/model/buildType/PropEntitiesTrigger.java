package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="triggers")
@SuppressWarnings("PublicField")
public class PropEntitiesTrigger {
  @XmlElement(name = "trigger")
  public List<PropEntityTrigger> propEntities;

  public PropEntitiesTrigger() {
  }

  public PropEntitiesTrigger(final BuildTypeSettings buildType) {
    propEntities = CollectionsUtil.convertCollection(buildType.getBuildTriggersCollection(), new Converter<PropEntityTrigger, BuildTriggerDescriptor>() {
          public PropEntityTrigger createFrom(@NotNull final BuildTriggerDescriptor source) {
            return new PropEntityTrigger(source, buildType);
          }
        });
  }
}
