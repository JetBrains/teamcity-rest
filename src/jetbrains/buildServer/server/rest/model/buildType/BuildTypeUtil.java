/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.buildType;

import com.intellij.openapi.diagnostic.Logger;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.BuildTypeDescriptor;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Option;
import jetbrains.buildServer.util.OptionSupport;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 04.01.12
 */
public class BuildTypeUtil {
  private static final Logger LOG = Logger.getInstance(BuildTypeUtil.class.getName());

  public static HashMap<String, String> getSettingsParameters(@NotNull final BuildTypeOrTemplate buildType) {
    HashMap<String, String> properties = new HashMap<String, String>();
    addAllOptionsAsProperties(properties, buildType.get());
    properties.put("checkoutDirectory", buildType.get().getCheckoutDirectory());
    if (buildType.getBuildType() != null){
      properties.put("buildNumberCounter", String.valueOf(buildType.getBuildType().getBuildNumbers().getBuildCounter()));
    }
    return properties;
  }

  /**
   * Caller must ensure 'name' is a valid name of a BuildType setting
   * @see #getSettingsParameters(jetbrains.buildServer.serverSide.SBuildType)
   */
  public static void setSettingsParameter(final BuildTypeOrTemplate buildType, final String name, final String value) {
    if ("checkoutDirectory".equals(name)) {
      buildType.get().setCheckoutDirectory(value);
    } else if ("checkoutMode".equals(name)) {
      buildType.get().setCheckoutType(BuildTypeDescriptor.CheckoutType.valueOf(value));
    } else if ("buildNumberCounter".equals(name)) {
      if (buildType.getBuildType() != null) {
        buildType.getBuildType().getBuildNumbers().setBuildNumberCounter(new Long(value));
      }else{
        throw new BadRequestException("Templates do not have build counter.");
      }
    } else {
      final Option option = Option.fromKey(name);
      if (option == null) {
        throw new IllegalArgumentException("No Build Type option found for name '" + name + "'");
      }
      final Object optionValue = option.fromString(value);
      //noinspection unchecked
      buildType.get().setOption(option, optionValue);
    }
  }

  //todo: might use a generic util for this (e.g. Static HTML plugin has alike code to get all Page Places)
  private static void addAllOptionsAsProperties(final HashMap<String, String> properties, final OptionSupport buildType) {
    Field[] declaredFields = BuildTypeOptions.class.getDeclaredFields();
    for (Field declaredField : declaredFields) {
      try {
        if (Option.class.isAssignableFrom(declaredField.get(buildType).getClass())) {
          Option option = null;
          option = (Option)declaredField.get(buildType);
          //noinspection unchecked
          properties.put(option.getKey(), buildType.getOption(option).toString());
        }
      } catch (IllegalAccessException e) {
        LOG.error("Error retrieving option '" + declaredField.getName() + "' , error: " + e.getMessage());
      }
    }
  }

  @NotNull
  public static SBuildFeatureDescriptor getBuildTypeFeature(final BuildTypeSettings buildType, @NotNull final String featureId) {
    if (StringUtil.isEmpty(featureId)){
      throw new BadRequestException("Feature Id cannot be empty.");
    }
    SBuildFeatureDescriptor feature = getBuildTypeFeatureOrNull(buildType, featureId);
    if (feature == null) {
      throw new NotFoundException("No feature with id '" + featureId + "' is found in the build configuration.");
    }
    return feature;
  }

  public static SBuildFeatureDescriptor getBuildTypeFeatureOrNull(final BuildTypeSettings buildType, final String featureId) {
    return CollectionsUtil.findFirst(buildType.getBuildFeatures(), new Filter<SBuildFeatureDescriptor>() {
      public boolean accept(@NotNull final SBuildFeatureDescriptor data) {
        return data.getId().equals(featureId);
      }
    });
  }

  public static String getParameter(@Nullable final String parameterName,
                                    @NotNull final UserParametersHolder parametrizedEntity,
                                    final boolean checkSecure,
                                    final boolean nameItProperty) {
    return getParameter(parameterName, parametrizedEntity.getParameters(), checkSecure, nameItProperty);
  }

