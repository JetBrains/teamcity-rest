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

import java.util.stream.Stream;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Abstraction allowing for possibly lazy item processing.
 * In essence, it supplies given ItemProcessor with items
 * until processor.processItem returns false. <br/>
 * Supports calling process(..) only once, can't be reused.
 */
public interface ItemHolder<P> {

  void process(@NotNull final ItemProcessor<P> processor);
  /**
   * The locator should be fully processed before calling this (i.e. locator should not be captured by the stream passed), otherwise "dimension is known but was ignored" error might be incorrectly  reported
   */
  @NotNull
  static <P> ItemHolder<P> of(@NotNull Stream<? extends P> items) {
    return processor -> items.filter(item -> !processor.processItem(item)).findFirst();
  }

  @NotNull
  static <P> ItemHolder<P> of(@NotNull Iterable<? extends P> items) {
    return new CollectionItemHolder<P>(items);
  }
}
