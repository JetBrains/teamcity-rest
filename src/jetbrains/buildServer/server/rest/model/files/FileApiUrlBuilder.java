package jetbrains.buildServer.server.rest.model.files;

import jetbrains.buildServer.util.browser.Element;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Rassokhin
 */
public interface FileApiUrlBuilder {
  public String getMetadataHref(@Nullable Element element);

  public String getChildrenHref(@Nullable Element element);

  public String getContentHref(@Nullable Element element);
}
