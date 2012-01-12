/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.ExtensionHolder;
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

  private static final Logger LOG = Logger.getInstance(JerseyWebComponent.class.getName());
  private ConfigurableApplicationContext myWebApplicationContext;
  private ExtensionHolder myExtensionHolder;

  @Override
  protected void initiate(ResourceConfig rc, WebApplication wa) {
    try {
      registerResourceProfiders(rc, myWebApplicationContext);
      wa.initiate(rc, new ExtensionHolderProviderFactory(myExtensionHolder));
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
  private void registerResourceProfiders(ResourceConfig rc, ConfigurableApplicationContext springContext) {
    //TODO: restrict search to current spring context without parent for speedup
    for (String name : BeanFactoryUtils.beanNamesIncludingAncestors(springContext)) {
      final Class<?> type = ClassUtils.getUserClass(springContext.getType(name));
      if (ResourceConfig.isProviderClass(type)) {
        LOG.info("Registering Spring bean, " + name +
                ", of type " + type.getName() +
                " as a provider class");
        rc.getClasses().add(type);
      } else if (ResourceConfig.isRootResourceClass(type)) {
        LOG.info("Registering Spring bean, " + name +
                ", of type " + type.getName() +
                " as a root resource class");
        rc.getClasses().add(type);
      }
    }
  }

  public void setWebApplicationContext(@NotNull final ConfigurableApplicationContext webApplicationContext) {
    myWebApplicationContext = webApplicationContext;
  }

  public void setExtensionHolder(@NotNull final ExtensionHolder extensionHolder) {
    myExtensionHolder = extensionHolder;
  }
}