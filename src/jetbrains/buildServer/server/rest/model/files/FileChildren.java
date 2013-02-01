package jetbrains.buildServer.server.rest.model.files;

import jetbrains.buildServer.server.rest.files.FileDefRef;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "files")
@XmlType(name = "FileChildren")
public class FileChildren {

  private Collection<FileDefRef> myChildren;
  private FileApiUrlBuilder myFileApiUrlBuilder;

  @SuppressWarnings("UnusedDeclaration")
  public FileChildren() {
  }

  public FileChildren(@NotNull final Collection<FileDefRef> children, @NotNull final FileApiUrlBuilder urlsBuilder) {
    myChildren = children;
    myFileApiUrlBuilder = urlsBuilder;
  }

  @NotNull
  @XmlElement(name = "file", nillable = false)
  public List<FileRef> getFiles() {
    return CollectionsUtil.convertCollection(myChildren, new Converter<FileRef, FileDefRef>() {
      public FileRef createFrom(@NotNull FileDefRef source) {
        return new FileRef(source, myFileApiUrlBuilder);
      }
    });
  }
}
