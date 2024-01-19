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

package jetbrains.buildServer.server.rest.data.finder.builder;

import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder.Filter;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder.ItemsFromDimension;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder.TypeFromItem;
import org.jetbrains.annotations.NotNull;

public interface TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> extends TypedFinderDimension<ITEM, TYPE> {
  /**
   * Provide a way to extract key from the given item, e.g. some field, which will be used for comparison via default filter.
   *
   * @param retriever key extracting mapper
   */
  @NotNull
  TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> valueForDefaultFilter(@NotNull TypeFromItem<TYPE_FOR_FILTER, ITEM> retriever);

  // all the same as in parent interface, with more precise return type

  @NotNull
  @Override
  TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> description(@NotNull String description);

  @NotNull
  @Override
  TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> hidden();

  @NotNull
  @Override
  TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> withDefault(@NotNull String value);

  @NotNull
  @Override
  TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> filter(@NotNull Filter<TYPE, ITEM> checker);

  @NotNull
  @Override
  TypedFinderDimensionWithDefaultChecker<ITEM, TYPE, TYPE_FOR_FILTER> toItems(@NotNull ItemsFromDimension<ITEM, TYPE> filteringMapper);
}