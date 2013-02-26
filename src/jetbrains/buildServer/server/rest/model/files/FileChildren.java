package jetbrains.buildServer.server.rest.model.files;

import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * @author Vladislav.Rassokhin
 * @since 8.0
 */
@XmlRootElement(name = "files")
@XmlType
public class FileChildren {

  private List<FileRef> myChildren;

  @SuppressWarnings("UnusedDeclaration")
  public FileChildren() {
  }

  public FileChildren(@NotNull final Iterable<Element> children, @NotNull final FileApiUrlBuilder urlsBuilder) {
    myChildren = CollectionsUtil.convertCollection(children, new Converter<FileRef, Element>() {
      public FileRef createFrom(@NotNull Element source) {
        return new FileRef(source, urlsBuilder);
      }
    });
  }

  @NotNull
  @XmlElementRef(name = "files", type = FileRef.class)
  public List<FileRef> getFiles() {
    return myChildren;
  }
}
