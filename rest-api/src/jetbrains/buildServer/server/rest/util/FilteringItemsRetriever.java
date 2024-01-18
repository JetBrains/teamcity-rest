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

package jetbrains.buildServer.server.rest.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.data.finder.Finder;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.model.ItemsProviders;
import jetbrains.buildServer.server.rest.model.PagerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FilteringItemsRetriever<T> implements ItemsProviders.ItemsRetriever<T> {
  private final ItemsProviders.ItemsRetriever<T> myUnfilteredItems;
  private List<T> myFilteredItems = null;
  private final String myLocator;
  private final Finder<T> myFinder;

  /**
   * Post-filters retrieved items via given locator.
   */
  public FilteringItemsRetriever(@NotNull final ItemsProviders.ItemsRetriever<T> unfilteredItems,
                                 @Nullable final String locator,
                                 @NotNull final Finder<T> finder) {
    myUnfilteredItems = unfilteredItems;
    myLocator = locator;
    myFinder = finder;
  }

  @NotNull
  @Override
  public List<T> getItems() {
    if (myLocator == null) {
      if (myFilteredItems == null) {
        myFilteredItems = getItemsFromRetriever();
      }

      return myFilteredItems;
    }

    if (myFilteredItems != null) {
      return myFilteredItems;
    }

    myFilteredItems = new ArrayList<>();
    ItemFilter<T> filter = myFinder.getFilter(myLocator);
    for (T promo : getItemsFromRetriever()) {
      if (filter.shouldStop(promo)) {
        break;
      }

      if (filter.isIncluded(promo)) {
        myFilteredItems.add(promo);
      }
    }

    return myFilteredItems;
  }

  @NotNull
  private List<T> getItemsFromRetriever() {
    List<T> items = myUnfilteredItems.getItems();
    return items == null ? Collections.emptyList() : items;
  }

  @Override
  public Integer getCount() {
    return getItems().size();
  }

  @Override
  public boolean isCountCheap() {
    // myFilteredItems will be null if getItems() was not called earlier, but we still can tell that
    // count is cheap when myLocator == null (i.e. filtering is not required).
    return myFilteredItems != null || myLocator == null;
  }

  @Nullable
  @Override
  public PagerData getPagerData() {
    return myUnfilteredItems.getPagerData();
  }
}