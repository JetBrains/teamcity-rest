/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 * Date: 11/11/2017
 */

public interface ItemsProviders {

  interface LocatorAware<T> {
    @NotNull
    T get(@Nullable final String locator);
  }

  interface ItemsRetriever<T> {
    @Nullable
    List<T> getItems();

    Integer getCount();

    boolean isCountCheap();

    @Nullable
    PagerData getPagerData();
  }



  static abstract class ItemsProvider<T> {
    @NotNull
    public abstract List<T> getItems(@Nullable final String locator);

    @Nullable
    public Integer getCheapCount(@Nullable final String locator) {
      return null;
    }


    @NotNull
    public static <S> ItemsProvider<S> items(@NotNull Function<String, List<S>> getItems) {
      return new ItemsProvider<S>() {
        @NotNull
        @Override
        public List<S> getItems(@Nullable final String locator) {
          return getItems.apply(locator);
        }
      };
    }

    @NotNull
    public static <S> ItemsProvider<S> items(@NotNull List<S> items) {
      return new ItemsProvider<S>() {
        @NotNull
        @Override
        public List<S> getItems(@Nullable final String locator) {
          return items;
        }

        @Override
        public Integer getCheapCount(@Nullable final String locator) {
          return items.size();
        }
      };
    }
  }



  class LocatorAwareItemsRetriever<T> implements LocatorAware<ItemsRetriever<T>> {
    @Nullable private final ItemsProvider<T> myItemsProvider;
    @Nullable private final Supplier<PagerData> myPagerData;

    public LocatorAwareItemsRetriever(@Nullable final ItemsProvider<T> itemsProvider, @Nullable final Supplier<PagerData> pagerData) {
      myItemsProvider = itemsProvider;
      myPagerData = pagerData;
    }

    @NotNull
    @Override
    public ItemsRetriever<T> get(@Nullable final String locator) {
      return new ItemsRetrieverImpl(locator);
    }


    class ItemsRetrieverImpl implements ItemsRetriever<T> {
      @Nullable private final String myLocator;

      @Nullable private List<T> myCachedItems = null;
      private Integer myCachedCheapCount = null;
      private boolean myCheapCountIsCalculated = false;


      ItemsRetrieverImpl(@Nullable final String locator) {
        myLocator = locator;
      }

      @Nullable
      @Override
      public PagerData getPagerData() {
        return myPagerData == null ? null : myPagerData.get();
      }


      @Nullable
      @Override
      public List<T> getItems() {
        if (myItemsProvider == null) return null;
        if (myCachedItems == null) {
          myCachedItems = myItemsProvider.getItems(myLocator);
        }
        return myCachedItems;
      }


      @Override
      public Integer getCount() {
        if (myItemsProvider == null) return null;
        Integer cheapCount = getCheapCount();
        if (cheapCount != null) {
          return cheapCount;
        }
        //noinspection ConstantConditions
        return getItems().size();
      }

      @Override
      public boolean isCountCheap() {
        if (myItemsProvider == null) return true;
        return getCheapCount() != null;
      }


      @Nullable
      Integer getCheapCount() {
        if (myCachedItems != null) {
          return myCachedItems.size();
        }
        if (!myCheapCountIsCalculated) {
          //noinspection ConstantConditions
          myCachedCheapCount = myItemsProvider.getCheapCount(myLocator);
          myCheapCountIsCalculated = true;
        }
        return myCachedCheapCount;
      }
    }
  }
}