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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.InheritableUserParametersHolder;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 17.01.12
 */
public class ParameterCondition {

  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String INHERITED = "inherited";
  public static final String TYPE = "matchType";
  public static final String IGNORE_CASE = "ignoreCase";
  protected static final String NAME_MATCH_CHECK = "matchScope"; // can be set to:
  // "all" to require all the name-matched params to match using "matchType"
  // "any" to require at least one of the name-matched params to match using "matchType"

  @NotNull private final ValueCondition myNameCondition;
  @NotNull private final ValueCondition myValueCondition;
  private final boolean myNameCheckShouldMatchAll;
  @Nullable private final Boolean myInheritedCondition;

  private ParameterCondition(@NotNull final ValueCondition nameCondition,
                             @NotNull final ValueCondition valueCondition,
                             final boolean nameCheckShouldMatchAll,
                             @Nullable final Boolean inherited) {
    myValueCondition = valueCondition;
    myNameCondition = nameCondition;
    myNameCheckShouldMatchAll = nameCheckShouldMatchAll;
    myInheritedCondition = inherited;
  }

  @Nullable
  @Contract("!null -> !null; null -> null")
  public static ParameterCondition create(@Nullable final String propertyConditionLocator) {
    if (propertyConditionLocator == null){
      return null;
    }
    final Locator locator = new Locator(propertyConditionLocator, NAME, VALUE, TYPE, IGNORE_CASE, NAME_MATCH_CHECK, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);

    final String value = locator.getSingleDimensionValue(VALUE);

    RequirementType requirement;

    final String type = locator.getSingleDimensionValue(TYPE);
    if (type != null){
      requirement = RequirementType.findByName(type);
      if (requirement == null){
        throw new BadRequestException("Unsupported value for '" + TYPE + "'. Supported are: " + getAllRequirementTypes());
      }
    } else{
      requirement = value != null ? RequirementType.CONTAINS : RequirementType.EXISTS;
    }

    ValueCondition nameCondition;
    final String name = locator.isSingleValue() ? locator.getSingleValue() : locator.getSingleDimensionValue(NAME);
    if (name == null) {
      nameCondition = new ValueCondition(RequirementType.ANY, name, null);
    } else {
      try {
        nameCondition = createValueCondition(name);
      } catch (BadRequestException e) {
        throw new BadRequestException("Wrong name condition: " + e.getMessage(), e);
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

    Boolean ignoreCase = locator.getSingleDimensionValueAsBoolean(IGNORE_CASE);
    Boolean inherited = locator.getSingleDimensionValueAsBoolean(INHERITED);
    locator.checkLocatorFullyProcessed();
    return new ParameterCondition(nameCondition, new ValueCondition(requirement, value, ignoreCase), nameCheckShouldMatchAll, inherited);
  }

  @Nullable
  @Contract("!null -> !null; null -> null")
  public static ValueCondition createValueCondition(@Nullable final String propertyConditionLocator) {
    if (propertyConditionLocator == null) {
      return null;
    }

    final Locator locator = new Locator(propertyConditionLocator, VALUE, TYPE, IGNORE_CASE, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);

    final String value = locator.isSingleValue() ? locator.getSingleValue() : locator.getSingleDimensionValue(VALUE);

    RequirementType requirement;
    final String type = locator.getSingleDimensionValue(TYPE);
    if (type != null) {
      requirement = RequirementType.findByName(type);
      if (requirement == null || RequirementType.EXISTS.equals(requirement) || RequirementType.NOT_EXISTS.equals(requirement)) {
        List<String> supportedSingleValueRequirementTypes = new ArrayList<>(getAllRequirementTypes());
        supportedSingleValueRequirementTypes.remove(RequirementType.EXISTS.getName());
        supportedSingleValueRequirementTypes.remove(RequirementType.NOT_EXISTS.getName());
        throw new BadRequestException("Unsupported value '" + type + "' for '" + TYPE + "' for single value condition. Supported are: " + supportedSingleValueRequirementTypes);
      }
    } else {
      if (locator.isSingleValue()) {
        requirement = RequirementType.EQUALS;
      } else {
        requirement = RequirementType.CONTAINS; //todo: make it equals by default?
      }
    }

    Boolean ignoreCase = locator.getSingleDimensionValueAsBoolean(IGNORE_CASE);
    locator.checkLocatorFullyProcessed();
    return new ValueCondition(requirement, value, ignoreCase);
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
    if (myInheritedCondition != null) throw new OperationException("Cannot filter by " + INHERITED + " dimension for the entity");
    return matchesInternal(parametersProvider);
  }

  private boolean matchesInternal(final @NotNull ParametersProvider parametersProvider) {
    String constantValueIfSimpleEqualsCondition = myNameCondition.getConstantValueIfSimpleEqualsCondition();
    if (!StringUtil.isEmpty(constantValueIfSimpleEqualsCondition)) {
      final String value = parametersProvider.get(constantValueIfSimpleEqualsCondition);
      return myValueCondition.matches(value);
    }
    boolean matched = false;
    for (Map.Entry<String, String> parameter : parametersProvider.getAll().entrySet()) {
      if (!myNameCondition.matches(parameter.getKey())) continue;
      if (myNameCheckShouldMatchAll) {
        if (myValueCondition.matches(parameter.getValue())) {
          matched = true;
        } else {
          return false;
        }
      } else {
        if (myValueCondition.matches(parameter.getValue())) return true;
      }
    }
    return matched;
  }

  public boolean matches(@NotNull final InheritableUserParametersHolder parametersHolder) {
    return matches(new MapParametersProviderImpl(parametersHolder.getParameters()), new MapParametersProviderImpl(parametersHolder.getOwnParameters()));
  }

  /**
   * @param parametersProvider
   * @param ownParametersProvider subset of 'parametersProvider' with all the same values which determines which parameter to consider not inherited
   * @return
   */
  public boolean matches(@NotNull final ParametersProvider parametersProvider, @NotNull final ParametersProvider ownParametersProvider) {
    if (myInheritedCondition != null) {
      if (!myInheritedCondition) return matchesInternal(ownParametersProvider);
      return matchesInternal(subtract(parametersProvider, ownParametersProvider));
    }
    return matchesInternal(parametersProvider);
  }

  @NotNull
  private ParametersProvider subtract(@NotNull final ParametersProvider larger, @NotNull final ParametersProvider smaller) {
    return new ParametersProvider() {
      @Nullable
      @Override
      public String get(@NotNull final String key) {
        if (smaller.get(key) != null) {
          return null;
        }
        return larger.get(key);
      }

      @Override
      public int size() {
        return getAll().size();
      }

      @Override
      public Map<String, String> getAll() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(larger.getAll());
        for (String key : smaller.getAll().keySet()) {
          result.remove(key);
        }
        return result;
      }
    };
  }

  public boolean parameterMatches(@NotNull final Parameter parameter, final boolean inherited) {
    if (myNameCheckShouldMatchAll) throw new OperationException("Dimension '" + NAME_MATCH_CHECK + "' is not supported for this filter");
    return myNameCondition.matches(parameter.getName()) && myValueCondition.matches(parameter.getValue()) && FilterUtil.isIncludedByBooleanFilter(myInheritedCondition, inherited);
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("Parameter condition (");
    result.append("name: ");
    if (myNameCondition.getConstantValueIfSimpleEqualsCondition() != null) {
      result.append(myNameCondition.getConstantValueIfSimpleEqualsCondition());
    } else {
      result.append(myNameCondition.toString());
    }
    result.append(", ").append("value condition: ").append(myValueCondition.toString());
    result.append(")");
    return result.toString();
  }
}
