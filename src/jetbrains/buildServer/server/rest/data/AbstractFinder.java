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


import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Convenience class for searching entities via locators
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public abstract class AbstractFinder<ITEM> {
  //todo: add set-filtering (filter by collection of items in prefiltering and in filter), e.g. see handling of ProjectFinder.DIMENSION_PROJECT
  private static final Logger LOG = Logger.getInstance(AbstractFinder.class.getName());

  public static final String DIMENSION_ID = "id";
  public static final String DIMENSION_LOOKUP_LIMIT = "lookupLimit";
  public static final String DIMENSION_ITEM = "item";
  public static final String DIMENSION_UNIQUE = "unique";

  private final String[] myKnownDimensions;

  public AbstractFinder(@NotNull final String[] knownDimensions) {
    myKnownDimensions = ArrayUtils.addAll(knownDimensions, PagerData.START, PagerData.COUNT, DIMENSION_LOOKUP_LIMIT);
  }

  @Nullable
  public Long getDefaultPageItemsCount() {
    return null;
  }

  @Nullable
  public Long getDefaultLookupLimit() {
    return null;
  }

  @NotNull
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = Locator.createLocator(locatorText, locatorDefaults, myKnownDimensions);
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    result.addHiddenDimensions(DIMENSION_ITEM, DIMENSION_UNIQUE); //experimental
    return result;
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  private Locator getLocatorOrNull(@Nullable final String locatorText) {
    return locatorText != null ? createLocator(locatorText, null) : null;
  }

  @Nullable
  @Contract("null, null -> null; !null, _ -> !null; _, !null -> !null")
  private Locator getLocatorOrNull(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    return (locatorText == null && locatorDefaults == null) ? null : createLocator(locatorText, locatorDefaults);
  }

  /**
   * @returns the items found by locatorText or empty collection if the locator does ot correspond to any item
   * @throws NotFoundException if there is locator sub-dimension which references a single entry which cannot be found (might need to return empty collection for the case as well)
   */
  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText) {
    return getItemsByLocator(getLocatorOrNull(locatorText), true);
  }

  /**
   * @returns the items found by locatorText or empty collection if the locator does ot correspond to any item
   * @throws NotFoundException if there is locator sub-dimension which  references a single entry which cannot be found (might need to return empty collection for the case as well)
   */
  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    return getItemsByLocator(getLocatorOrNull(locatorText, locatorDefaults), true);
  }

  @NotNull
  private PagedSearchResult<ITEM> getItemsByLocator(@Nullable final Locator originalLocator, final boolean multipleItemsQuery) {
    Locator locator;
    if (originalLocator == null) {
      //go on with empty locator
      locator = createLocator(null, Locator.createEmptyLocator());
    } else {
      locator = originalLocator;

      ITEM singleItem = null;
      try {
        singleItem = findSingleItem(locator);
      } catch (NotFoundException e) {
        if (multipleItemsQuery) { //consider adding comment/warning messages to PagedSearchResult, return it as a header in the response
          //returning empty collection for multiple items query
          return new PagedSearchResult<ITEM>(Collections.<ITEM>emptyList(), null, null);
        }
        throw e;
      }
      if (singleItem != null){
        final Set<String> singleItemUsedDimensions = locator.getUsedDimensions();
        // ignore start:0 dimension
        final Long startDimension = locator.getSingleDimensionValueAsLong(PagerData.START);
        if (startDimension == null || startDimension != 0) {
          locator.markUnused(PagerData.START);
        }

        ItemFilter<ITEM> filter = null;
        try {
          filter = getFilter(locator);
        } catch (NotFoundException e) {
          throw new NotFoundException("Invalid filter for found single item, try omitting extra dimensions: " + e.getMessage(), e);
        } catch (BadRequestException e) {
          throw new BadRequestException("Invalid filter for found single item, try omitting extra dimensions: " + e.getMessage(), e);
        } catch (Exception e) {
          throw new BadRequestException("Invalid filter for found single item, try omitting extra dimensions: " + e.toString(), e);
        }
        locator.checkLocatorFullyProcessed(); //checking before invoking filter to report any unused dimensions before possible error reporting in filter
        if (!filter.isIncluded(singleItem)) {
          final String message = "Found single item by " + StringUtil.pluralize("dimension", singleItemUsedDimensions.size()) + " " + singleItemUsedDimensions +
                                 ", but that was filtered out using the entire locator '" + locator + "'";
          if (multipleItemsQuery) {
            LOG.debug(message);
            return new PagedSearchResult<ITEM>(Collections.<ITEM>emptyList(), null, null);
          } else {
            throw new NotFoundException(message);
          }
        }

        locator.checkLocatorFullyProcessed();
        return new PagedSearchResult<ITEM>(Collections.singletonList(singleItem), null, null);
      }
      locator.markAllUnused(); // nothing found - no dimensions should be marked as used then
    }

    //it is important to call "getPrefilteredItems" first as that process some of the dimensions which  "getFilter" can then ignore for performance reasons
    ItemHolder<ITEM> unfilteredItems = getPrefilteredItemsWithItemsSupport(locator);
    final ItemFilter<ITEM> filter = getFilter(locator);

    final Long start = locator.getSingleDimensionValueAsLong(PagerData.START);
    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT, getDefaultPageItemsCount());
    Long lookupLimit = locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT, getDefaultLookupLimit());

    if (countFromFilter != null && lookupLimit != null && locator.getSingleDimensionValue(DIMENSION_LOOKUP_LIMIT) == null && lookupLimit < countFromFilter){
      // if count of items is set, but lookupLimit is not, process at least as many items as count
      lookupLimit = countFromFilter;
    }

    final PagingItemFilter<ITEM> pagingFilter = new PagingItemFilter<ITEM>(filter, start, countFromFilter == null ? null : countFromFilter.intValue(), lookupLimit);

    locator.checkLocatorFullyProcessed();
    return getItems(pagingFilter, unfilteredItems, locator);
  }

  @NotNull
  private PagedSearchResult<ITEM> getItems(final @NotNull PagingItemFilter<ITEM> filter, final @NotNull ItemHolder<ITEM> unfilteredItems, @NotNull final Locator locator) {
    final long startTime = System.nanoTime();
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    unfilteredItems.process(filterItemProcessor);
    final ArrayList<ITEM> result = filterItemProcessor.getResult();
    final long finishTime = System.nanoTime();
    final long totalItemsProcessed = filterItemProcessor.getTotalItemsProcessed();
    if (totalItemsProcessed >= TeamCityProperties.getLong("rest.finder.processedItemsLogLimit", 1)) {
      final String lookupLimitMessage =
        filter.isLookupLimitReached() ? " (lookupLimit of " + filter.getLookupLimit() + " reached). Last processed item: " + LogUtil.describe(filter.getLastProcessedItem()) : "";
      LOG.debug("While processing locator '" + locator + "', " + result.size() + " items were matched by the filter from " + totalItemsProcessed + " processed in total" +
                lookupLimitMessage); //todo make AbstractFilter loggable and add logging here
    }
    final long processingTimeMs = TimeUnit.MILLISECONDS.convert(finishTime - startTime, TimeUnit.NANOSECONDS);
    if (totalItemsProcessed > TeamCityProperties.getLong("rest.finder.processedItemsWarnLimit", 10000) ||
        processingTimeMs > TeamCityProperties.getLong("rest.finder.timeWarnLimit", 10000)) {
      LOG.info("Server performance can be affected by REST request with locator '" + locator + "': " +
               totalItemsProcessed + " items were processed and " + result.size() + " items were returned, took " + processingTimeMs + " ms");
    }
    return new PagedSearchResult<ITEM>(result, filter.getStart(), filter.getCount(), totalItemsProcessed,
                                       filter.getLookupLimit(), filter.isLookupLimitReached(), filter.getLastProcessedItem());
  }

  @NotNull
  public ITEM getItem(@Nullable final String locatorText) {
    return getItem(locatorText, null);
  }

  @NotNull
  public ITEM getItem(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty locator is not supported.");
    }
    final Locator locator = createLocator(locatorText, locatorDefaults);

    if (!locator.isSingleValue()){
      locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
      locator.addHiddenDimensions(PagerData.COUNT);
    }
    final PagedSearchResult<ITEM> items = getItemsByLocator(locator, false);
    final int entriesSize = items.myEntries.size();
    if (entriesSize == 0) {
      if (!items.myLookupLimitReached)
        throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "'.");
      LOG.debug("Returning \"Not Found\" response because of reaching lookupLimit. Last processed item: " + LogUtil.describe(items.getLastProcessedItem()));
      throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "' while processing first " +
                                       items.myLookupLimit + " items. Set " + DIMENSION_LOOKUP_LIMIT + " dimension to larger value to process more items.");
    }
    if (entriesSize != 1) {
      throw new OperationException("Found + " + entriesSize + " items for locator '" + locator.getStringRepresentation() + "' while a single item is expected.");
    }
    return items.myEntries.get(0);
  }

  @NotNull
  private ItemHolder<ITEM> getPrefilteredItemsWithItemsSupport(@NotNull Locator locator) {
    final List<String> itemsDimension = locator.getDimensionValue(DIMENSION_ITEM);
    if (itemsDimension.isEmpty()) {
      return getPrefilteredItems(locator);
    }

    Collection<ITEM> result;
    Boolean deduplicate = locator.getSingleDimensionValueAsBoolean(DIMENSION_UNIQUE);
    if (deduplicate!= null && deduplicate){
      result = new LinkedHashSet<ITEM>();
    } else{
      result = new ArrayList<>();
    }
    for (String itemLocator : itemsDimension) {
      result.addAll(getItems(itemLocator).myEntries);
    }
    return getItemHolder(result);
  }

  @NotNull
  protected ItemHolder<ITEM> getPrefilteredItems(@NotNull Locator locator) {
    throw new OperationException("Incorrect implementation: prefiltered items retrieval is not implemented.");
  }

  /**
   *
   * @param locator
   * @return the item found or null if this is not single item locator
   * @throws NotFoundException when the locator is for single item, but the item does not exist / is not accessible for the current user
   */
  @Nullable
  protected ITEM findSingleItem(@NotNull final Locator locator){
    return null;
  }

  /**
   * Returns filter based on passed locator
   * Should not have side-effects other than marking used locator dimensions
   * @param locator can be empty locator. Can
   */
  @NotNull
  protected abstract ItemFilter<ITEM> getFilter(@NotNull final Locator locator);

  public String[] getKnownDimensions() {
    return myKnownDimensions;
  }

  public interface ItemHolder<P> {
    boolean process(@NotNull final ItemProcessor<P> processor);
  }

  @NotNull
  public static <P> ItemHolder<P> getItemHolder(@NotNull Iterable < P > items){
    return new CollectionItemHolder<P>(items);
  }

  public static class CollectionItemHolder<P> implements ItemHolder<P> {
    @NotNull final private Iterable<P> myEntries;

    public CollectionItemHolder(@NotNull final Iterable<P> entries) {
      myEntries = entries;
    }

    public boolean process(@NotNull final ItemProcessor<P> processor) {
      for (P entry : myEntries) {
        if (!processor.processItem(entry)) {
          return false;
        }
      }
      return true;
    }
  }

  public static class AggregatingItemHolder<P> implements ItemHolder<P> {
    @NotNull final private List<ItemHolder<P>> myItemHolders = new ArrayList<>();

    public void add(ItemHolder<P> holder){
      myItemHolders.add(holder);
    }

    public boolean process(@NotNull final ItemProcessor<P> processor) {
      for (ItemHolder<P> itemHolder : myItemHolders) {
        if (!itemHolder.process(processor)){
          return false;
        }
      }
      return true;
    }
  }
}
