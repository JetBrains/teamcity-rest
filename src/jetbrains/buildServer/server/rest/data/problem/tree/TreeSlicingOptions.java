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

package jetbrains.buildServer.server.rest.data.problem.tree;

import java.util.Comparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TreeSlicingOptions<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
  private int myMaxChildren = 5;
  @NotNull
  private final Comparator<DATA> myDataComparator;

  @Nullable
  private Comparator<ScopeTree.Node<DATA, COUNTERS>> myNodeComparator = null;

  public TreeSlicingOptions(int maxChildren,
                            @NotNull Comparator<DATA> dataComparator,
                            @Nullable Comparator<ScopeTree.Node<DATA, COUNTERS>> nodeComparator) {
    myMaxChildren = maxChildren;
    myNodeComparator = nodeComparator;
    myDataComparator = dataComparator;
  }

  public TreeSlicingOptions(@NotNull Comparator<DATA> dataComparator) {
    myDataComparator = dataComparator;
  }

  @NotNull
  public TreeSlicingOptions<DATA, COUNTERS> withMaxChildren(int maxChildren) {
    return new TreeSlicingOptions<DATA, COUNTERS>(maxChildren, myDataComparator, myNodeComparator);
  }

  @NotNull
  public TreeSlicingOptions<DATA, COUNTERS> withNodeComparator(@NotNull Comparator<ScopeTree.Node<DATA, COUNTERS>> nodeComparator) {
    return new TreeSlicingOptions<DATA, COUNTERS>(myMaxChildren, myDataComparator, nodeComparator);
  }

  public int getMaxChildren() {
    return myMaxChildren;
  }

  @NotNull
  public Comparator<DATA> getDataComparator() {
    return myDataComparator;
  }

  @Nullable
  public Comparator<ScopeTree.Node<DATA, COUNTERS>> getNodeComparator() {
    return myNodeComparator;
  }
}
