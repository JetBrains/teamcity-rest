package jetbrains.buildServer.server.rest.model.files;

import jetbrains.buildServer.util.browser.Element;

/**
 * @author Vladislav.Rassokhin
 */
public interface FileApiUrlBuilder {
  public String getMetadataHref(Element element);

  public String getChildrenHref(Element element);

  public String getContentHref(Element element);
}
