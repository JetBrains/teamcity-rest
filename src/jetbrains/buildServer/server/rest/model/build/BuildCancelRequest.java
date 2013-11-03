package jetbrains.buildServer.server.rest.model.build;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Yegor.Yarko
 *         Date: 03.11.13
 */
@XmlRootElement(name = "buildCancelRequest")
@XmlType(name = "buildCancelRequest")
public class BuildCancelRequest {
  public BuildCancelRequest() {
  }

  @XmlAttribute(name = "comment")
  public String comment;

  @XmlAttribute(name = "readdIntoQueue")
  public boolean readdIntoQueue;

}