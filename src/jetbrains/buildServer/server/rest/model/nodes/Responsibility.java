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

package jetbrains.buildServer.server.rest.model.nodes;

import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.NodeResponsibility;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "responsibility")
@ModelDescription(
  value = "Represents a single responsibility of a TeamCity node.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/multinode-setup.html",
  externalArticleName = "Multi-node setup"
)
public class Responsibility {
  @XmlAttribute public String name;
  @XmlAttribute public String description;

  public Responsibility() {
  }

  public Responsibility(@NotNull NodeResponsibility responsibility, @NotNull Fields fields) {
    name = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("name"), responsibility.name());
    description = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("description"), responsibility.getDisplayName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Responsibility that = (Responsibility)o;
    return Objects.equals(name, that.name) && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, description);
  }
}
