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

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderConfig;
import io.swagger.models.*;
import io.swagger.models.properties.StringProperty;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;

import java.lang.reflect.Field;
import java.util.*;

public class LocatorAwareReader extends Reader {

  public static final Logger LOGGER = Logger.getInstance(LocatorAwareReader.class.getName());

  public LocatorAwareReader(Swagger swagger, ReaderConfig config) {
    super(swagger, config);
  }

  @Override
  public Swagger read(Set<Class<?>> classes) {
    Swagger swagger = super.read(classes);

    LOGGER.info("Populating examples for endpoint responses.");
    populateExamples(swagger); // Set empty Example Objects for each MIME-Type involved in each response to handle TW-56270

    LOGGER.info("Generating locator definitions.");
    for (Class<?> cls : classes) {
      if (cls.isAnnotationPresent(LocatorResource.class)) {
        populateLocatorDefinition(swagger, cls); // For every @LocatorResource annotated finder, generate Locator definition
      }
    }

    return swagger;
  }

  private void populateExamples(Swagger swagger) {
    for (Path path : swagger.getPaths().values()) {
      for (Operation operation : path.getOperations()) {
        List<String> produces = operation.getProduces(); // Returns a list of MIME-Type strings (specification expects one example per type)
        if (produces == null || produces.isEmpty() || operation.getResponses().isEmpty() || operation.getResponses() == null) {
          continue;
        }
        for (Response response : operation.getResponses().values()) {
          Map<String, Object> examples = new HashMap<String, Object>();
          for (String mimeType : produces) {
            if (response.getExamples() != null && response.getExamples().containsKey(mimeType)) { // Preserve existing Example Objects if any
              examples.put(mimeType, response.getExamples().get(mimeType));
            } else {
              examples.put(mimeType, "");
            }
          }
          response.setExamples(examples);
        }
      }
    }
  }

  private void populateLocatorDefinition(Swagger swagger, Class<?> cls) {
    LocatorResource annotation = cls.getAnnotation(LocatorResource.class);
    ModelImpl definition = new ModelImpl();

    if (!swagger.getDefinitions().containsKey(annotation.value())) { //case if definition is already present because of other swagger annotations present on class
      definition.setType(ModelImpl.OBJECT);
      definition.setName(annotation.value());
      definition.setVendorExtension(ExtensionType.X_BASE_TYPE, ObjectType.LOCATOR);
    } else {
      definition = (ModelImpl) swagger.getDefinitions().get(annotation.value());
    }

    ArrayList<String> dimensions = new ArrayList<String>();
    Collections.addAll(dimensions, annotation.extraDimensions());

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
