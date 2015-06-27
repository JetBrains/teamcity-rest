/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 *         Date: 09.09.2009
 */
public abstract class ProxyFilter<T> extends AbstractFilter<T> {
  @NotNull private final AbstractFilter<T> myFilter;

  public ProxyFilter(@NotNull AbstractFilter<T> filter) {
    super(null, null, null);
    myFilter = filter;
  }

  @Override
  protected boolean isIncludedByRange(final long matchedItemsIndex) {
    return myFilter.isIncludedByRange(matchedItemsIndex);
  }

  @Override
  protected boolean isBelowUpperRangeLimit(final long matchedItemsIndex, final long processedItemsIndex) {
    return myFilter.isBelowUpperRangeLimit(matchedItemsIndex, processedItemsIndex);
  }

  @Override
  protected boolean isIncluded(@NotNull T item) {
    return myFilter.isIncluded(item);
  }

  @Override
  public boolean shouldStop(final T item) {
    return myFilter.shouldStop(item);
  }

  @Override
  @Nullable
  public Long getStart() {
    return myFilter.getStart();
  }

  @Override
  @Nullable
  public Integer getCount() {
    return myFilter.getCount();
  }

  @Override
  @Nullable
  public Long getLookupLimit() {
    return myFilter.getLookupLimit();
  }

  @Override
  public boolean isLookupLimitReached() {
    return myFilter.isLookupLimitReached();
  }
}
