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

import java.util.*;
import jetbrains.buildServer.server.rest.data.problem.tree.LeafInfo;
import jetbrains.buildServer.server.rest.data.problem.tree.Scope;
import jetbrains.buildServer.server.rest.data.problem.tree.ScopeTree;
import jetbrains.buildServer.server.rest.data.problem.tree.TreeCounters;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.testng.annotations.Test;

@Test
public class ScopeTreeTest {

  public void testSimpleTree() {
    MyScope rootScope = new MyScope("ROOT",  false);
    MyScope child1 = new MyScope("C1",  false);
    MyScope child2 = new MyScope("C2",  false);

    List<MyLeaf> leafs = Arrays.asList(
      new MyLeaf(
        Arrays.asList(rootScope, child1, new MyScope("L1", true)),
        Arrays.asList(1, 2, 3)
      ),
      new MyLeaf(
        Arrays.asList(rootScope, child1, new MyScope("L2", true)),
        Arrays.asList(4, 5, 6)
      ),
      new MyLeaf(
        Arrays.asList(rootScope, child2, new MyScope("L3", true)),
        Arrays.asList(7, 8, 9)
      )
    );

    /*
           ROOT
        C1      C2
      L1  L2      L3
     */

    ScopeTree<Integer, Counters> tree = new ScopeTree<>(rootScope, new Counters(0), leafs);
    Map<String, ScopeTree.Node<Integer, Counters>> nodes = new HashMap<>();

    for(ScopeTree.Node<Integer, Counters> node : tree.getTopTreeSliceUpTo((n1, n2) -> Integer.compare(n1.hashCode(), n2.hashCode()), scope -> true)) {
      nodes.put(node.getId(), node);
    }
    Assert.assertEquals(6, nodes.size());

    Assert.assertEquals(9, nodes.get("ROOT").getCounters().myValue);

    Assert.assertEquals(6, nodes.get("C1").getCounters().myValue);
    Assert.assertEquals(3, nodes.get("C2").getCounters().myValue);

    Assert.assertEquals(3, nodes.get("L1").getCounters().myValue);
    Assert.assertEquals(3, nodes.get("L2").getCounters().myValue);
    Assert.assertEquals(3, nodes.get("L3").getCounters().myValue);
  }

  public void testNonOverlappingMerge() {
    MyScope rootScope1 = new MyScope("ROOT",  false);
    MyScope l1Scope = new MyScope("LEAF1", true);

    MyScope rootScope2 = new MyScope("ROOT",  false);
    MyScope l2Scope = new MyScope("LEAF2", true);

    List<MyLeaf> leafs1 = Arrays.asList(new MyLeaf(Arrays.asList(rootScope1, l1Scope), Arrays.asList(1, 2, 3)));
    List<MyLeaf> leafs2 = Arrays.asList(new MyLeaf(Arrays.asList(rootScope2, l2Scope), Arrays.asList(4, 5)));

    ScopeTree<Integer, Counters> tree1 = new ScopeTree<>(rootScope1, new Counters(0), leafs1);
    ScopeTree<Integer, Counters> tree2 = new ScopeTree<>(rootScope2, new Counters(0), leafs2);

    tree1.merge(tree2);


    Map<String, ScopeTree.Node<Integer, Counters>> nodes = new HashMap<>();
    for(ScopeTree.Node<Integer, Counters> node : tree1.getTopTreeSliceUpTo((n1, n2) -> Integer.compare(n1.hashCode(), n2.hashCode()), scope -> true)) {
      nodes.put(node.getId(), node);
    }
    Assert.assertEquals(3, nodes.size());
    Assert.assertEquals(5, nodes.get("ROOT").getCounters().myValue);
  }

