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

package jetbrains.buildServer.server.rest.swagger;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
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
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelBaseType;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelExperimental;
import jetbrains.buildServer.server.rest.swagger.constants.ExtensionType;
import jetbrains.buildServer.server.rest.swagger.constants.ObjectType;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

public class ExtensionModelResolver extends ModelResolver {
  private static final String MODEL_PACKAGE = "jetbrains.buildServer.server.rest.model";

  public ExtensionModelResolver(ObjectMapper mapper) {
    super(mapper);
  }

  private static final Logger LOGGER = Logger.getInstance(ExtensionModelResolver.class.getName());

  @Override
  protected boolean ignore(Annotated member, XmlAccessorType xmlAccessorTypeAnnotation, String propName, Set<String> propertiesToIgnore) {
    if(super.ignore(member, xmlAccessorTypeAnnotation, propName, propertiesToIgnore)) {
      return true;
    }

    return member.hasAnnotation(ModelExperimental.class);
  }

  @Override
  public Model resolve(JavaType type, ModelConverterContext context, Iterator<ModelConverter> next) {
    ModelImpl model = (ModelImpl) super.resolve(type, context, next);

    if (model != null) {
      BeanDescription beanDesc = _mapper.getSerializationConfig().introspect(type);

      final ModelBaseType baseTypeAnnotation = beanDesc.getClassAnnotations().get(ModelBaseType.class);
      if (baseTypeAnnotation != null) {
        model.setVendorExtension(ExtensionType.X_BASE_TYPE, baseTypeAnnotation.value());
        if (!baseTypeAnnotation.baseEntity().isEmpty()) {
          model.setVendorExtension(ExtensionType.X_BASE_ENTITY, baseTypeAnnotation.baseEntity());
        }
      }

      final ModelDescription descriptionAnnotation = beanDesc.getClassAnnotations().get(ModelDescription.class);
      if (descriptionAnnotation != null) {
        model.setDescription(descriptionAnnotation.value());

        if (!descriptionAnnotation.externalArticleLink().isEmpty()) {
          if (!descriptionAnnotation.externalArticleName().isEmpty()) {
            model.setVendorExtension(ExtensionType.X_HELP_ARTICLE_NAME, descriptionAnnotation.externalArticleName());
          }
          else {
            model.setVendorExtension(ExtensionType.X_HELP_ARTICLE_NAME, model.getName());
          }
          model.setVendorExtension(ExtensionType.X_HELP_ARTICLE_LINK, descriptionAnnotation.externalArticleLink());
        }
      }

      //set default x-base-type vendor extension
      if (baseTypeAnnotation == null || !model.getVendorExtensions().containsKey(ExtensionType.X_BASE_TYPE)) {
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
          replace(shortName, "");
      StringUtils.strip(packageName, "."); // remove leading and trailing dots
      if (!packageName.isEmpty()) {
        model.setVendorExtension(ExtensionType.X_SUBPACKAGE, packageName);
      }
    }
  }

  private void setParamExtensions(ModelImpl model) {
    String baseType = (String) model.getVendorExtensions().get(ExtensionType.X_BASE_TYPE);
    String[] baseDefinedParams = ExtensionType.paramToObjectType.get(baseType);

    if(model.getProperties() == null) return;

    for (Property property : model.getProperties().values()) {
      if (Arrays.stream(baseDefinedParams).anyMatch(property.getName()::equalsIgnoreCase)) {
        ((AbstractProperty)property).setVendorExtension(ExtensionType.X_DEFINED_IN_BASE, true);
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

      if (containerFilterResult.isPresent() && !model.getVendorExtensions().containsKey(ExtensionType.X_BASE_ENTITY)) {
        model.setVendorExtension(
            ExtensionType.X_BASE_ENTITY, convertKebabCaseToCamelCase(containerFilterResult.get().getName())
        );
      }
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

  private String convertKebabCaseToCamelCase(String kebabCasedString) {
    String[] words = kebabCasedString.split("-");
    String result = "";

    for (String word : words) {
      result += StringUtils.capitalize(word);
    }

    return result;
  }

  private void setDescriptionVendorExtension(ModelImpl model) {

    if (
        model.getVendorExtensions().containsKey(ExtensionType.X_BASE_ENTITY)
            && (model.getDescription() == null || model.getDescription().isEmpty())
    ) {
      String baseType = (String) model.getVendorExtensions().get(ExtensionType.X_BASE_TYPE);
      String baseEntity = (String) model.getVendorExtensions().get(ExtensionType.X_BASE_ENTITY);

      switch (baseType) {
        case (ObjectType.LIST):
          model.setDescription(String.format("Represents a list of %s entities.", baseEntity));
          break;
        case (ObjectType.PAGINATED):
          model.setDescription(String.format("Represents a paginated list of %s entities.", baseEntity));
          break;
      }
    }
  }

}