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

package jetbrains.buildServer.server.rest.data.util;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.server.rest.data.FinderDataBinding;
import jetbrains.buildServer.util.ItemProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Passes items from each of the given item holders in the order they were added.
 */
public class AggregatingItemHolder<P> implements FinderDataBinding.ItemHolder<P> {
  @NotNull final private List<FinderDataBinding.ItemHolder<P>> myItemHolders = new ArrayList<>();

  public void add(FinderDataBinding.ItemHolder<P> holder) {
    myItemHolders.add(holder);
  }

  public void process(@NotNull final ItemProcessor<P> processor) {
    boolean[] processingContinues = new boolean[1];
    processingContinues[0] = true;
    for (FinderDataBinding.ItemHolder<P> itemHolder : myItemHolders) {
      itemHolder.process((item) -> processingContinues[0] = processor.processItem(item));
      if (!processingContinues[0]) return;
    }
  }
}
