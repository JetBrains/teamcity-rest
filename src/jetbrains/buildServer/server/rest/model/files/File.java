package jetbrains.buildServer.server.rest.model.files;

import java.util.Date;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.web.artifacts.browser.ArtifactTreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "file")
@XmlType(name = "file", propOrder = {"size", "modificationTime",
"parent", "content", "children"})
public class File extends FileRef {

  protected final FileApiUrlBuilder fileApiUrlBuilder;
  protected final Element parent;
  protected final ArtifactTreeElement element;

  @SuppressWarnings("UnusedDeclaration")
  public File() {
    fileApiUrlBuilder = null;
    parent = null;
    element = null;
  }

  public File(@NotNull final ArtifactTreeElement element, @Nullable final Element parent, @NotNull final FileApiUrlBuilder builder) {
    super(element, builder);
    this.element = element;
    this.fileApiUrlBuilder = builder;
    this.parent = parent;
  }

  @Nullable
  @XmlAttribute(name = "size")
  public Long getSize() {
    return element.isContentAvailable() ? element.getSize() : null;
  }

  @Nullable
  @XmlAttribute(name = "modificationTime")
  public String getModificationTime() {
    final Long lastModified = element.getLastModified();
    if (lastModified == null || lastModified <= 0) {
      return null;
    }
    //noinspection ConstantConditions
    return Util.formatTime(new Date(lastModified));
  }

  @Nullable
  @XmlElement(name = "parent")
  public FileRef getParent() {
    return parent != null ? new FileRef(parent, fileApiUrlBuilder) : null;
  }

  @Nullable
  @XmlElement(name = "content")
  public Href getContent() {
    if (!element.isContentAvailable()) {
      return null;
    }
    return new Href(fileApiUrlBuilder.getContentHref(element));
  }

  @Nullable
  @XmlElement(name = "children")
  public Href getChildren() {
    if (element.isLeaf()) {
      return null;
    }
    return new Href(fileApiUrlBuilder.getChildrenHref(element));
  }
}
