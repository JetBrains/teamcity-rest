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

package jetbrains.buildServer.server.rest.data.util.finderBuilder;

import jetbrains.buildServer.server.rest.data.TypedFinderBuilder.Filter;
import jetbrains.buildServer.server.rest.data.TypedFinderBuilder.ItemsFromDimension;
import org.jetbrains.annotations.NotNull;

/**
 * Locator dimension builder. Allows to specify how to filter and retrieve items by given dimension.
 *
 * @param <ITEM> items, produced by corresponding Finder
 * @param <TYPE> dimension value type, integer, boolean, string, etc.
 */
public interface TypedFinderDimension<ITEM, TYPE> {
  /**
   * Specifies description of the dimension used in locator help, error messages and similar places.
   */
  @NotNull
  TypedFinderDimension<ITEM, TYPE> description(@NotNull String description);

  /**
   * Marks dimension as hidden, excluding it from locator help response.
   */
  @NotNull
  TypedFinderDimension<ITEM, TYPE> hidden();

  /**
   * Defines default value for the dimension, in case it's not present in the locator.
   */
  @NotNull
  TypedFinderDimension<ITEM, TYPE> withDefault(@NotNull String value);

  /**
   * Defines filter for the items obtained from other dimesnions.
   */
  @NotNull
  TypedFinderDimension<ITEM, TYPE> filter(@NotNull Filter<TYPE, ITEM> filter);

  /**
   * Defines a way to obtain items with given dimension value.
   * Items returned via {@link ItemsFromDimension} should be filtered exactly as if they were filtered via {@link #filter(Filter)}.
   *
   * @param filteringMapper mapping function producing items given the dimension value.
   */
  @NotNull
  TypedFinderDimension<ITEM, TYPE> toItems(@NotNull ItemsFromDimension<ITEM, TYPE> filteringMapper);

  /**
   * Defines a default filter for the dimension.
   *
   * @param checker           predicate function, decideing whether to include given item in the result or not.
   * @param <TYPE_FOR_FILTER>
   */
  @NotNull
  <TYPE_FOR_FILTER> TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> defaultFilter(@NotNull Filter<TYPE, TYPE_FOR_FILTER> checker);
}
