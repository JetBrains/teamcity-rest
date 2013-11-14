package jetbrains.buildServer.server.rest.data.investigations;

import java.util.List;
import jetbrains.buildServer.server.rest.data.AbstractFilter;
import jetbrains.buildServer.server.rest.data.FilterItemProcessor;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public abstract class ItemBridge<ITEM> {
  public List<ITEM> getItems(final AbstractFilter<ITEM> filter) {
    //todo: current implementation is not effective: consider pre-filtering by filter fields, if specified
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    AbstractFilter.processList(getAllItems(), filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  public abstract List<ITEM> getAllItems();
}
