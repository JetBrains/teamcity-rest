/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Property;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.cleanup.CleanupSettingsSupport;
import jetbrains.buildServer.serverSide.impl.cleanup.HistoryRetentionPolicy;
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

  public static HashMap<String, String> getSettingsParameters(@NotNull final BuildTypeOrTemplate buildType, final boolean onlyOwn) {
    HashMap<String, String> properties = new HashMap<String, String>();
    addAllOptionsAsProperties(properties, buildType.get(), onlyOwn);
    if (buildType.getBuildType() != null) {
      properties.put("buildNumberCounter", String.valueOf(buildType.getBuildType().getBuildNumbers().getBuildCounter()));
    }
    return properties;
  }

  /**
   * Caller must ensure 'name' is a valid name of a BuildType setting
   * @see #getSettingsParameters(jetbrains.buildServer.serverSide.SBuildType)
   */
  public static void setSettingsParameter(final BuildTypeOrTemplate buildType, final String name, final String value) {
    if ("buildNumberCounter".equals(name)) {
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
  private static void addAllOptionsAsProperties(final HashMap<String, String> properties, final OptionSupport buildType, final boolean onlyOwn) {
    Collection<Option> ownOptions = buildType.getOwnOptions();
    Field[] declaredFields = BuildTypeOptions.class.getDeclaredFields();
    for (Field declaredField : declaredFields) {
      try {
        if (Option.class.isAssignableFrom(declaredField.get(buildType).getClass())) {
          Option option = null;
          option = (Option)declaredField.get(buildType);
          if (!onlyOwn || ownOptions.contains(option)) {
            //noinspection unchecked
            properties.put(option.getKey(), buildType.getOption(option).toString());
          }
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
                                    final boolean nameItProperty,
                                    @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException(nameItProperty ? "Property" : "Parameter" + " name cannot be empty.");
    }
    Parameter parameter = getParameter(parametrizedEntity, parameterName, nameItProperty);
    if (checkSecure && Property.isSecure(parameter, serviceLocator)) {
      throw new BadRequestException("Secure " + (nameItProperty ? "properties" : "parameters") + " cannot be retrieved via remote API by default.");
    }
    return parameter.getValue();
  }

  @NotNull
  private static Parameter getParameter(@NotNull final UserParametersHolder parametrizedEntity, @NotNull final String parameterName, final boolean nameItProperty) {
    for (Parameter parameter : parametrizedEntity.getParametersCollection()) { //TeamCity API issue: no way to get parameter object by name
      if (parameterName.equals(parameter.getName())) return parameter;
    }
    throw new NotFoundException((nameItProperty ? "No property" : "No parameter") + " with name '" + parameterName + "' is found.");
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

  public static void changeParameter(@Nullable final String parameterName,
                                     @Nullable final String newValue,
                                     @NotNull final UserParametersHolder parametrizedEntity,
                                     @NotNull final ServiceLocator serviceLocator) {
    if (StringUtil.isEmpty(parameterName)) {
      throw new BadRequestException("Parameter name cannot be empty.");
    }

    if (newValue == null) {
      throw new BadRequestException("Value for parameter '" + parameterName + "' should be specified.");
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
    if (existingValue == null){
      throw new NotFoundException("Parameter with name '" + parameterName + "' not found");
    }
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

  /**
   * Compares the build types excluding id, name, temlateFlag, parent project, template. Considers inlined settings.
   * The message of the exception details the difference in the
   *
   * @param compareInheritedFlag whether to ignore the source of the settings (own or inherited) or treat the different source as difference
   * @return human-friendly description of the different settings. 'null' if they are "equal". Can contain partial result.
   */
  public static String compareBuildTypes(@Nullable final BuildTypeSettingsEx a, @Nullable final BuildTypeSettingsEx b, boolean compareInheritedFlag, boolean compareIds) {
    if (a == b) return null;
    if (a == null) return "null <-> not null";
    if (b == null) return "not null <-> null";
    final Map<String, String> idMapping = new HashMap<>();
    StringBuilder result = new StringBuilder();
    result.append(compare("option", a.getOptions(), a.getOwnOptions(), b.getOptions(), b.getOwnOptions(), true, (result1, oA, oB, entityName, fieldPrefix) -> {
      result1.append(compareObjects(entityName, oA.getKey(), oB.getKey(), fieldPrefix + "key"));
      result1.append(compareObjects(entityName, a.getOption(oA), b.getOption(oB), fieldPrefix + "value"));
    }));
    result.append(compare("vcsRootEntry", a.getVcsRootEntries(), a.getOwnVcsRootEntries(), b.getVcsRootEntries(), b.getOwnVcsRootEntries(), compareInheritedFlag,
                          (result1, oA, oB, entityName, fieldPrefix) -> {
                            result1.append(compareObjects(entityName, oA.getCheckoutRules().getAsString(), oB.getCheckoutRules().getAsString(), fieldPrefix + "checkoutRules"));
                            result1.append(compareObjects(entityName, oA.getVcsRoot().getId(), oB.getVcsRoot().getId(), fieldPrefix + "vcRootId"));
                          }));
    result.append(compare("step", a.getBuildRunners(), a.getOwnBuildRunners(), b.getBuildRunners(), b.getOwnBuildRunners(), compareInheritedFlag,
                          (result1, oA, oB, entityName, fieldPrefix) -> {
                            idMapping.put(oA.getId(), oB.getId());
                            if (compareIds) result1.append(compareObjects("step", oA.getId(), oB.getId(), fieldPrefix + "id"));
                            result1.append(compareObjects(entityName, oA.getRunType().getType(), oB.getRunType().getType(), fieldPrefix + "type"));
                            result1.append(compareObjects(entityName, oA.getName(), oB.getName(), fieldPrefix + "name"));
                            result1.append(compareObjects(entityName, oA.getOwnBuildParameters(), oB.getOwnBuildParameters(), fieldPrefix + "parameters"));
                          }));
    result.append(compare("feature", a.getBuildFeatures(), a.getOwnBuildFeatures(), b.getBuildFeatures(), b.getOwnBuildFeatures(), compareInheritedFlag,
                          (result1, oA, oB, entityName, fieldPrefix) -> {
                            idMapping.put(oA.getId(), oB.getId());
                            if (compareIds) result1.append(compareObjects("feature", oA.getId(), oB.getId(), fieldPrefix + "id"));
                            result1.append(compareObjects(entityName, oA.getBuildFeature().getType(), oB.getBuildFeature().getType(), fieldPrefix + "type"));
                            result1.append(compareObjects(entityName, oA.getParameters(), oB.getParameters(), fieldPrefix + "parameters"));
                          }));
    result.append(compare("snapshotDependency", a.getDependencies(), a.getOwnDependencies(), b.getDependencies(), b.getOwnDependencies(), compareInheritedFlag,
                          (result1, oA, oB, entityName, fieldPrefix) -> {
                            result1.append(compareObjects(entityName, oA.getDependOnExternalId(), oB.getDependOnExternalId(), fieldPrefix + "source buildType id"));
                            result1.append(compareObjects(entityName, oA.getOptions(), oB.getOptions(), fieldPrefix + "options"));
                          }));
    result.append(compare("artifactDependency", a.getArtifactDependencies(), a.getOwnArtifactDependencies(), b.getArtifactDependencies(), b.getOwnArtifactDependencies(),
                          compareInheritedFlag,
                          (result1, oA, oB, entityName, fieldPrefix) -> {
                            idMapping.put(oA.getId(), oB.getId());
                            if (compareIds) result1.append(compareObjects(entityName, oA.getId(), oB.getId(), fieldPrefix + "id"));
                            result1.append(compareObjects(entityName, oA.getSourceExternalId(), oB.getSourceExternalId(), fieldPrefix + "source buildType id"));
                            result1.append(compareObjects(entityName, oA.getSourcePaths(), oB.getSourcePaths(), fieldPrefix + "paths"));
                            result1.append(compareObjects(entityName, oA.isCleanDestinationFolder(), oB.isCleanDestinationFolder(), fieldPrefix + "clean destination"));
                            result1.append(compareObjects(entityName, oA.getRevisionRule().getName(), oB.getRevisionRule().getName(), fieldPrefix + "revision name"));
                            result1.append(compareObjects(entityName, oA.getRevisionRule().getRevision(), oB.getRevisionRule().getRevision(), fieldPrefix + "revision"));
                            result1.append(compareObjects(entityName, oA.getRevisionRule().getBranch(), oB.getRevisionRule().getBranch(), fieldPrefix + "revision branch"));
                          }));
    result.append(compare("trigger", a.getBuildTriggersCollection(), a.getOwnBuildTriggers(), b.getBuildTriggersCollection(), b.getOwnBuildTriggers(),
                          compareInheritedFlag,
                          (result1, oA, oB, entityName, fieldPrefix) -> {
                            idMapping.put(oA.getId(), oB.getId());
                            if (compareIds) result1.append(compareObjects(entityName, oA.getId(), oB.getId(), fieldPrefix + "id"));
                            result1.append(compareObjects(entityName, oA.getType(), oB.getType(), fieldPrefix + "type"));
                            result1.append(compareObjects(entityName, oA.getTriggerName(), oB.getTriggerName(), fieldPrefix + "name"));
                            result1.append(compareObjects(entityName, oA.getParameters(), oB.getParameters(), fieldPrefix + "parameters"));
                            result1.append(compareObjects(entityName, oA.getProperties(), oB.getProperties(), fieldPrefix + "properties"));
                          }));
    result.append(compare("requirement", a.getRequirements(), a.getOwnRequirements(), b.getRequirements(), b.getOwnRequirements(), compareInheritedFlag,
                          (result1, oA, oB, entityName, fieldPrefix) -> {
                            idMapping.put(oA.getId(), oB.getId());
                            if (compareIds) result1.append(compareObjects(entityName, oA.getId(), oB.getId(), fieldPrefix + "id"));
                            result1.append(compareObjects(entityName, oA.getType(), oB.getType(), fieldPrefix + "type"));
                            result1.append(compareObjects(entityName, oA.getPropertyName(), oB.getPropertyName(), fieldPrefix + "property name"));
                            result1.append(compareObjects(entityName, oA.getPropertyValue(), oB.getPropertyValue(), fieldPrefix + "value"));
                          }));
    result.append(compare("parameter", a.getParametersCollection(), a.getOwnParametersCollection(), b.getParametersCollection(), b.getOwnParametersCollection(),
                          compareInheritedFlag,
                          (result1, oA, oB, entityName, fieldPrefix) -> {
                            result1.append(compareObjects(entityName, oA.getName(), oB.getName(), fieldPrefix + "name"));
                            result1.append(compareObjects(entityName, oA.getValue(), oB.getValue(), fieldPrefix + "value"));
                            if (oA.getControlDescription() == null && oB.getControlDescription() == null) return;
                            if (oA.getControlDescription() == null || oB.getControlDescription() == null) {
                              result1.append(compareObjects(entityName, oA.getControlDescription() == null, oB.getControlDescription() == null, fieldPrefix + "type"));
                              return;
                            }
                            result1.append(compareObjects(entityName, oA.getControlDescription().getParameterType(), oB.getControlDescription().getParameterType(),
                                                         fieldPrefix + "type"));
                            result1.append(compareObjects(entityName, oA.getControlDescription().getParameterTypeArguments(), oB.getControlDescription().getParameterTypeArguments(),
                                                         fieldPrefix + "type arguments"));
                          }));
    result.append(compareCleanupOptions(a.getOwnCleanupSupport(), b.getOwnCleanupSupport(), compareInheritedFlag));

    if (compareIds) {
      result.append(compare("disabledSetting", a.getDisabledParameterDescriptorIds(), a.getOwnDisabledParameterDescriptorIds(), b.getDisabledParameterDescriptorIds(),
                            b.getOwnDisabledParameterDescriptorIds(), compareInheritedFlag,
                            (result1, oA, oB, entityName, fieldPrefix) -> {
                              result1.append(compareObjects(entityName, oA, oB, fieldPrefix + "id"));
                            }));
    } else {
      {
        Collection<String> disabledB = b.getDisabledParameterDescriptorIds();
        Set<String> disabledBRest = new HashSet<>(disabledB);
        for (String aId : a.getDisabledParameterDescriptorIds()) {
          boolean contained = disabledBRest.remove(idMapping.get(aId));
          compareObjects("disabledSetting", true, contained, aId);
        }
        for (String bId : disabledBRest) {
          compareObjects("disabledSetting", false, true, bId);
        }
      }
      {
        Collection<String> disabledB = b.getOwnDisabledParameterDescriptorIds();
        Set<String> disabledBRest = new HashSet<>(disabledB);
        for (String aId : a.getOwnDisabledParameterDescriptorIds()) {
          boolean contained = disabledBRest.remove(idMapping.get(aId));
          compareObjects("disabledSetting", true, contained, "own " + aId);
        }
        for (String bId : disabledBRest) {
          compareObjects("disabledSetting", false, true, "own " + bId);
        }
      }
    }

    return result.length() == 0 ? null : result.toString();

/* Not using for comparison:
ProjectEx getProject()
boolean isTemplateBased()
BuildTypeTemplateEx getTemplate()

to process:
String[] getOwnRunnersOrder()

use?:
getOwnSerializableParameters
CleanupSettingsSupport getOwnCleanupSupport()


//derived:
List<Requirement> getRunTypeRequirements()
List<Requirement> getImplicitRequirements()
     */
  }

  static DiffCalculator<HistoryRetentionPolicy> CLEANUP_OPTIONS_DIFF_CALCULATOR = new DiffCalculator<HistoryRetentionPolicy>() {
    @Override
    public void calculate(@NotNull final StringBuilder result,
                          @Nullable final HistoryRetentionPolicy oA,
                          @Nullable final HistoryRetentionPolicy oB,
                          @NotNull final String entityName,
                          @NotNull final String fieldPrefix) {
      result.append(compareObjects("cleanupOption", oA.getType(), oB.getType(), fieldPrefix + "type"));
      result.append(compareObjects("cleanupOption", oA.getCleanupLevel(), oB.getCleanupLevel(), fieldPrefix + "level"));
      result.append(compareObjects("cleanupOption", oA.getParameters(), oB.getParameters(), fieldPrefix + "parameters"));
    }
  };

  private static String compareCleanupOptions(final CleanupSettingsSupport a, final CleanupSettingsSupport b, final boolean compareInheritedFlag) {
    StringBuilder result = new StringBuilder();
    result.append(compareObjects("cleanupOption", a.getCleanupOptions(), b.getCleanupOptions(), "option"));
    result.append(compare("cleanupOption", a.getCleanupPolicies(), b.getCleanupPolicies(), "", CLEANUP_OPTIONS_DIFF_CALCULATOR));
    if (compareInheritedFlag) {
      result.append(compareObjects("cleanupOption", a.getOwnCleanupOptions(), b.getOwnCleanupOptions(), "own option"));
      result.append(compare("cleanupOption", a.getOwnCleanupPolicies(), b.getOwnCleanupPolicies(), "own ", CLEANUP_OPTIONS_DIFF_CALCULATOR));
    }
    return result.toString();
  }


  @NotNull
  static String compareObjects(@Nullable Object a, @Nullable Object b, @NotNull String entity, @NotNull String fieldA, @NotNull String fieldB) {
    if (a == b || (a != null && a.equals(b))) {
      return "";
    }
    return entity + ": " + fieldA + "=" + String.valueOf(a) + " <-> " + fieldB + "=" + String.valueOf(b) + "\n";
  }

  @NotNull
  static String compareObjects(@NotNull String entity, @Nullable Object a, @Nullable Object b, @NotNull String field) {
    return compareObjects(a, b, entity, field, field);
  }

  private interface DiffCalculator<T> {
    void calculate(@NotNull StringBuilder result, @NotNull final T oA, @NotNull final T oB, @NotNull final String entityName, @NotNull final String fieldPrefix);
  }

  private interface Retriever<T> {
    @Nullable
    T get(@NotNull final T a);
  }

  private static <T> String compare(@NotNull final String entityName,
                                    @Nullable final Collection<T> a, @Nullable final Collection<T> aOwn,
                                    @Nullable final Collection<T> b, @Nullable final Collection<T> bOwn,
                                    final boolean compareInheritedFlag, @NotNull final DiffCalculator<T> diffCalculator) {
    StringBuilder result = new StringBuilder();
    result.append(compare(entityName, a, b, "", diffCalculator));
    if (compareInheritedFlag) {
      result.append(compare(entityName, aOwn, bOwn, "own ", diffCalculator));
    }
    return result.toString();
  }

  //private static <T> StringBuilder compare(@NotNull final String entityName, @Nullable final Collection<T> a, @Nullable final Collection<T> b,
  //                                         @NotNull final String fieldPrefix, @NotNull final DiffCalculator<T> diffCalculator) {
  //  StringBuilder result = new StringBuilder();
  //  if (a == b) return result;
  //  if (a == null || b == null) {
  //    result.append(compareObjects(entityName, a, b, fieldPrefix));
  //    return result;
  //  }
  //  result.append(compareObjects(entityName, a.size(), b.size(), fieldPrefix + "size"));
  //  Iterator<T> itA = a.iterator();
  //  Iterator<T> itB = b.iterator();
  //  while (itA.hasNext() || itB.hasNext()) {
  //    T oA = itA.hasNext() ? itA.next() : null;
  //    T oB = itB.hasNext() ? itB.next() : null;
  //    if (oA == oB) return result;
  //    if (oA == null || oB == null) {
  //      result.append(compareObjects(entityName, oA, oB, fieldPrefix));
  //      continue;
  //    }
  //    diffCalculator.calculate(result, oA, oB, entityName, fieldPrefix);
  //  }
  //  return result;
  //}

  private static <T> StringBuilder compare(@NotNull final String entityName, @Nullable final Collection<T> a, @Nullable final Collection<T> b,
                                           @NotNull final String fieldPrefix, @NotNull final DiffCalculator<T> diffCalculator) {
    StringBuilder result = new StringBuilder();
    if (a == b) return result;
    if (a == null || b == null) {
      result.append(compareObjects(entityName, a, b, fieldPrefix));
      return result;
    }
    result.append(compareObjects(entityName, a.size(), b.size(), fieldPrefix + "size"));
    Iterator<T> iB = b.iterator();
    result.append(compareWithoutOrder(entityName, a, a1 -> iB.hasNext() ? iB.next() : null, fieldPrefix, diffCalculator));
    while (iB.hasNext()) {
      result.append(compareObjects(entityName, null, iB.next(), fieldPrefix));
    }
    return result;
  }

  private static <T> StringBuilder compareWithoutOrder(@NotNull final String entityName, @NotNull final Collection<T> a, @NotNull final Retriever<T> b,
                                                       @NotNull final String fieldPrefix, @NotNull final DiffCalculator<T> diffCalculator) {
    StringBuilder result = new StringBuilder();
    for (final T oA : a) {
      T oB = b.get(oA);
      if (oB == null) {
        result.append(compareObjects(entityName, oA, null, fieldPrefix));
        continue;
      }
      diffCalculator.calculate(result, oA, oB, entityName, fieldPrefix);
    }
    return result;
  }
}
