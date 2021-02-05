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
import io.swagger.models.properties.*;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
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

    LOGGER.info("Populating examples for endpoint responses and fixing duplicate operationIds.");
    for (Path path : swagger.getPaths().values()) {
      populateExamples(path); // Set empty Example Objects for each MIME-Type involved in each response to handle TW-56270
      fixDuplicateOperationId(swagger, path); // Replace numeric values in operationIds with tag, if present
    }

    LOGGER.info("Generating locator definitions and setting package hint extensions.");
    for (Class<?> cls : classes) {
      if (cls.isAnnotationPresent(LocatorResource.class)) {
        populateLocatorDefinition(swagger, cls); // For every @LocatorResource annotated finder, generate Locator definition
      }
    }

    return swagger;
  }

  private void populateExamples(Path path) {
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

  private void populateLocatorDefinition(Swagger swagger, Class<?> cls) {
    LocatorResource locatorAnnotation = cls.getAnnotation(LocatorResource.class);
    ModelImpl definition = new ModelImpl();

    if (!swagger.getDefinitions().containsKey(locatorAnnotation.value())) { //case if definition is already present because of other swagger annotations present on class
      definition.setType(ModelImpl.OBJECT);
      definition.setName(locatorAnnotation.value());
      definition.setVendorExtension(ExtensionType.X_BASE_TYPE, ObjectType.LOCATOR);
      definition.setVendorExtension(ExtensionType.X_IS_LOCATOR, true);
      definition.setVendorExtension(ExtensionType.X_SUBPACKAGE, "locator");
    } else {
      definition = (ModelImpl) swagger.getDefinitions().get(locatorAnnotation.value());
    }

    // as annotation should be compile-time constant, we keep dimension names in the annotation and resolve them in runtime
    ArrayList<LocatorDimension> dimensions = new ArrayList<LocatorDimension>();
    for (String extraDimensionName : locatorAnnotation.extraDimensions()) {
      if (CommonLocatorDimensionsList.dimensionHashMap.containsKey(extraDimensionName)) {
        dimensions.add(CommonLocatorDimensionsList.dimensionHashMap.get(extraDimensionName));
      } else {
        LOGGER.warn(String.format("Common locator dimension %s was not found.", extraDimensionName));
      }
    }

    // iterate through class fields to see if any have LocatorDimension annotation
    for (Field field : cls.getDeclaredFields()) { //iterate through the class fields
      if (field.isAnnotationPresent(LocatorDimension.class)) {
        LocatorDimension dimension = field.getAnnotation(LocatorDimension.class);
        dimensions.add(dimension);
      }
    }

    Collections.sort(dimensions, new Comparator<LocatorDimension>() {
      @Override
      public int compare(LocatorDimension o1, LocatorDimension o2) {
        return o1.value().compareTo(o2.value());
      }
    });

    for (LocatorDimension dimension : dimensions) {
      AbstractProperty property = resolveLocatorDimensionProperty(dimension);
      definition.addProperty(dimension.value(), property);
    }

    definition.setVendorExtension(ExtensionType.X_BASE_ENTITY, locatorAnnotation.baseEntity());
    definition.setDescription(
        String.format(
            "Represents a locator string for filtering %s entities.", locatorAnnotation.baseEntity()
        )
    );

    if (locatorAnnotation.examples().length != 0) {
      definition.setVendorExtension(ExtensionType.X_MODEL_EXAMPLES, locatorAnnotation.examples());
    }

    swagger.addDefinition(locatorAnnotation.value(), definition);

  }

  private AbstractProperty resolveLocatorDimensionProperty(LocatorDimension dimension) {
    AbstractProperty property;

    switch (dimension.dataType()) {
      case (LocatorDimensionDataType.INTEGER):
        property = new IntegerProperty();
        break;
      case (LocatorDimensionDataType.BOOLEAN):
        property = new BooleanProperty();
        break;
      case (LocatorDimensionDataType.TIMESTAMP):
        property = new DateTimeProperty();
        break;
      default:
        property = new StringProperty();
        if (!dimension.format().isEmpty()) {
          property.setFormat(dimension.format());
        }
        break;
    }

    if (!dimension.notes().isEmpty()) {
      property.setDescription(dimension.notes());
    }

    return property;
  }

  private void fixDuplicateOperationId(Swagger swagger, Path path) {
    for (Operation operation : path.getOperations()) {
      String operationId = operation.getOperationId();
      List<String> tags = operation.getTags();

      boolean duplicateOperationId = false;
      for (Path knownPath : swagger.getPaths().values()) {
        for (Operation op : knownPath.getOperations()) {
          if (operationId.equalsIgnoreCase(op.getOperationId()) && !op.equals(operation)) {
            duplicateOperationId = true;
          }
        }
      }

      if (duplicateOperationId && !tags.isEmpty()) {
        operation.setOperationId(operationId + "Of" + tags.get(0));
      }
    }
  }
}
