/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.util.tree.Node;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.TestCounters;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.model.tree.AbstractLeaf;
import jetbrains.buildServer.server.rest.model.tree.AbstractNode;
import jetbrains.buildServer.server.rest.model.tree.AbstractScopeTree;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.STestRun;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;


@XmlRootElement(name = "testScopeTree")
@XmlType(name = "testScopeTree")
@XmlSeeAlso({TestScopeTree.NodeImpl.class, TestScopeTree.Leaf.class})
public class TestScopeTree extends AbstractScopeTree<STestRun, TestCountersData, TestScopeTree.NodeImpl, TestScopeTree.Leaf> {
  public TestScopeTree() {
    super();
  }

  public TestScopeTree(@NotNull List<Node<STestRun, TestCountersData>> sourceNodes,
                       @NotNull Fields fields,
                       @NotNull BeanContext context) {
    super(sourceNodes, fields, context);
  }

  @XmlElement(name = "node")
  @Override
  public List<NodeImpl> getNodes() {
    return super.getNodes();
  }

  @XmlElement(name = "leaf")
  @Override
  public List<Leaf> getLeafs() {
    return super.getLeafs();
  }

  @Override
  protected NodeImpl buildNode(@NotNull Node<STestRun, TestCountersData> source, @NotNull Fields fields) {
    return new NodeImpl(source, fields);
  }

  @Override
  protected Leaf buildLeaf(@NotNull Node<STestRun, TestCountersData> source, @NotNull Fields fields, @NotNull BeanContext context) {
    return new Leaf(source, fields, context);
  }

  @XmlType(name = "testTreeNode")
  public static class NodeImpl extends AbstractNode<STestRun, TestCountersData> {
    public NodeImpl() {
      super();
    }

    public NodeImpl(@NotNull Node<STestRun, TestCountersData> node, @NotNull Fields fields) {
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

    public Leaf(@NotNull Node<STestRun, TestCountersData> node, @NotNull Fields fields, @NotNull BeanContext beanContext) {
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