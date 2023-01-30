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

package jetbrains.buildServer.server.rest.data;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.data.util.FilterUtil;
import jetbrains.buildServer.server.rest.data.util.Matcher;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.InheritableUserParametersHolder;
import jetbrains.buildServer.serverSide.Parameter;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see ValueCondition
 * @author Yegor.Yarko
 *         Date: 17.01.12
 */
public class ParameterCondition {
  private static Logger LOG = Logger.getInstance(ParameterCondition.class.getName());

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
  private final boolean myNameCheckShouldMatchAll; // all parameters matching by name must be matching by value too.
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

  @NotNull
  public static String getNameAndNotEmptyValueLocator(@NotNull String name) {
    return Locator.getStringLocator(NAME, name, TYPE, RequirementType.EXISTS.name().toLowerCase());
  }

  @Nullable
  @Contract("!null -> !null; null -> null")
  public static ParameterCondition create(@Nullable final String propertyConditionLocator) {
    if (propertyConditionLocator == null){
      return null;
    }
    Locator locator = new Locator(propertyConditionLocator);
    ParameterCondition result = create(locator);
    locator.checkLocatorFullyProcessed();
    return result;
  }

  @Nullable
  @Contract("!null -> !null; null -> null")
  public static ParameterCondition create(@Nullable final Locator locator) {
    if (locator == null){
      return null;
    }
    locator.addSupportedDimensions(NAME, VALUE, TYPE, IGNORE_CASE, NAME_MATCH_CHECK, INHERITED, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    locator.processHelpRequest();
    final String value = locator.getSingleDimensionValue(VALUE);

    RequirementType requirement;

    final String type = locator.getSingleDimensionValue(TYPE);
    if (type != null){
      requirement = RequirementType.findByName(type);
      if (requirement == null){
        throw new BadRequestException("Unsupported value '" + type + "' for '" + TYPE + "'. Supported are: " + getAllRequirementTypes());
      }
    } else{
      requirement = value != null ? getDefaultMatchCondition(locator) : RequirementType.EXISTS;
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
    return new ParameterCondition(nameCondition, new ValueCondition(requirement, value, ignoreCase), nameCheckShouldMatchAll, inherited);
  }

  @NotNull
  private static RequirementType getDefaultMatchCondition(@Nullable final Locator locator) {
    String defaultMatchType = TeamCityProperties.getPropertyOrNull("rest.parameterCondition.defaultMatchType");
    if (defaultMatchType == null) return RequirementType.EQUALS;
    if (defaultMatchType.contains("log")) {
      LOG.info("Got request with property condition and without matchType specified. Locator: \"" + locator + "\". Thread name: \"" + Thread.currentThread().getName() + "\"");
    }
    if (defaultMatchType.contains("contains")) {
      return RequirementType.CONTAINS;
    }
    return RequirementType.EQUALS;
  }

  @Nullable
  @Contract("!null -> !null; null -> null")
  public static ValueCondition createValueConditionFromPlainValueOrCondition(@Nullable final String propertyConditionLocator) {
    try {
      return createValueCondition(propertyConditionLocator, false);
    } catch (LocatorProcessException e) {
      //not a valid locator - consider it a plain text value then
      return new ValueCondition(RequirementType.EQUALS, propertyConditionLocator, false);
    }
  }

  @Nullable
  @Contract("!null -> !null; null -> null")
  public static ValueCondition createValueCondition(@Nullable final String propertyConditionLocator) {
    return createValueCondition(propertyConditionLocator, true);
  }

  @Nullable
  @Contract("!null -> !null; null -> null")
  private static ValueCondition createValueCondition(@Nullable final String propertyConditionLocator, boolean surroundingBracesHaveSpecialMeaning) {
    if (propertyConditionLocator == null) {
      return null;
    }

    final Locator locator = new Locator(propertyConditionLocator, new Locator.Metadata(false, surroundingBracesHaveSpecialMeaning),
                                        VALUE, TYPE, IGNORE_CASE, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);

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
        requirement = getDefaultMatchCondition(locator);
      }
    }

    Boolean ignoreCase = locator.getSingleDimensionValueAsBoolean(IGNORE_CASE);
    locator.checkLocatorFullyProcessed();
    return new ValueCondition(requirement, value, ignoreCase);
  }

  @NotNull
  public static Matcher<ParametersProvider> create(@Nullable final List<String> propertyConditionLocators) {
    if (propertyConditionLocators == null || propertyConditionLocators.isEmpty()) {
      return parametersProvider -> true;
    }

    final List<ParameterCondition> list = propertyConditionLocators
      .stream()
      .map(ParameterCondition::create)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    return parametersProvider -> {
      return list.stream().allMatch(condition -> condition.matches(parametersProvider));
    };
  }

  private static List<String> getAllRequirementTypes() {
    return CollectionsUtil.convertCollection(RequirementType.ALL_REQUIREMENT_TYPES, RequirementType::getName);
  }

  public static String getLocatorExactValueMatch(@NotNull final String value) {
    //if (value.equals(Locator.getValueForRendering(value))) return value;
    return Locator.getStringLocator(VALUE, value, IGNORE_CASE, "false", TYPE, RequirementType.EQUALS.getName());
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

  public boolean parameterMatches(@NotNull final Parameter parameter, @Nullable final Boolean inherited) {
    if (myNameCheckShouldMatchAll) throw new OperationException("Dimension '" + NAME_MATCH_CHECK + "' is not supported for this filter");
    return myNameCondition.matches(parameter.getName()) && myValueCondition.matches(parameter.getValue()) && FilterUtil.isIncludedByBooleanFilter(myInheritedCondition, inherited);
  }

  /** Use this condition to check parameters obtained from parametersProvider and return only matched ones.
   * @param parametersProvider
   * @return parameters stream, matching the condition.
   */
  @NotNull
  public Stream<Parameter> filterAllMatchingParameters(final @NotNull ParametersProvider parametersProvider) {
    // It is unclear what to do in a case when myNameCheckShouldMatchAll is true and some parameters do not pass this check.
    if (myNameCheckShouldMatchAll) {
      throw new OperationException("Dimension '" + NAME_MATCH_CHECK + "' is not supported for this filter");
    }

    String exactParameterName = myNameCondition.getConstantValueIfSimpleEqualsCondition();
    if (!StringUtil.isEmpty(exactParameterName)) {
      final String value = parametersProvider.get(exactParameterName);
      if(value != null && myValueCondition.matches(value)) {
        return Stream.of(new SimpleParameter(exactParameterName, value));
      }

      return Stream.empty();
    }

    return parametersProvider.getAll().entrySet().stream()
                             .filter(entry -> myNameCondition.matches(entry.getKey()))
                             .filter(entry -> myValueCondition.matches(entry.getValue()))
                             .map(entry -> new SimpleParameter(entry.getKey(), entry.getValue()));
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
