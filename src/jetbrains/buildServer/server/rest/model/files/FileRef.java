package jetbrains.buildServer.server.rest.model.files;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "file-ref")
@XmlType(name = "file-ref", propOrder = {"name", "href"})
public class FileRef {

  @NotNull @XmlAttribute public String name;
  @NotNull @XmlAttribute public String href;

  protected FileRef() {
  }

  public FileRef(@NotNull final Element element, @NotNull final FileApiUrlBuilder builder) {
    this.href = builder.getMetadataHref(element);
    this.name = element.getName();
  }
}
