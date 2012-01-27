package jetbrains.buildServer.server.rest.model.change;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "vcs-root-instance-ref")
@XmlType(name = "vcs-root-instance-ref", propOrder = {"href", "name", "vcsRootId", "id"})
public class VcsRootInstanceRef {
  @XmlAttribute
  public String id;
  @XmlAttribute(name = "vcs-root-id")
  public String vcsRootId;
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String href;

  public VcsRootInstanceRef() {
  }

  public VcsRootInstanceRef(jetbrains.buildServer.vcs.VcsRootInstance root, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    this.id = String.valueOf(root.getId());
    this.vcsRootId = String.valueOf(root.getParentId());
    this.name = root.getName();
    this.href = apiUrlBuilder.getHref(root);
  }
}
