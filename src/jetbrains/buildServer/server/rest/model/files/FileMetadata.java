package jetbrains.buildServer.server.rest.model.files;

import jetbrains.buildServer.server.rest.files.FileDef;
import jetbrains.buildServer.server.rest.files.FileDefRef;
import jetbrains.buildServer.server.rest.model.Href;
import jetbrains.buildServer.server.rest.model.Util;
import jetbrains.buildServer.util.ArchiveType;
import jetbrains.buildServer.util.ArchiveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Date;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "file")
@XmlType(name = "FileMetadata", propOrder = {"name", "href", "size", "modificationTime", "content", "parent", "children"})
public class FileMetadata extends FileRef {

  protected final FileDef fdr;
  protected final FileApiUrlBuilder fileApiUrlBuilder;

  @SuppressWarnings("UnusedDeclaration")
  public FileMetadata() {
    this.fdr = null;
    this.fileApiUrlBuilder = null;
  }

  public FileMetadata(@NotNull final FileDef fd, @NotNull FileApiUrlBuilder builder) {
    super(fd, builder);
    this.fdr = fd;
    this.fileApiUrlBuilder = builder;
  }

  @Nullable
  @XmlAttribute(name = "size")
  public Long getSize() {
    return fdr.isDirectory() ? null : fdr.getSize();
  }

  @NotNull
  @XmlAttribute(name = "modificationTime")
  public String getModificationTime() {
    //noinspection ConstantConditions
    return Util.formatTime(new Date(fdr.getTimestamp()));
  }

  @Nullable
  @XmlElement(name = "children")
  public Href getChildren() {
    if (!fdr.isDirectory() && ArchiveUtil.getArchiveType(fdr.getName()) == ArchiveType.NOT_ARCHIVE) {
      return null;
    }
    return new Href(fileApiUrlBuilder.getChildrenHref(fdr));
  }

  @Nullable
  @XmlElement(name = "content")
  public Href getContent() {
    if (fdr.isDirectory()) {
      return null;
    }
    return new Href(fileApiUrlBuilder.getContentHref(fdr));
  }

  @Nullable
  @XmlElement(name = "parent")
  public FileRef getParent() {
    FileDefRef parent = fdr.getParent();
    if (parent == null) {
      return null;
    }
    return new FileRef(parent, fileApiUrlBuilder);
  }

}
