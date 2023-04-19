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

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 26.03.13
 */
public class PagedSearchResult<T> {
  @NotNull private final List<T> myEntries;
  private final int myActualCount;
  @Nullable private final Long myStart;
  @Nullable private final Integer myCount;
  @Nullable private final Long myActuallyProcessedCount;
  private final boolean myLookupLimitReached;
  @Nullable private final Long myLookupLimit;
  @Nullable private T myLastProcessedItem;

  public PagedSearchResult(@NotNull final List<T> entries, @Nullable final Long requestedStart, @Nullable final Integer requestedCount) {
    myEntries = entries;
    myActualCount = entries.size();
    myStart = requestedStart;
    myCount = requestedCount;
    myActuallyProcessedCount = null;
    myLookupLimit = null;
    myLookupLimitReached = false;
  }

  public PagedSearchResult(@NotNull final List<T> entries, @Nullable final Long requestedStart, @Nullable final Integer requestedCount,
                           @Nullable final Long actuallyProcessedCount, @Nullable final Long lookupLimit, final boolean lookupLimitReached, @Nullable final T lastProcessedItem) {
    myEntries = entries;
    myActualCount = entries.size();
    myStart = requestedStart;
    myCount = requestedCount;
    myActuallyProcessedCount = actuallyProcessedCount;
    myLookupLimit = lookupLimit;
    myLookupLimitReached = lookupLimitReached;
    myLastProcessedItem = lastProcessedItem;
  }

  @Nullable
  public T getLastProcessedItem() {
    return myLastProcessedItem;
  }

  @NotNull
  public List<T> getEntries() {
    return myEntries;
  }

  public int getActualCount() {
    return myActualCount;
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
  public Long getActuallyProcessedCount() {
    return myActuallyProcessedCount;
  }

  public boolean getLookupLimitReached() {
    return myLookupLimitReached;
  }

  @Nullable
  public Long getLookupLimit() {
    return myLookupLimit;
  }

  public boolean isNextPageAvailable(){
    return myCount != null && myActualCount >= myCount || myLookupLimit != null && myLookupLimitReached;
  }

}
