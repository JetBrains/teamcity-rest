package jetbrains.buildServer.server.rest;

import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Yegor.Yarko
 * Date: 29.11.2009
 * Time: 12:26:49
 * To change this template use File | Settings | File Templates.
 */
public interface PathTransformator {
  @NotNull
  String getTransformedPath(@NotNull String path);
}
