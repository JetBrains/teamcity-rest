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


import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.PagerData;
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
  public PagedSearchResult<ITEM> getItemsByLocator(@Nullable final Locator locator) {
    if (locator == null) {
      return new PagedSearchResult<ITEM>(getAllItems(), null, null);
    }

    ITEM singleItem = findSingleItem(locator);
    if (singleItem != null){
      // ignore start:0 dimension
      final Long startDimension = locator.getSingleDimensionValueAsLong(PagerData.START);
      if (startDimension == null || startDimension != 0) {
        locator.markUnused(PagerData.START);
      }

      /*
      //todo: consider enabling this after check (report 404 instead of "locator is not fully processed"
      //and do not report "locator is not fully processed" if the single result complies)
      if (!locator.isLocatorFullyProcessed()) {
        AbstractFilter<ITEM> filter = getFilter(locator);
        if (!filter.isIncluded(singleItem)) {
          return new PagedSearchResult<ITEM>(new ArrayList<ITEM>(), null, null);
        }
      }
      */


      locator.checkLocatorFullyProcessed();
      return new PagedSearchResult<ITEM>(Collections.singletonList(singleItem), null, null);
    }
    locator.markAllUnused(); // nothing found - no dimensions should be marked as used then

    //it is important to call "getPrefilteredItems" first as that process some of the dimensions which  "getFilter" can then ignore for performance reasons
    final List<ITEM> unfilteredItems = getPrefilteredItems(locator);
    AbstractFilter<ITEM> filter = getFilter(locator);
    locator.checkLocatorFullyProcessed();
    return new PagedSearchResult<ITEM>(getItems(filter, unfilteredItems), filter.getStart(), filter.getCount());
  }

  @NotNull
  protected List<ITEM> getItems(final @NotNull AbstractFilter<ITEM> filter, final @NotNull List<ITEM> unfilteredItems) {
    final FilterItemProcessor<ITEM> filterItemProcessor = new FilterItemProcessor<ITEM>(filter);
    AbstractFilter.processList(unfilteredItems, filterItemProcessor);
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

  protected List<ITEM> getPrefilteredItems(@NotNull Locator locator) {
    return getAllItems();
  }

  @Nullable
  protected ITEM findSingleItem(@NotNull final Locator locator){
    return null;
  }

  @NotNull
  public abstract List<ITEM> getAllItems();

  @NotNull
  protected abstract AbstractFilter<ITEM> getFilter(final Locator locator);

  public String[] getKnownDimensions() {
    return myKnownDimensions;
  }
}
