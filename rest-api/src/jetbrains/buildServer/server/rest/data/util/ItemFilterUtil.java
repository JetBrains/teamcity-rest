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

package jetbrains.buildServer.server.rest.data.util;

import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

public class ItemFilterUtil {
  private static final ItemFilter<?> EMPTY = new ItemFilter<Object>() {
    @Override
    public boolean shouldStop(@NotNull Object item) {
      return false;
    }

    @Override
    public boolean isIncluded(@NotNull Object item) {
      return true;
    }
  };

  public static <T> ItemFilter<T> and(List<ItemFilter<T>> filters) {
    if (filters.isEmpty()) {
      return ItemFilterUtil.empty();
    }

    return new ItemFilter<T>() {
      @Override
      public boolean shouldStop(@NotNull final T item) {
        return filters.stream().anyMatch(checker -> checker.shouldStop(item));
      }

      @Override
      public boolean isIncluded(@NotNull final T item) {
        return filters.stream().allMatch(checker -> checker.isIncluded(item));
      }
    };
  }

  public static <T> ItemFilter<T> or(List<ItemFilter<T>> filters) {
    if (filters.isEmpty()) {
      return ItemFilterUtil.empty();
    }

    return new ItemFilter<T>() {
      @Override
      public boolean shouldStop(@NotNull final T item) {
        return filters.stream().anyMatch(checker -> checker.shouldStop(item));
      }

      @Override
      public boolean isIncluded(@NotNull final T item) {
        return filters.stream().anyMatch(checker -> checker.isIncluded(item));
      }
    };
  }

  public static <T> ItemFilter<T> dropAll() {
    return new ItemFilter<T>() {
      @Override
      public boolean shouldStop(@NotNull final T item) {
        return false;
      }

      @Override
      public boolean isIncluded(@NotNull final T item) {
        return false;
      }
    };
  }

  public static <T> ItemFilter<T> ofPredicate(Predicate<T> predicate) {
    return new ItemFilter<T>() {
      @Override
      public boolean shouldStop(@NotNull final T item) {
        return false;
      }

      @Override
      public boolean isIncluded(@NotNull final T item) {
        return predicate.test(item);
      }
    };
  }

  /**
   * Returns singleton instance, which accepts any elements and never signals to stop proessing.
   */
  public static <T> ItemFilter<T> empty() {
    //noinspection unchecked
    return (ItemFilter<T>)EMPTY;
  }


}
