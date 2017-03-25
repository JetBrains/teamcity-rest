/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationContext;

/**
 * @author Yegor.Yarko
 *         Date: 09.08.2010
 */
public class BeanFactory {
  private final ApplicationContext ctx;

  public BeanFactory(ApplicationContext ctx) {
    this.ctx = ctx;
  }

  @Nullable
  private <T> Constructor<T> findConstructor(@NotNull final Class<T> clazz, Class<?>[] argTypes) {
    try {
      return clazz.getConstructor(argTypes);
    } catch (NoSuchMethodException e) {
      //NOP
    }

    for (Constructor c : clazz.getConstructors()) {
      final Class[] reqTypes = c.getParameterTypes();
      if (checkParametersMatch(argTypes, reqTypes)) {
        //noinspection unchecked
        return (Constructor<T>)c;
      }
    }
    return null;
  }

  private boolean checkParametersMatch(final Class<?>[] argTypes, final Class[] reqTypes) {
    if (reqTypes.length != argTypes.length) return false;
    for (int i = 0; i < argTypes.length; i++) {
      final Class<?> paramType = argTypes[i];
      final Class<?> reqType = reqTypes[i];

      if (!reqType.isAssignableFrom(paramType)) {
        return false;
      }
    }
    return true;
  }

  public <T> T create(Class<T> clazz, Object... params) {
    Class[] types = new Class[params.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = params[i].getClass();
    }

    final Constructor<T> constructor = findConstructor(clazz, types);
    if (constructor == null) {
      throw new OperationException("Could not find constructor for class " + clazz.getName() + " with parameters " + describe(params));
    }

    final T t;
    try {
      t = constructor.newInstance(params);
    } catch (InstantiationException e) {
      throw new OperationException("Could not instantiate class " + clazz.getName() + " with parameters " + describe(params), e);
    } catch (IllegalAccessException e) {
      throw new OperationException("Could not instantiate class " + clazz.getName() + " with parameters " + describe(params), e);
    } catch (InvocationTargetException e) {
      throw new OperationException("Could not instantiate class " + clazz.getName() + " with parameters " + describe(params), e);
    }
    return autowire(t);
  }

  public <T> T autowire(T t){
    ctx.getAutowireCapableBeanFactory().autowireBean(t);
    return t;
  }

  private String describe(Object... params) {
    StringBuilder result = new StringBuilder();
    result.append("[");
    for (int i = 0; i < params.length; i++) {
      if (i != 0) {
        result.append(",");
      }
      final Object param = params[i];
      if (param == null) {
        result.append("null");
      } else {
        result.append(param.getClass().getName()).append("(").append(param.toString()).append(")");
      }
    }
    return result.append("]").toString();
  }
}
