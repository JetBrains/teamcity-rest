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

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by yaegor on 11/04/2016.
 */
public class LocatorBasedFilterBuilder<T> {
  private final Locator myLocator;
  @NotNull private final List<FilterConditionChecker<T>> myFilters = new ArrayList<FilterConditionChecker<T>>();

  public LocatorBasedFilterBuilder(@NotNull final Locator locator) {
    myLocator = locator;
  }

  public LocatorBasedFilterBuilder<T> add(FilterConditionChecker<T> checker) {
    myFilters.add(checker);
    return this;
  }

  public ItemFilter<T> getFilter() {
    return new ItemFilter<T>() {
      @Override
      public boolean shouldStop(@NotNull final T item) {
        return false;
      }

      @Override
      public boolean isIncluded(@NotNull final T item) {
          for (FilterConditionChecker<T> checker : myFilters) {
            if (!checker.isIncluded(item)) {
              return false;
            }
          }
          return true;
        }
    };
  }

  public ItemFilter<T> getOrFilter() {
    return new ItemFilter<T>() {
      @Override
      public boolean shouldStop(@NotNull final T item) {
        return false;
      }

      @Override
      public boolean isIncluded(@NotNull final T item) {
        for (FilterConditionChecker<T> checker : myFilters) {
          if (checker.isIncluded(item)) {
            return true;
          }
        }
        return myFilters.isEmpty();
      }
    };
  }

  /**
   * Note that filter is not added if the locator dimension is already marked as "used" - this requires proper dimension unmarking if it was retrieved but the results
   * were not fully filtered by the dimension
   *
   * @param locatorBasedAlternative a way to get value of the dimension from the locator if 'dimensionName' dimension is not present (e.g. for legacy support)
   * @return
   */
  public LocatorBasedFilterBuilder<T> addFilter(@NotNull final String dimensionName,
                                                @Nullable final NullableValue<String, Locator> locatorBasedAlternative,
                                                @NotNull ValueFromString<FilterConditionChecker<T>> retriever) {
    if (!myLocator.isUnused(dimensionName) && TeamCityProperties.getBooleanOrTrue("rest.finder.excludeUsedDimensionsFromFilter")) {
      return this;
    }
    List<String> dimensionValueTexts = myLocator.getDimensionValue(dimensionName);
    if (dimensionValueTexts.isEmpty() && locatorBasedAlternative != null){
      final String alternative = locatorBasedAlternative.get(myLocator);
      if (alternative != null) dimensionValueTexts = Collections.singletonList(alternative);
    }
    for (String dimensionValueText : dimensionValueTexts) {
      final FilterConditionChecker<T> checker;
      try {
        checker = retriever.get(dimensionValueText);
        if (checker == null){
          continue;
        }
      } catch (LocatorProcessException e) {
        throw new LocatorProcessException("Invalid value of dimension '" + dimensionName + ": " + e.getMessage(), e);
      }catch (RuntimeException e) {
        throw new LocatorProcessException("Invalid value of dimension '" + dimensionName + ": " + e.toString(), e);
      }
      myFilters.add(checker);
    }
    return this;
  }

  public LocatorBasedFilterBuilder<T> addFilter(@NotNull final String dimensionName, @NotNull ValueFromString<FilterConditionChecker<T>> retriever) {
    return addFilter(dimensionName, null, retriever);
  }

  public <V> LocatorBasedFilterBuilder<T> addSingleValueFilter(@NotNull final String dimensionName, @NotNull ValueFromString<V> retriever, @NotNull ValueChecker<T, V> checker) {
    return addFilter(dimensionName, new ValueFromString<FilterConditionChecker<T>>() {
      @Override
      public FilterConditionChecker<T> get(@NotNull final String valueText) {
        final V context = retriever.get(valueText);
        if (context == null){
          return null;
        }
        return new FilterConditionChecker<T>() {
          @Override
          public boolean isIncluded(@NotNull final T item) {
            return checker.isIncluded(context, item);
          }
        };
      }
    });
  }

  public LocatorBasedFilterBuilder<T> addStringFilter(@NotNull final String dimensionName, @NotNull ValueChecker<T, String> checker) {
    return addSingleValueFilter(dimensionName, new ValueFromString<String>() {
      @Override
      public String get(@NotNull final String valueText) {
        return valueText;
      }
    }, checker);
  }

  public static final ValueFromString<Long> LONG = new ValueFromString<Long>() {
    @Override
    public Long get(@NotNull final String valueText) {
      try {
        return Long.parseLong(valueText);
      } catch (NumberFormatException e) {
        throw new LocatorProcessException("Invalid value '" + valueText + "'. Should be a number.");
      }
    }
  };

  public LocatorBasedFilterBuilder<T> addLongFilter(@NotNull final String dimensionName, @NotNull ValueChecker<T, Long> checker) {
    return addSingleValueFilter(dimensionName, LONG, checker);
  }


  public LocatorBasedFilterBuilder<T> addBooleanMatchFilter(@NotNull final String dimensionName, @NotNull final NotNullValue<Boolean, T> valueRetriever) {
    return addSingleValueFilter(dimensionName, new ValueFromString<Boolean>() {
      @Override
      public Boolean get(@NotNull final String valueText) {
        return Locator.getBooleanByValue(valueText);
      }
    }, new ValueChecker<T, Boolean>() {
      @Override
      public boolean isIncluded(@NotNull final Boolean value, @NotNull final T item) {
        return FilterUtil.isIncludedByBooleanFilter(value, valueRetriever.get(item));
      }
    });
  }

  public LocatorBasedFilterBuilder<T> addParameterConditionFilter(@NotNull final String dimensionName, @NotNull final NullableValue<String, T> valueRetriever) {
    return addSingleValueFilter(dimensionName, new ValueFromString<ValueCondition>() {
      @Override
      public ValueCondition get(@NotNull final String valueText) {
        return ParameterCondition.createValueCondition(valueText);
      }
    }, new ValueChecker<T, ValueCondition>() {
      @Override
      public boolean isIncluded(@NotNull final ValueCondition value, @NotNull final T item) {
        return value.matches(valueRetriever.get(item));
      }
    });
  }

  public LocatorBasedFilterBuilder<T> addParametersConditionFilter(@NotNull final String dimensionName, @NotNull final NotNullValue<ParametersProvider, T> valueRetriever) {
    return addSingleValueFilter(dimensionName, new ValueFromString<ParameterCondition>() {
      @Override
      public ParameterCondition get(@NotNull final String valueText) {
        return ParameterCondition.create(valueText);
      }
    }, new ValueChecker<T, ParameterCondition>() {
      @Override
      public boolean isIncluded(@NotNull final ParameterCondition value, @NotNull final T item) {
        return value.matches(valueRetriever.get(item));
      }
    });
  }


  public static interface ValueFromString<V> {
    @Nullable
    V get(@NotNull final String valueText);
  }

  public static interface NullableValue<R, S> {
    @Nullable
    R get(@NotNull final S item);
  }

  public static interface NotNullValue<R, S> {
    @NotNull
    R get(@NotNull final S item);
  }

  public static interface ValueChecker<T, V> {
    boolean isIncluded(@NotNull V value, @NotNull T item);
  }
}
