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

package jetbrains.buildServer.server.rest.model.problem.scope;

import java.util.List;
import javax.xml.bind.annotation.*;
import jetbrains.buildServer.server.rest.data.problem.TestCountersData;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeInfo;
import jetbrains.buildServer.server.rest.data.problem.tree.ScopeTree;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.TestCounters;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STestRun;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;


@XmlRootElement(name = "testScopeTree")
@XmlType(name = "testScopeTree")
@XmlSeeAlso({TestScopeTree.Node.class, TestScopeTree.Leaf.class})
public class TestScopeTree extends AbstractScopeTree<STestRun, TestCountersData, TestScopeTree.Node, TestScopeTree.Leaf> {
  public TestScopeTree() {
    super();
  }

  public TestScopeTree(@NotNull List<ScopeTree.Node<STestRun, TestCountersData>> sourceNodes,
                       @NotNull Fields fields,
                       @NotNull BeanContext context) {
    super(sourceNodes, fields, context);
  }

  @XmlElement(name = "node")
  @Override
  public List<Node> getNodes() {
    return super.getNodes();
  }

  @XmlElement(name = "leaf")
  @Override
  public List<Leaf> getLeafs() {
    return super.getLeafs();
  }

  @Override
  protected Node buildNode(@NotNull ScopeTree.Node<STestRun, TestCountersData> source, @NotNull Fields fields) {
    return new Node(source, fields);
  }

  @Override
  protected Leaf buildLeaf(@NotNull ScopeTree.Node<STestRun, TestCountersData> source, @NotNull Fields fields, @NotNull BeanContext context) {
    return new Leaf(source, fields, context);
  }

  @XmlType(name = "testTreeNode")
  public static class Node extends AbstractNode<STestRun, TestCountersData> {
    public Node() {
      super();
    }

    public Node(@NotNull ScopeTree.Node<STestRun, TestCountersData> node, @NotNull Fields fields) {
      super(node, fields);
    }

    @XmlElement(name = "testCounters")
    public TestCounters getCounters() {
      if(BooleanUtils.isNotTrue(myFields.isIncluded("testCounters"))) {
        return null;
      }

      return new TestCounters(myFields.getNestedField("testCounters"), myNode.getCounters());
    }

    @XmlAttribute(name = "type")
    public String getType() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("type"), ((TestScopeInfo) myNode.getScope()).getType().name());
    }

  }

  @XmlType(name = "testTreeLeaf")
  public static class Leaf extends AbstractLeaf<STestRun, TestCountersData> {
    public Leaf() {
      super();
    }

    public Leaf(@NotNull ScopeTree.Node<STestRun, TestCountersData> node, @NotNull Fields fields, @NotNull BeanContext beanContext) {
      super(node, fields, beanContext);
    }

    @XmlElement(name = "testOccurrences")
    public TestOccurrences getTestOccurrences() {
      if(BooleanUtils.isNotTrue(myFields.isIncluded("testOccurrences"))) {
        return null;
      }

      return new TestOccurrences(myNode.getData(), null, null, null, myFields.getNestedField("testOccurrences"), myContext);
    }
  }
}
