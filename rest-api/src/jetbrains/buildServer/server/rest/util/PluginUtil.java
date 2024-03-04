package jetbrains.buildServer.server.rest.util;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;
import jetbrains.buildServer.server.rest.RESTControllerExtension;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

public final class PluginUtil {
  private PluginUtil() { }

  /**
   * Human-readable plugin description to be used in logs and thread names.
   * Usefull to be able to distinguish between different versions of rest-api plugin installed on the same server.
   */
  @NotNull
  public static String getIdentifyingText(@NotNull PluginDescriptor pluginDescriptor) {
    return "REST API plugin (" + pluginDescriptor.getPluginName() + ")";
  }

  /**
   * Human-readable plugin description to be used in logs and thread names.
   * Usefull to be able to distinguish between different versions of rest-api plugin installed on the same server.
   */
  @NotNull
  public static Logger getLoggerWithPluginName(@NotNull Class<?> clazz, @NotNull PluginDescriptor pluginDescriptor) {
    return Logger.getInstance(clazz.getName() + "/" + pluginDescriptor.getPluginName());
  }

  @NotNull
  public static Collection<RESTControllerExtension> getRestExtensions(@NotNull SBuildServer server) {
    return server.getExtensions(RESTControllerExtension.class);
  }
}
