/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.core.spi.scanning.PackageNamesScanner;
import com.sun.jersey.spi.container.ReloadListener;
import com.sun.jersey.spi.scanning.AnnotationScannerListener;
import com.sun.jersey.spi.scanning.PathProviderScannerListener;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.plugins.bean.ServerPluginInfo;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.RESTControllerExtension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.LocatorResourceListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Based on {@link com.sun.jersey.api.core.ScanningResourceConfig}
 * But uses classloaders from extensions.
 *
 * @since 9.0
 */
public class ExtensionsAwareResourceConfig extends DefaultResourceConfig implements ReloadListener {
  private static Logger LOG = Logger.getInstance(ExtensionsAwareResourceConfig.class.getName());

  @NotNull private final APIController myController;
  private final Set<Class<?>> myCachedClasses = new HashSet<Class<?>>();

  @Autowired
  public ExtensionsAwareResourceConfig(@NotNull final APIController controller, @SuppressWarnings("SpringJavaAutowiringInspection") final ServerPluginInfo pluginDescriptor) {
    myController = controller;
    LOG = Logger.getInstance(ExtensionsAwareResourceConfig.class.getName() + "/" + pluginDescriptor.getPluginName());
  }

  @NotNull
  public Collection<Pair<String[], ClassLoader>> getScanningInfo() {
    final ArrayList<Pair<String[], ClassLoader>> scanners = new ArrayList<Pair<String[], ClassLoader>>();
    {
      final ClassLoader cl = getClass().getClassLoader();
      scanners.add(new Pair<String[], ClassLoader>(myController.getBasePackages(), cl));
    }
    final Set<String> packagesFromExtensions = new TreeSet<String>();
    for (RESTControllerExtension extension : myController.getExtensions()) {
      final ClassLoader cl = extension.getClass().getClassLoader();
      final String pkg = extension.getPackage();
      scanners.add(new Pair<String[], ClassLoader>(new String[]{pkg}, cl));
      packagesFromExtensions.add(pkg);
    }
    if (!packagesFromExtensions.isEmpty()) {
      LOG.info("Packages registered by rest extensions: " + packagesFromExtensions);
    }
    return scanners;
  }

  /**
   * Initialize and scan for root resource and provider classes in rest-core and extensions.
   */
  public void init() {
    final Set<Class<?>> classes = getClasses();
    for (Pair<String[], ClassLoader> pair : getScanningInfo()) {
      final AnnotationScannerListener asl = new PathProviderScannerListener(pair.second);
      final LocatorResourceListener lrl = new LocatorResourceListener(pair.second);
      final PackageNamesScanner scanner = new PackageNamesScanner(pair.second, pair.first);
      try {
        scanner.scan(asl);
        scanner.scan(lrl);
      } catch (Throwable e) {
        String message = "Error initializing REST component while scanning for resources for " + myController.getPluginIdentifyingText() +
                         " for packages " + Arrays.toString(pair.first) + " via classloader '" + pair.second.toString() + "'.";
        if (Arrays.stream(pair.first).anyMatch(s -> s.startsWith("jetbrains.buildServer.server.rest."))) {
          // treat this as core plugin initialization error, so do not let anything to initialize and report errors on following requests instead of ignoring the extensions
          // (replying with 500 response and erorr detials instead of 404)
          throw new RuntimeException(message, e);
        }
        message += " Jersey resources located in the packages are ignored. Error: " + e.toString() + ExceptionMapperBase.addKnownExceptionsData(e, "");
        LOG.error(message, e);
        Loggers.SERVER.error(message);
      }
      classes.addAll(asl.getAnnotatedClasses());
      classes.addAll(lrl.getAnnotatedClasses());
    }

    if (!classes.isEmpty()) {
      final Set<Class> rootResourceClasses = get(Path.class);
      if (rootResourceClasses.isEmpty()) {
        LOG.warn("No root resource classes found.");
      } else {
        logClasses("Root resource classes found:", rootResourceClasses);
      }

      final Set<Class> providerClasses = get(Provider.class);
      if (providerClasses.isEmpty()) {
        LOG.info("No provider classes found.");
      } else {
        logClasses("Provider classes found:", providerClasses);
      }

      final Set<Class> locatorClasses = get(LocatorResource.class);
      if (locatorClasses.isEmpty()) {
        LOG.info("No locator classes found.");
      } else {
        logClasses("Locator classes found:", locatorClasses);
      }

    }

    myCachedClasses.clear();
    myCachedClasses.addAll(classes);
  }

  public void onReload() {
    final Set<Class<?>> add = new HashSet<Class<?>>();
    final Set<Class<?>> remove = new HashSet<Class<?>>();
    final Set<Class<?>> classes = getClasses();

    for (Class c : classes) {
      if (!myCachedClasses.contains(c)) {
        add.add(c);
      }
    }

    for (Class c : myCachedClasses) {
      if (!classes.contains(c)) {
        remove.add(c);
      }
    }

    classes.clear();

    init();

    classes.addAll(add);
    classes.removeAll(remove);
  }

  @NotNull
  private Set<Class> get(@NotNull final Class<? extends Annotation> ac) {
    Set<Class> s = new HashSet<Class>();
    for (Class c : getClasses()) {
      if (c.isAnnotationPresent(ac)) {
        s.add(c);
      }
    }
    return s;
  }

  private void logClasses(@NotNull final String s, @NotNull final Set<Class> classes) {
    final StringBuilder b = new StringBuilder();
    b.append(s);
    for (Class c : classes) {
      b.append('\n').append("  ").append(c);
    }

    LOG.info(b.toString());
  }
}
