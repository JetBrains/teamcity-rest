/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import javax.inject.Inject;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;

@Provider
public class BeanContextProvider implements Feature {
  @Override
  public boolean configure(FeatureContext context) {
    context.register(new AbstractBinder() {
      @Override
      protected void configure() {
        bindFactory(Resolver.class).to(BeanContext.class)
                                   .in(RequestScoped.class);
      }
    });

    return true;
  }

  public static class Resolver implements Factory<BeanContext> {
    @Inject
    private BeanFactory myBeanFactory;
    @Inject
    private ServiceLocator myServiceLocator;

    @Inject
    private ApiUrlBuilder myApiUrlBuilder;

    @Override
    public BeanContext provide() {
      return new BeanContext(myBeanFactory, myServiceLocator, myApiUrlBuilder);
    }

    @Override
    public void dispose(BeanContext instance) { }
  }
}
