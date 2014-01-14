/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.ArrayList;
import jetbrains.buildServer.util.ItemProcessor;

/**
* @author Yegor.Yarko
*         Date: 13.09.2010
*/
public class FilterItemProcessor<T> implements ItemProcessor<T> {
  private long myCurrentIndex = 0;
  private long myTotalItemsProcessed = 0;
  private final AbstractFilter<T> myFilter;
  private final ArrayList<T> myList = new ArrayList<T>();

  public FilterItemProcessor(final AbstractFilter<T> filter) {
    myFilter = filter;
  }

  public boolean processItem(final T item) {
    final boolean withinRange = myFilter.isBelowUpperRangeLimit(myCurrentIndex, myTotalItemsProcessed++);
    if (!withinRange){
      return false;
    }

    if (myFilter.shouldStop(item)){
      return false;
    }
    if (!myFilter.isIncluded(item)) {
      return true;
    }
    if (myFilter.isIncludedByRange(myCurrentIndex++)) {
      myList.add(item);
    }
    return true;
  }

  public ArrayList<T> getResult() {
    return myList;
  }

  public long getProcessedItemsCount() {
    return myCurrentIndex;
  }
}
