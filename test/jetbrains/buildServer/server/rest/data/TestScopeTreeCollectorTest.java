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

package jetbrains.buildServer.server.rest.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeInfo;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeTreeCollector;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeType;
import jetbrains.buildServer.server.rest.data.problem.tree.ScopeTree;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestScopeTreeCollectorTest extends BaseTestScopesCollectorTest {
  private TestScopeTreeCollector myTestScopeTreeCollector;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myTestScopeTreeCollector = new TestScopeTreeCollector(myTestScopesCollector, myTestOccurrenceFinder);
  }

  @Test
  public void testFirstChildrenSliceOrderedByName() {
    buildTree();

    List<String> result = getNodeNames(myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:1,orderBy:name")));

    assertSameElements(result, "_Root", "project", "project1", "project1_Buildconf1", "build", "suite1: ", "packageA", "class1");
  }

  @Test
  public void testFirstChildrenSliceOrderedByCountDesc() {
    buildTree();

    List<String> result = getNodeNames(myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:1,orderBy:count:desc")));

    assertSameElements(result, "_Root", "project", "project2", "subproject21", "subproject21_Buildconf1", "build", "suite2: ", "packageC", "class2");
  }

  @Test
  public void testSliceOrderedByCountDesc() {
    buildTree();

    List<String> result = getNodeNames(myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:1,orderBy:name:desc")));

    assertSameElements(result, "_Root", "project", "project2", "subproject21", "subproject21_Buildconf1", "build", "suite2: ", "packageC", "class2");
  }

  @Test
  public void testFullTree() {
    buildTree();
    List<ScopeTree.Node<STestRun, TestCountersData>> result = myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:100"));

    assertEquals("29 nodes in a tree + _Root", 30, result.size()); // 26 nodes + 3 builds
    assertEquals("Root must always be first", "_Root", result.get(0).getScope().getName());

    checkAncestorsBeforeChildren(result);
  }

  @Test
  public void testSliceUpToBuildType() {
    buildTree();
    List<ScopeTree.Node<STestRun, TestCountersData>> result = myTestScopeTreeCollector.getTopSlicedTree(Locator.locator("build:(affectedProject:project)"));

    assertEquals("5 projects + 3 buildTypes in a tree + _Root", 9, result.size());
    checkAncestorsBeforeChildren(result);
  }

  @Test
  public void testCountersAreCorrect1() {
    buildTree();

    List<ScopeTree.Node<STestRun, TestCountersData>> result = myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:100"));

    assertEquals(new Integer(14), result.get(0).getCounters().getCount());
    checkAncestorsBeforeChildren(result);
  }

  @Test
  public void testCountersAreCorrect2() {
    buildTree();

    List<ScopeTree.Node<STestRun, TestCountersData>> result = myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:subproject21),maxChildren:100"));
    checkAncestorsBeforeChildren(result);

    List<Integer> expectedCounters = Arrays.asList(
      8, 8, 8, 8, 8, 8, // _Root, project2, subproject21, buildconf1, build
      1, 1, 1,       // suite1, packageB, class1
      6, 6, 6,       // suite2, packageC, class2
      1, 1, 1        // suite0, packageZ, classZ
    );
    List<Integer> resultCounters = result.stream().map(node -> node.getCounters().getCount()).collect(Collectors.toList());

    assertSameElements(resultCounters, expectedCounters);
  }

  @Test
  public void testCountersAreCorrectWithMaxChildren() {
    buildTree();

    List<ScopeTree.Node<STestRun, TestCountersData>> result = myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:1,orderBy:count:desc"));

    assertEquals("Root (and all other nodes) must contain counters which were correct **before** tree cutting.", new Integer(14), result.get(0).getCounters().getCount());
    checkAncestorsBeforeChildren(result);
  }

  @Test
  public void testDataSorting() {
    buildTree();

    List<ScopeTree.Node<STestRun, TestCountersData>> result = myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:3"));
    checkAncestorsBeforeChildren(result);

    ScopeTree.Node<STestRun, TestCountersData> nodeOfInterest1 = null;
    ScopeTree.Node<STestRun, TestCountersData> nodeOfInterest2 = null;
    for(ScopeTree.Node<STestRun, TestCountersData> node : result) {
      if(node.getScope().isLeaf() && node.getData().size() == 3 && node.getScope().getName().equals("class1")) {
        nodeOfInterest1 = node;
      }

      if(node.getScope().isLeaf() && node.getData().size() == 3 && node.getScope().getName().equals("class2")) {
        nodeOfInterest2 = node;
      }
    }

    assertNotNull(nodeOfInterest1);
    assertNotNull(nodeOfInterest2);

    List<String> names1 = Arrays.asList("a", "b", "c");
    List<String> names2 = Arrays.asList("c", "d", "e");

    List<String> result1 = nodeOfInterest1.getData().stream().map(tr -> tr.getTest().getName().getTestMethodName()).collect(Collectors.toList());
    List<String> result2 = nodeOfInterest2.getData().stream().map(tr -> tr.getTest().getName().getTestMethodName()).collect(Collectors.toList());

    assertSameElements(result1, names1);
    assertSameElements(result2, names2);
  }

  @Test
  public void testSubtreeHasCorrectNodes() {
    buildTree();

    List<ScopeTree.Node<STestRun, TestCountersData>> fullTree = myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:100"));
    checkAncestorsBeforeChildren(fullTree);

    // Let's find node:  _Root -> project -> project2 -> subproject21
    int subTreeRootIdx = -1;
    for(int i = 0; i < fullTree.size(); i++) {
      if(fullTree.get(i).getScope().getName().equals("subproject21")) {
        subTreeRootIdx = i;
        break;
      }
    }
    assertTrue(subTreeRootIdx != -1);

    ScopeTree.Node<STestRun, TestCountersData> subTreeRoot = fullTree.get(subTreeRootIdx);

    String subTreeLocator = String.format("build:(affectedProject:project),subTreeRootId:%s,maxChildren:100", subTreeRoot.getId());
    List<ScopeTree.Node<STestRun, TestCountersData>> subTree = myTestScopeTreeCollector.getSlicedTree(Locator.locator(subTreeLocator));
    checkAncestorsBeforeChildren(subTree, subTreeRoot.getParent().getId());
    assertEquals(
      "Subtree must contain following 12 nodes: subproject21, buildconf1, build, suite2, suite0, suite1, packageC, packageZ, packageB, class2, classZ, class1",
      12,
      subTree.size()
    );

    List<String> fullTreeNodeIds = fullTree.stream().map(node -> node.getId()).collect(Collectors.toList());
    int lastIdx = subTreeRootIdx - 1;
    for (ScopeTree.Node<STestRun, TestCountersData> subTreeNode : subTree) {
      int idx = fullTreeNodeIds.indexOf(subTreeNode.getId());

      assertTrue("Subtree nodes must be in the same order as in a full tree", idx > lastIdx);
      assertEquals("Nodes with the same id must be the same.", fullTree.get(idx).getScope(), subTreeNode.getScope());

      lastIdx = idx;
    }

    assertEquals("There must not be duplicates", subTree.size(), subTree.stream().map(node -> node.getId()).distinct().count());
  }

  @Test
  public void testSubtreeLeafNode() {
    buildTree();

    List<ScopeTree.Node<STestRun, TestCountersData>> fullTree = myTestScopeTreeCollector.getSlicedTree(Locator.locator("build:(affectedProject:project),maxChildren:100"));
    final String leafNodeId = fullTree.stream()
                                      .filter(node -> node.getScope().getName().equals("classZ"))
                                      .map(node -> node.getId())
                                      .findFirst().get();

    final Locator leafNodeLocator = Locator.locator(String.format("build:(affectedProject:project),subTreeRootId:%s,maxChildren:100", leafNodeId));
    List<ScopeTree.Node<STestRun, TestCountersData>> result = myTestScopeTreeCollector.getSlicedTree(leafNodeLocator);

    assertEquals(1, result.size());
    assertEquals(1, result.get(0).getData().size());
    assertEquals("classZ", result.get(0).getScope().getName());
  }

  @NotNull
  private List<String> getNodeNames(@NotNull List<ScopeTree.Node<STestRun, TestCountersData>> tree) {
    return tree.stream()
               .map(node -> {
                  TestScopeType scopeType = ((TestScopeInfo) node.getScope()).getType();
                  if(scopeType.equals(TestScopeType.BUILD)) return "build";
                  return node.getScope().getName();
               })
               .collect(Collectors.toList());
  }

  private void checkAncestorsBeforeChildren(@NotNull List<ScopeTree.Node<STestRun, TestCountersData>> result) {
    checkAncestorsBeforeChildren(result, null);
  }

  private void checkAncestorsBeforeChildren(@NotNull List<ScopeTree.Node<STestRun, TestCountersData>> result, @Nullable String seenParent) {
    Set<String> seenNodes = new HashSet<>();
    if(seenParent != null) {
      seenNodes.add(seenParent);
    }

    for(ScopeTree.Node<STestRun, TestCountersData> node : result) {
      if(node.getParent() != null) {
        assertTrue(
          String.format("Parent must always come before children. %s was returned before it's parent.", node),
          seenNodes.contains(node.getParent().getId())
        );
      }

      seenNodes.add(node.getId());
    }
  }
}
