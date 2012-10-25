package jetbrains.buildServer.server.rest;

import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 29.11.2009
 */
public interface PathTransformator {
  @NotNull
  String getTransformedPath(@NotNull String path);
}
