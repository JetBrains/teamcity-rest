/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.spring.container.servlet.SpringServlet;
import java.util.Collection;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * @author Yegor.Yarko
 *         Date: 24.03.2009
 */


public class JerseyWebComponent extends SpringServlet {
  private static final long serialVersionUID = 5686455305749079671L;
  private final String myPluginName;

  private Logger LOG = Logger.getInstance(JerseyWebComponent.class.getName());
  private final ExtensionHolder myExtensionHolder;
  private Collection<ConfigurableApplicationContext> myContexts;

  public JerseyWebComponent(final PluginInfo descriptor, final ExtensionHolder extensionHolder) {
    myPluginName = descriptor.getPluginName();
    LOG = Logger.getInstance(JerseyWebComponent.class.getName() + "/" + myPluginName);
    myExtensionHolder = extensionHolder;
  }

  @Override
  protected void initiate(ResourceConfig rc, WebApplication wa) {
    try {
      for (ConfigurableApplicationContext context : myContexts) {
        registerResourceProviders(rc, context);
      }
      wa.initiate(rc, new ExtensionHolderProviderFactory(myExtensionHolder, myPluginName));
    } catch (RuntimeException e) {
      LOG.error("Exception occurred during REST API initialization", e);
      throw e;
    }
  }

  /**
   * Checks for all beans that have @Provider annotation and
   * registers them into Jersey ResourceConfig
   * @param rc config
   * @param springContext spring context
   */
  private void registerResourceProviders(ResourceConfig rc, ConfigurableApplicationContext springContext) {
    //TODO: restrict search to current spring context without parent for speedup
    for (String name : BeanFactoryUtils.beanNamesIncludingAncestors(springContext)) {
      final Class<?> type = ClassUtils.getUserClass(springContext.getType(name));
      if (ResourceConfig.isProviderClass(type)) {
        LOG.debug("Registering Spring bean, " + name + ", of type " + type.getName() + " as a provider class");
        rc.getClasses().add(type);
      } else if (ResourceConfig.isRootResourceClass(type)) {
        LOG.debug("Registering Spring bean, " + name + ", of type " + type.getName() + " as a root resource class");
        rc.getClasses().add(type);
      }
    }
  }

  public void setContexts(@NotNull Collection<ConfigurableApplicationContext> contexts) {
    myContexts = contexts;
  }
}