package jetbrains.buildServer.server.rest.data.investigations;

import java.util.List;
import jetbrains.buildServer.server.rest.data.AbstractFilter;
import jetbrains.buildServer.server.rest.data.FilterItemProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public abstract class ItemBridge<ITEM> {
  @NotNull
  public List<ITEM> getItems(final @NotNull AbstractFilter<ITEM> filter, final @NotNull List<ITEM> unfilteredItems) {
    //todo: current implementation is not effective: consider pre-filtering by filter fields, if specified
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    AbstractFilter.processList(unfilteredItems, filterItemProcessor);
    return filterItemProcessor.getResult();
  }

  @NotNull
  public abstract List<ITEM> getAllItems();
}
