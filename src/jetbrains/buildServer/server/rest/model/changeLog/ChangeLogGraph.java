/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.changeLog;


import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.controllers.buildType.tabs.Graph;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "changeLogGraph")
public class ChangeLogGraph {
  private final Graph myGraph;

  public ChangeLogGraph(@NotNull Graph graph) {
    myGraph = graph;
  }

  @XmlElement(name = "columns")
  public List<ChangeLogGraphColumn> getColumns() {
    return myGraph.getColumns().stream().map(ChangeLogGraphColumn::new).collect(Collectors.toList());
  }
}
