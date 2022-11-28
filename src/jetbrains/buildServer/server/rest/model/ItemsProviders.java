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
  interface ItemsRetriever<T> {
    @Nullable
    List<T> getItems();

    Integer getCount();

    boolean isCountCheap();

    @Nullable
    PagerData getPagerData();
  }

  abstract class ItemsProvider<T> {
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
}