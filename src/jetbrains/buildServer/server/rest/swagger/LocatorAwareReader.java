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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class LocatorAwareReader extends Reader {
  private static final String ENTITY_DEFINITION_NAME = "Entity"; //possible usage with v3 as a type for the definitions
  private static final String LOCATOR_DEFINITION_NAME = "Locator"; //same

  public LocatorAwareReader(Swagger swagger, ReaderConfig config) {
    super(swagger, config);
  }

  @Override
  public Swagger read(Set<Class<?>> classes) {
    Swagger swagger = super.read(classes);

    for (Class<?> cls : classes) {
      if (cls.isAnnotationPresent(LocatorResource.class)) { //iterate through the annotated classes
        LocatorResource annotation = cls.getAnnotation(LocatorResource.class);
        ModelImpl definition = new ModelImpl();
        definition.setType(ModelImpl.OBJECT);
        definition.setName(annotation.value());

        ArrayList<String> dimensions = new ArrayList<String>();
        for (String dimension : annotation.extraDimensions()) { //iterate through the annotation fields
          dimensions.add(dimension);
        }

        for (Field field : cls.getDeclaredFields()) { //iterate through the class fields
          if (field.isAnnotationPresent(LocatorDimension.class)) {
            String dimension = field.getAnnotation(LocatorDimension.class).value();
            dimensions.add(dimension);
          }
        }

        Collections.sort(dimensions);
        for (String dimension : dimensions) {
          definition.addProperty(dimension, new StringProperty());
        }

        swagger.addDefinition(annotation.value(), definition);
      }
    }
    return swagger;
  }
}
