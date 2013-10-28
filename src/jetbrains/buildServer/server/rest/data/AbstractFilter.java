/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2009
 */
public abstract class AbstractFilter<T> {
  @Nullable protected final Long myStart;
  @Nullable protected final Integer myCount;
  @Nullable private final Long myLookupLimit;
  private final long myActualStart;

  public AbstractFilter(@Nullable final Long start, @Nullable final Integer count, @Nullable final Long lookupLimit) {
    myStart = start;
    myCount = count;
    myLookupLimit = lookupLimit;

    myActualStart = myStart == null ? 0 : myStart;
  }

  protected boolean isIncludedByRange(final long matchedItemsIndex) {
    return (matchedItemsIndex >= myActualStart) && (myCount == null || matchedItemsIndex < myActualStart + myCount);
  }

  protected boolean isBelowUpperRangeLimit(final long matchedItemsIndex, final long processedItemsIndex) {
    return  (myCount == null || matchedItemsIndex < myActualStart + myCount) && (myLookupLimit == null || processedItemsIndex < myLookupLimit);
  }


  protected abstract boolean isIncluded(@NotNull final T item);

  public static <P> void processList(final List<P> entries, final ItemProcessor<P> processor) {
    for (P entry : entries) {
      if (!processor.processItem(entry)){
        break;
      }
    }
  }

  public boolean shouldStop(final T item) {
    return false;
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
}
