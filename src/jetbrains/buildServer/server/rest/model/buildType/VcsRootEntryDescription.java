package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.change.VcsRootRef;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "vcs-root-entry", namespace = "submit")
@XmlType(name = "vcs-root-entry", namespace = "submit")
public class VcsRootEntryDescription {
  @XmlAttribute
  public String vcsRootLocator;

  @XmlElement(name = "checkout-rules")
  public String checkoutRules;

  @XmlElement(name = "vcs-root", namespace = "ref")
  public VcsRootRef vcsRootRef;

  public VcsRootEntryDescription() {
  }
}
