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

package jetbrains.buildServer.server.rest.data.util.itemholder;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Abstraction allowing for possibly lazy item processing.
 * In essence, it supplies given ItemProcessor with items
 * until processor.processItem returns false. <br/>
 * Supports calling process(..) only once, can't be reused.
 */
@FunctionalInterface
public interface ItemHolder<T> {

  void process(@NotNull final ItemProcessor<T> processor);

  /**
   * Intermediate operation.
   */
  default ItemHolder<T> filter(Predicate<T> predicate) {
    return (processor) -> process(item -> {
      if (predicate.test(item)) {
        return processor.processItem(item);
      }
      return true;
    });
  }

  /**
   * Intermediate operation.
   * Replacement for ex WrappingItemHolder.
   */
  default <R> ItemHolder<R> map(Function<T, R> mapper) {
    return processor -> process(item -> {
      return processor.processItem(mapper.apply(item));
    });
  }

  /**
   * Intermediate operation.
   * Replacement for ex FlatteningItemHolder.
   *
   * @param mapper
   * @param <R>    the output item type.
   * @return new ItemHolder
   */
  default <R> ItemHolder<R> flatMap(Function<T, ItemHolder<R>> mapper) {
    return processor -> process(itemHolderSource -> {
      boolean[] processingContinues = new boolean[1];
      processingContinues[0] = true;
      ItemHolder<R> itemHolder = mapper.apply(itemHolderSource);
      itemHolder.process(item -> {
        if (!processingContinues[0]) {
          return false;
        }

        boolean shouldContinue = processor.processItem(item);

        if (!shouldContinue) {
          processingContinues[0] = false;
          return false;
        } else {
          return true;
        }
      });
      return processingContinues[0];
    });
  }

  static <T> ItemHolder<T> empty() {
    return processor -> { };
  }

  /**
   * @param collection only created when terminal operation is executed.
   */
  static <T> ItemHolder<T> of(Supplier<Iterable<T>> collection) {
    return processor -> ItemHolder.of(collection.get()).process(processor);
  }

  /**
   * The locator should be fully processed before calling this (i.e. locator should not be captured by the stream passed), otherwise "dimension is known but was ignored" error might be incorrectly  reported
   */
  @NotNull
  static <T> ItemHolder<T> of(@NotNull Stream<? extends T> items) {
    return processor -> items.filter(item -> !processor.processItem(item)).findFirst();
  }


  @NotNull
  static <T> ItemHolder<T> of(@NotNull Iterable<? extends T> items) {
    return processor -> {
      for (T entry : items) {
        if (!processor.processItem(entry)) return;
      }
    };
  }

  @NotNull
  static <T> ItemHolder<T> concat(@NotNull Iterable<ItemHolder<T>> items) {
    return ItemHolder.of(items).flatMap(Function.identity());
  }
}
