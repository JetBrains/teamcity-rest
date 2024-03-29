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

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.server.rest.data.util.tree.Node;
import jetbrains.buildServer.server.rest.data.util.tree.TreeCounters;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.util.BeanContext;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;


public abstract class AbstractLeaf<DATA, COUNTERS extends TreeCounters<COUNTERS>> {
  protected BeanContext myContext;
  protected Fields myFields;
  protected Node<DATA, COUNTERS> myNode;

  public AbstractLeaf() {
  }

  public AbstractLeaf(@NotNull Node<DATA, COUNTERS> node, @NotNull Fields fields, @NotNull BeanContext beanContext) {
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