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

package jetbrains.buildServer.server.rest.data.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2009
 */
public class PagingItemFilter<T> implements ItemFilter<T> {
  @NotNull private final ItemFilter<T> myFilter;
  @Nullable protected final Long myStart;
  @Nullable protected final Integer myCount;
  @Nullable private final Long myLookupLimit;
  private final long myActualStart;
  private boolean myLookupLimitReached = false;
  @Nullable private T myLastProcessedItem = null;

  public PagingItemFilter(@NotNull final ItemFilter<T> filter, @Nullable final Long start, @Nullable final Integer count, @Nullable final Long lookupLimit) {
    myFilter = filter;
    myStart = start;
    myCount = count;
    myLookupLimit = lookupLimit;

    myActualStart = myStart == null ? 0 : myStart;
  }

  public boolean isIncludedByRange(final long matchedItemsIndex) {
    return (matchedItemsIndex >= myActualStart) && (myCount == null || matchedItemsIndex < myActualStart + myCount);
  }

  public boolean isBelowUpperRangeLimit(final long matchedItemsIndex, final long processedItemsIndex) {
    if (myCount != null && matchedItemsIndex >= myActualStart + myCount) return false;
    if (myLookupLimit != null && processedItemsIndex >= myLookupLimit) {
      myLookupLimitReached = true;
      return false;
    }
    return true;
  }

  public boolean isIncluded(@NotNull final T item) {
    myLastProcessedItem = item;
    return myFilter.isIncluded(item);
  }

  public boolean shouldStop(@NotNull final T item) {
    return myFilter.shouldStop(item);
  }


  @Nullable
  public Long getStart() {
    return myStart;
  }

  @Nullable
  public Integer getCount() {
    return myCount;
  }

  @Nullable
  public Long getLookupLimit() {
    return myLookupLimit;
  }

  public boolean isLookupLimitReached() {
    return myLookupLimitReached;
  }

  @Nullable
  public T getLastProcessedItem() {
    return myLastProcessedItem;
  }
}
