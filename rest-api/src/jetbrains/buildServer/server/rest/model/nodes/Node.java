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

package jetbrains.buildServer.server.rest.model.nodes;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.swagger.annotations.ModelDescription;
import jetbrains.buildServer.server.rest.util.ValueWithDefault;
import jetbrains.buildServer.serverSide.TeamCityNode;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;

@XmlRootElement(name = "node")
@XmlType(propOrder = {"id", "url", "state", "role", "current", "enabledResponsibilities", "disabledResponsibilities", "effectiveResponsibilities"})
@ModelDescription(
  value = "Represents a TeamCity node.",
  externalArticleLink = "https://www.jetbrains.com/help/teamcity/multinode-setup.html",
  externalArticleName = "Multi-node setup"
)
public class Node {
  @XmlAttribute public String id;
  @XmlAttribute public String url;
  @XmlAttribute public String role;
  @XmlAttribute public NodeState state;
  @XmlAttribute public Boolean current;
  @XmlElement public EnabledResponsibilities enabledResponsibilities;
  @XmlElement public DisabledResponsibilities disabledResponsibilities;
  @XmlElement public EffectiveResponsibilities effectiveResponsibilities;

  public enum NodeState {
    online, offline, stopping, starting
  }

  public enum NodeRole {
    main_node, secondary_node
  }

  public Node() {
  }

  public Node(TeamCityNode node, final Fields fields, @NotNull final PermissionChecker permissionChecker) {
    boolean canViewSettings = permissionChecker.hasGlobalPermission(Permission.VIEW_SERVER_SETTINGS);

    url = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("url"), canViewSettings ? node.getUrl() : null);
    id = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("id"), node.getId());
    role = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("role"), getNodeRole(node).name());
    state = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("state"), getNodeState(node));
    current = ValueWithDefault.decideIncludeByDefault(fields.isIncluded("current"), node.isCurrent());

    if (canViewSettings) {
      enabledResponsibilities = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("enabledResponsibilities", false), () -> {
          final Fields nestedFields = fields.getNestedField("enabledResponsibilities", Fields.NONE, Fields.LONG);
          return new EnabledResponsibilities(node, nestedFields);
      });

      disabledResponsibilities = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("disabledResponsibilities", false), () -> {
          final Fields nestedFields = fields.getNestedField("disabledResponsibilities", Fields.NONE, Fields.LONG);
          return new DisabledResponsibilities(node, nestedFields);
      });

      effectiveResponsibilities = ValueWithDefault.decideDefaultIgnoringAccessDenied(fields.isIncluded("effectiveResponsibilities", false), () -> {
          final Fields nestedFields = fields.getNestedField("effectiveResponsibilities", Fields.NONE, Fields.LONG);
          return new EffectiveResponsibilities(node, nestedFields);
      });
    } else {
      enabledResponsibilities = null;
      effectiveResponsibilities = null;
      disabledResponsibilities = null;
    }
  }

  @NotNull
  public static NodeRole getNodeRole(@NotNull TeamCityNode node) {
    if (node.isMainNode()) {
      return NodeRole.main_node;
    }

    return NodeRole.secondary_node;
  }

  @NotNull
  public static NodeState getNodeState(@NotNull TeamCityNode node) {
    if (!node.isOnline()) return NodeState.offline;
    if (node.isStopping()) return NodeState.stopping;
    if (node.isOnline() && !node.canAcceptHTTPRequests()) return NodeState.starting;
    return NodeState.online;
  }
}