/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest;

import java.util.ArrayList;
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

  public AbstractFilter(@Nullable final Long start, @Nullable final Integer count) {
    myStart = start;
    myCount = count;
  }

  protected boolean isIncludedByRange(final long index) {
    final long actualStart = myStart == null ? 0 : myStart;
    return (index >= actualStart) && (myCount == null || index < actualStart + myCount);
  }

  protected boolean isBelowUpperRangeLimit(final long index) {
    final long actualStart = myStart == null ? 0 : myStart;
    return myCount == null || index < actualStart + myCount;
  }


  protected abstract boolean isIncluded(@NotNull final T item);

  protected void processList(final List<T> entries, final ItemProcessor<T> processor) {
    for (T entry : entries) {
      processor.processItem(entry);
    }
  }


  protected static class FilterItemProcessor<T> implements ItemProcessor<T> {
    long myCurrentIndex = 0;
    private final AbstractFilter<T> myFilter;
    private final ArrayList<T> myList = new ArrayList<T>();

    public FilterItemProcessor(final AbstractFilter<T> filter) {
      myFilter = filter;
    }

    public boolean processItem(final T item) {
      if (!myFilter.isIncluded(item)) {
        return true;
      }
      if (myFilter.isIncludedByRange(myCurrentIndex)) {
        myList.add(item);
      }
      ++myCurrentIndex;
      return myFilter.isBelowUpperRangeLimit(myCurrentIndex);
    }

    public ArrayList<T> getResult() {
      return myList;
    }
  }
}
