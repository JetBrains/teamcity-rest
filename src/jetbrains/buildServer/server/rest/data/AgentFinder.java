/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.BuildAgentManager;
import jetbrains.buildServer.serverSide.BuildAgentManagerEx;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 25.12.13
 */
public class AgentFinder extends AbstractFinder<SBuildAgent> {
  protected static final String NAME = "name";
  public static final String CONNECTED = "connected";
  public static final String AUTHORIZED = "authorized";
  public static final String PARAMETER = "parameter";
  public static final String ENABLED = "enabled";
  protected static final String IP = "ip";
  protected static final String PROTOCOL = "protocol";
  protected static final String DEFAULT_FILTERING = "defaultFilter";
  protected static final String POOL = "pool";

  @NotNull private final BuildAgentManager myAgentManager;
  @NotNull private final ServiceLocator myServiceLocator;

  public AgentFinder(final @NotNull BuildAgentManager agentManager, @NotNull final ServiceLocator serviceLocator) {
    super(new String[]{DIMENSION_ID, NAME, CONNECTED, AUTHORIZED, ENABLED, PARAMETER, IP, POOL, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME});
    myAgentManager = agentManager;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  @Override
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator result = super.createLocator(locatorText, locatorDefaults);
    result.addHiddenDimensions(PROTOCOL);    //hide this for now
    result.addHiddenDimensions(DEFAULT_FILTERING);
    result.addHiddenDimensions(DIMENSION_LOOKUP_LIMIT);
    return result;
  }

  @Nullable
  @Override
  public ItemHolder<SBuildAgent> getAllItems() {
    return null;
  }

  //todo: check view agent details permission before returning unauthorized agents, here and in prefiltering
  @Override
  @Nullable
  protected SBuildAgent findSingleItem(@NotNull final Locator locator) {

    if (locator.isSingleValue()) {
      // no dimensions found, assume it's name
      final SBuildAgent agent = myAgentManager.findAgentByName(locator.getSingleValue(), true);
      if (agent == null) {
        throw new NotFoundException("No agent can be found by name '" + locator.getSingleValue() + "'.");
      }
      return agent;
    }

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      final SBuildAgent agent = myAgentManager.findAgentById(id.intValue(), true);
      if (agent == null) {
        throw new NotFoundException("No agent can be found by id '" + locator.getSingleDimensionValue(DIMENSION_ID) + "'.");
      }
      return agent;
    }

    String name = locator.getSingleDimensionValue(NAME);
    if (name != null) {
      final SBuildAgent agent =  myAgentManager.findAgentByName(name, true);
      if (agent != null) {
        return agent;
      }
      throw new NotFoundException("No agent can be found by name '" + name + "'.");
    }

    return null;
  }

  @NotNull
  @Override
  protected ItemFilter<SBuildAgent> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final MultiCheckerFilter<SBuildAgent> result = new MultiCheckerFilter<SBuildAgent>();


    final Boolean authorizedDimension = locator.getSingleDimensionValueAsBoolean(AUTHORIZED);
    if (authorizedDimension != null) {
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return FilterUtil.isIncludedByBooleanFilter(authorizedDimension, item.isAuthorized());
        }
      });
    }

     final Boolean enabledDimension = locator.getSingleDimensionValueAsBoolean(ENABLED);
    if (enabledDimension != null) {
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return FilterUtil.isIncludedByBooleanFilter(enabledDimension, item.isEnabled());
        }
      });
    }

    final Boolean connectedDimension = locator.getSingleDimensionValueAsBoolean(CONNECTED);
    if (connectedDimension != null) {
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return FilterUtil.isIncludedByBooleanFilter(connectedDimension, item.isRegistered());
        }
      });
    }

    final String poolDimension = locator.getSingleDimensionValue(POOL); //see also AgentPoolsFinder.getPoolAgents()
    if (poolDimension != null) {
      AgentPoolsFinder agentPoolsFinder = myServiceLocator.getSingletonService(AgentPoolsFinder.class);
      final AgentPool agentPool = agentPoolsFinder.getAgentPool(poolDimension); //get id here to support not existing pools?
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return ((BuildAgentEx)item).getAgentType().getAgentPoolId() == agentPool.getAgentPoolId(); //TeamCity API issue: cast
        }
      });
    }

    final String ipDimension = locator.getSingleDimensionValue(IP);
    if (ipDimension != null) {
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return ipDimension.equals(Agent.getFieldValue(item, "ip", myServiceLocator)); //name of the field, not locator dimension
        }
      });
    }

    final String protocolDimension = locator.getSingleDimensionValue(PROTOCOL);
    if (protocolDimension != null) {
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return protocolDimension.equals(Agent.getFieldValue(item, "protocol", myServiceLocator)); //name of the field, not locator dimension
        }
      });
    }

    final String parameterDimension = locator.getSingleDimensionValue(PARAMETER);
    if (parameterDimension != null) {
      final ParameterCondition parameterCondition = ParameterCondition.create(parameterDimension);
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return parameterCondition.matches(new MapParametersProviderImpl(item.getAvailableParameters()));
        }
      });
    }

    return result;
  }

  @NotNull
  @Override
  protected ItemHolder<SBuildAgent> getPrefilteredItems(@NotNull final Locator locator) {
    List<SBuildAgent> result = new ArrayList<SBuildAgent>();

    setLocatorDefaults(locator);

    final Boolean authorizedDimension = locator.getSingleDimensionValueAsBoolean(AUTHORIZED);
    final boolean includeUnauthorized = authorizedDimension == null || !authorizedDimension;

    final Boolean connectedDimension = locator.getSingleDimensionValueAsBoolean(CONNECTED);
    if (connectedDimension == null || connectedDimension) {
      result.addAll(myAgentManager.getRegisteredAgents(includeUnauthorized));
    }

    if (connectedDimension == null || !connectedDimension) {
      result.addAll(((BuildAgentManagerEx)myAgentManager).getUnregisteredAgents(includeUnauthorized));  //TeamCIty API issue: cast
    }
    return getItemHolder(result);
  }

  private void setLocatorDefaults(@NotNull final Locator locator) {
    final Boolean defaultFiltering = locator.getSingleDimensionValueAsBoolean(DEFAULT_FILTERING, true);
    if (!locator.isSingleValue() && (defaultFiltering == null || defaultFiltering)) {
      locator.setDimensionIfNotPresent(AUTHORIZED, "true");
    }
  }

  /**
   *
   * @return found agent pool or null if cannot extract agent pool from the locator
   */
  @Nullable
  public static AgentPool getAgentPoolFromLocator(@NotNull final String locatorText, @NotNull final AgentPoolsFinder agentPoolsFinder) {
    final Locator locator = new Locator(locatorText);
    final String poolDimension = locator.getSingleDimensionValue(POOL);
    if (poolDimension != null){
      return agentPoolsFinder.getAgentPool(poolDimension);
    }
    return null; //reporting no errors by method contract
  }
}
