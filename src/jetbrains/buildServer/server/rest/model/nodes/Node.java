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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityNode;

@XmlRootElement(name = "nodes")
@XmlType(propOrder = {"id", "url", "online", "main", "current"})
@ModelDescription(
  value = "Represents a TeamCity node.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/multinode-setup.html",
  externalArticleName = "Multi-node setup"
)
public class Node {
  @XmlAttribute public String id;
  @XmlAttribute public String url;
  @XmlAttribute public Boolean main;
  @XmlAttribute public Boolean online;
  @XmlAttribute public Boolean current;

  public Node(TeamCityNode node, final Fields fields) {
    url = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("url"), node.getUrl());
    id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), node.getId());
    main = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("mainNode"), node.isMainNode());
    online = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("online"), node.isOnline());
    current = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("current"), node.isCurrent());
  }

  public String getId() {
    return id;
  }

  public String getUrl() {
    return url;
  }

  public Boolean getMain() {
    return main;
  }

  public Boolean getOnline() {
    return online;
  }

  public Boolean getCurrent() {
    return current;
  }
}
