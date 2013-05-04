package jetbrains.buildServer.server.rest.model.change;

import com.intellij.openapi.util.text.StringUtil;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.VcsRootFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 16.04.2009
 */
@SuppressWarnings("PublicField")
@XmlRootElement(name = "vcs-root-ref")
@XmlType(name = "vcs-root-ref", propOrder = {"id", "name", "href"})
public class VcsRootRef {
  @XmlAttribute
  public String id;
  @XmlAttribute
  public String name;
  @XmlAttribute
  public String href;
  /**
   * This is used only when posting a link to a build type.
   */
  @XmlAttribute public String locator;

  public VcsRootRef() {
  }

  public VcsRootRef(jetbrains.buildServer.vcs.VcsRoot root, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    this.id = String.valueOf(root.getId());
    this.href = apiUrlBuilder.getHref(root);
    this.name = root.getName();
  }

  @NotNull
  public SVcsRoot getVcsRoot(@NotNull VcsRootFinder vcsRootFinder) {
    String locatorText = "";
//    if (internalId != null) locatorText = "internalId:" + internalId;
    if (id != null) locatorText += (!locatorText.isEmpty() ? "," : "") + "id:" + id;
    if (locatorText.isEmpty()) {
      locatorText = locator;
    } else {
      if (locator != null) {
        throw new BadRequestException("Both 'locator' and 'id' attributes are specified. Only one should be present.");
      }
    }
    if (StringUtil.isEmpty(locatorText)){
      throw new BadRequestException("No VCS root specified. Either 'id' or 'locator' attribute should be present.");
    }
    return vcsRootFinder.getVcsRoot(locatorText);
  }
}
