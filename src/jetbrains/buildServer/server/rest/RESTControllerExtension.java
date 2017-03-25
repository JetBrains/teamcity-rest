/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import jetbrains.buildServer.serverSide.ServerExtension;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ConfigurableApplicationContext;

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

  /**
   * @return application context for this controller extension
   */
  @NotNull
  ConfigurableApplicationContext getContext();
}
