/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeOptions;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.UserParametersHolder;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Option;
import jetbrains.buildServer.util.OptionSupport;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 04.01.12
 */
public class BuildTypeUtil {
  private static final Logger LOG = Logger.getInstance(BuildTypeUtil.class.getName());

  public static HashMap<String, String> getSettingsParameters(final BuildTypeOrTemplate buildType) {
    HashMap<String, String> properties = new HashMap<String, String>();
    addAllOptionsAsProperties(properties, buildType.get());
    //todo: is the right way to do?
    properties.put("artifactRules", buildType.get().getArtifactPaths());
    properties.put("checkoutDirectory", buildType.get().getCheckoutDirectory());
    properties.put("checkoutMode", buildType.get().getCheckoutType().name());
    if (buildType.isBuildType()){
      properties.put("buildNumberCounter", (new Long(buildType.getBuildType().getBuildNumbers().getBuildCounter())).toString());
    }
    return properties;
  }

  /**
   * Caller must ensure 'name' is a valid name of a BuildType setting
   * @see #getSettingsParameters(jetbrains.buildServer.serverSide.SBuildType)
   */
  public static void setSettingsParameter(final BuildTypeOrTemplate buildType, final String name, final String value) {
    if ("artifactRules".equals(name)) {
      buildType.get().setArtifactPaths(value);
    } else if ("checkoutDirectory".equals(name)) {
      buildType.get().setCheckoutDirectory(value);
    } else if ("checkoutMode".equals(name)) {
      buildType.get().setCheckoutType(BuildTypeDescriptor.CheckoutType.valueOf(value));
    } else if ("buildNumberCounter".equals(name)) {
      if (buildType.isBuildType()) {
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

  public static String getParameter(final String parameterName, final UserParametersHolder parametrizedEntity) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }

    Map<String, String> parameters = parametrizedEntity.getParameters();
    if (parameters.containsKey(parameterName)) {
      if (!Properties.isPropertyToExclude(parameterName)) {
        //TODO: need to process spec type to filter secure fields, may be include display value
        //TODO: might support type spec here
        return parameters.get(parameterName);
      }else{
        throw new BadRequestException("Secure parameters cannot be retrieved via remote API by default.");
      }
    }
    throw new NotFoundException("No parameter with name '" + parameterName + "' is found.");
  }

  public static void changeParameter(final String parameterName,
                                     final String newValue,
                                     final UserParametersHolder parametrizedEntity,
                                     final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }

    //TODO: support type spec here
    parametrizedEntity.addParameter(getParameterFactory(serviceLocator).createSimpleParameter(parameterName, newValue));
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
}
