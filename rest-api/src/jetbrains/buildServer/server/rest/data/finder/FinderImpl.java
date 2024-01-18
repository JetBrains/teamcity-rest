/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.RestContext;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildPromotionFinder;
import jetbrains.buildServer.server.rest.data.util.*;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TimePrinter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 06/06/2016
 */
public class FinderImpl<ITEM> implements Finder<ITEM> {
  private static final Logger LOG = Logger.getInstance(FinderImpl.class.getName());

  public static final String DIMENSION_ID = "id";
  public static final String DIMENSION_LOOKUP_LIMIT = "lookupLimit";

  public static final String LOGIC_OP_OR = "or";
  public static final String LOGIC_OP_AND = "and";
  public static final String LOGIC_OP_NOT = "not";
  public static final String DIMENSION_ITEM = "item";
  public static final String DIMENSION_UNIQUE = "unique";
  protected static final String OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND = "$reportErrorOnNothingFound";

  protected static final String CONTEXT_ITEM_DIMENSION_NAME = "$contextItem";

  public static final Long NO_COUNT = -1L;

  @Nullable
  protected String myName;

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

  public void setName(@NotNull final String finderName) {
    myName = finderName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName != null ? myName : getClass().getSimpleName();
  }

  @NotNull
  @Override
  public String getCanonicalLocator(@NotNull final ITEM item) {
    return myDataBinding.getItemLocator(item);
  }

  @Override
  @NotNull
  public ITEM getItem(@Nullable final String locatorText) {
    return NamedThreadFactory.executeWithNewThreadNameFuncThrow(
      "Using " + getName() + " to get single item for locator \"" + locatorText + "\"",
      () -> getItem(locatorText, null)
    );
  }

