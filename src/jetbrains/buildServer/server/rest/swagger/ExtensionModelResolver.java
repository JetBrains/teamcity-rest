/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.swagger;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ExtensionProperty;
import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.jackson.ModelResolver;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;

import java.util.Iterator;

public class ExtensionModelResolver extends ModelResolver {
  public ExtensionModelResolver(ObjectMapper mapper) {
    super(mapper);
  }

  @Override
  public Model resolve(JavaType type, ModelConverterContext context, Iterator<ModelConverter> next) {
    ModelImpl model = (ModelImpl) super.resolve(type, context, next);
    BeanDescription beanDesc = _mapper.getSerializationConfig().introspect(type);

    final Extension extensions = beanDesc.getClassAnnotations().get(Extension.class);
    if (extensions != null) {
      for (ExtensionProperty property : extensions.properties()) {
        model.setVendorExtension(property.name(), property.value());
      }
    } else { //set default x-base-type vendor extension
      model.setVendorExtension(ExtensionType.X_BASE_TYPE, ObjectType.DATA);
    }

    return model;
  }
}
