package jetbrains.buildServer.server.rest;

import jetbrains.buildServer.serverSide.ServerExtension;
import org.jetbrains.annotations.NotNull;

/**
 * Extension to add new resources (services) to REST API plugin.
 * Please use <tt>RESTControllerExtensionAdapter<tt> when implementing the interface.
 *
 * @author Yegor.Yarko
 *         Date: 01.08.2010
 * @see jetbrains.buildServer.server.rest.RESTControllerExtensionAdapter
 */
public interface RESTControllerExtension extends ServerExtension {
  /**
   * Allows to add packages to scan for JAX-RS resources for REST API resources.
   * The classes from the package should be available in the same classloader as the REST API plugin.
   *
   * @return fully-qualified name of the package to scan for JAX-RS resources.
   */
  @NotNull
  String getPackage();
}
