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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import jetbrains.buildServer.controllers.buildType.tabs.GraphBuild;
import jetbrains.buildServer.controllers.buildType.tabs.GraphCommit;
import jetbrains.buildServer.controllers.buildType.tabs.GraphVertex;
import org.jetbrains.annotations.NotNull;

public abstract class ChangeLogGraphVertex<V extends GraphVertex> {
  private final V myVertex;

  public ChangeLogGraphVertex(V vertex) {
    myVertex = vertex;
  }

  protected V getVertex() {
    return myVertex;
  }

  @XmlAttribute(name = "id")
  public String getId() {
    return myVertex.getId();
  }

  @XmlAttribute(name = "type")
  public String getType() {
    return myVertex.getType();
  }

  @XmlAttribute(name = "description")
  public String getDescription() {
    return myVertex.getDescription();
  }

  @XmlAttribute(name = "position")
  public int getPosition() {
    return myVertex.getPosition();
  }

  @XmlElement(name = "parents")
  public List<Integer> getParents() {
    return myVertex.getParents();
  }

  @XmlElement(name = "lines")
  public List<Integer[]> getLines() {
    return myVertex.getLines().stream().map(line -> line.getPositions().toArray(new Integer[0])).collect(Collectors.toList());
  }

  public static ChangeLogGraphVertex<?> produce(@NotNull GraphVertex vertex) {
    if(vertex instanceof GraphCommit) {
      return new ChangeLogGraphChangeVertex((GraphCommit) vertex);
    }
    return new ChangeLogGraphBuildVertex((GraphBuild) vertex);
  }
}
