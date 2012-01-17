package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import jetbrains.buildServer.util.ItemProcessor;

/**
* @author Yegor.Yarko
*         Date: 13.09.2010
*/
class FilterItemProcessor<T> implements ItemProcessor<T> {
  private long myCurrentIndex = 0;
  private final AbstractFilter<T> myFilter;
  private final ArrayList<T> myList = new ArrayList<T>();

  public FilterItemProcessor(final AbstractFilter<T> filter) {
    myFilter = filter;
  }

  public boolean processItem(final T item) {
    if (myFilter.shouldStop(item)){
      return false;
    }
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
