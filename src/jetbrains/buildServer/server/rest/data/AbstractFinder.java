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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.TeamCityProperties;
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
  //todo: add mandatory filter to apply on any returned results: single, prefiltered, etc. (e.g. permissions checked in VCSRoot*Finder)
  private static final Logger LOG = Logger.getInstance(AbstractFinder.class.getName());

  public static final String DIMENSION_ID = "id";
  public static final String DIMENSION_LOOKUP_LIMIT = "lookupLimit";

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
    return result;
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public Locator getLocatorOrNull(@Nullable final String locatorText) {
    return locatorText != null ? createLocator(locatorText, null) : null;
  }

  @Nullable
  @Contract("null, null -> null; !null, _ -> !null; _, !null -> !null")
  public Locator getLocatorOrNull(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
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
      final ItemHolder<ITEM> allItems = getAllItems();
      if (allItems != null){
        return new PagedSearchResult<ITEM>(toList(allItems), null, null);
      }
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

        final Set<String> unusedDimensions = locator.getUnusedDimensions();
        if (!unusedDimensions.isEmpty()) {
          ItemFilter<ITEM> filter = getFilter(locator);
          locator.checkLocatorFullyProcessed();
          if (!filter.isIncluded(singleItem)) {
            if (multipleItemsQuery) {
              LOG.debug("Found single item by " + StringUtil.pluralize("dimension", singleItemUsedDimensions.size()) + " " + singleItemUsedDimensions +
                        ", but that was filtered out using the entire locator '" + locator + "'");
              return new PagedSearchResult<ITEM>(Collections.<ITEM>emptyList(), null, null);
            } else {
              throw new NotFoundException("Found single item by " + StringUtil.pluralize("dimension", singleItemUsedDimensions.size()) + " " + singleItemUsedDimensions +
                                          ", but that was filtered out using the entire locator '" + locator + "'");
            }
          }
        }

        locator.checkLocatorFullyProcessed();
        return new PagedSearchResult<ITEM>(Collections.singletonList(singleItem), null, null);
      }
      locator.markAllUnused(); // nothing found - no dimensions should be marked as used then
    }

    //it is important to call "getPrefilteredItems" first as that process some of the dimensions which  "getFilter" can then ignore for performance reasons
    final ItemHolder<ITEM> unfilteredItems = getPrefilteredItems(locator);
    final ItemFilter<ITEM> filter = getFilter(locator);

    final Long start = locator.getSingleDimensionValueAsLong(PagerData.START);
    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT, getDefaultPageItemsCount());
    final Long lookupLimit = locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT, getDefaultLookupLimit());

    final PagingItemFilter<ITEM> pagingFilter = new PagingItemFilter<ITEM>(filter, start, countFromFilter == null ? null : countFromFilter.intValue(), lookupLimit);

    locator.checkLocatorFullyProcessed();
    return getItems(pagingFilter, unfilteredItems, locator);
  }

  @NotNull
  protected PagedSearchResult<ITEM> getItems(final @NotNull PagingItemFilter<ITEM> filter, final @NotNull ItemHolder<ITEM> unfilteredItems, @NotNull final Locator locator) {
    final long startTime = System.nanoTime();
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    unfilteredItems.process(filterItemProcessor);
    final ArrayList<ITEM> result = filterItemProcessor.getResult();
    final long finishTime = System.nanoTime();
    final long totalItemsProcessed = filterItemProcessor.getTotalItemsProcessed();
    if (totalItemsProcessed >= TeamCityProperties.getLong("rest.finder.processedItemsLogLimit", 1)) {
      LOG.debug("While processing locator '" + locator + "', " + result.size() + " items were matched by the filter from " + totalItemsProcessed + " processed in total" +
                (filter.isLookupLimitReached() ? " (lookup limit of " + filter.getLookupLimit() + " reached)" : "")); //todo make AbstractFilter loggable and add logging here
    }
    final long processingTimeMs = TimeUnit.MILLISECONDS.convert(finishTime - startTime, TimeUnit.NANOSECONDS);
    if (totalItemsProcessed > TeamCityProperties.getLong("rest.finder.processedItemsWarnLimit", 10000) ||
        processingTimeMs > TeamCityProperties.getLong("rest.finder.timeWarnLimit", 10000)) {
      LOG.info("Server performance can be affected by REST request with locator '" + locator + "': " +
               totalItemsProcessed + " items were processed and " + result.size() + " items were returned, took " + processingTimeMs + " ms");
    }
    return new PagedSearchResult<ITEM>(result, filter.getStart(), filter.getCount(), totalItemsProcessed, filter.getLookupLimit(), filter.isLookupLimitReached());
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
    if (items.myEntries.size() == 0) {
      if (!items.myLookupLimitReached)
        throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "'.");
      //todo: consider logging this; also log last processed build
      throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "' while processing first " +
                                       items.myLookupLimit + " items. Set " + DIMENSION_LOOKUP_LIMIT + " dimension to larger value to process more items.");
    }
    assert items.myEntries.size()== 1;
    return items.myEntries.get(0);
  }

  @NotNull   //todo: change overrides, drop getAllItems at all
  protected ItemHolder<ITEM> getPrefilteredItems(@NotNull Locator locator) {
    final ItemHolder<ITEM> allItems = getAllItems();
    if (allItems == null){
      throw new OperationException("Incorrect implementation: nor all items nor prefiltered items are defined.");
    }
    return allItems;
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
   *
   * @return null if all items are not supported and usual scheme (get prefiltered + filtering) should be applied
   */
  @Nullable  //todo: change overrides
  public abstract ItemHolder<ITEM> getAllItems();

  @NotNull
  protected abstract ItemFilter<ITEM> getFilter(final Locator locator);

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

  @NotNull
  public List<ITEM> toList(@NotNull final ItemHolder<ITEM> items) {//todo support lookuplimit here?
    final ArrayList<ITEM> result = new ArrayList<ITEM>();
    items.process(new ItemProcessor<ITEM>() {
      public boolean processItem(final ITEM item) {
        result.add(item);
        return true;
      }
    });
    return result;
  }
}
