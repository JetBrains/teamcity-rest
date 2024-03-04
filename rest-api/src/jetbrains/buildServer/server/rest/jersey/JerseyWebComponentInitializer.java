package jetbrains.buildServer.server.rest.jersey;

import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import jetbrains.buildServer.server.rest.util.PluginUtil;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.FuncThrow;
import jetbrains.buildServer.util.NamedDaemonThreadFactory;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.util.Util.doUnderContextClassLoader;

@Component
public class JerseyWebComponentInitializer {
  private final Logger LOG;
  private final AtomicBoolean myWebComponentInitialized = new AtomicBoolean(false);
  private final JerseyWebComponent myWebComponent;
  private final ExtensionsAwareResourceConfig myResourceConfig;
  private final PluginDescriptor myPluginDescriptor;
  private final ClassLoader myClassloader;

  public JerseyWebComponentInitializer(
    @NotNull JerseyWebComponent jerseyWebComponent,
    @NotNull ExtensionsAwareResourceConfig resourceConfig,
    @NotNull PluginDescriptor pluginDescriptor
  ) {
    myResourceConfig = resourceConfig;
    myWebComponent = jerseyWebComponent;
    myPluginDescriptor = pluginDescriptor;
    myClassloader = getClass().getClassLoader();

    LOG = PluginUtil.getLoggerWithPluginName(JerseyWebComponentInitializer.class, myPluginDescriptor);
  }

  public void initJerseyWebComponentAsync() {
    new NamedDaemonThreadFactory("Async Jersey initializer for " + PluginUtil.getIdentifyingText(myPluginDescriptor))
      .newThread(() -> initJerseyWebComponent(() -> "via background initial initialization"))
      .start();
  }

  public void initJerseyWebComponent(@NotNull final Supplier<String> contextDetails) {
    if (!myWebComponentInitialized.get()) {
      NamedThreadFactory.executeWithNewThreadName("Initializing Jersey for " + PluginUtil.getIdentifyingText(myPluginDescriptor) + " " + contextDetails.get(), () -> {
        synchronized (myWebComponentInitialized) {
          if (myWebComponentInitialized.get()) return;

          try {
            // Workaround for https://youtrack.jetbrains.com/issue/TW-7656
            doUnderContextClassLoader(myClassloader, (FuncThrow<Void, Throwable>)() -> {
              // ExtensionsAwareResourceConfig not initialized yet. We should wait for all extensions to load first.
              // Now it's time to initialize and scan for extensions.
              myResourceConfig.onReload(null);

              myWebComponent.init();
              return null;
            });

            myWebComponentInitialized.set(true);
          } catch (Throwable e) {
            LOG.error("Error initializing REST API " + contextDetails.get() + ": " + e + ExceptionMapperBase.addKnownExceptionsData(e, ""), e);
            ExceptionUtil.rethrowAsRuntimeException(e);
          }
        }
      });
    }
  }
}
