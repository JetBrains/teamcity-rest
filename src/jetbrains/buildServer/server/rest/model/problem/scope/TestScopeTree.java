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

package jetbrains.buildServer.server.rest.model.problem.scope;

import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.TestCounters;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "testScopeTree")
@XmlType(name = "testScopeTree")
public class TestScopeTree {
  private List<jetbrains.buildServer.server.rest.data.problem.scope.TestScopeTree.Node> myNodes;
  private Fields myFields;
  private BeanContext myContext;

  public TestScopeTree() { }

  public TestScopeTree(@NotNull List<jetbrains.buildServer.server.rest.data.problem.scope.TestScopeTree.Node> nodes, @NotNull Fields fields, @NotNull BeanContext context) {
    myNodes = nodes;
    myFields = fields;
    myContext = context;
  }

  @XmlElement(name = "node")
  public List<Node> getNodes() {
    if(BooleanUtils.isNotTrue(myFields.isIncluded("node"))) {
      return null;
    }

    Fields nodeFields = myFields.getNestedField("node");
    return myNodes.stream()
                  .map(node -> new Node(node, nodeFields))
                  .collect(Collectors.toList());
  }

  @XmlElement(name = "leaf")
  public List<Leaf> getLeafs() {
    if(BooleanUtils.isNotTrue(myFields.isIncluded("leaf"))) {
      return null;
    }

    Fields leafFields = myFields.getNestedField("leaf");
    return myNodes.stream()
                  .filter(node -> node.getTestRuns().size() > 0)
                  .map(node -> new Leaf(node, leafFields, myContext))
                  .collect(Collectors.toList());
  }

  @XmlRootElement(name = "node")
  @XmlType(name = "node")
  public static class Node {
    private jetbrains.buildServer.server.rest.data.problem.scope.TestScopeTree.Node myNode;
    private Fields myFields;

    public Node() {}

    public Node(@NotNull jetbrains.buildServer.server.rest.data.problem.scope.TestScopeTree.Node node, @NotNull Fields fields) {
      myFields = fields;
      myNode = node;
    }

    @XmlElement(name = "testCounters")
    public TestCounters getCounters() {
      if(BooleanUtils.isNotTrue(myFields.isIncluded("testCounters"))) {
        return null;
      }

      return new TestCounters(myFields.getNestedField("testCounters"), myNode.getCountersData());
    }

    @XmlAttribute(name = "name")
    public String getName() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("name"), myNode.getName());
    }

    @XmlAttribute(name = "id")
    public Integer getId() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("id"), myNode.getId());
    }

    @XmlAttribute(name = "type")
    public TestScopeType getType() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("type"), myNode.getType());
    }

    @XmlAttribute(name = "parentId")
    public Integer getParent() {
      if(BooleanUtils.isNotTrue(myFields.isIncluded("parentId")) || myNode.getParent() == null) {
        return null;
      }

      return myNode.getParent().getId();
    }

    @XmlAttribute(name = "childrenCount")
    public Integer getChildrenCount() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("childrenCount"), myNode.getChildren().size());
    }
  }

  @XmlRootElement(name = "leaf")
  @XmlType(name = "leaf")
  public static class Leaf {
    private BeanContext myContext;
    private Fields myFields;
    private jetbrains.buildServer.server.rest.data.problem.scope.TestScopeTree.Node myNode;

    public Leaf() {}

    public Leaf(@NotNull jetbrains.buildServer.server.rest.data.problem.scope.TestScopeTree.Node node, @NotNull Fields fields, @NotNull BeanContext beanContext) {
      myNode = node;
      myFields = fields;
      myContext = beanContext;
    }

    @XmlElement(name = "testOccurrences")
    public TestOccurrences getTestOccurrences() {
      if(BooleanUtils.isNotTrue(myFields.isIncluded("testOccurrences"))) {
        return null;
      }

      return new TestOccurrences(myNode.getTestRuns(), null, null, null, myFields.getNestedField("testOccurrences"), myContext);
    }
    @XmlAttribute(name = "nodeId")
    public Integer getNodeId() {
      if(BooleanUtils.isNotTrue(myFields.isIncluded("nodeId"))) {
        return null;
      }

      return myNode.getId();
    }
  }
}
