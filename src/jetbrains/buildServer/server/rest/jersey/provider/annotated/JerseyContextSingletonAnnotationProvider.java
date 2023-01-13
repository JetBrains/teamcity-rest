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

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import java.lang.reflect.Type;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Provides beans annotated with @JerseyContextSingletom to Jersey to be injected usin @Context annotation.
 *
 * This class aims to get rid of usage jersey @Provider class ber bean.
 */
@Provider
@Component
public class JerseyContextSingletonAnnotationProvider implements InjectableProvider<Context, Type> {

  private final ConfigurableApplicationContext myApplicationContext;

  public JerseyContextSingletonAnnotationProvider(ConfigurableApplicationContext applicationContext) {
    myApplicationContext = applicationContext;
  }

  @Override
  public ComponentScope getScope() {
    return ComponentScope.Singleton;
  }

  @Override
  public Injectable<?> getInjectable(ComponentContext ic, Context context, Type type) {
    if (!(type instanceof Class)) return null;

    Class<?> clazz = (Class<?>)type;

    if (!(clazz.isAnnotationPresent(JerseyContextSingleton.class))) {
      return null;
    }

    String[] beanNamesForType = myApplicationContext.getBeanNamesForType(clazz);
    if (beanNamesForType.length == 0) {
      return null;
    }

    if (!myApplicationContext.getBeanFactory().getBeanDefinition(beanNamesForType[0]).isSingleton()) {
      throw new NoSuchBeanDefinitionException("Bean of type " + clazz.getSimpleName() + " is not singleton.");
    }

    Object singletonService = myApplicationContext.getBean(clazz);

    return () -> singletonService;
  }
}
