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

import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderConfig;
import io.swagger.models.ModelImpl;
import io.swagger.models.Swagger;
import io.swagger.models.properties.StringProperty;

import java.lang.reflect.Field;
import java.util.Set;

public class LocatorAwareReader extends Reader {
  private static final String ENTITY_DEFINITION_NAME = "Entity";
  private static final String LOCATOR_DEFINITION_NAME = "Locator";

  public LocatorAwareReader(Swagger swagger, ReaderConfig config) {
    super(swagger, config);
  }

  @Override
  public Swagger read(Set<Class<?>> classes) {
    Swagger swagger = super.read(classes);

    /*ModelImpl entityDefinition = new ModelImpl();
    entityDefinition.setName(ENTITY_DEFINITION_NAME);
    entityDefinition.setType(ModelImpl.OBJECT);
    swagger.addDefinition(ENTITY_DEFINITION_NAME, entityDefinition);*/

    ModelImpl locatorDefinition = new ModelImpl();
    locatorDefinition.setName(LOCATOR_DEFINITION_NAME);
    locatorDefinition.setType(ModelImpl.OBJECT);
    swagger.addDefinition(LOCATOR_DEFINITION_NAME, locatorDefinition);

    for (Class<?> cls : classes) {
      if (cls.isAnnotationPresent(LocatorResource.class)) {
        LocatorResource annotation = cls.getAnnotation(LocatorResource.class);
        ModelImpl definition = new ModelImpl();
        definition.setType(LOCATOR_DEFINITION_NAME);
        definition.setName(annotation.value());

        for (String dimension : annotation.extraDimensions()) {
          definition.addProperty(dimension, new StringProperty());
        }

        for (Field field : cls.getDeclaredFields()) {
          if (field.isAnnotationPresent(LocatorDimension.class)) {
            String locatorDimension = field.getAnnotation(LocatorDimension.class).value();
            definition.addProperty(locatorDimension, new StringProperty());
          }
        }

        swagger.addDefinition(annotation.value(), definition);
      }
    }
    return swagger;
  }
}
