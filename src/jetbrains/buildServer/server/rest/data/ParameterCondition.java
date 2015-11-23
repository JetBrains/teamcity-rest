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
import java.util.Collection;
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
  @Nullable private final String myParameterName;
  @Nullable private final String myParameterValue;
  @NotNull private final RequirementType myRequirementType;

  public ParameterCondition(@Nullable final String name, @Nullable final String value, final @NotNull RequirementType requirementType) {
    myParameterName = name;
    myParameterValue = value;
    myRequirementType = requirementType;
  }

  public static ParameterCondition create(@Nullable final String propertyConditionLocator) {
    if (propertyConditionLocator == null){
      return null;
    }
    final Locator locator = new Locator(propertyConditionLocator, NAME, VALUE, TYPE);

    final String name = locator.getSingleDimensionValue(NAME);
    final String value = locator.getSingleDimensionValue(VALUE);

    RequirementType requirement = value != null ? RequirementType.CONTAINS : RequirementType.EXISTS;

    final String type = locator.getSingleDimensionValue(TYPE);
    if (type != null){
      requirement = RequirementType.findByName(type);
      if (requirement == null){
        throw new BadRequestException("Unsupported parameter match type. Supported are: " + getAllRequirementTypes());
      }
    }
    final ParameterCondition result = new ParameterCondition(name, value, requirement);
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
    if (!StringUtil.isEmpty(myParameterName)) {
      final String value = parametersProvider.get(myParameterName);
      return matches(value);
    }
    for (Map.Entry<String, String> parameter : parametersProvider.getAll().entrySet()) {
      if (matches(parameter.getValue())) return true;
    }
    return false;
  }

  public boolean matches(@NotNull final Collection<Parameter> parametersProvider) {
    for (Parameter parameter : parametersProvider) {
      if (parameterMatches(parameter)) return true;
    }
    return false;
  }

  public boolean parameterMatches(@NotNull final Parameter parameter) {
    if (myRequirementType.isParameterRequired() && myParameterValue == null) {
      return false;
    }
    if (!StringUtil.isEmpty(myParameterName)) {
      if (myParameterName.equals(parameter.getName())) {
        return matches(parameter.getValue());
      } else {
        return false;
      }
    } else {
      return matches(parameter.getValue());
    }
  }

  public boolean matches(@Nullable final String value) {
    if (myRequirementType.isParameterRequired() && myParameterValue == null) {
      return false;
    }
    if (myRequirementType.isActualValueRequired() && value == null) {
      return false;
    }
    if (!myRequirementType.isActualValueCanBeEmpty() && (value == null || value.length() == 0)) {
      return false;
    }
    return myRequirementType.matchValues(myParameterValue, value);
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
