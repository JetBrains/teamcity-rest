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

import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.server.rest.errors.InvalidStateException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScopeTree<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
  private final Node<DATA, COUNTERS> myRoot;
  private final Map<String, Node<DATA, COUNTERS>> myIdToNodesMap = new HashMap<>();

  public ScopeTree(@NotNull Scope rootScope,
                   @NotNull COUNTERS rootCounters,
                   @NotNull Iterable<? extends LeafInfo<DATA, COUNTERS>> leafs) {
    myRoot = new Node<>(rootScope.getId(), rootScope, rootCounters, null);
    myIdToNodesMap.put(myRoot.getId(), myRoot);

    buildTree(leafs);
  }

  private void buildTree(@NotNull Iterable<? extends LeafInfo<DATA, COUNTERS>> leafs) {
    for (LeafInfo<DATA, COUNTERS> leafInfo : leafs) {
      COUNTERS counters = leafInfo.getCounters();
      Node<DATA, COUNTERS> parent = myRoot;
      parent.merge(counters);

      for (Scope scope : leafInfo.getPath()) {
        if(scope.equals(myRoot.getScope())) {
          continue;
        }
        if(scope.isLeaf()) {
          Node<DATA, COUNTERS> leaf = new Node<>(scope.getId(), scope, leafInfo.getData(), leafInfo.getCounters(), parent);
          myIdToNodesMap.put(leaf.getId(), leaf);
          parent.putChild(leaf);
          break;
        }

        parent = getOrCreateNode(scope, counters, parent);
      }
    }
  }

  private Node<DATA, COUNTERS> getOrCreateNode(@NotNull Scope scope, @NotNull COUNTERS counters, @NotNull Node<DATA, COUNTERS> parent) {
    Node<DATA, COUNTERS> child = parent.getChild(scope.getId());
    if (child != null) {
      child.merge(counters);
      return child;
    }

    child = new Node<>(scope.getId(), scope, counters, parent);
    myIdToNodesMap.put(child.getId(), child);
    parent.putChild(child);

    return child;
  }

  @NotNull
  public List<Node<DATA, COUNTERS>> getSlicedOrderedTree(int maxChildren, @NotNull Comparator<DATA> dataComparator, @Nullable Comparator<Node<DATA, COUNTERS>> nodeComparator) {
    return getSlicedOrderedSubtree(myRoot, maxChildren, dataComparator, nodeComparator);
  }

  @NotNull
  public List<Node<DATA, COUNTERS>> getFullNodeAndSlicedOrderedSubtree(@NotNull String nodeId, int maxChildren, @NotNull Comparator<DATA> dataComparator, @Nullable Comparator<Node<DATA, COUNTERS>> nodeComparator) {
    Node<DATA, COUNTERS> node = myIdToNodesMap.get(nodeId);
    if(node == null) {
      return Collections.emptyList();
    }

    if(node.getScope().isLeaf()) {
      // We still need children to be sorted, so passing maxChildren = data.size()
      return Collections.singletonList(sliceLeaf(node, node.getData().size(), dataComparator));
    }

    List<Node<DATA, COUNTERS>> immediateChildren = new ArrayList<>(node.getChildren());
    if(nodeComparator != null) {
      immediateChildren.sort(nodeComparator);
    }

    List<Node<DATA, COUNTERS>> result = new ArrayList<>();
    result.add(node);
    for(Node<DATA, COUNTERS> child : immediateChildren) {
      getSlicedOrderedSubtree(child, maxChildren, dataComparator, nodeComparator).forEach(result::add);
    }

    return result;
  }

  @NotNull
  private List<Node<DATA, COUNTERS>> getSlicedOrderedSubtree(@NotNull Node<DATA, COUNTERS> subTreeRoot, int maxChildren, @NotNull Comparator<DATA> dataComparator, @Nullable Comparator<Node<DATA, COUNTERS>> nodeComparator) {
    Queue<Node<DATA, COUNTERS>> nodeQueue = new ArrayDeque<>(maxChildren + 1);
    nodeQueue.add(subTreeRoot);

    List<Node<DATA, COUNTERS>> result = new ArrayList<>();

    while (!nodeQueue.isEmpty()) {
      Node<DATA, COUNTERS> node = nodeQueue.poll();
      final boolean isLeaf = node.getScope().isLeaf();

      if(isLeaf) {
        result.add(sliceLeaf(node, maxChildren, dataComparator));
        continue;
      }
      result.add(node);

      Stream<Node<DATA, COUNTERS>> children = node.getChildren().stream();

      if (nodeComparator != null) {
        children = children.sorted(nodeComparator);
      }

      children.limit(maxChildren)
              .forEach(nodeQueue::add);
    }

    return result;
  }

  /**
   * Slices a leaf node and returns a new one.<br/>
   * <b>Important:</b> modifies parent, replacing <code>node</code> with a new sliced one.
   */
  private Node<DATA, COUNTERS> sliceLeaf(@NotNull Node<DATA, COUNTERS> node, int maxChildren, @NotNull Comparator<DATA> dataComparator) {
    // Actually, it shouldn't be the root node, so there should always be a parent, but just to be safe.
    if(!node.getScope().isLeaf() || node.getParent() == null) {
      throw new InvalidStateException("Can't slice a non-leaf.");
    }
    List<DATA> slicedData = node.getData().stream()
                                .sorted(dataComparator)
                                .limit(maxChildren)
                                .collect(Collectors.toList());

    Node<DATA, COUNTERS> slicedLeaf = new Node<>(node.getId(), node.getScope(), slicedData, node.getCounters(), node.getParent());

    node.getParent().putChild(slicedLeaf);

    return slicedLeaf;
  }

  @NotNull
  public List<Node<DATA, COUNTERS>> getTopTreeSliceUpTo(@Nullable Comparator<Node<DATA, COUNTERS>> order, @NotNull Predicate<Scope> isIncludedLevel) {
    Queue<Node<DATA, COUNTERS>> nodeQueue = new ArrayDeque<>();
    nodeQueue.add(myRoot);

    List<Node<DATA, COUNTERS>> slicedNodes = new ArrayList<>();
    while (!nodeQueue.isEmpty()) {
      Node<DATA, COUNTERS> node = nodeQueue.poll();
      if(!isIncludedLevel.test(node.getScope())) {
        continue;
      }

      slicedNodes.add(node);

      if(order != null) {
        node.getChildren().stream()
            .sorted(order)
            .forEach(nodeQueue::add);
      } else {
        nodeQueue.addAll(node.getChildren());
      }
    }

    return slicedNodes;
  }

  public static class Node<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
    @Nullable
    private final Node<DATA, COUNTERS> myParent;
    @NotNull
    private final Scope myScope;
    @NotNull
    private final List<DATA> myTestRuns;
    @NotNull
    private COUNTERS myCountersData;
    @NotNull
    private final Map<String, Node<DATA, COUNTERS>> myChildren; // id -> node
    @NotNull
    private final String myId;

    /** Construct a non-leaf node. It has zero testRuns and may contain children. */
    Node(@NotNull String id, @NotNull Scope scope, @NotNull COUNTERS counters, @Nullable Node<DATA, COUNTERS> parent) {
      myId = id;
      myParent = parent;
      myScope = scope;
      myCountersData = counters;
      myChildren = new LinkedHashMap<>();
      myTestRuns = Collections.emptyList();
    }

    /** Construct a leaf node. It is always a node of type CLASS and has zero children. */
    Node (@NotNull String id,
          @NotNull Scope scope,
          @NotNull Collection<DATA> sTestRuns,
          @NotNull COUNTERS counters,
          @Nullable Node<DATA, COUNTERS> parent) {
      myId = id;
      myParent = parent;
      myScope = scope;
      myCountersData = counters;
      myTestRuns = new ArrayList<>(sTestRuns);
      myChildren = Collections.emptyMap();
    }

    @NotNull
    public String getId() {
      return myId;
    }

    @NotNull
    public Scope getScope() {
      return myScope;
    }

    @NotNull
    public COUNTERS getCounters() {
      return myCountersData;
    }

    @NotNull
    public List<DATA> getData() {
      return myTestRuns;
    }

    @NotNull
    public Collection<Node<DATA, COUNTERS>> getChildren() {
      return myChildren.values();
    }

    @Nullable
    public Node<DATA, COUNTERS> getChild(@NotNull String childScopeName) {
      return myChildren.get(childScopeName);
    }

    @Nullable
    public Node<DATA, COUNTERS> getParent() {
      return myParent;
    }

    public void merge(@NotNull COUNTERS counters) {
      myCountersData = myCountersData.combinedWith(counters);
    }

    public void putChild(@NotNull Node<DATA, COUNTERS> child) {
      myChildren.put(child.getId(), child);
    }

    @Override
    public String toString() {
      return "Node{id=" + myId + ", scope=" + myScope + "}";
    }
  }
}