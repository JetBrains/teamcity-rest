package jetbrains.buildServer.server.rest.model;

import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "href")
@XmlType(name = "HReference")
public class Href {
  protected String href;

  public Href() {
  }

  public Href(@NotNull final String href) {
    this.href = href;
  }

  @XmlAttribute(name = "href")
  public String getHref() {
    return href;
  }
}
