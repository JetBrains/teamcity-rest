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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.util.ItemProcessor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Convenience class for searching entities via locators
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public abstract class AbstractFinder<ITEM> {
  public static final String DIMENSION_ID = "id";
  public static final String DIMENSION_LOOKUP_LIMIT = "lookupLimit";

  private final String[] myKnownDimensions;

  public AbstractFinder(final String[] knownDimensions) {
    myKnownDimensions = new String[knownDimensions.length];
    System.arraycopy(knownDimensions, 0, myKnownDimensions, 0, knownDimensions.length);
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

  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText) {
    return getItemsByLocator(getLocatorOrNull(locatorText));
  }

  @NotNull
  public PagedSearchResult<ITEM> getItems(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    return getItemsByLocator(getLocatorOrNull(locatorText, locatorDefaults));
  }

  @NotNull
  public PagedSearchResult<ITEM> getItemsByLocator(@Nullable final Locator originalLocator) {
    Locator locator;
    if (originalLocator == null) {
      final ItemHolder<ITEM> allItems = getAllItems();
      if (allItems != null){
        return new PagedSearchResult<ITEM>(toList(allItems), null, null);
      }
      locator = Locator.createEmptyLocator(null, null);
    } else{
      locator = originalLocator;
    }

    ITEM singleItem = findSingleItem(locator);
    if (singleItem != null){
      // ignore start:0 dimension
      final Long startDimension = locator.getSingleDimensionValueAsLong(PagerData.START);
      if (startDimension == null || startDimension != 0) {
        locator.markUnused(PagerData.START);
      }

      //todo: consider enabling this after check (report 404 instead of "locator is not fully processed"
      //and do not report "locator is not fully processed" if the single result complies)
      final Set<String> unusedDimensions = locator.getUnusedDimensions();
      if (!unusedDimensions.isEmpty()) {
        AbstractFilter<ITEM> filter = getFilter(locator);
        locator.checkLocatorFullyProcessed();
        if (!filter.isIncluded(singleItem)) {
          final Set<String> usedDimensions = locator.getUsedDimensions();
          throw new NotFoundException("Found single item by " + StringUtil.pluralize("dimension", usedDimensions.size()) + " " + usedDimensions +
                                      ", but that was filtered out using all dimensions");
        }
      }

      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<ITEM>(Collections.singletonList(singleItem), null, null);
    }
    locator.markAllUnused(); // nothing found - no dimensions should be marked as used then

    //it is important to call "getPrefilteredItems" first as that process some of the dimensions which  "getFilter" can then ignore for performance reasons
    final ItemHolder<ITEM> unfilteredItems = getPrefilteredItems(locator);
    AbstractFilter<ITEM> filter = getFilter(locator);
    locator.checkLocatorFullyProcessed();
    return new PagedSearchResult<ITEM>(getItems(filter, unfilteredItems), filter.getStart(), filter.getCount());
  }

  @NotNull
  protected List<ITEM> getItems(final @NotNull AbstractFilter<ITEM> filter, final @NotNull ItemHolder<ITEM> unfilteredItems) {
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    unfilteredItems.process(filterItemProcessor);
    return filterItemProcessor.getResult();
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
    final PagedSearchResult<ITEM> items = getItemsByLocator(locator);
    if (items.myEntries.size() == 0) {
      throw new NotFoundException("Nothing is found by locator '" + locator.getStringRepresentation() + "'.");
    }
    assert items.myEntries.size()== 1;
    return items.myEntries.get(0);
  }

  @NotNull   //todo: change overrides
  protected ItemHolder<ITEM> getPrefilteredItems(@NotNull Locator locator) {
    return getAllItems();
  }

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
  protected abstract AbstractFilter<ITEM> getFilter(final Locator locator);

  public String[] getKnownDimensions() {
    return myKnownDimensions;
  }

  public interface ItemHolder<P> {
    boolean process(@NotNull final ItemProcessor<P> processor);
  }

  @NotNull
  public static <P> ItemHolder<P> getItemHolder(@NotNull Iterable<P> items){
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
