/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.jersey;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.*;
import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.RESTControllerExtension;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.Spring2JerseyBridge;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.util.PluginUtil;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.message.MessageProperties;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.internal.scanning.AnnotationAcceptingListener;
import org.glassfish.jersey.server.internal.scanning.PackageNamesScanner;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * This class is responsible for initializing all the necessary resources and manually binding some services which can't be bind via auto discovery.
 * Looks through plugin extentions and binds all necessary classes as well.
 */
@Component
public class ExtensionsAwareResourceConfig extends ResourceConfig implements ContainerLifecycleListener {
  private static Logger LOG = Logger.getInstance(ExtensionsAwareResourceConfig.class.getName());
  private final SBuildServer myServer;

  public ExtensionsAwareResourceConfig(@NotNull SBuildServer server,
                                       @NotNull ServerPluginInfo pluginDescriptor,
                                       @NotNull Spring2JerseyBridge spring2JerseyBridge) {
    myServer = server;
    LOG = PluginUtil.getLoggerWithPluginName(ExtensionsAwareResourceConfig.class, pluginDescriptor);

    register(MultiPartFeature.class);

    register(spring2JerseyBridge);

    property(ServerProperties.WADL_FEATURE_DISABLE, true);
    property(MessageProperties.XML_FORMAT_OUTPUT, TeamCityProperties.getBoolean(APIController.REST_RESPONSE_PRETTYFORMAT));
  }

  /**
   * Initialize and scan for root resource and provider classes in this plugin and it's extensions.
   */
  public void init() {
    // Scan and register provider classes for this plugin
    packagesWithLocatorResources(getClass().getClassLoader(), APIController.getBasePackages());

    Set<Class<?>> loggedClasses = new HashSet<>(getClasses());
    LOG.info("Registered following providers in the rest plugin itself");
    logRegisteredResources(loggedClasses);

    for (RESTControllerExtension extension : PluginUtil.getRestExtensions(myServer)) {
      final ClassLoader cl = extension.getClass().getClassLoader();
      final String pkg = extension.getPackage();

      packagesWithLocatorResources(cl, pkg);
      registerSpringBeans(extension.getContext());

      LOG.info("Registered following providers from extention at " + pkg);

      Set<Class<?>> pluginClasses = new HashSet<>(getClasses());
      pluginClasses.removeAll(loggedClasses);
      logRegisteredResources(pluginClasses);

      loggedClasses = new HashSet<>(getClasses());
    }
  }

  private void logRegisteredResources(@NotNull Set<Class<?>> classes) {
    final Set<Class<?>> rootResourceClasses = get(Path.class, classes);
    if (rootResourceClasses.isEmpty()) {
      LOG.warn("No @Path resource classes found.");
    } else {
      logClasses("@Path resource classes found:", rootResourceClasses);
    }

    final Set<Class<?>> providerClasses = get(Provider.class, classes);
    if (providerClasses.isEmpty()) {
      LOG.info("No @Provider classes found.");
    } else {
      logClasses("@Provider classes found:", providerClasses);
    }

    final Set<Class<?>> locatorClasses = get(LocatorResource.class, classes);
    if (locatorClasses.isEmpty()) {
      LOG.info("No @LocatorResource classes found.");
    } else {
      logClasses("@LocatorResource classes found:", locatorClasses);
    }
  }

  /**
   *  This is implemented looking at Jersey org.glassfish.jersey.server.ResourceConfig#scanClasses.
   */
  private void packagesWithLocatorResources(@NotNull ClassLoader classLoader, @NotNull String... packages) {
    packages(true, classLoader, packages);

    AnnotationAcceptingListener annotationListener = new AnnotationAcceptingListener(classLoader, LocatorResource.class);

    try(PackageNamesScanner scanner = new PackageNamesScanner(classLoader, packages, true)) {
      while (scanner.hasNext()) {
        final String next = scanner.next();
        if (annotationListener.accept(next)) {
          final InputStream in = scanner.open();
          try {
            annotationListener.process(next, in);
          } catch (final IOException e) {
            LOG.infoAndDebugDetails(String.format("Unable to process resource '%s' when looking for classes annotated with '%s'", next, LocatorResource.class.getSimpleName()), e);
          } finally {
            try {
              in.close();
            } catch (final IOException ex) {
              LOG.debug(String.format("Unable to close InputStream while processing resource '%s' due to:\n %s", next, ex));
            }
          }
        }
      }
    }

    registerClasses(annotationListener.getAnnotatedClasses());
  }

  @Override
  public void onStartup(Container container) { }


  @Override
  public void onShutdown(Container container) { }

  @Override
  public void onReload(Container container) {
    // TODO: This is a container reload, not a plugin reload, so we may want to avoid rescaning everything and use cached classes instead.

    init();
  }

  private void registerSpringBeans(ConfigurableApplicationContext extentionContext) {
    //TODO: restrict search to current spring context without parent for speedup
    for (String name : BeanFactoryUtils.beanNamesIncludingAncestors(extentionContext)) {
      Class<?> beanClass = extentionContext.getType(name);
      if (beanClass == null) {
        // Should not happen
        continue;
      }
      Class<?> type = ClassUtils.getUserClass(beanClass);
      if (isProviderClass(type)) {
        LOG.debug("Registering Spring bean, " + name + ", of type " + type.getName() + " as a provider class");
        register(type);
      } else if (isRootResourceClass(type)) {
        LOG.debug("Registering Spring bean, " + name + ", of type " + type.getName() + " as a root resource class");
        register(type);
      } else if (isLocatorResourceClass(type)) {
        LOG.debug("Registering Spring bean, " + name + ", of type " + type.getName() + " as a locator resource class");
        register(type);
      }
    }
  }

  @NotNull
  private static Set<Class<?>> get(@NotNull final Class<? extends Annotation> ac, @NotNull Set<Class<?>> classes) {
    Set<Class<?>> s = new HashSet<>();
    for (Class<?> c : classes) {
      if (c.isAnnotationPresent(ac)) {
        s.add(c);
      }
    }
    return s;
  }

  private static void logClasses(@NotNull final String s, @NotNull final Set<Class<?>> classes) {
    final StringBuilder b = new StringBuilder();
    b.append(s);
    for (Class<?> c : classes) {
      b.append('\n').append("  ").append(c);
    }

    LOG.info(b.toString());
  }

  /**
   * Determine if a class is a root resource class.
   *
   * @param c the class.
   * @return true if the class is a root resource class, otherwise false
   *         (including if the class is null).
   */
  private static boolean isRootResourceClass(Class<?> c) {
    if (c == null)
      return false;

    if (c.isAnnotationPresent(Path.class)) return true;

    for (Class<?> i : c.getInterfaces())
      if (i.isAnnotationPresent(Path.class)) return true;

    return false;
  }

  /**
   * Determine if a class is a provider class.
   *
   * @param c the class.
   * @return true if the class is a provider class, otherwise false
   *         (including if the class is null)
   */
  private static boolean isProviderClass(Class<?> c) {
    return c != null && c.isAnnotationPresent(Provider.class);
  }

  /**
   * Determine if a class is a locator resource class.
   *
   * @param c the class.
   * @return true if the class is a locator resource class, otherwise false
   *         (including if the class is null)
   */
  private static boolean isLocatorResourceClass(Class<?> c) {
    return c != null && c.isAnnotationPresent(LocatorResource.class);
  }
}