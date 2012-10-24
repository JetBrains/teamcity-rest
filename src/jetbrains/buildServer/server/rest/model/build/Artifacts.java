package jetbrains.buildServer.server.rest.model.build;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "artifacts")
public class Artifacts {
  private List<Artifact> myArtifactList;

  public Artifacts() {
  }

  public Artifacts(final List<Artifact> artifactList) {
    myArtifactList = new ArrayList<Artifact>(artifactList);
  }

  @XmlElementRef
  public List<Artifact> getArtifact() {
    return myArtifactList;
  }

  @XmlAttribute(name = "count")
  public int getCount() {
    return myArtifactList.size();
  }
}