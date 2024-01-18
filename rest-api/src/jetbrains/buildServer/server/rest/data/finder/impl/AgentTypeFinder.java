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

import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.finder.DelegatingFinder;
import jetbrains.buildServer.server.rest.data.finder.TypedFinderBuilder;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyInjectable;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import static jetbrains.buildServer.server.rest.data.finder.syntax.AgentTypeDimensions.*;

@JerseyInjectable
@Component("restAgentTypeFinder")
public class AgentTypeFinder extends DelegatingFinder<SAgentType> {
  private final jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder myAgentTypeFinder;
  private final ServiceLocator myServiceLocator;

  public AgentTypeFinder(@NotNull jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder agentTypeFinder,
                         @NotNull ServiceLocator serviceLocator) {
    myAgentTypeFinder = agentTypeFinder;
    myServiceLocator = serviceLocator;

    setDelegate(new AgentTypeFinderBuilder().build());
  }

  class AgentTypeFinderBuilder extends TypedFinderBuilder<SAgentType> {
    public AgentTypeFinderBuilder() {
      name("AgentTypeFinder");

      singleDimension(dimValue -> Collections.singletonList(myAgentTypeFinder.findAgentType(Integer.parseInt(dimValue))));
      dimensionLong(ID).valueForDefaultFilter(at -> (long) at.getAgentTypeId())
                       .toItems(id -> {
                         SAgentType agentType = myAgentTypeFinder.findAgentType(id.intValue());
                         return agentType == null ? null : Collections.singletonList(agentType);
                       });

      dimensionAgents(AGENT, myServiceLocator).filter(this::filterByAgents);

      fallbackItemRetriever(dimensions -> ItemHolder.of(myAgentTypeFinder.getActiveAgentTypes()));
    }

    private boolean filterByAgents(@NotNull List<SBuildAgent> dimAgents, @NotNull SAgentType testItem) {
      return dimAgents.stream()
                      .map(at -> at.getAgentTypeId())
                      .anyMatch(id -> id == testItem.getAgentPoolId());
    }
  }
}
