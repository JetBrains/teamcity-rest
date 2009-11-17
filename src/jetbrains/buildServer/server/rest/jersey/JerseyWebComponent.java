/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.sun.jersey.spi.spring.container.SpringComponentProviderFactory;
import com.sun.jersey.spi.spring.container.servlet.SpringServlet;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author Yegor.Yarko
 *         Date: 24.03.2009
 */


public class JerseyWebComponent extends SpringServlet {
  private static final long serialVersionUID = 5686455305749079671L;

  private static final Logger LOG = Logger.getInstance(JerseyWebComponent.class.getName());
  private ConfigurableApplicationContext myWebApplicationContext;

  @Override
  protected void initiate(ResourceConfig rc, WebApplication wa) {
    try {
      final ConfigurableApplicationContext springContext = getSpringWebContext();

      wa.initiate(rc, new SpringComponentProviderFactory(rc, springContext));
    } catch (RuntimeException e) {
      LOG.error("Exception occurred during REST API initialization", e);
      throw e;
    }
  }

  ConfigurableApplicationContext getSpringWebContext() {
    return myWebApplicationContext;
  }

  public void setWebApplicationContext(final ConfigurableApplicationContext webApplicationContext) {
    myWebApplicationContext = webApplicationContext;
  }
}