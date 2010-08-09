/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import org.springframework.context.ApplicationContext;

/**
 * @author Yegor.Yarko
 *         Date: 09.08.2010
 */
public class BeanFactory {
  private ApplicationContext ctx;

  public BeanFactory(ApplicationContext ctx) {
    this.ctx = ctx;
  }

  public <T> T create(Class<T> clazz, Object... params) {
    Class[] types = new Class[params.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = params[i].getClass();
    }

    final Constructor<T> constructor;
    try {
      constructor = clazz.getConstructor(types);
    } catch (NoSuchMethodException e) {
      throw new OperationException("Could not find contructor for class " + clazz.getName() + " with parameters " + describe(params), e);
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