  /**
   * @throws NotFoundException if there is locator sub-dimension which references a single entry which cannot be found (might need to return empty collection for the case as well)
   * @returns the items found by locatorText or empty collection if the locator does ot correspond to any item
   */
  @Override
  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText) {
    return NamedThreadFactory.executeWithNewThreadNameFuncThrow(
      "Using " + getName() + " to get items for locator \"" + locatorText + "\"",
      () -> getItemsByLocator(getLocatorOrNull(locatorText), true)
    );
  }

  @NotNull
  @Override
  public ItemFilter<ITEM> getFilter(@NotNull final String locatorText) {
    final Locator locator = createLocator(locatorText, null);
    final ItemFilter<ITEM> result;
    try {
      result = getFilterWithLogicOpsSupport(locator, myDataBinding.getLocatorDataBinding(locator));
    } catch (LocatorProcessException | BadRequestException e) {
      if (!locator.isHelpRequested()) {
        throw e;
      }
      throw new BadRequestException(
        e.getMessage() +
        "\nLocator details: " + locator.getLocatorDescription(locator.helpOptions().getSingleDimensionValueAsStrictBoolean("hidden", false)), e
      );
    }
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


  /**
   * Creates locator from {@code locatorText}.
   * Modifies locator with some finder defaults.
   * @param locatorText if null, then the empty locator with some finder specific locator defaults is created.
   * @param locatorDefaults optional parameter. if {@code locatorText} does not have some dimensions, then they are going to be retrieved from this.
   * @return
   */
  @NotNull
  protected Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = Locator.createLocator(locatorText, locatorDefaults, getSupportedDimensions());
    result.addIgnoreUnusedDimensions(PagerData.COUNT);
    result.addIgnoreUnusedDimensions(OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND);
    result.addHiddenDimensions(LOGIC_OP_OR, LOGIC_OP_AND, LOGIC_OP_NOT, AbstractFinder.DIMENSION_ITEM);  //experimental
    result.addHiddenDimensions(AbstractFinder.DIMENSION_UNIQUE);  //experimental, should actually depend on FinderDataBinding.getContainerSet returning not null
    result.addHiddenDimensions(OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND); //experimental
    result.addHiddenDimensions(CONTEXT_ITEM_DIMENSION_NAME); //experimental, internal
    for (String hiddenDimension : myDataBinding.getHiddenDimensions()) {
      result.addHiddenDimensions(hiddenDimension);
    }
    Locator.DescriptionProvider descriptionProvider = myDataBinding.getLocatorDescriptionProvider();
    if (descriptionProvider != null) {
      result.setDescriptionProvider(descriptionProvider);
    }
    return result;
  }

  @NotNull
  private String[] getSupportedDimensions() {
    LinkedHashSet<String> knownDimensions = new LinkedHashSet<>(Arrays.asList(myDataBinding.getKnownDimensions()));
    knownDimensions.add(PagerData.START);
    knownDimensions.add(PagerData.COUNT);
    knownDimensions.add(DIMENSION_LOOKUP_LIMIT);
    knownDimensions.add(OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND);
    knownDimensions.add(CONTEXT_ITEM_DIMENSION_NAME); //experimental, internal
    return knownDimensions.toArray(new String[knownDimensions.size()]);
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
    final int entriesSize = items.getEntries().size();
    if (entriesSize == 0) {
      if (!items.getLookupLimitReached()) {
        throw new NotFoundException("Nothing is found by " + getLocatorDetailsForMessage(locator) + ".");
      }
      LOG.debug("Returning \"Not Found\" response because of reaching lookupLimit. Last processed item: " + LogUtil.describe(items.getLastProcessedItem()));
      throw new NotFoundException("Nothing is found by " + getLocatorDetailsForMessage(locator) + " while processing first " +
                                  items.getLookupLimit() + " items. Set " + DIMENSION_LOOKUP_LIMIT + " dimension to larger value to process more items.");
    }
    if (entriesSize != 1) {
      throw new OperationException("Found " + entriesSize + " items for " + getLocatorDetailsForMessage(locator) + " while a single item is expected.");
    }
    return items.getEntries().get(0);
  }

  @NotNull
  private PagedSearchResult<ITEM> getItemsByLocator(@Nullable final Locator originalLocator, final boolean multipleItemsQuery) {
    long startTime = System.nanoTime();
    Locator locator;
    if (originalLocator == null) {
      //go on with empty locator
      locator = createLocator(null, Locator.createEmptyLocator());
    } else {
      locator = originalLocator;

      locator.processHelpRequest();
    }

    String contextItemText = locator.getSingleDimensionValue(CONTEXT_ITEM_DIMENSION_NAME);
    if (contextItemText != null) {
      List<ITEM> contextObjects = getContextItems(contextItemText);
      if (contextObjects == null) throw new BadRequestException("Context variable '" + contextItemText + "' is used in locator, but is not present in the context");
      if (contextObjects.isEmpty()) throw new BadRequestException("Context variable '" + contextItemText + "' is used in locator, but the list does not contain any elements");

      //so far do not support additional filtering or other dimensions if context item is used
      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<>(contextObjects, null, null);
    }

    if (!locator.isEmpty()) {
      final ITEM singleItem;
      try {
        singleItem = myDataBinding.findSingleItem(locator);
      } catch (NotFoundException e) {
        if (multipleItemsQuery && !isReportErrorOnNothingFound(locator)) {
          //consider adding comment/warning messages to PagedSearchResult, return it as a header in the response
          //returning empty collection for multiple items query
          return new PagedSearchResult<>(Collections.emptyList(), null, null);
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

        ItemFilter<ITEM> filter;
        try {
          filter = getDataBindingWithLogicOpsSupport(locator, myDataBinding).getFilter();
        } catch (NotFoundException e) {
          throw new NotFoundException("Invalid filter for found single item, try omitting extra dimensions: " + e.getMessage(), e);
        } catch (BadRequestException e) {
          throw new BadRequestException("Invalid filter for found single item, try omitting extra dimensions: " + e.getMessage(), e);
        } catch (Exception e) {
          throw new BadRequestException("Invalid filter for found single item, try omitting extra dimensions: " + e.toString(), e);
        }
        locator.markUsed(DIMENSION_UNIQUE); //mark as used as it has no influence on single item
        locator.checkLocatorFullyProcessed(); //checking before invoking filter to report any unused dimensions before possible error reporting in filter
        if (!filter.isIncluded(singleItem)) {
          final String message = "Found single item by " + StringUtil.pluralize("dimension", singleItemUsedDimensions.size()) + " " + singleItemUsedDimensions +
                                 ", but that was filtered out using the entire locator '" + locator + "'";
          if (multipleItemsQuery && !isReportErrorOnNothingFound(locator)) {
            LOG.debug(message);
            return new PagedSearchResult<>(Collections.emptyList(), null, null);
          } else {
            throw new NotFoundException(message);
          }
        }

        return new PagedSearchResult<>(Collections.singletonList(singleItem), null, null);
      }
      locator.markAllUnused(); // nothing found - no dimensions should be marked as used then
    }
    ItemHolder<ITEM> unfilteredItems;
    PagingItemFilter<ITEM> pagingFilter;
    try {
      LocatorDataBinding<ITEM> locatorDataBinding = getDataBindingWithLogicOpsSupport(locator, myDataBinding);
      unfilteredItems = locatorDataBinding.getPrefilteredItems();
      DuplicateChecker<ITEM> duplicateChecker = myDataBinding.createDuplicateChecker();
      if (duplicateChecker != null) {
        //get the dimension only for supporting finders so that unused dimension is reported otherwise
        boolean deduplicate = locator.getSingleDimensionValueAsStrictBoolean(DIMENSION_UNIQUE, locator.isAnyPresent(DIMENSION_ITEM));
        if (deduplicate) {
          unfilteredItems = unfilteredItems.filter(item -> !duplicateChecker.checkDuplicateAndRemember(item));
        }
      }

      pagingFilter = getPagingFilter(locator, locatorDataBinding.getFilter());
    } catch (LocatorProcessException | BadRequestException | IllegalArgumentException e) {
      if (!locator.isHelpRequested()) {
        throw e;
      }
      throw new BadRequestException(e.getMessage() +
                                    "\nLocator details: " + locator.getLocatorDescription(locator.helpOptions().getSingleDimensionValueAsStrictBoolean("hidden", false)), e);
    }
    locator.checkLocatorFullyProcessed();
    return getItems(pagingFilter, unfilteredItems, locator, startTime);
  }

  @NotNull
  public PagingItemFilter<ITEM> getPagingFilter(@NotNull final Locator locator, @NotNull final ItemFilter<ITEM> filter) {
    final Long start = locator.getSingleDimensionValueAsLong(PagerData.START);
    final Long count = getCountNotMarkingAsUsed(locator);
    locator.markUsed(PagerData.COUNT);
    final Long lookupLimit = getLookupLimit(locator);

    return new PagingItemFilter<>(filter, start, count == null ? null : count.intValue(), lookupLimit);
  }

  @Nullable
  private List<ITEM> getContextItems(@NotNull final String contextItemText) {
    Object o = RestContext.getThreadLocal().getVar(contextItemText);
    if (o == null) return null;
    if (o instanceof List) {
      return (List)o;  //this never produces ClassCastException as generics are lost on run-time
    }
    return Collections.singletonList((ITEM)o);  //this never produces ClassCastException as generics are lost on run-time
  }

  @Nullable
  protected Long getLookupLimit(@NotNull final Locator locator) {
    Long lookupLimit = locator.getSingleDimensionValueAsLong(DIMENSION_LOOKUP_LIMIT, myDataBinding.getDefaultLookupLimit());

    if (lookupLimit != null && locator.lookupSingleDimensionValue(DIMENSION_LOOKUP_LIMIT) == null) {
      //default was used, make sure it is not less than "count"
      final Long count = getCountNotMarkingAsUsed(locator);
      if (count != null && lookupLimit < count) {
        // if count of items is set, but lookupLimit is not, process at least as many items as count
        lookupLimit = count;
      }
    }
    return lookupLimit;
  }

  @Nullable
  protected Long getCountNotMarkingAsUsed(@NotNull final Locator locator) {
    Long result = locator.lookupSingleDimensionValueAsLong(PagerData.COUNT, myDataBinding.getDefaultPageItemsCount());
    if (NO_COUNT.equals(result)) return null;
    return result;
  }

  private static boolean isReportErrorOnNothingFound(@NotNull final Locator locator) {
    return locator.getSingleDimensionValueAsStrictBoolean(OPTIONS_REPORT_ERROR_ON_NOTHING_FOUND, false) || locator.isHelpRequested();
  }

  @NotNull
  private PagedSearchResult<ITEM> getItems(@NotNull final PagingItemFilter<ITEM> filter,
                                           @NotNull final ItemHolder<ITEM> unfilteredItems,
                                           @NotNull final Locator locator, final long startTime) {
    final long filteringStartTime = System.nanoTime();
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<>(filter);
    unfilteredItems.process(filterItemProcessor);
    final ArrayList<ITEM> result = filterItemProcessor.getResult();
    final long finishTime = System.nanoTime();
    final long processingTimeMs = TimeUnit.MILLISECONDS.convert(finishTime - startTime, TimeUnit.NANOSECONDS);
    final long totalItemsProcessed = filterItemProcessor.getTotalItemsProcessed();

    if (totalItemsProcessed >= TeamCityProperties.getLong("rest.finder.processedItemsLogLimit", 1)) {
      final String lookupLimitMessage =
        filter.isLookupLimitReached() ? " (lookupLimit of " + filter.getLookupLimit() + " reached). Last processed item: " + LogUtil.describe(filter.getLastProcessedItem()) : "";
      if (LOG.isDebugEnabled()) {
        LOG.debug("While processing locator '" + locator + "' by finder " + getName() + ", " + result.size() + " items were matched by the filter from " +
                  totalItemsProcessed + " processed in total" + lookupLimitMessage + ", took " + processingTimeMs + " ms (filtering " +
                  TimeUnit.MILLISECONDS.convert(finishTime - filteringStartTime, TimeUnit.NANOSECONDS) + " ms)");
      }
    }
    if (isHeavyRequest(processingTimeMs, totalItemsProcessed, result.size())) {
      LOG.info("Server performance can be affected by REST request and finder " + getName() + " with locator '" + locator + "': " +
               totalItemsProcessed + " items were processed and " + result.size() + " items were returned, took " + TimePrinter
                 .createMillisecondsFormatter().formatTime(processingTimeMs));
    }
    if (result.isEmpty() && isReportErrorOnNothingFound(locator)) {
      throw new NotFoundException("Nothing is found by " + getLocatorDetailsForMessage(locator) + ".");
    }
    return new PagedSearchResult<>(result, filter.getStart(), filter.getCount(), totalItemsProcessed,
                                   filter.getLookupLimit(), filter.isLookupLimitReached(), filter.getLastProcessedItem());
  }

  private static boolean isHeavyRequest(long processingTimeMs, long totalItemsProcessed, int resultSize) {
    if (processingTimeMs > TeamCityProperties.getLong("rest.finder.timeWarnLimit", 10000)) {
      return true;
    }

    if (processingTimeMs < TeamCityProperties.getLong("rest.finder.minimumTimeWarnLimit", 1000)) {
      return false;
    }

    return (totalItemsProcessed - resultSize) > TeamCityProperties.getLong("rest.finder.processedAndFilteredItemsWarnLimit", 10000)
           || totalItemsProcessed > TeamCityProperties.getLong("rest.finder.processedItemsWarnLimit", 100000);
  }

  @NotNull
  private ItemFilter<ITEM> getFilterWithLogicOpsSupport(@NotNull final Locator locator, @NotNull final LocatorDataBinding<ITEM> dataBinding) {
    List<ItemFilter<ITEM>> result = new ArrayList<>();
    result.add(dataBinding.getFilter());

    final String orDimension = locator.getSingleDimensionValue(LOGIC_OP_OR); //consider adding for multiple support here, use getItemsAnd()
    if (orDimension != null) {
      result.add(getFilterOr(getListOfSubLocators(orDimension)));
    }

    final String andDimension = locator.getSingleDimensionValue(LOGIC_OP_AND);  //consider adding for multiple support here, use getItemsAnd()
    if (andDimension != null) {
      result.add(getFilter(andDimension));
    }

    final String notDimension = locator.getSingleDimensionValue(LOGIC_OP_NOT);  //consider adding for multiple support here, use getItemsAnd()
    if (notDimension != null) {
      ItemFilter<ITEM> notFilter = getFilter(notDimension);
      result.add(ItemFilterUtil.ofPredicate(item -> !notFilter.isIncluded(item)));
    }

    return ItemFilterUtil.and(result);
  }

  /**
   * @return null, if no logic ops are found within locator and it should be processed as usual, otherwise, result items which require no additional processing.
   */
  @NotNull
  private LocatorDataBinding<ITEM> getDataBindingWithLogicOpsSupport(@NotNull final Locator locator,
                                                                     @NotNull final FinderDataBinding<ITEM> originalDataBinding) {
    return new LocatorDataBinding<ITEM>() {
      private LocatorDataBinding<ITEM> myLocatorDataBinding;

      @NotNull
      @Override
      public ItemHolder<ITEM> getPrefilteredItems() {
        if(Objects.equals(locator.lookupSingleDimensionValueAsLong(PagerData.COUNT, null), 0L)) {
          return ItemHolder.empty();
        }
        final List<String> itemDimension = locator.getDimensionValue(DIMENSION_ITEM);
        if (!itemDimension.isEmpty()) {
          return getItemsOr(itemDimension);
        }

        if (myLocatorDataBinding == null) {
          myLocatorDataBinding = originalDataBinding.getLocatorDataBinding(locator);
        }
        return myLocatorDataBinding.getPrefilteredItems();
      }

      @NotNull
      @Override
      public ItemFilter<ITEM> getFilter() {
        if (myLocatorDataBinding == null) {
          myLocatorDataBinding = originalDataBinding.getLocatorDataBinding(locator);
        }
        return getFilterWithLogicOpsSupport(locator, myLocatorDataBinding);
      }
    };
  }

  @NotNull
  private String getLocatorDetailsForMessage(@NotNull final Locator locator) {
    StringBuilder result = new StringBuilder();
    result.append("locator '").append(locator.getStringRepresentation()).append("'");
    List<String> contextVars = getContextVars(locator);
    if (!contextVars.isEmpty()) {
      result.append(", context: ");
      result.append(contextVars.stream().map(s -> s + "=" + Optional.ofNullable(getContextItems(s)).map(
                                 v -> v.stream().map(vElem -> (vElem == null ? "<null>" : "'" + vElem.toString() + "'")).collect(Collectors.joining(", ", "{", "}"))).orElse("<null>"))
                               .collect(Collectors.joining(", ", "{", "}")));
      //vElem.toString() might produce not due presentation
      result.append('}');
    }
    return result.toString();
  }

  @NotNull
  private List<String> getContextVars(@NotNull final Locator locator) {
    if (locator.isSingleValue()) return Collections.emptyList();

    ArrayList<String> result = new ArrayList<>();
    try {
      for (String name : locator.getDefinedDimensions()) {
        if (BuildPromotionFinder.CONTEXT_ITEM_DIMENSION_NAME.equals(name)) {
          result.addAll(locator.getDimensionValue(name));
        } else {
          for (String value : locator.getDimensionValue(name)) {
            try {
              result.addAll(getContextVars(new Locator(value, getSupportedDimensions())));
            } catch (Exception e) {
              //ignore
            }
          }
        }
      }
    } catch (Exception e) {
      //ignore
    }
    return result;
  }

  @NotNull
  private static List<String> getListOfSubLocators(@NotNull final String locatorText) {
    Locator locator = new Locator(locatorText);
    if (locator.isSingleValue()) {
      return Collections.singletonList(locator.getStringRepresentation());
    }
    List<String> result = new ArrayList<>();
    for (String dimensionName : locator.getDefinedDimensions()) {
      for (String value : locator.getDimensionValue(dimensionName)) {
        result.add(Locator.getStringLocator(dimensionName, value));
      }
    }
    return result;
  }

  @NotNull
  private ItemFilter<ITEM> getFilterOr(@NotNull final List<String> itemsDimension) {
    List<ItemFilter<ITEM>> filters = itemsDimension.stream().map(this::getFilter).collect(Collectors.toList());
    return ItemFilterUtil.or(filters);
  }

  @NotNull
  private ItemHolder<ITEM> getItemsOr(@NotNull final List<String> itemsDimension) {
    return processor -> {
      for (String itemLocator : itemsDimension) {
        ItemHolder.of(getItems(itemLocator).getEntries()).process(processor);  //todo: rework APIs to add itemHolders instead of serialized collection
      }
    };
  }

  @Override
  public String toString() {
    return getName();
  }

  /*
  @NotNull
  private FinderDataBinding.LocatorDataBinding<ITEM> getItemsAnd(@NotNull final List<String> itemsDimension) {
    if (itemsDimension.size() == 0) {
      throw new BadRequestException("Unsupported empty locator for 'and' processing");
    }
    if (itemsDimension.size() == 1) {
      return new FinderDataBinding.LocatorDataBinding<ITEM>() {
        @NotNull
        @Override
        public FinderDataBinding.ItemHolder<ITEM> getPrefilteredItems() {
          return ItemHolder.of(getItems(itemsDimension.get(0)).myEntries);
        }

        @NotNull
        @Override
        public ItemFilter<ITEM> getFilter() {
          return DO_NOTHING_FILTER;
        }
      };
    }
    Iterator<String> it = itemsDimension.iterator();
    List<ITEM> firstSet = getItems(it.next()).myEntries;
    MultiCheckerFilter<ITEM> filter = new MultiCheckerFilter<ITEM>();
    while (it.hasNext()) {
      filter.add(getFilter(it.next()));
    }
    return new FinderDataBinding.LocatorDataBinding<ITEM>() {
      @NotNull
      @Override
      public FinderDataBinding.ItemHolder<ITEM> getPrefilteredItems() {
        return ItemHolder.of(firstSet);
      }

      @NotNull
      @Override
      public ItemFilter<ITEM> getFilter() {
        return new ItemFilter<ITEM>() {
          @Override
          public boolean shouldStop(@NotNull final ITEM item) {
            return false;
          }

          @Override
          public boolean isIncluded(@NotNull final ITEM item) {
            return filter.isIncluded(item);
          }
        };
      }
    };
  }

  private final ItemFilter<ITEM> DO_NOTHING_FILTER = new ItemFilter<ITEM>() {
    @Override
    public boolean shouldStop(@NotNull final ITEM item) {
      return false;
    }

    @Override
    public boolean isIncluded(@NotNull final ITEM item) {
      return true;
    }
  };
  */
}
