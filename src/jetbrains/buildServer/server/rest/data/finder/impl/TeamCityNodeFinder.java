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

package jetbrains.buildServer.server.rest.data.finder.impl;

import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.util.MultiCheckerFilter;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.nodes.Node;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.TeamCityNode;
import jetbrains.buildServer.serverSide.TeamCityNodes;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@LocatorResource(value = LocatorName.TEAMCITY_NODE,
  extraDimensions = {
    AbstractFinder.DIMENSION_ITEM
  },
  baseEntity = "Node",
  examples = {
    "`id:node-1` - find a node with id `node-1`.",
    "`state:<stateLocator>` - find all nodes having the given state.",
    "`role:<role>` - finds a node with the specified role, possible values are: main_node or secondary_node."
  }
)
@JerseyContextSingleton
@Component("restTeamCityNodesFinder")
public class TeamCityNodeFinder extends AbstractFinder<TeamCityNode> {
  private final TeamCityNodes myTeamCityNodes;
  @LocatorDimension("state")
  protected static final String STATE = "state";
  @LocatorDimension("role")
  protected static final String ROLE = "role";

  public TeamCityNodeFinder(@NotNull TeamCityNodes teamCityNodes) {
    super(DIMENSION_ID, STATE, ROLE);
    myTeamCityNodes = teamCityNodes;
  }

  @NotNull
  @Override
  public ItemHolder<TeamCityNode> getPrefilteredItems(@NotNull Locator locator) {
    return ItemHolder.of(myTeamCityNodes.getNodes().stream());
  }

  @NotNull
  @Override
  public ItemFilter<TeamCityNode> getFilter(@NotNull Locator locator) {
    final MultiCheckerFilter<TeamCityNode> result = new MultiCheckerFilter<>();

    if (locator.isUnused(DIMENSION_ID)) {
      final String id = locator.getSingleDimensionValue(DIMENSION_ID);
      if (id != null) {
        result.add(item -> id.equals(item.getId()));
      }
    }

    if (locator.isUnused(STATE)) {
      final String state = locator.getSingleDimensionValue(STATE);
      if (state != null) {
        result.add(item -> state.equals(Node.getNodeState(item).name()));
      }
    }

    if (locator.isUnused(ROLE)) {
      final String role = locator.getSingleDimensionValue(ROLE);
      if (role != null) {
        result.add(item -> role.equals(Node.getNodeRole(item).name()));
      }
    }

    return result;
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull TeamCityNode teamCityNode) {
    return Locator.getStringLocator(DIMENSION_ID, teamCityNode.getId());
  }
}
