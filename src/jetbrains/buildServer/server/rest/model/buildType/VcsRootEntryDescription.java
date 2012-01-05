package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.change.VcsRoot;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "vcs-root-entry")
public class VcsRootEntryDescription {
  @XmlAttribute
  public String vcsRootLocator;

  @XmlElement(name = "checkout-rules")
  public String checkoutRules;

  @XmlElement(name = "vcs-root")
  public VcsRoot.VcsRootRef vcsRootRef;

  public VcsRootEntryDescription() {
  }
}
