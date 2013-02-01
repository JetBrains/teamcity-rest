package jetbrains.buildServer.server.rest.model.files;

import jetbrains.buildServer.server.rest.files.FileDefRef;
import jetbrains.buildServer.server.rest.model.Href;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "file")
@XmlType(name = "FileReference")
public class FileRef extends Href {

  protected FileDefRef fdr;

  protected FileRef() {
  }

  public FileRef(@NotNull final FileDefRef fdr, @NotNull final FileApiUrlBuilder builder) {
    super(builder.getMetadataHref(fdr));
    this.fdr = fdr;
  }

  @NotNull
  @XmlAttribute(name = "name")
  public String getName() {
    return fdr.getName();
  }

  @NotNull
//  @XmlAttribute(name = "path")
  @XmlTransient
  public String getPath() {
    return fdr.getRelativePath();
  }
}
