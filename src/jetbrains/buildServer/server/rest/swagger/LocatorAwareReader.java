/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.locator.*;
import jetbrains.buildServer.server.rest.data.locator.definition.LocatorDefinition;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.CommonLocatorDimensionsList;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is used for Swagger documentation generation.
 * <p/>
 * It scans the classes and add additional information to OPENAPI docs.
 * <br/>
 * For example, information about @LocatorDimension
 */
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

  /**
   * Set empty Example Objects for each MIME-Type involved in each response to handle TW-56270
   */
  private static void populateExamples(Path path) {
    for (Operation operation : path.getOperations()) {
      setExamplesIfAbsent(operation);
    }
  }

  private static void setExamplesIfAbsent(Operation operation) {
    List<String> produces = operation.getProduces(); // Returns a list of MIME-Type strings (specification expects one example per type)
    if (produces == null || produces.isEmpty() || operation.getResponses().isEmpty() || operation.getResponses() == null) {
      return;
    }
    for (Response response : operation.getResponses().values()) {
      Map<String, Object> examples = new HashMap<>();
      for (String mimeType : produces) {
        boolean hasExample = response.getExamples() != null && response.getExamples().containsKey(mimeType);
        // Preserve existing Example Objects if any
        Object example = hasExample ? response.getExamples().get(mimeType) : "";
        examples.put(mimeType, example);
      }
      response.setExamples(examples);
    }
  }

  private static void populateLocatorDefinition(Swagger swagger, Class<?> cls) {
    LocatorResource locatorAnnotation = cls.getAnnotation(LocatorResource.class);
    ModelImpl definition;

    if (!swagger.getDefinitions().containsKey(locatorAnnotation.value())) { //case if definition is already present because of other swagger annotations present on class
      definition = new ModelImpl();
      definition.setType(ModelImpl.OBJECT);
      definition.setName(locatorAnnotation.value());
      definition.setVendorExtension(ExtensionType.X_BASE_TYPE, ObjectType.LOCATOR);
      definition.setVendorExtension(ExtensionType.X_IS_LOCATOR, true);
      definition.setVendorExtension(ExtensionType.X_SUBPACKAGE, "locator");
    } else {
      definition = (ModelImpl) swagger.getDefinitions().get(locatorAnnotation.value());
    }

    if(LocatorDefinition.class.isAssignableFrom(cls)) {
      fillDimensionSyntax((Class<? extends LocatorDefinition>) cls, definition);
    } else {
      // fallback for locators which are not converted to the new scheme yet
      fillDimensionSyntaxUsingAnnotations(cls, locatorAnnotation, definition);
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

  private static void fillDimensionSyntax(Class<? extends LocatorDefinition> cls, ModelImpl definition) {
    SubDimensionSyntax complexSyntax = new SubDimensionSyntaxImpl(cls);

    TreeMap<String, AbstractProperty> propsAlphabetically = new TreeMap<>();
    for(Dimension dim : complexSyntax.getSubDimensions()) {
      if(dim.isHidden()) continue;

      AbstractProperty prop;
      Syntax syntax = dim.getSyntax();
      if(syntax instanceof Int64Value) {
        prop = new LongProperty();
      } else if(syntax instanceof EnumValue) {
        prop = new StringProperty();
        ((StringProperty) prop).setEnum(((EnumValue) syntax).getValues());
      } else {
        prop = new StringProperty();
        prop.setFormat(syntax.getFormat());
      }

      prop.setName(dim.getName());
      prop.setDescription(dim.getDescription());

      propsAlphabetically.put(dim.getName(), prop);
    }

    propsAlphabetically.forEach(definition::addProperty);
  }

  private static void fillDimensionSyntaxUsingAnnotations(Class<?> cls, LocatorResource locatorAnnotation, ModelImpl definition) {
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
        if(!dimension.hidden()) {
          dimensions.add(dimension);
        }
      }
    }

    Collections.sort(dimensions, (dim1, dim2) -> dim1.value().compareTo(dim2.value()));

    for (LocatorDimension dimension : dimensions) {
      AbstractProperty property = resolveLocatorDimensionProperty(dimension);
      definition.addProperty(dimension.value(), property);
    }
  }

  private static AbstractProperty resolveLocatorDimensionProperty(LocatorDimension dimension) {
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
        if (!dimension.allowableValues().isEmpty()) {
          List<String> values = Stream.of(dimension.allowableValues().split(",", -1))
              .collect(Collectors.toList());
          ((StringProperty) property).setEnum(values);
        }
        break;
    }

    if (!dimension.notes().isEmpty()) {
      property.setDescription(dimension.notes());
    }

    return property;
  }

  private static void fixDuplicateOperationId(Swagger swagger, Path path) {
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
