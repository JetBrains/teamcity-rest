/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.problem.scope;

import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.problem.Orders;
import jetbrains.buildServer.server.rest.data.problem.SortTestRunsByNewComparator;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestScopeTree {
  public static final Orders<Node> SUPPORTED_ORDERS = new Orders<Node>()
    .add("name", Comparator.comparing(node -> node.getName()))
    .add("duration", Comparator.comparing(node -> node.getCountersData().getDuration()))
    .add("count", Comparator.comparing(node -> node.getCountersData().getCount()))
    .add("childrenCount", Comparator.comparing(node -> node.getChildren().size()));

  private final Map<Integer, Node> myTree = new HashMap<>();
  private final Node myHead;
  private int myNodeIdCounter = 0;
  private final SortTestRunsByNewComparator myNewTestRunsFirstComparator = new SortTestRunsByNewComparator();

  public TestScopeTree(@NotNull Iterable<TestScope> testScopes) {
    myHead = new Node(SProject.ROOT_PROJECT_ID, TestScopeType.PROJECT, new TestCountersData(0, 0, 0, 0, 0, 0, 0l), null);
    myTree.put(myHead.getId(), myHead);

    buildTree(testScopes);
  }

  private void buildTree(@NotNull Iterable<TestScope> testScopes) {
    testScopes.forEach(scope -> {
      SBuildType bt = scope.getBuildType();
      if(bt == null || scope.getPackage() == null || scope.getClass1() == null) {
        // TODO: should not be possible in a first place
        throw new RuntimeException("Can't build a tree. Scopes must contain a build type, package and class.");
      }
      TestCountersData countersData = scope.getOrCalcCountersData();

      // Sum counters data in all parent nodes and create such nodes if they are absent
      Node parent = myTree.get(myHead.getId());
      parent.mergeCountersData(countersData);

      List<SProject> ancestors = bt.getProject().getProjectPath();
      for(int i = 1; i < ancestors.size(); i++) {
        String ancestorId = ancestors.get(i).getExternalId();

        parent = getOrCreateNode(countersData, parent, ancestorId, TestScopeType.PROJECT);
      }

      parent = getOrCreateNode(countersData, parent, bt.getExternalId(), TestScopeType.BUILD_TYPE);
      parent = getOrCreateNode(countersData, parent, scope.getSuite(), TestScopeType.SUITE);
      parent = getOrCreateNode(countersData, parent, scope.getPackage(), TestScopeType.PACKAGE);

      // Leaf node of type CLASS does not exist, we always need to create it
      Node leaf = new Node(scope.getClass1(), scope.getTestRuns(), countersData, parent);
      parent.getChildren().put(leaf.getName(), leaf);
      myTree.put(leaf.getId(), leaf);
    });
  }

  @NotNull
  public List<Node> getSlicedOrderedTree(int maxChildren, @Nullable Comparator<Node> order) {
    Queue<Node> nodeQueue = new ArrayDeque<>(maxChildren + 1);
    nodeQueue.add(myHead);

    List<Node> slicedNodes = new ArrayList<>();

    while (!nodeQueue.isEmpty()) {
      Node top = nodeQueue.poll();

      if(top.getTestRuns().size() > maxChildren) {
        // this is a leaf node and there are more children then asked, so let's create a replacement node with less testRuns
        // also is is not a root node, so there always is a parent
        Map<String, Node> parentChildren = top.getParent().getChildren();

        List<STestRun> testRuns = top.getTestRuns().stream()
                                     .sorted(myNewTestRunsFirstComparator)
                                     .limit(maxChildren)
                                     .collect(Collectors.toList());;

        Node replacement = new Node(top.getName(), testRuns, top.getCountersData(), top.getParent());
        parentChildren.put(top.getName(), replacement);

        top = replacement;
      }
      slicedNodes.add(top);

      List<Node> sortedChildren = new ArrayList<>(top.getChildren().values());
      if(order != null)
        sortedChildren.sort(order);

      nodeQueue.addAll(sortedChildren.subList(0, Math.min(maxChildren, sortedChildren.size())));
    }

    return slicedNodes;
  }

  /**
   * Return all projects and build types in the tree, exclude everything else.
   */
  @NotNull
  public List<Node> getTopTreeSliceUpToBuildTypes(@Nullable Comparator<Node> order) {
    Queue<Node> nodeQueue = new ArrayDeque<>();
    nodeQueue.add(myHead);

    List<Node> slicedNodes = new ArrayList<>();
    while (!nodeQueue.isEmpty()) {
      Node top = nodeQueue.poll();
      slicedNodes.add(top);

      if(top.myType == TestScopeType.PROJECT) {
        if(order != null) {
          top.myChildren.values()
                        .stream()
                        .sorted(order)
                        .forEach(nodeQueue::add);
        } else {
          nodeQueue.addAll(top.myChildren.values());
        }
      }
    }

    return slicedNodes;
  }

  @NotNull
  private Node getOrCreateNode(@NotNull TestCountersData countersData, @NotNull Node parent, @NotNull String name, @NotNull TestScopeType type) {
    Node node = parent.getChildren().get(name);

    if(node != null) {
      node.mergeCountersData(countersData);
    } else {
      node = new Node(name, type, countersData, parent);
      parent.getChildren().put(name, node);
      myTree.put(node.getId(), node);
    }
    return node;
  }

  public class Node {
    @Nullable
    private final Node myParent;
    @NotNull
    private final TestScopeType myType;
    @NotNull
    private final List<STestRun> myTestRuns;
    @NotNull
    private TestCountersData myCountersData;
    @NotNull
    private final Map<String, Node> myChildren; // name -> node, name is unique across children
    @NotNull
    private final String myName;

    private final int myId = myNodeIdCounter++;

    /** Construct a non-leaf node. It has zero testRuns and may contain children. */
    Node(@NotNull String name, @NotNull TestScopeType type, @NotNull TestCountersData countersData, @Nullable Node parent) {
      myParent = parent;
      myType = type;
      myCountersData = countersData;
      myName = name;
      myChildren = new HashMap<>();
      myTestRuns = Collections.emptyList();
    }

    /** Construct a leaf node. It is always a node of type CLASS and has zero children. */
    Node(@NotNull String name, @NotNull List<STestRun> testRuns, @NotNull TestCountersData countersData, @NotNull Node parent) {
      myParent = parent;
      myType = TestScopeType.CLASS;
      myCountersData = countersData;
      myName = name;
      myTestRuns = testRuns;
      myChildren = Collections.emptyMap();
    }

    public void mergeCountersData(@NotNull TestCountersData additionalData) {
      Integer passed    = (myCountersData.getPassed() != null && additionalData.getPassed() != null) ? myCountersData.getPassed() + additionalData.getPassed() : null;
      Integer failed    = (myCountersData.getFailed() != null && additionalData.getFailed() != null) ? myCountersData.getFailed() + additionalData.getFailed() : null;
      Integer ignored   = (myCountersData.getIgnored() != null && additionalData.getIgnored() != null) ? myCountersData.getIgnored() + additionalData.getIgnored() : null;
      Integer muted     = (myCountersData.getMuted() != null && additionalData.getMuted() != null) ? myCountersData.getMuted() + additionalData.getMuted() : null;
      Integer newFailed = (myCountersData.getNewFailed() != null && additionalData.getNewFailed() != null) ? myCountersData.getNewFailed() + additionalData.getNewFailed() : null;
      Long    duration  = (myCountersData.getDuration() != null && additionalData.getDuration() != null) ? myCountersData.getDuration() + additionalData.getDuration() : null;

      myCountersData = new TestCountersData(
        myCountersData.getCount() + additionalData.getCount(),
        passed,
        failed,
        muted,
        ignored,
        newFailed,
        duration
      );
    }

    @NotNull
    public List<STestRun> getTestRuns() {
      return myTestRuns;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    public int getId() {
      return myId;
    }

    @NotNull
    public TestScopeType getType() {
      return myType;
    }

    @Nullable
    public Node getParent() {
      return myParent;
    }

    @NotNull
    public TestCountersData getCountersData() {
      return myCountersData;
    }

    @NotNull
    public Map<String, Node> getChildren() {
      return myChildren;
    }
  }
}
