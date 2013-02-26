package jetbrains.buildServer.server.rest.model.files;

import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "file")
@XmlType
public class FileRef extends Href {

  @NotNull
  private String name;
  @NotNull
  private String path;

  protected FileRef() {
  }

  public FileRef(@NotNull final String name, @NotNull final String path, @NotNull final String href) {
    super(href);
    this.name = name;
    this.path = path;
  }

  public FileRef(@NotNull final Element element, @NotNull final FileApiUrlBuilder builder) {
    super(builder.getMetadataHref(element));
    this.name = element.getName();
    this.path = element.getFullName();
  }

  @NotNull
  @XmlAttribute(name = "name")
  public String getName() {
    return name;
  }

  @NotNull
//  @XmlAttribute(name = "path")
  @XmlTransient
  public String getPath() {
    return path;
  }
}
