/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.jersey.provider.annotated;

import java.lang.reflect.Type;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import jetbrains.buildServer.ServiceLocator;
import org.glassfish.hk2.api.*;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Provides a one-way bridge between Jersey 2 IoC implementation (HK2) and TeamCity Spring context,
 * allowing to get beans from spring context and inject using either @Inject or @Context annotations.
 * <br/>
 * This class needs to be a @Component as we need to inject jetbrains.buildServer.ServiceLocator instance to bootstrap the bridge. <br/>
 * By the same reasoning, instance of this class must be manually registered in our Jersey ResourceConfig implementation,
 * see {@link jetbrains.buildServer.server.rest.jersey.ExtensionsAwareResourceConfig}.
 */
@Component
public class Spring2JerseyBridge extends AbstractBinder {
  private final ServiceLocator myServiceLocator;
  private final JerseyInjectableBeansHolder myJerseyInjectableBeansHolder;

  public Spring2JerseyBridge(@NotNull ServiceLocator serviceLocator, @NotNull JerseyInjectableBeansHolder jerseyInjectableBeansHolder) {
    myServiceLocator = serviceLocator;
    myJerseyInjectableBeansHolder = jerseyInjectableBeansHolder;
  }

  @Override
  protected void configure() {
    // Need to bind this explicitely to avoid bootstrapping issues.
    bind(myServiceLocator)
      .to(ServiceLocator.class);

    bind(myJerseyInjectableBeansHolder)
      .to(JerseyInjectableBeansHolder.class);

    bind(ContextAnnotationInjectionResolver.class)
      .to(new TypeLiteral<InjectionResolver<Context>>() {})
      .in(Singleton.class)
      .ranked(Integer.MAX_VALUE);

    bind(InjectAnnotationInjectionResolver.class)
      .to(new TypeLiteral<InjectionResolver<Inject>>() {})
      .in(Singleton.class)
      .ranked(Integer.MAX_VALUE);
  }

  private static Object resolve(Injectee injectee, JerseyInjectableBeansHolder beansHolder, ServiceLocator serviceLocator) {
    Type type = injectee.getRequiredType();
    if (!(type instanceof Class)) {
      return null;
    }

    Class<?> clazz = (Class<?>)type;
    if (!beansHolder.contains(clazz) && !clazz.isAnnotationPresent(JerseyInjectable.class)) {
      return null;
    }

    return serviceLocator.findSingletonService(clazz);
  }

  public static class ContextAnnotationInjectionResolver implements InjectionResolver<Context> {
    @Inject
    private ServiceLocator myServiceLocator;

    @Inject
    private JerseyInjectableBeansHolder myBeansHolder;

    @Inject
    @Named(org.glassfish.hk2.api.InjectionResolver.SYSTEM_RESOLVER_NAME)
    private InjectionResolver<Inject> systemInjectionResolver;

    @Override
    public Object resolve(Injectee injectee, ServiceHandle root) {
      Object result = Spring2JerseyBridge.resolve(injectee, myBeansHolder, myServiceLocator);
      if (result == null) {
        return systemInjectionResolver.resolve(injectee, root);
      }

      return result;
    }

    @Override
    public boolean isConstructorParameterIndicator() {
      return false;
    }

    @Override
    public boolean isMethodParameterIndicator() {
      return false;
    }
  }

  public static class InjectAnnotationInjectionResolver implements InjectionResolver<Inject> {
    @Inject
    private ServiceLocator myServiceLocator;

    @Inject
    private JerseyInjectableBeansHolder myBeansHolder;

    @Inject
    @Named(org.glassfish.hk2.api.InjectionResolver.SYSTEM_RESOLVER_NAME)
    private InjectionResolver<Inject> systemInjectionResolver;

    @Override
    public Object resolve(Injectee injectee, ServiceHandle root) {
      Object result = Spring2JerseyBridge.resolve(injectee, myBeansHolder, myServiceLocator);
      if (result == null) {
        return systemInjectionResolver.resolve(injectee, root);
      }

      return result;
    }

    @Override
    public boolean isConstructorParameterIndicator() {
      return false;
    }

    @Override
    public boolean isMethodParameterIndicator() {
      return false;
    }
  }
}
