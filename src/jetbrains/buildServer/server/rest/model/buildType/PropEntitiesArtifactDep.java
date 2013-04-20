package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="artifact-dependencies")
@SuppressWarnings("PublicField")
public class PropEntitiesArtifactDep {
  @XmlElement(name = "artifact-dependency")
  public List<PropEntityArtifactDep> propEntities;

  public PropEntitiesArtifactDep() {
  }

  public PropEntitiesArtifactDep(final BuildTypeSettings buildType, @NotNull final BeanContext context) {
    final List<SArtifactDependency> artifactDependencies = buildType.getArtifactDependencies();
    propEntities = new ArrayList<PropEntityArtifactDep>(artifactDependencies.size());
    int orderNumber = 0;
    for (SArtifactDependency dependency : artifactDependencies) {
      propEntities.add(new PropEntityArtifactDep(dependency, orderNumber, context));
      orderNumber++;
    }
  }
}
