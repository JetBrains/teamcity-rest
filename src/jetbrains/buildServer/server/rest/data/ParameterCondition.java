/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.01.12
 */
public class ParameterCondition {

  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String TYPE = "matchType";
  protected static final String NAME_MATCH_TYPE = "nameMatchType"; // condition to restrict parameters by name before checking value with "matchType"
  protected static final String NAME_MATCH_CHECK = "matchScope"; // when "nameMatchType" is specified, can be set to:
  // "all" to require all the name-matched params to match using "matchType"
  // "any" to require at least one of the name-matched params to match using "matchType"

  @Nullable private final String myParameterName;
  @Nullable private final String myParameterValue;
  @NotNull private final RequirementType myRequirementType;
  @Nullable private RequirementType myNameRequirementType;
  private boolean myNameCheckShouldMatchAll;

  public ParameterCondition(@Nullable final String name,
                            @Nullable final String value,
                            final @NotNull RequirementType requirementType,
                            @Nullable final RequirementType nameRequirementType,
                            final boolean nameCheckShouldMatchAll) {
    myParameterName = name;
    myParameterValue = value;
    myRequirementType = requirementType;
    myNameRequirementType = nameRequirementType;
    myNameCheckShouldMatchAll = nameCheckShouldMatchAll;
  }

  public static ParameterCondition create(@Nullable final String propertyConditionLocator) {
    if (propertyConditionLocator == null){
      return null;
    }
    final Locator locator = new Locator(propertyConditionLocator, NAME, VALUE, TYPE, NAME_MATCH_TYPE, NAME_MATCH_CHECK);

    final String name = locator.getSingleDimensionValue(NAME);
    final String value = locator.getSingleDimensionValue(VALUE);

    RequirementType requirement = value != null ? RequirementType.CONTAINS : RequirementType.EXISTS;

    final String type = locator.getSingleDimensionValue(TYPE);
    if (type != null){
      requirement = RequirementType.findByName(type);
      if (requirement == null){
        throw new BadRequestException("Unsupported value for '" + TYPE + "'. Supported are: " + getAllRequirementTypes());
      }
    }

    RequirementType nameRequirement = null;
    final String nameType = locator.getSingleDimensionValue(NAME_MATCH_TYPE);
    if (nameType != null) {
      nameRequirement = RequirementType.findByName(nameType);
      if (nameRequirement == null) {
        throw new BadRequestException("Unsupported value for '" + NAME_MATCH_TYPE + "'. Supported are: " + getAllRequirementTypes());
      }
    }
    final String nameRequirementCheck = locator.getSingleDimensionValue(NAME_MATCH_CHECK);
    final boolean nameCheckShouldMatchAll;
    if (StringUtil.isEmpty(nameRequirementCheck) || "any".equals(nameRequirementCheck) || "or".equals(nameRequirementCheck)) {
      nameCheckShouldMatchAll = false;
    } else if ("all".equals(nameRequirementCheck) || "and".equals(nameRequirementCheck)) {
      nameCheckShouldMatchAll = true;
    } else {
      throw new BadRequestException("Unsupported value for '" + NAME_MATCH_CHECK + "'. Supported are: " + "any, all");
    }
    final ParameterCondition result = new ParameterCondition(name, value, requirement, nameRequirement, nameCheckShouldMatchAll);
    locator.checkLocatorFullyProcessed();
    return result;
  }

  @NotNull
  public static Matcher<ParametersProvider> create(@Nullable final List<String> propertyConditionLocators) {
    if (propertyConditionLocators == null || propertyConditionLocators.isEmpty()) {
      return new Matcher<ParametersProvider>() {
        public boolean matches(@NotNull final ParametersProvider parametersProvider) {
          return true;
        }
      };
    }

    final List<ParameterCondition> list = new ArrayList<ParameterCondition>(propertyConditionLocators.size());
    for (String propertyConditionLocator : propertyConditionLocators) {
      final ParameterCondition condition = create(propertyConditionLocator);
      if (condition != null) list.add(condition);
    }
    return new Matcher<ParametersProvider>() {
      public boolean matches(@NotNull final ParametersProvider parametersProvider) {
        for (ParameterCondition condition : list) {
          if (!condition.matches(parametersProvider)) return false;
        }
        return true;
      }
    };
  }

  private static List<String> getAllRequirementTypes() {
    return CollectionsUtil.convertCollection(RequirementType.ALL_REQUIREMENT_TYPES, new Converter<String, RequirementType>() {
      public String createFrom(@NotNull final RequirementType source) {
        return source.getName();
      }
    });
  }

  public boolean matches(@NotNull final ParametersProvider parametersProvider) {
    if (myRequirementType.isParameterRequired() && myParameterValue == null) {
      return false;
    }
    if (myNameRequirementType == null && !StringUtil.isEmpty(myParameterName)) {
      final String value = parametersProvider.get(myParameterName);
      return matches(myRequirementType, myParameterValue, value);
    }
    boolean matched = false;
    for (Map.Entry<String, String> parameter : parametersProvider.getAll().entrySet()) {
      if (myNameRequirementType != null) {
        if (!matches(myNameRequirementType, myParameterName, parameter.getKey())) continue;
      }
      if (myNameCheckShouldMatchAll) {
        if (matches(myRequirementType, myParameterValue, parameter.getValue())) {
          matched = true;
        } else {
          return false;
        }
      } else {
        if (matches(myRequirementType, myParameterValue, parameter.getValue())) return true;
      }
    }
    return matched;
  }

  public boolean parameterMatches(@NotNull final Parameter parameter) {
    if (myRequirementType.isParameterRequired() && myParameterValue == null) {
      return false;
    }
    if (!StringUtil.isEmpty(myParameterName)) {
      if (myParameterName.equals(parameter.getName())) {
        return matches(myRequirementType, myParameterValue, parameter.getValue());
      } else {
        return false;
      }
    } else {
      return matches(myRequirementType, myParameterValue, parameter.getValue());
    }
  }

  private static boolean matches(final RequirementType requirementType, final String requirementValue, @Nullable final String actualValue) {
    if (requirementType.isParameterRequired() && requirementValue == null) {
      return false;
    }
    if (requirementType.isActualValueRequired() && actualValue == null) {
      return false;
    }
    if (!requirementType.isActualValueCanBeEmpty() && (actualValue == null || actualValue.length() == 0)) {
      return false;
    }
    try {
      return requirementType.matchValues(requirementValue, actualValue);
    } catch (Exception e) {
      //e.g. more-than can throw NumberFormatException for non-number
      return false;
    }
  }

  public boolean matches(@Nullable final String value) {
    return matches(myRequirementType, myParameterValue, value);
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("Parameter condition (");
    result.append("name:").append(myParameterName).append(", ");
    if (myParameterValue!= null) result.append("value:").append(myParameterValue).append(", ");
    result.append(")");
    return result.toString();
  }
}
