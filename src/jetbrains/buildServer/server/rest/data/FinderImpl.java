/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 06/06/2016
 */
public class FinderImpl<ITEM> implements Finder<ITEM> {
  private static final Logger LOG = Logger.getInstance(AbstractFinder.class.getName());

  public static final String DIMENSION_ID = "id";
  public static final String DIMENSION_LOOKUP_LIMIT = "lookupLimit";
  public static final String DIMENSION_ITEM = "item";
  public static final String DIMENSION_UNIQUE = "unique";

  //todo: add set-filtering (filter by collection of items in prefiltering and in filter), e.g. see handling of ProjectFinder.DIMENSION_PROJECT
  private FinderDataBinding<ITEM> myDataBinding;

  public FinderImpl(@NotNull final FinderDataBinding<ITEM> dataBinding) {
    myDataBinding = dataBinding;
  }

  //todo: rework to remove the constructor and add final to  myDataBinding
  public FinderImpl() {
  }

  public void setDataBinding(@NotNull final FinderDataBinding<ITEM> dataBinding) {
    if (myDataBinding != null) throw new OperationException("Logic error: cannot re-initialize dataBinding in FinderImpl");
    myDataBinding = dataBinding;
  }

  @NotNull
  @Override
  public String getCanonicalLocator(@NotNull final ITEM item) {
    return myDataBinding.getItemLocator(item);
  }

  @Override
  @NotNull
  public ITEM getItem(@Nullable final String locatorText) {
    return getItem(locatorText, null);
  }

