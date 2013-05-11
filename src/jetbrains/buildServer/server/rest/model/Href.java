package jetbrains.buildServer.server.rest.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Rassokhin
 */
@XmlRootElement(name = "href")
@XmlType(name = "HReference")
public class Href {
  protected String href;

  public Href() {
  }

  public Href(@NotNull final String longHref) {
    href = longHref;
  }

  public Href(@NotNull final String shortHref, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    href = apiUrlBuilder.transformRelativePath(shortHref);
  }

  @NotNull
  @XmlAttribute(name = "href")
  public String getHref() {
    return href;
  }
}
