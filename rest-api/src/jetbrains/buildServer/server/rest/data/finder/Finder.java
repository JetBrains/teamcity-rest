/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder;

import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main purpose of Finder is to retrieve data for REST API from TeamCity core.
 * <p/>
 * This class allows to search data using {@link Locator} - TeamCity internal query language.
 * <p/>
 * Finder is similar to DAO/Repository pattern.
 *
 * @param <ITEM> the type of items, handled by this finder.
 * @author Yegor Yarko
 * @date 06.06.2016
 * @see Locator Locator - data query object
 * @see FinderDataBinding
 */
public interface Finder<ITEM> {

  /**
   * Reverse opertion to {@link #getItem(String)}.
   *
   * @return string "canonical" representation for a locator for the item passed
   */
  @NotNull
  String getCanonicalLocator(@NotNull ITEM item);

  /**
   * @param locatorText locator of the item.
   * @return single item found by locator.
   * @throws jetbrains.buildServer.server.rest.errors.BadRequestException if {@code locatorText} is null.
   * @see {@link Locator#Locator(String)}
   */
  @NotNull
  ITEM getItem(@Nullable String locatorText);

  /**
   * @param locatorText locator of the group of items.
   * @return items, matched by this locator.
   * @see {@link Locator#Locator(String)}
   */
  @NotNull
  PagedSearchResult<ITEM> getItems(@Nullable String locatorText);

  /**
   * The filter returned can be used for checking is a single item is matching the finder condition.
   * The filter returned should check all the Finder's conditions.
   *
   * @param locatorText
   * @return
   */
  @NotNull
  ItemFilter<ITEM> getFilter(@NotNull String locatorText);

  @NotNull
  default String getName() {
    return getClass().getSimpleName();
  }
}
