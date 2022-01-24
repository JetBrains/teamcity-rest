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
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeType;
import jetbrains.buildServer.server.rest.data.problem.tree.Scope;
import jetbrains.buildServer.server.rest.data.problem.tree.ScopeTree;
import jetbrains.buildServer.server.rest.data.problem.tree.TreeCounters;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;


public class AbstractNode<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
  protected ScopeTree.Node<DATA, COUNTERS> myNode;
  protected Fields myFields;

  public AbstractNode() {
  }

  public AbstractNode(@NotNull ScopeTree.Node<DATA, COUNTERS> node, @NotNull Fields fields) {
    myFields = fields;
    myNode = node;
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("name"), myNode.getScope().getName());
  }

  @XmlAttribute(name = "id")
  public String getId() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("id"), myNode.getId());
  }

  @XmlAttribute(name = "parentId")
  public String getParent() {
    if (BooleanUtils.isNotTrue(myFields.isIncluded("parentId")) || myNode.getParent() == null) {
      return null;
    }

    return myNode.getParent().getId();
  }

  @XmlAttribute(name = "childrenCount")
  public Integer getChildrenCount() {
    return ValueWithDefault.decideDefault(myFields.isIncluded("childrenCount"), myNode.getChildren().size());
  }
}
