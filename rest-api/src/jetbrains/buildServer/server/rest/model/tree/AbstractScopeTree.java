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

package jetbrains.buildServer.server.rest.model.tree;

import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.util.tree.Node;
import jetbrains.buildServer.server.rest.data.util.tree.TreeCounters;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;


public abstract class AbstractScopeTree<DATA, COUNTERS extends TreeCounters<COUNTERS>, N extends AbstractNode<DATA, COUNTERS>, L extends AbstractLeaf<DATA, COUNTERS>> {
  private List<Node<DATA, COUNTERS>> myNodes;
  private Fields myFields;
  private BeanContext myContext;

  public AbstractScopeTree() { }

  public AbstractScopeTree(@NotNull List<Node<DATA, COUNTERS>> sourceNodes, @NotNull Fields fields, @NotNull BeanContext context) {
    myNodes = sourceNodes;
    myFields = fields;
    myContext = context;
  }

  protected abstract N buildNode(@NotNull Node<DATA, COUNTERS> source, @NotNull Fields fields);
  protected abstract L buildLeaf(@NotNull Node<DATA, COUNTERS> source, @NotNull Fields fields, @NotNull BeanContext context);

  public List<N> getNodes() {
    if(BooleanUtils.isNotTrue(myFields.isIncluded("node"))) {
      return null;
    }

    Fields nodeFields = myFields.getNestedField("node");
    return myNodes.stream()
                  .map(node -> buildNode(node, nodeFields))
                  .collect(Collectors.toList());
  }

  public List<L> getLeafs() {
    if(BooleanUtils.isNotTrue(myFields.isIncluded("leaf"))) {
      return null;
    }

    Fields leafFields = myFields.getNestedField("leaf");
    return myNodes.stream()
                  .filter(node -> node.getData().size() > 0)
                  .map(node -> buildLeaf(node, leafFields, myContext))
                  .collect(Collectors.toList());
  }
}