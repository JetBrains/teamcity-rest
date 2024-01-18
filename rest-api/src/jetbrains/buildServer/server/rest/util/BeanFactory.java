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

package jetbrains.buildServer.server.rest.util;

import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * A way to create REST API classes via Spring beans -aware factory.
 * BeanFactory support assignable constructor parameters
 * @deprecated deprecated for removal because unused.
 * @author Yegor.Yarko
 * Date: 09.08.2010
 */
@Deprecated
@JerseyInjectable
@Component
public class BeanFactory {
  private final ApplicationContext ctx;

  public BeanFactory(ApplicationContext ctx) {
    this.ctx = ctx;
  }

  public <T> T autowire(T t){
    ctx.getAutowireCapableBeanFactory().autowireBean(t);
    return t;
  }

}