  /**
   * @throws NotFoundException if there is locator sub-dimension which references a single entry which cannot be found (might need to return empty collection for the case as well)
   * @returns the items found by locatorText or empty collection if the locator does ot correspond to any item
   */
  @Override
  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText) {
    return getItemsByLocator(getLocatorOrNull(locatorText), true);
  }

  @NotNull
  @Override
  public ItemFilter<ITEM> getFilter(@NotNull final String locatorText) {
    final Locator locator = createLocator(locatorText, null);
    final ItemFilter<ITEM> result = myDataBinding.getLocatorDataBinding(locator).getFilter();
    locator.checkLocatorFullyProcessed();
    return result;
  }


  /**
   * @throws NotFoundException if there is locator sub-dimension which  references a single entry which cannot be found (might need to return empty collection for the case as well)
   * @returns the items found by locatorText or empty collection if the locator does ot correspond to any item
   */
  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    return getItemsByLocator(getLocatorOrNull(locatorText, locatorDefaults), true);
  }


  @NotNull
  protected Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    List<String> knownDimensions = new ArrayList<>(Arrays.asList(myDataBinding.getKnownDimensions()));
    knownDimensions.add(PagerData.START);
    knownDimensions.add(PagerData.COUNT);
    knownDimensions.add(DIMENSION_LOOKUP_LIMIT);
    final Locator result = Locator.createLocator(locatorText, locatorDefaults, knownDimensions.toArray(new String[knownDimensions.size()]));
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    result.addHiddenDimensions(AbstractFinder.DIMENSION_ITEM, AbstractFinder.DIMENSION_UNIQUE); //experimental
    for (String hiddenDimension : myDataBinding.getHiddenDimensions()) {
      result.addHiddenDimensions(hiddenDimension);
    }
    Locator.DescriptionProvider descriptionProvider = myDataBinding.getLocatorDescriptionProvider();
    if (descriptionProvider != null) {
      result.setDescriptionProvider(descriptionProvider);
    }
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

  @NotNull
  private ITEM getItem(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    if (StringUtil.isEmpty(locatorText)) {
      throw new BadRequestException("Empty locator is not supported.");
    }
    final Locator locator = createLocator(locatorText, locatorDefaults);

    if (!locator.isSingleValue()) {
      locator.setDimension(PagerData.COUNT, "1"); //get only the first one that matches
      locator.addHiddenDimensions(PagerData.COUNT);
    }
    final PagedSearchResult<ITEM> items = getItemsByLocator(locator, false);
    final int entriesSize = items.myEntries.size();
    if (entriesSize == 0) {
      if (!items.myLookupLimitReached) {
        throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "'.");
      }
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
  private PagedSearchResult<ITEM> getItemsByLocator(@Nullable final Locator originalLocator, final boolean multipleItemsQuery) {
    Locator locator;
    if (originalLocator == null) {
      //go on with empty locator
      locator = createLocator(null, Locator.createEmptyLocator());
    } else {
      locator = originalLocator;

      ITEM singleItem = null;
      try {
        singleItem = myDataBinding.findSingleItem(locator);
      } catch (NotFoundException e) {
        if (multipleItemsQuery) { //consider adding comment/warning messages to PagedSearchResult, return it as a header in the response
          //returning empty collection for multiple items query
          return new PagedSearchResult<ITEM>(Collections.<ITEM>emptyList(), null, null);
        }
        throw e;
      }
      if (singleItem != null) {
        final Set<String> singleItemUsedDimensions = locator.getUsedDimensions();
        // ignore start:0 dimension
        final Long startDimension = locator.getSingleDimensionValueAsLong(PagerData.START);
        if (startDimension == null || startDimension != 0) {
          locator.markUnused(PagerData.START);
        }

        ItemFilter<ITEM> filter = null;
        try {
          filter = myDataBinding.getLocatorDataBinding(locator).getFilter();
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

        return new PagedSearchResult<ITEM>(Collections.singletonList(singleItem), null, null);
      }
      locator.markAllUnused(); // nothing found - no dimensions should be marked as used then
    }

    FinderDataBinding.LocatorDataBinding<ITEM> locatorDataBinding = myDataBinding.getLocatorDataBinding(locator);
    final FinderDataBinding.ItemHolder<ITEM> unfilteredItems = getPrefilteredItemsWithItemsSupport(locator, locatorDataBinding); //todo : check locator used dimensions

    final Long start = locator.getSingleDimensionValueAsLong(PagerData.START);
    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT, myDataBinding.getDefaultPageItemsCount());
    Long lookupLimit = locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT, myDataBinding.getDefaultLookupLimit());

    if (countFromFilter != null && lookupLimit != null && locator.getSingleDimensionValue(DIMENSION_LOOKUP_LIMIT) == null && lookupLimit < countFromFilter) {
      // if count of items is set, but lookupLimit is not, process at least as many items as count
      lookupLimit = countFromFilter;
    }

    final PagingItemFilter<ITEM> pagingFilter = new PagingItemFilter<ITEM>(locatorDataBinding.getFilter(),
                                                                           start, countFromFilter == null ? null : countFromFilter.intValue(),
                                                                           lookupLimit);

    locator.checkLocatorFullyProcessed();
    return getItems(pagingFilter, unfilteredItems, locator);
  }

  @NotNull
  private PagedSearchResult<ITEM> getItems(final @NotNull PagingItemFilter<ITEM> filter,
                                           final @NotNull FinderDataBinding.ItemHolder<ITEM> unfilteredItems,
                                           @NotNull final Locator locator) {
    final long startTime = System.nanoTime();
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    unfilteredItems.process(filterItemProcessor);
    final ArrayList<ITEM> result = filterItemProcessor.getResult();
    final long finishTime = System.nanoTime();
    final long totalItemsProcessed = filterItemProcessor.getTotalItemsProcessed();
    final long processingTimeMs = TimeUnit.MILLISECONDS.convert(finishTime - startTime, TimeUnit.NANOSECONDS);
    if (totalItemsProcessed >= TeamCityProperties.getLong("rest.finder.processedItemsLogLimit", 1)) {
      final String lookupLimitMessage =
        filter.isLookupLimitReached() ? " (lookupLimit of " + filter.getLookupLimit() + " reached). Last processed item: " + LogUtil.describe(filter.getLastProcessedItem()) : "";
      LOG.debug("While processing locator '" + locator + "', " + result.size() + " items were matched by the filter from " + totalItemsProcessed + " processed in total" +
                lookupLimitMessage + ", took " + processingTimeMs + " ms"); //todo make AbstractFilter loggable and add logging here
    }
    if (totalItemsProcessed > TeamCityProperties.getLong("rest.finder.processedItemsWarnLimit", 10000) ||
        processingTimeMs > TeamCityProperties.getLong("rest.finder.timeWarnLimit", 10000)) {
      LOG.info("Server performance can be affected by REST request with locator '" + locator + "': " +
               totalItemsProcessed + " items were processed and " + result.size() + " items were returned, took " + processingTimeMs + " ms");
    }
    return new PagedSearchResult<ITEM>(result, filter.getStart(), filter.getCount(), totalItemsProcessed,
                                       filter.getLookupLimit(), filter.isLookupLimitReached(), filter.getLastProcessedItem());
  }

  @NotNull
  private FinderDataBinding.ItemHolder<ITEM> getPrefilteredItemsWithItemsSupport(@NotNull final Locator locator,
                                                                                 @NotNull final FinderDataBinding.LocatorDataBinding<ITEM> locatorDataBinding) {
    final List<String> itemsDimension = locator.getDimensionValue(FinderImpl.DIMENSION_ITEM);
    if (itemsDimension.isEmpty()) {
      Boolean deduplicate = locator.getSingleDimensionValueAsBoolean(FinderImpl.DIMENSION_UNIQUE);
      if (deduplicate != null && deduplicate) {
        Collection<ITEM> result = new LinkedHashSet<ITEM>();
        locatorDataBinding.getPrefilteredItems().process(new ItemProcessor<ITEM>() {
          @Override
          public boolean processItem(final ITEM item) {
            result.add(item);
            return true;
          }
        });
        return FinderDataBinding.getItemHolder(result);
      } else {
        return locatorDataBinding.getPrefilteredItems();
      }
    }

    Collection<ITEM> result;
    Boolean deduplicate = locator.getSingleDimensionValueAsBoolean(FinderImpl.DIMENSION_UNIQUE);
    if (deduplicate != null && deduplicate) {
      result = new LinkedHashSet<ITEM>();
    } else {
      result = new ArrayList<>();
    }
    for (String itemLocator : itemsDimension) {
      result.addAll(getItems(itemLocator).myEntries);
    }
    return FinderDataBinding.getItemHolder(result);
  }
}
