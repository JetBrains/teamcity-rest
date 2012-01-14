package jetbrains.buildServer.server.rest.model.buildType;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "snapshot-dependencies")
@SuppressWarnings("PublicField")
public class PropEntitiesSnapshotDep {
  @XmlElement(name = "snapshot-dependency")
  public List<PropEntitySnapshotDep> propEntities;

  public PropEntitiesSnapshotDep() {
  }

  public PropEntitiesSnapshotDep(final BuildTypeSettings buildType) {
    propEntities = CollectionsUtil.convertCollection(buildType.getDependencies(), new Converter<PropEntitySnapshotDep, Dependency>() {
      public PropEntitySnapshotDep createFrom(@NotNull final Dependency source) {
        return new PropEntitySnapshotDep(source);
      }
    });
  }
}
