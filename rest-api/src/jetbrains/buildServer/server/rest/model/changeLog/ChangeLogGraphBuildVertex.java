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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.controllers.buildType.tabs.GraphBuild;
import jetbrains.buildServer.messages.Status;

@XmlType(name = "changeLogGraphBuildVertex")
public class ChangeLogGraphBuildVertex extends ChangeLogGraphVertex<GraphBuild> {
  public ChangeLogGraphBuildVertex(GraphBuild vertex) {
    super(vertex);
  }

  @XmlAttribute(name = "status")
  public String getStatus() {
    Status status = getVertex().getBuild().getBuildId() < 0 ? Status.UNKNOWN : getVertex().getBuild().getBuildStatus();
    return status.getText().toLowerCase();
  }
}
