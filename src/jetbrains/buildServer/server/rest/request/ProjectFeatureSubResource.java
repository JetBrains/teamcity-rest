/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import io.swagger.annotations.Api;
import jetbrains.buildServer.server.rest.model.project.PropEntitiesProjectFeature;
import jetbrains.buildServer.server.rest.model.project.PropEntityProjectFeature;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.jetbrains.annotations.NotNull;

/**
 * The class contains no logic but is necessary to preserve type information in runtime as otherwise Jersey will not be able to figure out how to unmarshal resource methods parameters
 * With Jersey 1.19 generic types in resource methods cause:
 * java.lang.NullPointerException
 *       at com.sun.jersey.core.provider.jaxb.AbstractJAXBProvider.getUnmarshaller(AbstractJAXBProvider.java:140)
 *       at com.sun.jersey.core.provider.jaxb.AbstractJAXBProvider.getUnmarshaller(AbstractJAXBProvider.java:123)
 *       at com.sun.jersey.core.impl.provider.entity.XMLRootObjectProvider.isReadable(XMLRootObjectProvider.java:121)
 *       at com.sun.jersey.core.spi.factory.MessageBodyFactory._getMessageBodyReader(MessageBodyFactory.java:345)
 *       at com.sun.jersey.core.spi.factory.MessageBodyFactory._getMessageBodyReader(MessageBodyFactory.java:309)
 *       at com.sun.jersey.core.spi.factory.MessageBodyFactory.getMessageBodyReader(MessageBodyFactory.java:298)
 *       at com.sun.jersey.spi.container.ContainerRequest.getEntity(ContainerRequest.java:464)
 *       at com.sun.jersey.server.impl.model.method.dispatch.EntityParamDispatchProvider$EntityInjectable.getValue(EntityParamDispatchProvider.java:123)
 *       at com.sun.jersey.server.impl.inject.InjectableValuesProvider.getInjectableValues(InjectableValuesProvider.java:86)
 *       ... 66 more
 *
 * @author Yegor.Yarko
 *         Date: 06/06/2016
 */
@Api(hidden = true) // To prevent appearing in Swagger#definitions
public class ProjectFeatureSubResource extends FeatureSubResource<PropEntitiesProjectFeature, PropEntityProjectFeature>{
  public ProjectFeatureSubResource(@NotNull final BeanContext beanContext,
                                   @NotNull final Entity<PropEntitiesProjectFeature, PropEntityProjectFeature> entity) {
    super(beanContext, entity);
  }
}
