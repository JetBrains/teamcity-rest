/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.DataProvider;

/**
 * @author Yegor.Yarko
 *         Date: 15.11.2009
 */
@Provider
public class DataProviderContextProvider implements InjectableProvider<Context, Type>, Injectable<DataProvider> {
  private final DataProvider myDataProvider;

  public DataProviderContextProvider(final DataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public ComponentScope getScope() {
    return ComponentScope.Singleton;
  }

  public Injectable getInjectable(final ComponentContext ic, final Context context, final Type type) {
    if (type.equals(DataProvider.class)) {
      return this;
    }
    return null;
  }

  public DataProvider getValue() {
    return myDataProvider;
  }
}