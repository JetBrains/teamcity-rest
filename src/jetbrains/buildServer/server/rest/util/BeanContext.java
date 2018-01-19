/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.util;

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.ServiceNotFoundException;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 20.04.13
 */
public class BeanContext {
  private final BeanFactory myFactory;
  @NotNull private final ServiceLocator myServiceLocator;
  @NotNull private final ApiUrlBuilder myApiUrlBuilder;

  public BeanContext(final BeanFactory factory, @NotNull final ServiceLocator serviceLocator, @NotNull ApiUrlBuilder apiUrlBuilder) {
    myFactory = factory;
    myServiceLocator = serviceLocator;
    myApiUrlBuilder = apiUrlBuilder;
  }

  /**
   * @deprecated let's not use this at all
   */
  public <T> void autowire(T t){
    myFactory.autowire(t);
  }

  @NotNull
  public <T> T getSingletonService(@NotNull Class<T> serviceClass) throws ServiceNotFoundException {
    return myServiceLocator.getSingletonService(serviceClass);
  }

  @NotNull
  public ApiUrlBuilder getApiUrlBuilder(){
        return myApiUrlBuilder;
  }

  @NotNull
  public ServiceLocator getServiceLocator(){
        return myServiceLocator;
  }

  @NotNull
  public ApiUrlBuilder getContextService(@NotNull Class<ApiUrlBuilder> serviceClass) throws ServiceNotFoundException {
        return myApiUrlBuilder;
  }
}
