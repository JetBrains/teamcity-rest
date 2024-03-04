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

import java.util.*;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import jetbrains.buildServer.plugins.bean.PluginInfo;
import org.glassfish.jersey.servlet.ServletContainer;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * This is the entry point into Jersey, which itself routes requests to proper *Request class.
 * We route all requests here from APIController.
 */
@Component("jerseyWebComponent")
public class JerseyWebComponent extends ServletContainer {
  private static final long serialVersionUID = 5686455305749079671L;
  private final Map<String, String> initParameters = new HashMap<>();
  private final ApplicationContext myApplicationContext;

  public JerseyWebComponent(@NotNull ExtensionsAwareResourceConfig resourceConfig,
                            @NotNull ApplicationContext applicationContext) {
    super(resourceConfig);

    myApplicationContext = applicationContext;
  }

  @Override
  public ServletConfig getServletConfig() {
    return new ServletConfig() {
      @Override
      public String getServletName() {
        return "jerseyServlet";
      }

      @Override
      public ServletContext getServletContext() {
        return JerseyWebComponent.this.getServletContext();
      }

      @Override
      public String getInitParameter(String name) {
        return JerseyWebComponent.this.getInitParameter(name);
      }

      @Override
      public Enumeration<String> getInitParameterNames() {
        return JerseyWebComponent.this.getInitParameterNames();
      }
    };
  }

  @Override
  public String getInitParameter(final String s) {
    return initParameters.get(s);
  }

  @Override
  public Enumeration<String> getInitParameterNames() {
    return new Vector<>(initParameters.keySet()).elements();
  }

  @Override
  public ServletContext getServletContext() {
    //return APIController.this.getServletContext();
    // workaround for https://youtrack.jetbrains.com/issue/TW-7656
    for (ApplicationContext ctx = myApplicationContext; ctx != null; ctx = ctx.getParent()) {
      if (ctx instanceof WebApplicationContext) {
        return ((WebApplicationContext)ctx).getServletContext();
      }
    }
    throw new RuntimeException("WebApplication context was not found.");
  }
}