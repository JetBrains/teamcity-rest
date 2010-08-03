package jetbrains.buildServer.server.rest;

import org.jetbrains.annotations.NotNull;

/**
 * @see jetbrains.buildServer.server.rest.RESTControllerExtension
 * @author Yegor.Yarko
 *         Date: 03.08.2010
 */
public abstract class RESTControllerExtensionAdapter implements RESTControllerExtension{
  @NotNull
  public abstract String getPackage();
}
