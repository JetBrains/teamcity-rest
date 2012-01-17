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
@XmlRootElement(name = "vcs-root", namespace = "ref")
@XmlType(name = "vcs-root", namespace = "ref", propOrder = {"href", "name"})
public class VcsRootRef {
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String href;

  public VcsRootRef() {
  }

  public VcsRootRef(jetbrains.buildServer.vcs.VcsRoot root, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    this.href = apiUrlBuilder.getHref(root);
    this.name = root.getName();
  }
}
