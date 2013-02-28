package jetbrains.buildServer.server.rest.model.files;

import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * @author Vladislav.Rassokhin
 * @since 8.0
 */
@SuppressWarnings("UnusedDeclaration")
@XmlRootElement(name = "files")
@XmlType
public class Files {

  private List<File> myChildren;

  public Files() {
  }

  public Files(@NotNull final List<File> children) {
    myChildren = children;
  }

  @NotNull
  @XmlElementRef(name = "files", type = File.class)
  public List<File> getFiles() {
    return myChildren;
  }
}
