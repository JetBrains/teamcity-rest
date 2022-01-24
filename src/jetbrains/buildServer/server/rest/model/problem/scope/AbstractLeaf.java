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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.problem.tree.ScopeTree;
import jetbrains.buildServer.server.rest.data.problem.tree.TreeCounters;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;


public abstract class AbstractLeaf<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
  protected BeanContext myContext;
  protected Fields myFields;
  protected ScopeTree.Node<DATA, COUNTERS> myNode;

  public AbstractLeaf() {
  }

  public AbstractLeaf(@NotNull ScopeTree.Node<DATA, COUNTERS> node, @NotNull Fields fields, @NotNull BeanContext beanContext) {
    myNode = node;
    myFields = fields;
    myContext = beanContext;
  }

  @XmlAttribute(name = "nodeId")
  public String getNodeId() {
    if (BooleanUtils.isNotTrue(myFields.isIncluded("nodeId"))) {
      return null;
    }

    return myNode.getId();
  }
}
