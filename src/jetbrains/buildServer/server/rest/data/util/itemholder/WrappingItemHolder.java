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
import java.util.stream.Stream;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps items on the fly before passing them to the processor via given wrapper function.
 */
public class WrappingItemHolder<UNWRAPPED, WRAPPED> implements ItemHolder<WRAPPED> {
  private final Function<UNWRAPPED, WRAPPED> myWrapper;
  private final ItemHolder<? extends UNWRAPPED> myDelegate;

  public WrappingItemHolder(@NotNull final ItemHolder<? extends UNWRAPPED> delegate, @NotNull Function<UNWRAPPED, WRAPPED> wrapper) {
    myWrapper = wrapper;
    myDelegate = delegate;
  }

  public WrappingItemHolder(@NotNull final Iterable<? extends UNWRAPPED> items, @NotNull Function<UNWRAPPED, WRAPPED> wrapper) {
    myWrapper = wrapper;
    myDelegate = new CollectionItemHolder<>(items);
  }

  public WrappingItemHolder(@NotNull final Stream<? extends UNWRAPPED> items, @NotNull Function<UNWRAPPED, WRAPPED> wrapper) {
    myWrapper = wrapper;
    myDelegate = ItemHolder.of(items);
  }

  @Override
  public void process(@NotNull ItemProcessor<WRAPPED> processor) {
    myDelegate.process(item -> processor.processItem(myWrapper.apply(item)));
  }
}
