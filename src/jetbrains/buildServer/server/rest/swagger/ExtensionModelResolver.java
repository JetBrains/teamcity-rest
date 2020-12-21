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
import com.intellij.openapi.diagnostic.Logger;
import io.swagger.annotations.ExtensionProperty;
import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.jackson.ModelResolver;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.AbstractProperty;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import jetbrains.buildServer.server.rest.swagger.annotations.Extension;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import org.apache.commons.lang3.StringUtils;
import jetbrains.buildServer.serverSide.maintenance.BackupProcessManager;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

public class ExtensionModelResolver extends ModelResolver {
  private static final String MODEL_PACKAGE = "jetbrains.buildServer.server.rest.model";

  public ExtensionModelResolver(ObjectMapper mapper) {
    super(mapper);
  }

  private static final Logger LOGGER = Logger.getInstance(ExtensionModelResolver.class.getName());
  private static final String BACKUP_PROCESS_MANAGER = "BackupProcessManager";

  @Override
  public Model resolve(JavaType type, ModelConverterContext context, Iterator<ModelConverter> next) {
    if (type.getRawClass().getSimpleName().equals(BACKUP_PROCESS_MANAGER)) { // crude solution for TW-69356
      return null;
    }

    ModelImpl model = (ModelImpl) super.resolve(type, context, next);

    if (model != null) {
      BeanDescription beanDesc = _mapper.getSerializationConfig().introspect(type);

      final Extension extensions = beanDesc.getClassAnnotations().get(Extension.class);
      if (extensions != null) {
        for (ExtensionProperty property : extensions.properties()) {
          model.setVendorExtension(property.name(), property.value());
        }
      }

      //set default x-base-type vendor extension
      if (extensions == null || !model.getVendorExtensions().containsKey(ExtensionType.X_BASE_TYPE)) {
        model.setVendorExtension(ExtensionType.X_BASE_TYPE, ObjectType.DATA);
      }

      setPackageExtension(model, type);
      setIsTypeVendorExtension(model);
      setParamExtensions(model);
      setDescriptionVendorExtension(model);
    } else {
      LOGGER.debug(type.toString() + " type resolved to null");
    }

    return model;
  }

  private void setPackageExtension(ModelImpl model, JavaType type) {
    String fullyQualifiedName = type.getRawClass().getName();
    String shortName = type.getRawClass().getSimpleName();

    if (fullyQualifiedName.startsWith(MODEL_PACKAGE)) {
      String packageName = fullyQualifiedName.
          replace(MODEL_PACKAGE, "").
          replace(shortName, "").
          replace(".", "");
      if (!packageName.isEmpty()) {
        model.setVendorExtension(ExtensionType.X_SUBPACKAGE, packageName);
      }
    }
  }

  private void setParamExtensions(ModelImpl model) {
    String baseType = (String) model.getVendorExtensions().get(ExtensionType.X_BASE_TYPE);
    String[] baseDefinedParams = ExtensionType.paramToObjectType.get(baseType);

    for (Property property : model.getProperties().values()) {
      if (Arrays.stream(baseDefinedParams).anyMatch(property.getName()::equalsIgnoreCase)) {
        ((AbstractProperty) property).setVendorExtension(ExtensionType.X_DEFINED_IN_BASE, true);
      }
    }

    if (baseType.equals(ObjectType.LIST) || baseType.equals(ObjectType.PAGINATED)) {
      Optional<Property> containerFilterResult = model.getProperties().values().stream().
          filter(x -> x instanceof ArrayProperty).
          findFirst();
      containerFilterResult.ifPresent(
          property -> ((AbstractProperty) property).setVendorExtension(
              ExtensionType.X_IS_FIRST_CONTAINER_VAR, true
          )
      );
    }
  }

  private void setIsTypeVendorExtension(ModelImpl model) {
    String baseType = (String) model.getVendorExtensions().get(ExtensionType.X_BASE_TYPE);
    switch (baseType) {
      case (ObjectType.DATA):
        model.setVendorExtension(ExtensionType.X_IS_DATA, true);
        break;
      case (ObjectType.LIST):
        model.setVendorExtension(ExtensionType.X_IS_DATA, true);
        model.setVendorExtension(ExtensionType.X_IS_LIST, true);
        break;
      case (ObjectType.PAGINATED):
        model.setVendorExtension(ExtensionType.X_IS_DATA, true);
        model.setVendorExtension(ExtensionType.X_IS_LIST, true);
        model.setVendorExtension(ExtensionType.X_IS_PAGINATED, true);
        break;
    }
  }

  private void setDescriptionVendorExtension(ModelImpl model) {
    String baseType = (String) model.getVendorExtensions().get(ExtensionType.X_BASE_TYPE);
    Optional<Property> containerFilterResult = model.getProperties().values().stream().
        filter(x -> x.getVendorExtensions().containsKey(ExtensionType.X_IS_FIRST_CONTAINER_VAR)).
        findFirst();

    if (containerFilterResult.isPresent()) {
      String containerType = StringUtils.capitalize(containerFilterResult.get().getName());
      switch (baseType) {
        case (ObjectType.LIST):
          model.setVendorExtension(ExtensionType.X_DESCRIPTION, String.format("Represents a list of %s entities.", containerType));
          break;
        case (ObjectType.PAGINATED):
          model.setVendorExtension(ExtensionType.X_DESCRIPTION, String.format("Represents a paginated list of %s entities.", containerType));
          break;
      }
    }
  }
}