  public void testOverlappingMerge() {
    /* First tree
           ROOT
        C1      C2
      L1  L2  L3  L4
       3   3   3   3
     */
    MyScope rootScope = new MyScope("ROOT",  false);
    MyScope c1 = new MyScope("C1",  false);
    MyScope c21 = new MyScope("C2",  false);
    List<MyLeaf> leafs = Arrays.asList(
      new MyLeaf(
        Arrays.asList(rootScope, c1, new MyScope("L1", true)),
        Arrays.asList(1, 2, 3)
      ),
      new MyLeaf(
        Arrays.asList(rootScope, c1, new MyScope("L2", true)),
        Arrays.asList(4, 5, 6)
      ),
      new MyLeaf(
        Arrays.asList(rootScope, c21, new MyScope("L3", true)),
        Arrays.asList(7, 8, 9)
      ),
      new MyLeaf(
        Arrays.asList(rootScope, c21, new MyScope("L4", true)),
        Arrays.asList(7, 8, 9)
      )
    );
    ScopeTree<Integer, Counters> tree = new ScopeTree<>(rootScope, new Counters(0), leafs);


    /* Second tree
           ROOT
        C2      C3
          L4  L5  L6
           3   3   3
     */
    MyScope rootScope2 = new MyScope("ROOT",  false);
    MyScope c22 = new MyScope("C2",  false);
    MyScope c3 = new MyScope("C3",  false);

    List<MyLeaf> leafs2 = Arrays.asList(
      new MyLeaf(
        Arrays.asList(rootScope2, c22, new MyScope("L4", true)),
        Arrays.asList(1, 2, 3)
      ),
      new MyLeaf(
        Arrays.asList(rootScope2, c3, new MyScope("L5", true)),
        Arrays.asList(4, 5, 6)
      ),
      new MyLeaf(
        Arrays.asList(rootScope2, c3, new MyScope("L6", true)),
        Arrays.asList(7, 8, 9)
      )
    );
    ScopeTree<Integer, Counters> tree2 = new ScopeTree<>(rootScope, new Counters(0), leafs2);


    /* When merged:
               ROOT
           /     |    \
        C1      C2      C3
       / \      / \    / \
      L1  L2  L3  L4  L5  L6
       3   3   3   6   3   3
     */
    tree.merge(tree2);


    Map<String, ScopeTree.Node<Integer, Counters>> nodes = new HashMap<>();

    for(ScopeTree.Node<Integer, Counters> node : tree.getTopTreeSliceUpTo((n1, n2) -> Integer.compare(n1.hashCode(), n2.hashCode()), scope -> true)) {
      nodes.put(node.getId(), node);
    }
    Assert.assertEquals(10, nodes.size());

    Assert.assertEquals(21, nodes.get("ROOT").getCounters().myValue);
    Assert.assertEquals(6, nodes.get("C1").getCounters().myValue);
    Assert.assertEquals(9, nodes.get("C2").getCounters().myValue);
    Assert.assertEquals(6, nodes.get("C3").getCounters().myValue);

    Assert.assertEquals(3, nodes.get("L1").getCounters().myValue);
    Assert.assertEquals(3, nodes.get("L2").getCounters().myValue);

    Assert.assertEquals(3, nodes.get("L3").getCounters().myValue);
    Assert.assertEquals("3 come from the first tree + 3 come from the second tree",
                        6, nodes.get("L4").getCounters().myValue);

    Assert.assertEquals(3, nodes.get("L5").getCounters().myValue);
    Assert.assertEquals(3, nodes.get("L6").getCounters().myValue);
  }

  private class MyLeaf implements LeafInfo<Integer, Counters> {
    private final List<Scope> myPath;
    private final List<Integer> myData;

    public MyLeaf(List<Scope> path, List<Integer> data) {
      myPath = path;
      myData = data;
    }

    @NotNull
    @Override
    public Counters getCounters() {
      return new Counters(myData.size());
    }

    @NotNull
    @Override
    public Iterable<Scope> getPath() {
      return myPath;
    }

    @NotNull
    @Override
    public Collection<Integer> getData() {
      return myData;
    }
  }

  private class MyScope implements Scope {
    private final String myId;
    private final boolean myIsLeaf;

    public MyScope(@NotNull String id, boolean isLeaf) {
      myId = id;
      myIsLeaf = isLeaf;
    }

    @NotNull
    @Override
    public String getName() {
      return myId;
    }

    @NotNull
    @Override
    public String getId() {
      return myId;
    }

    @Override
    public boolean isLeaf() {
      return myIsLeaf;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyScope myScope = (MyScope)o;

      if (myIsLeaf != myScope.myIsLeaf) return false;
      return myId.equals(myScope.myId);
    }

    @Override
    public int hashCode() {
      int result = myId.hashCode();
      result = 31 * result + (myIsLeaf ? 1 : 0);
      return result;
    }
  }

  private class Counters implements TreeCounters<Counters> {
    private final int myValue;

    public Counters(int c) {
      myValue = c;
    }

    @Override
    public Counters combinedWith(@NotNull Counters other) {
      return new Counters(myValue + other.myValue);
    }
  }
}
