/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import java.lang.reflect.Type;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import jetbrains.buildServer.ServiceLocator;

/**
 * @author Yegor.Yarko
 *         Date: 06.08.2010
 */
@Provider
public class ServiceLocatorContextProvider implements InjectableProvider<Context, Type>, Injectable<ServiceLocator> {
  private final ServiceLocator myValue;

  public ServiceLocatorContextProvider(final ServiceLocator contextLocator) {
    myValue = contextLocator;
  }

  public ComponentScope getScope() {
    return ComponentScope.Singleton;
  }

  public Injectable getInjectable(final ComponentContext ic, final Context context, final Type type) {
    if (type.equals(ServiceLocator.class)) {
      return this;
    }
    return null;
  }

  public ServiceLocator getValue() {
    return myValue;
  }
}