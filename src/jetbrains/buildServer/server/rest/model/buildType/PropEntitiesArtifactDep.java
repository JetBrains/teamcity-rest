package jetbrains.buildServer.server.rest.model.buildType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name="artifact-dependencies")
@SuppressWarnings("PublicField")
public class PropEntitiesArtifactDep {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "artifact-dependency")
  public List<PropEntityArtifactDep> propEntities;

  public PropEntitiesArtifactDep() {
  }

  public PropEntitiesArtifactDep(@NotNull final List<SArtifactDependency> artifactDependencies, @NotNull final BeanContext context) {
    propEntities = new ArrayList<PropEntityArtifactDep>(artifactDependencies.size());
    int orderNumber = 0;
    for (SArtifactDependency dependency : artifactDependencies) {
      propEntities.add(new PropEntityArtifactDep(dependency, orderNumber, context));
      orderNumber++;
    }
    count = ValueWithDefault.decideDefault(null, propEntities.size());
  }

  @NotNull
  public List<SArtifactDependency> getFromPosted(@NotNull final ServiceLocator serviceLocator) {
    if (propEntities == null){
      return Collections.emptyList();
    }
    final ArrayList<SArtifactDependency> result = new ArrayList<SArtifactDependency>(propEntities.size());
    for (PropEntityArtifactDep entity : propEntities) {
      result.add(entity.createDependency(serviceLocator));
    }
    return result;
  }
}
