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

package jetbrains.buildServer.server.rest.util;

import java.util.List;
import jetbrains.buildServer.server.rest.model.ItemsProviders;
import jetbrains.buildServer.server.rest.model.PagerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ListBasedItemsRetriever<T> implements ItemsProviders.ItemsRetriever<T> {
  private final List<T> myItems;
  private final PagerData myPagerData;

  public ListBasedItemsRetriever(@NotNull List<T> items) {
    myItems = items;
    myPagerData = null;
  }

  public ListBasedItemsRetriever(@NotNull List<T> items, @Nullable PagerData pagerData) {
    myItems = items;
    myPagerData = pagerData;
  }

  @Nullable
  @Override
  public List<T> getItems() {
    return myItems;
  }

  @Override
  public Integer getCount() {
    return myItems.size();
  }

  @Override
  public boolean isCountCheap() {
    return true;
  }

  @Nullable
  @Override
  public PagerData getPagerData() {
    return myPagerData;
  }
}
