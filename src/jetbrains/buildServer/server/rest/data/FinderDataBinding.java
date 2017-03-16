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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 06/06/2016
 */
public interface FinderDataBinding<ITEM> {

  @Nullable
  Long getDefaultPageItemsCount();

  @Nullable
  Long getDefaultLookupLimit();

  @NotNull
  String[] getKnownDimensions();

  @NotNull
  String[] getHiddenDimensions();

  @Nullable
  Locator.DescriptionProvider getLocatorDescriptionProvider();

  /**
   * @return the item found or null if this is not single item locator
   * @throws NotFoundException when the locator is for single item, but the item does not exist / is not accessible for the current user
   */
  @Nullable
  ITEM findSingleItem(@NotNull final Locator locator);

  @NotNull
  LocatorDataBinding<ITEM> getLocatorDataBinding(@NotNull final Locator locator);

  interface LocatorDataBinding<ITEM> {
    @NotNull
    ItemHolder<ITEM> getPrefilteredItems();

    /**
     * Returns filter based on passed locator
     * Should not have side-effects other than marking used locator dimensions
     *
     * @param locator can be empty locator. Can
     */
    @NotNull
    ItemFilter<ITEM> getFilter();
  }

  @NotNull
  String getItemLocator(@NotNull final ITEM item);

  /**
   * Returns new empty set which ensures proper items matching
   * Is used for "unique" dimension processing
   * @return null if "unique" dimension is not supported
   */
  @Nullable
  Set<ITEM> createContainerSet();

  interface ItemHolder<P> {
    void process(@NotNull final ItemProcessor<P> processor);
  }

  @NotNull
  static <P> ItemHolder<P> getItemHolder(@NotNull Iterable<? extends P> items) {
    return new CollectionItemHolder<P>(items);
  }

  static class CollectionItemHolder<P> implements ItemHolder<P> {
    @NotNull final private Iterable<? extends P> myEntries;

    public CollectionItemHolder(@NotNull final Iterable<? extends P> entries) {
      myEntries = entries;
    }

    public void process(@NotNull final ItemProcessor<P> processor) {
      for (P entry : myEntries) {
        processor.processItem(entry);
      }
    }
  }

  static class AggregatingItemHolder<P> implements ItemHolder<P> {
    @NotNull final private List<ItemHolder<P>> myItemHolders = new ArrayList<>();

    public void add(ItemHolder<P> holder) {
      myItemHolders.add(holder);
    }

    public void process(@NotNull final ItemProcessor<P> processor) {
      for (ItemHolder<P> itemHolder : myItemHolders) {
        itemHolder.process(processor);
      }
    }
  }

  /**
   * Works only for P with due hash/equals
   *
   * @param <P>
   */
  static class DeduplicatingItemHolder<P> implements ItemHolder<P> {
    @NotNull private final ItemHolder<P> myItemHolder;
    private @NotNull Set<P> myProcessed;

    public DeduplicatingItemHolder(@NotNull final ItemHolder<P> itemHolder, @NotNull final Set<P> processed) {
      myItemHolder = itemHolder;
      myProcessed = processed;
    }

    public void process(@NotNull final ItemProcessor<P> processor) {
      myItemHolder.process(new ItemProcessor<P>() {
        @Override
        public boolean processItem(final P item) {
          if (myProcessed.add(item)) return processor.processItem(item);
          return true;
        }
      });
    }
  }
}
