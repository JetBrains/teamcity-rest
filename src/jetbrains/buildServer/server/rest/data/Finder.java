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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 06/06/2016
 */
public interface Finder<ITEM> {
  /**
   * Reverse opertion to {@link #getItem(String)}.
   * @return string "canonical" representation for a locator for the item passed
   */
  @NotNull
  String getCanonicalLocator(@NotNull ITEM item);

  /**
   * @param locatorText if null, BadRequestException is thrown
   * @return
   */
  @NotNull
  ITEM getItem(@Nullable String locatorText);

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
  default String getName(){ return getClass().getSimpleName();}
}
