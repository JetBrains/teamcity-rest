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

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.function.Function;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.NodeResponsibility;
import jetbrains.buildServer.serverSide.TeamCityNode;
import org.jetbrains.annotations.NotNull;

import static java.util.stream.Collectors.toList;

@XmlRootElement(name = "enabledResponsibilities")
@ModelDescription(
  value = "Represents a set of enabled responsibilities of a TeamCity node.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/multinode-setup.html",
  externalArticleName = "Multi-node setup"
)
public class EnabledResponsibilities {
  @XmlAttribute
  public Integer count;

  @XmlElement(name = "responsibility")
  public List<Responsibility> responsibilities;

  public EnabledResponsibilities() {
  }

  public EnabledResponsibilities(@NotNull final TeamCityNode node, @NotNull final Fields fields) {
    this.responsibilities = ValueWithDefault.decideDefault(fields.isIncluded("responsibility", true), () ->
      node.getEnabledResponsibilities().stream().map(toResponsibility(fields)).collect(toList())
    );
    this.count = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("count"), Iterables.size(responsibilities));
  }

  @NotNull
  private Function<NodeResponsibility, Responsibility> toResponsibility(final @NotNull Fields fields) {
    return r -> new Responsibility(r, fields.getNestedField("responsibility", Fields.SHORT, Fields.LONG));
  }
}
