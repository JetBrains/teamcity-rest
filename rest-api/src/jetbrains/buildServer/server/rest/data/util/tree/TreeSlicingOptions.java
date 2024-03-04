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

package jetbrains.buildServer.server.rest.data.util.tree;

import java.util.Comparator;
import java.util.function.Function;
import jetbrains.buildServer.server.rest.data.util.tree.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TreeSlicingOptions<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
  /**
   * This function should always return non-null non-negative integer value. <br/>
   * If no limit could be provided, then {@link Integer.MAX_VALUE} should be returned.
   */
  @NotNull
  private final Function<Node<DATA, COUNTERS>, Integer> myMaxChildren;
  @NotNull
  private final Comparator<DATA> myDataComparator;
  @Nullable
  private final Comparator<Node<DATA, COUNTERS>> myNodeComparator;
  @Nullable
  private final Integer myMaxTotalNodes;

  public TreeSlicingOptions(
    @NotNull Function<Node<DATA, COUNTERS>, Integer> maxChildren,
    @NotNull Comparator<DATA> dataComparator,
    @Nullable Comparator<Node<DATA, COUNTERS>> nodeComparator,
    @Nullable Integer maxTotalNodes
  ) {
    myMaxChildren = maxChildren;
    myNodeComparator = nodeComparator;
    myDataComparator = dataComparator;
    myMaxTotalNodes = maxTotalNodes;
  }

  public TreeSlicingOptions(
    int maxChildren,
    @NotNull Comparator<DATA> dataComparator,
    @Nullable Comparator<Node<DATA, COUNTERS>> nodeComparator
  ) {
    this((__) -> maxChildren, dataComparator, nodeComparator, null);
  }

  public TreeSlicingOptions(@NotNull Function<Node<DATA, COUNTERS>, Integer> maxChildren,
                            @NotNull Comparator<DATA> dataComparator,
                            @Nullable Comparator<Node<DATA, COUNTERS>> nodeComparator) {
    this(maxChildren, dataComparator, nodeComparator, null);
  }

  @NotNull
  public TreeSlicingOptions<DATA, COUNTERS> withMaxChildren(int maxChildren) {
    return new TreeSlicingOptions<DATA, COUNTERS>((__) -> maxChildren, myDataComparator, myNodeComparator, myMaxTotalNodes);
  }

  @NotNull
  public TreeSlicingOptions<DATA, COUNTERS> withMaxChildren(@NotNull Function<Node<DATA, COUNTERS>, Integer> maxChildren) {
    return new TreeSlicingOptions<DATA, COUNTERS>(maxChildren, myDataComparator, myNodeComparator, myMaxTotalNodes);
  }

  public TreeSlicingOptions<DATA, COUNTERS> withMaxNodes(int maxTotalNodes) {
    return new TreeSlicingOptions<DATA, COUNTERS>(myMaxChildren, myDataComparator, myNodeComparator, maxTotalNodes);
  }

  /**
   * See issue TW-71521.
   * Previously, this was just a const value, but we need to configure max children depending on the parent node.
   *
   * @param node the node which has a limit on the count of the children
   * @return the max amount of children or {@link Integer.MAX_VALUE} if no limit present.
   */
  public int getMaxChildren(@NotNull Node<DATA, COUNTERS> node) {
    return myMaxChildren.apply(node);
  }

  @NotNull
  public Comparator<DATA> getDataComparator() {
    return myDataComparator;
  }

  @Nullable
  public Comparator<Node<DATA, COUNTERS>> getNodeComparator() {
    return myNodeComparator;
  }

  @Nullable
  public Integer getMaxTotalNodes() {
    return myMaxTotalNodes;
  }
}
