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
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.controllers.buildType.tabs.GraphColumn;
import org.jetbrains.annotations.NotNull;

@XmlType(name = "changeLogGraphColumn")
public class ChangeLogGraphColumn {
  private final GraphColumn myColumn;

  public ChangeLogGraphColumn(@NotNull GraphColumn column) {
    myColumn = column;
  }

  @XmlAttribute(name = "id")
  public String getId() {
    return myColumn.getId();
  }

  @XmlAttribute(name = "name")
  public String getName() {
    return myColumn.getName();
  }

  @XmlAttribute(name = "maxWidth")
  public int getMaxWidth() {
    return myColumn.getMaxWidth();
  }

  @XmlElement(name = "vertices")
  public List<ChangeLogGraphVertex<?>> getVertices() {
    return myColumn.getVertices().stream().map(ChangeLogGraphVertex::produce).collect(Collectors.toList());
  }

  @XmlElement(name = "startVertices")
  public List<Integer> getStartVertices() {
    return myColumn.getStartVertices();
  }

  @XmlElement(name = "lines")
  public List<Integer[]> getLines() {
    return myColumn.getLines().stream().map(line -> line.getPositions().toArray(new Integer[0])).collect(Collectors.toList());
  }
}
