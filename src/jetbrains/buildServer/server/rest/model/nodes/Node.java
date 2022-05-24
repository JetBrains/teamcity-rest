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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.server.Server;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityNode;

@XmlRootElement(name = "node")
@XmlType(propOrder = {"id", "url", "online", "role", "current", "enabledResponsibilities", "effectiveResponsibilities"})
@ModelDescription(
  value = "Represents a TeamCity node.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/multinode-setup.html",
  externalArticleName = "Multi-node setup"
)
public class Node {
  @XmlAttribute public String id;
  @XmlAttribute public String url;
  @XmlAttribute public String role;
  @XmlAttribute public Boolean online;
  @XmlAttribute public Boolean current;
  @XmlElement public EnabledResponsibilities enabledResponsibilities;
  @XmlElement public EffectiveResponsibilities effectiveResponsibilities;

  public Node() {
  }

  public Node(TeamCityNode node, final Fields fields) {
    url = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("url"), node.getUrl());
    id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), node.getId());
    role = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("role"), Server.nodeRole(node));
    online = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("online"), node.isOnline());
    current = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("current"), node.isCurrent());

    enabledResponsibilities = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("enabledResponsibilities", false), new ValueWithDefault.Value<EnabledResponsibilities>() {
      public EnabledResponsibilities get() {
        final Fields nestedFields = fields.getNestedField("enabledResponsibilities", Fields.NONE, Fields.LONG);
        return new EnabledResponsibilities(node, nestedFields);
      }
    });

    effectiveResponsibilities = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("effectiveResponsibilities", false), new ValueWithDefault.Value<EffectiveResponsibilities>() {
      public EffectiveResponsibilities get() {
        final Fields nestedFields = fields.getNestedField("effectiveResponsibilities", Fields.NONE, Fields.LONG);
        return new EffectiveResponsibilities(node, nestedFields);
      }
    });
  }
}
