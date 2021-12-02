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
import javax.xml.bind.annotation.*;
import jetbrains.buildServer.server.rest.data.problem.scope.ProblemOccurrencesTreeCollector;
import jetbrains.buildServer.server.rest.data.problem.tree.ScopeTree;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.problem.ProblemOccurrences;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import org.jetbrains.annotations.NotNull;


@XmlRootElement(name = "problemOccurrencesTree")
@XmlType(name = "problemOccurrencesTree")
@XmlSeeAlso({ProblemOccurrencesTree.Node.class, ProblemOccurrencesTree.BuildProblemTypeNode.class, ProblemOccurrencesTree.Leaf.class})
public class ProblemOccurrencesTree extends AbstractScopeTree<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters, ProblemOccurrencesTree.Node, ProblemOccurrencesTree.Leaf> {
  public ProblemOccurrencesTree() {
    super();
  }

  public ProblemOccurrencesTree(@NotNull List<ScopeTree.Node<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters>> sourceNodes,
                                @NotNull Fields fields, @NotNull BeanContext context) {
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
  protected Node buildNode(@NotNull ScopeTree.Node<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters> source, @NotNull Fields fields) {
    if(source.getScope().isLeaf()) {
      return new BuildProblemTypeNode(source, fields);
    }
    return new Node(source, fields);
  }

  @Override
  protected Leaf buildLeaf(@NotNull ScopeTree.Node<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters> source, @NotNull Fields fields, @NotNull BeanContext context) {
    return new Leaf(source, fields, context);
  }

  @XmlType(name = "problemTreeNode")
  public static class Node extends AbstractNode<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters> {
    public Node() {
      super();
    }

    public Node(@NotNull ScopeTree.Node<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters> node, @NotNull Fields fields) {
      super(node, fields);
    }

    @XmlAttribute(name = "count")
    public Integer getCount() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("count"), myNode.getCounters().getCount());
    }

    @XmlAttribute(name = "newFailedCount")
    public Integer getNewFailureCount() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("newFailedCount"), myNode.getCounters().getNewFailed());
    }

    @XmlAttribute(name = "type")
    public String getType() {
      return ValueWithDefault.decideDefault(myFields.isIncluded("type"), ((ProblemOccurrencesTreeCollector.ProblemScope) myNode.getScope()).getType().name());
    }
  }

  @XmlType(name = "buildProblemNode")
  public static class BuildProblemTypeNode extends Node {
    public BuildProblemTypeNode() {
      super();
    }

    public BuildProblemTypeNode(@NotNull ScopeTree.Node<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters> node, @NotNull Fields fields) {
      super(node, fields);
    }

    @XmlAttribute(name = "name")
    @Override
    public String getName() {
      return ValueWithDefault.decideDefault(
        myFields.isIncluded("name"),
        () -> {
          return myNode.getData().get(0).getTypeDescription();
        }
      );
    }
  }

  @XmlType(name = "problemTreeLeaf")
  public static class Leaf extends AbstractLeaf<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters> {
    public Leaf() {
      super();
    }

    public Leaf(@NotNull ScopeTree.Node<BuildProblem, ProblemOccurrencesTreeCollector.ProblemCounters> node,
                @NotNull Fields fields,
                @NotNull BeanContext beanContext) {
      super(node, fields, beanContext);
    }

    @XmlElement(name = "problemOccurrences")
    public ProblemOccurrences getProblemOccurrences() {
      return ValueWithDefault.decideDefault(
        myFields.isIncluded("problemOccurrences", false, true),
        () -> new ProblemOccurrences(myNode.getData(), null, null, myFields.getNestedField("problemOccurrences"), myContext)
      );
    }
  }
}