  public static String getParameter(@Nullable final String parameterName, @NotNull final Map<String, String> parameters, final boolean checkSecure, final boolean nameItProperty) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException(nameItProperty ? "Property" : "Parameter" + " name cannot be empty.");
    }
    if (parameters.containsKey(parameterName)) { // this processes stored "null" values duly, but this might not be necessary...
      if (!checkSecure || !Property.isPropertyToExclude(parameterName)) {
        return parameters.get(parameterName);
      } else {
        throw new BadRequestException("Secure " + (nameItProperty ? "properties" : "parameters") + " cannot be retrieved via remote API by default.");
      }
    }
    throw new NotFoundException((nameItProperty ? "No property" : "No parameter") + " with name '" + parameterName + "' is found.");
  }

  public static String getParameter(@Nullable final String parameterName, @NotNull final ParametersProvider parameters, final boolean checkSecure, final boolean nameItProperty) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException(nameItProperty ? "Property" : "Parameter" + " name cannot be empty.");
    }
    final String value = parameters.get(parameterName);
    if (value != null) {
      if (!checkSecure || !Property.isPropertyToExclude(parameterName)) {
        return value;
      } else {
        throw new BadRequestException("Secure " + (nameItProperty ? "properties" : "parameters") + " cannot be retrieved via remote API by default.");
      }
    }
    throw new NotFoundException((nameItProperty ? "No property" : "No parameter") + " with name '" + parameterName + "' is found.");
  }

  public static void changeParameter(final String parameterName,
                                     final String newValue,
                                     @NotNull final UserParametersHolder parametrizedEntity,
                                     @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }

    final ControlDescription typeSpec = getExistingParameterTypeSpec(parametrizedEntity, parameterName);
    if (typeSpec != null){
      parametrizedEntity.addParameter(getParameterFactory(serviceLocator).createParameter(parameterName, newValue, typeSpec));
    }else{
      parametrizedEntity.addParameter(getParameterFactory(serviceLocator).createSimpleParameter(parameterName, newValue));
    }
  }

  public static void changeParameterType(final String parameterName,
                                         @Nullable final String newRawTypeValue,
                                         @NotNull final UserParametersHolder parametrizedEntity,
                                         @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }

    final String existingValue = parametrizedEntity.getParameters().get(parameterName);
    if (newRawTypeValue != null) {
      parametrizedEntity.addParameter(getParameterFactory(serviceLocator).createTypedParameter(parameterName, existingValue, newRawTypeValue));
    } else {
      parametrizedEntity.addParameter(getParameterFactory(serviceLocator).createSimpleParameter(parameterName, existingValue));
    }
  }

  @Nullable
  private static ControlDescription getExistingParameterTypeSpec(@NotNull final UserParametersHolder parametrizedEntity, @NotNull final String parameterName) {
    for (Parameter parameter : parametrizedEntity.getParametersCollection()) {
      if (parameterName.equals(parameter.getName())){
        return parameter.getControlDescription();
      }
    }
    return null;
  }

  @NotNull
  private static ParameterFactory getParameterFactory(final ServiceLocator serviceLocator) {
    return serviceLocator.getSingletonService(ParameterFactory.class);
  }

  public static void deleteParameter(final String parameterName, final UserParametersHolder parametrizedEntity) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }
    parametrizedEntity.removeParameter(parameterName);
  }

  public static void removeAllParameters(final UserParametersHolder holder) {
    for (String p: holder.getParameters().keySet()) {
      holder.removeParameter(p);
    }
  }

  public static void checkCanUseBuildTypeAsDependency(final String buildTypeExternalId, final ServiceLocator serviceLocator) {
    // see also TW-39209
    if(!TeamCityProperties.getBooleanOrTrue("rest.dependency.checkPermissionsOnChange")){
      return;
    }
    final PermissionChecker permissionChecker = serviceLocator.getSingletonService(PermissionChecker.class);
    if (permissionChecker.isPermissionGranted(Permission.VIEW_PROJECT, null)){
      return;
    }
    final SBuildType buildType = serviceLocator.getSingletonService(ProjectManager.class).findBuildTypeByExternalId(buildTypeExternalId);
    if (buildType == null){
      if(TeamCityProperties.getBoolean("rest.dependency.allowMissingBuildTypeDependency")){
        return;
      }
      throw new BadRequestException("Cannot find build type with id '" + buildTypeExternalId +"'");
    }
    permissionChecker.checkProjectPermission(Permission.VIEW_PROJECT, buildType.getProjectId());
  }
}
