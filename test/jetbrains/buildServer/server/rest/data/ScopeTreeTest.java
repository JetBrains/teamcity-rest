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
     /*
           ROOT
        C1      C2
      L1  L2      L3
     */
    ScopeTree<Integer, Counters> tree = buildTree(
      MyLeaf.at("ROOT", "C1", "L1").withData(1, 2, 3),
      MyLeaf.at("ROOT", "C1", "L2").withData(4, 5, 6),
      MyLeaf.at("ROOT", "C2", "L3").withData(7, 8, 9)
    );
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
    ScopeTree<Integer, Counters> tree1 = buildTree(
      MyLeaf.at("ROOT", "LEAF1").withData(1, 2, 3)
    );
    ScopeTree<Integer, Counters> tree2 = buildTree(
      MyLeaf.at("ROOT", "LEAF2").withData(4, 5)
    );

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
    ScopeTree<Integer, Counters> tree = buildTree(
      MyLeaf.at("ROOT", "C1", "L1").withData(1, 2, 3),
      MyLeaf.at("ROOT", "C1", "L2").withData(4, 5, 6),
      MyLeaf.at("ROOT", "C2", "L3").withData(7, 8, 9),
      MyLeaf.at("ROOT", "C2", "L4").withData(7, 8, 9)
    );

    /* Second tree
           ROOT
        C2      C3
          L4  L5  L6
           3   3   3
     */
    ScopeTree<Integer, Counters> tree2 = buildTree(
      MyLeaf.at("ROOT", "C2", "L4").withData(1, 2, 3),
      MyLeaf.at("ROOT", "C3", "L5").withData(4, 5, 6),
      MyLeaf.at("ROOT", "C3", "L6").withData(7, 8, 9)
    );


    /* When merged we expect:
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

  private ScopeTree<Integer, Counters> buildTree(MyLeaf... leafs) {
    MyScope rootScope = (MyScope) leafs[0].getPath().iterator().next();

    return new ScopeTree<>(rootScope, new Counters(0), Arrays.asList(leafs));
  }

  private static class MyLeaf implements LeafInfo<Integer, Counters> {
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

    public static Builder at(String... path) {
      return new Builder(path);
    }
    private static class Builder {
      private final List<Scope> myPath;
      private Builder(String... path) {
        myPath = new ArrayList<>();
        for(int i = 0; i < path.length - 1; i++) {
          myPath.add(new MyScope(path[i], false));
        }
        myPath.add(new MyScope(path[path.length - 1], true));
      }

      public MyLeaf withData(Integer... data) {
        return new MyLeaf(myPath, Arrays.asList(data));
      }
    }
  }

  private static class MyScope implements Scope {
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

  private static class Counters implements TreeCounters<Counters> {
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
