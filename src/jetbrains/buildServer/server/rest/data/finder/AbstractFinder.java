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


import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.PagedSearchResult;
import jetbrains.buildServer.server.rest.data.util.DuplicateChecker;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.util.SetDuplicateChecker;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Convenience class for searching entities via locators
 *
 * @author Yegor.Yarko
 *         Date: 09.11.13
 */
public abstract class AbstractFinder<ITEM> extends FinderImpl<ITEM> implements FinderDataBinding<ITEM> {

  private final String[] myKnownDimensions;
  private String[] myHiddenDimensions = new String[]{};

  public AbstractFinder(@NotNull final String... knownDimensions) {
    setDataBinding(this);
    myKnownDimensions = ArrayUtils.addAll(knownDimensions);
  }

  @Nullable
  @Override
  public Long getDefaultPageItemsCount() {
    return null;
  }

  @Nullable
  @Override
  public Long getDefaultLookupLimit() {
    return null;
  }

  @Nullable
  @Override
  public ITEM findSingleItem(@NotNull final Locator locator) {
    return null;
  }

  @NotNull
  public abstract ItemHolder<ITEM> getPrefilteredItems(@NotNull final Locator locator);

  @NotNull
  public abstract ItemFilter<ITEM> getFilter(@NotNull final Locator locator);

  @NotNull
  @Override
  public LocatorDataBinding<ITEM> getLocatorDataBinding(@NotNull final Locator locator) {
    return new LocatorDataBinding<ITEM>() {
      @NotNull
      @Override
      public ItemHolder<ITEM> getPrefilteredItems() {
        return AbstractFinder.this.getPrefilteredItems(locator);
      }

      @NotNull
      @Override
      public ItemFilter<ITEM> getFilter() {
        return AbstractFinder.this.getFilter(locator);
      }
    };
  }

  @NotNull
  @Override
  public String[] getKnownDimensions() {
    return myKnownDimensions;
  }

  @NotNull
  @Override
  public String[] getHiddenDimensions() {
    return myHiddenDimensions;
  }

  @Nullable
  @Override
  public Locator.DescriptionProvider getLocatorDescriptionProvider() {
    return null;
  }

  /**
   * Determines the dimensions which should not be returned by help request.
   * @param hiddenDimensions the names of the hidden dimensions
   */
  public void setHiddenDimensions(@NotNull final String... hiddenDimensions) {
    myHiddenDimensions = hiddenDimensions;
  }

  @Nullable
  @Override
  public DuplicateChecker<ITEM> createDuplicateChecker() {
    return new SetDuplicateChecker<>();
  }

  @NotNull
  public PagedSearchResult<ITEM> getItemsNotEmpty(@Nullable final String locatorText) {
    PagedSearchResult<ITEM> result = super.getItems(locatorText);
    if (result.myEntries.isEmpty()) {
      throw new NotFoundException("Nothing is found by locator '" + locatorText + "'.");
    }
    return result;
  }
}
