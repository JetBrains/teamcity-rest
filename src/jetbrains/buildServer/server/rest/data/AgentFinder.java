/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.collect.ComparisonChain;
import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentTypes.AgentType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 25.12.13
 */
public class AgentFinder extends AbstractFinder<SBuildAgent> {
  private static final Logger LOG = Logger.getInstance(AgentFinder.class.getName());

  protected static final String NAME = "name";
  protected static final String AGENT_TYPE_ID = "typeId";  //"imageId" might suiite better, but "typeId" is already used in Agent
  public static final String CONNECTED = "connected";
  public static final String AUTHORIZED = "authorized";
  public static final String PARAMETER = "parameter";
  public static final String ENABLED = "enabled";
  protected static final String IP = "ip";
  protected static final String PROTOCOL = "protocol";
  protected static final String DEFAULT_FILTERING = "defaultFilter";
  protected static final String POOL = "pool";
  protected static final String BUILD = "build";
  protected static final String COMPATIBLE = "compatible";
  protected static final String INCOMPATIBLE = "incompatible";
  protected static final String COMPATIBLE_BUILD_TYPE = "buildType";
  protected static final String COMPATIBLE_BUILD = "build";

  @NotNull private final BuildAgentManager myAgentManager;
  @NotNull private final ServiceLocator myServiceLocator;

  public AgentFinder(final @NotNull BuildAgentManager agentManager, @NotNull final ServiceLocator serviceLocator) {
    super(DIMENSION_ID, NAME, CONNECTED, AUTHORIZED, ENABLED, PARAMETER, IP, POOL, BUILD, COMPATIBLE, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    setHiddenDimensions(PROTOCOL, INCOMPATIBLE, AGENT_TYPE_ID, DEFAULT_FILTERING, DIMENSION_LOOKUP_LIMIT);
    myAgentManager = agentManager;
    myServiceLocator = serviceLocator;
  }

  @NotNull
  public static String getLocator(@NotNull final SBuildAgent buildAgent) {
    return Locator.getStringLocator(DIMENSION_ID, String.valueOf(buildAgent.getId()));
  }

  @NotNull
  @Override
  public String getItemLocator(@NotNull final SBuildAgent buildAgent) {
    return AgentFinder.getLocator(buildAgent);
  }

  public static String getCompatibleAgentsLocator(final SBuildType buildType) {
    return Locator.getStringLocator(COMPATIBLE, Locator.getStringLocator(COMPATIBLE_BUILD_TYPE, BuildTypeFinder.getLocator(buildType)));
  }

  @NotNull
  public static String getLocator(@NotNull final AgentPool pool) {
    return Locator.getStringLocator(POOL, AgentPoolFinder.getLocator(pool), DEFAULT_FILTERING, "false");
  }

  //todo: check view agent details permission before returning unauthorized agents, here and in prefiltering
  @Override
  @Nullable
  public SBuildAgent findSingleItem(@NotNull final Locator locator) {

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
      final SBuildAgent agent = myAgentManager.findAgentByName(name, true);
      if (agent != null) {
        return agent;
      }
      throw new NotFoundException("No agent can be found by name '" + name + "'.");
    }

    return null;
  }

  @NotNull
  @Override
  public ItemFilter<SBuildAgent> getFilter(@NotNull final Locator locator) {
    final MultiCheckerFilter<SBuildAgent> result = new MultiCheckerFilter<SBuildAgent>();

    //filtering unauthorized agents as UI does
    if (!myServiceLocator.getSingletonService(PermissionChecker.class).isPermissionGranted(Permission.VIEW_AGENT_DETAILS, null)) {
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return item.isAuthorized();
        }
      });
    }

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
      AgentPoolFinder agentPoolFinder = myServiceLocator.getSingletonService(AgentPoolFinder.class);
      final AgentPool agentPool = agentPoolFinder.getItem(poolDimension); //get id here to support not existing pools?
      result.add(new FilterConditionChecker<SBuildAgent>() {
        public boolean isIncluded(@NotNull final SBuildAgent item) {
          return ((BuildAgentEx)item).getAgentType().getAgentPoolId() == agentPool.getAgentPoolId(); //TeamCity API issue: cast
        }
      });
    }

    if (locator.isUnused(BUILD)) {
      final String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        Set<SBuildAgent> agents = getBuildRelatedAgents(buildDimension);
        result.add(new FilterConditionChecker<SBuildAgent>() {
          public boolean isIncluded(@NotNull final SBuildAgent item) {
            return agents.contains(item);
          }
        });
      }
    }

    if (locator.isUnused(AGENT_TYPE_ID)) {
      final String agentTypeLocator = locator.getSingleDimensionValue(AGENT_TYPE_ID);
      if (agentTypeLocator != null) {
        int agentTypeId = getAgentType(agentTypeLocator, myServiceLocator.getSingletonService(AgentTypeFinder.class)).getAgentTypeId();
        result.add(new FilterConditionChecker<SBuildAgent>() {
          public boolean isIncluded(@NotNull final SBuildAgent item) {
            return agentTypeId == item.getId();
          }
        });
      }
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

    final String compatible = locator.getSingleDimensionValue(COMPATIBLE); //compatible with at least with one of the buildTypes
    if (compatible != null) {
      final CompatibleLocatorParseResult compatibleData = getBuildTypesFromCompatibleDimension(compatible);
      if (compatibleData.buildTypes != null) {
        result.add(new FilterConditionChecker<SBuildAgent>() {
          public boolean isIncluded(@NotNull final SBuildAgent item) {
            return isCompatibleWithAny(item, compatibleData.buildTypes);
          }
        });
      } else {
        result.add(new FilterConditionChecker<SBuildAgent>() {
          public boolean isIncluded(@NotNull final SBuildAgent item) {
            assert compatibleData.buildPromotions != null;
            return isCompatibleWithAnyBuild(item, compatibleData.buildPromotions);
          }
        });
      }
    }

    final String incompatible = locator.getSingleDimensionValue(INCOMPATIBLE); //incompatible with at least with one of the buildTypes
    if (incompatible != null) {
      final CompatibleLocatorParseResult compatibleData = getBuildTypesFromCompatibleDimension(incompatible);
       if (compatibleData.buildTypes != null) {
         result.add(new FilterConditionChecker<SBuildAgent>() {
           public boolean isIncluded(@NotNull final SBuildAgent item) {
             return !isCompatibleWithAll(item, compatibleData.buildTypes);
           }
         });
       } else {
         result.add(new FilterConditionChecker<SBuildAgent>() {
           public boolean isIncluded(@NotNull final SBuildAgent item) {
             assert compatibleData.buildPromotions != null;
             return !isCompatibleWithAllBuild(item, compatibleData.buildPromotions);
           }
         });
       }
    }

    return result;
  }

  @NotNull
  private Set<SBuildAgent> getBuildRelatedAgents(@NotNull final String buildDimension) {
    BuildPromotionFinder finder = myServiceLocator.getSingletonService(BuildPromotionFinder.class);
    List<BuildPromotion> builds = finder.getItems(buildDimension).myEntries;
    //agents with the same id can be returned (not existing agents)
    TreeSet<SBuildAgent> result = createContainerSet();
    for (BuildPromotion build : builds) {
      SQueuedBuild queuedBuild = build.getQueuedBuild();
      if (queuedBuild != null) {
        result.addAll(queuedBuild.getCanRunOnAgents());
      } else {
        SBuild associatedBuild = build.getAssociatedBuild();
        if (associatedBuild != null) {
          result.add(associatedBuild.getAgent());
        }
      }
    }
    return result;
  }

  @NotNull
  public TreeSet<SBuildAgent> createContainerSet() {
    return new TreeSet<>(new Comparator<SBuildAgent>() {
      @Override
      public int compare(final SBuildAgent o1, final SBuildAgent o2) {
        return ComparisonChain.start()
                              .compare(o1.getId(), o2.getId())
                              .compare(o1.getAgentTypeId(), o2.getAgentTypeId())
                              .compare(o1.getName(), o2.getName())
                              .result();
      }
    });
  }

  @NotNull
  private CompatibleLocatorParseResult getBuildTypesFromCompatibleDimension(@NotNull final String compatibleDimensionValue) {
    Locator compatibleLocator = new Locator(compatibleDimensionValue, COMPATIBLE_BUILD_TYPE);
    try {
      compatibleLocator.addHiddenDimensions(COMPATIBLE_BUILD);
      String compatibleBuildType = compatibleLocator.getSingleDimensionValue(COMPATIBLE_BUILD_TYPE);
      if (compatibleBuildType != null) {
        BuildTypeFinder buildTypeFinder = myServiceLocator.getSingletonService(BuildTypeFinder.class);
        return CompatibleLocatorParseResult.fromBuildTypes(buildTypeFinder.getBuildTypes(null, compatibleBuildType));
      }
      String compatibleBuild = compatibleLocator.getSingleDimensionValue(COMPATIBLE_BUILD);
      if (compatibleBuild != null) {
        return CompatibleLocatorParseResult.fromBuilds(myServiceLocator.getSingletonService(BuildPromotionFinder.class).getItems(compatibleBuild).myEntries);
      }
    } finally {
      compatibleLocator.checkLocatorFullyProcessed();
    }
    throw new BadRequestException("Invalid compatible locator: should contain '" + COMPATIBLE_BUILD_TYPE + "' dimension");
  }

  private static class CompatibleLocatorParseResult {
    @Nullable final List<SBuildType> buildTypes;
    @Nullable final List<BuildPromotion> buildPromotions;

    private CompatibleLocatorParseResult(final List<SBuildType> buildTypes, final List<BuildPromotion> buildPromotions) {
      this.buildTypes = buildTypes;
      this.buildPromotions = buildPromotions;
    }

    public static CompatibleLocatorParseResult fromBuildTypes(@NotNull final List<SBuildType> buildTypes) {
      return new CompatibleLocatorParseResult(buildTypes, null);
    }

    public static CompatibleLocatorParseResult fromBuilds(@NotNull final List<BuildPromotion> builds) {
      return new CompatibleLocatorParseResult(null, builds);
    }
  }

  private boolean isCompatibleWithAny(@NotNull final SBuildAgent agent, @NotNull final List<SBuildType> buildTypes) {
    SAgentType agentType = getAgentType(agent);
    for (final SBuildType buildType : buildTypes) {
      if (canActuallyRun(agentType, buildType)) return true;
    }
    return false;
  }

  private boolean isCompatibleWithAll(@NotNull final SBuildAgent agent, @NotNull final List<SBuildType> buildTypes) {
    SAgentType agentType = getAgentType(agent);
    for (final SBuildType buildType : buildTypes) {
      if (!canActuallyRun(agentType, buildType)) return false;
    }
    return true;
  }

  private boolean isCompatibleWithAnyBuild(final @NotNull SBuildAgent agent, final List<BuildPromotion> buildPromotions) {
    SAgentType agentType = getAgentType(agent);
    for (BuildPromotion buildPromotion : buildPromotions) {
      if (canActuallyRun(agentType, buildPromotion)) return true;
    }
    return false;
  }

  private boolean isCompatibleWithAllBuild(final @NotNull SBuildAgent agent, final List<BuildPromotion> buildPromotions) {
    SAgentType agentType = getAgentType(agent);
    for (BuildPromotion buildPromotion : buildPromotions) {
      if (!canActuallyRun(agentType, buildPromotion)) return false;
    }
    return true;
  }

  public static boolean canActuallyRun(@NotNull final SAgentType agentType, @NotNull final SBuildType buildType) {
    if (agentType.getPolicy().isBuildTypeAllowed(buildType.getBuildTypeId())) {
      final AgentCompatibility compatibility = ((BuildTypeEx)buildType).getAgentTypeCompatibility(agentType);
      if (compatibility.isActive() && compatibility.isCompatible()) return true;
    }
    return false;
  }

  public static boolean canActuallyRun(@NotNull final SAgentType agentType, @NotNull final BuildPromotion build) {
    if (agentType.getPolicy().isBuildTypeAllowed(build.getBuildTypeId())) {
      return !build.getCompatibleAgents(Collections.singletonList(agentType.getRealAgent())).isEmpty();
    }
    return false;
  }

  private boolean canActuallyRun(@NotNull final SBuildAgent agent, @NotNull final BuildPromotion build, @NotNull final CompatibilityResult compatibilityResult) {
    AgentType agentType = getAgentType(agent);
    if (agentType.getPolicy().isBuildTypeAllowed(build.getBuildTypeId())) {
      if (compatibilityResult.isCompatible()) return true;
    }
    return false;
  }

  public static boolean canActuallyRun(@NotNull final AgentCompatibility compatibility) {
    AgentType agentType = getAgentType(compatibility);
    if (agentType.getPolicy().isBuildTypeAllowed(compatibility.getBuildType().getBuildTypeId())) {
      if (compatibility.isActive() && compatibility.isCompatible()) return true;
    }
    return false;
  }

  @NotNull
  public static AgentType getAgentType(@NotNull final AgentCompatibility compatibility) {
    AgentDescription agentDescription = compatibility.getAgentDescription();
    if (agentDescription instanceof AgentType) {
      return (AgentType)agentDescription;
    } else if (agentDescription instanceof BuildAgentEx) {
      return ((BuildAgentEx)agentDescription).getAgentType();
    }
    throw new OperationException("Unsupported agent details of type: " + agentDescription.getClass());
  }

  @NotNull
  public static SAgentType getAgentType(@NotNull final SBuildAgent agent) {
    return ((BuildAgentEx)agent).getAgentType();
  }


  @NotNull
  @Override
  public ItemHolder<SBuildAgent> getPrefilteredItems(@NotNull final Locator locator) {
    setLocatorDefaults(locator);

    final String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      return getItemHolder(getBuildRelatedAgents(buildDimension));
    }

    final Boolean authorizedDimension = locator.getSingleDimensionValueAsBoolean(AUTHORIZED);
    final boolean includeUnauthorized = authorizedDimension == null || !authorizedDimension;

    if (!includeUnauthorized && locator.lookupSingleDimensionValue(COMPATIBLE) != null) {
      final String compatible = locator.getSingleDimensionValue(COMPATIBLE); //compatible with at least with one of the buildTypes
      assert compatible != null;
      TreeSet<SBuildAgent> result = createContainerSet();
      CompatibleLocatorParseResult compatibleData = getBuildTypesFromCompatibleDimension(compatible);
      if (compatibleData.buildTypes != null) {
        for (SBuildType buildType : compatibleData.buildTypes) {
          List<AgentCompatibility> agentCompatibilities = buildType.getAgentCompatibilities();
          for (AgentCompatibility compatibility : agentCompatibilities) {
            if (canActuallyRun(compatibility)) {
              AgentDescription agentDescription = compatibility.getAgentDescription();
              if (agentDescription instanceof SBuildAgent) {
                result.add((SBuildAgent)agentDescription);
              } else {
                LOG.debug("Cloud agents are not supported in REST API, skipping " + LogUtil.describe(agentDescription));
                //todo: support
              }
            }
          }
        }
      } else {
        ArrayList<SBuildAgent> allAgents = new ArrayList<>(myAgentManager.getRegisteredAgents(true));
        allAgents.addAll(myAgentManager.getUnregisteredAgents());
        assert compatibleData.buildPromotions != null;
        for (BuildPromotion build : compatibleData.buildPromotions) {
          result.addAll(build.getCompatibleAgents(allAgents));
        }
      }
      return getItemHolder(result);
    }

    if (!includeUnauthorized && locator.lookupSingleDimensionValue(INCOMPATIBLE) != null) {
      final String incompatible = locator.getSingleDimensionValue(INCOMPATIBLE); //incompatible with at least with one of the buildTypes
      assert incompatible != null;
      TreeSet<SBuildAgent> result = createContainerSet();
      CompatibleLocatorParseResult compatibleData = getBuildTypesFromCompatibleDimension(incompatible);
      if (compatibleData.buildTypes != null){
        for (SBuildType buildType : compatibleData.buildTypes) {
          List<AgentCompatibility> agentCompatibilities = buildType.getAgentCompatibilities();
          for (AgentCompatibility compatibility : agentCompatibilities) {
            if (!canActuallyRun(compatibility)) {
              AgentDescription agentDescription = compatibility.getAgentDescription();
              if (agentDescription instanceof SBuildAgent) {
                result.add((SBuildAgent)agentDescription);
              } else {
                LOG.debug("Cloud agents are not supported in REST API, skipping " + LogUtil.describe(agentDescription));
                //todo: support
              }
            }
          }
        }
      } else {
        ArrayList<SBuildAgent> allAgents = new ArrayList<>(myAgentManager.getRegisteredAgents(true));
        allAgents.addAll(myAgentManager.getUnregisteredAgents());
        assert compatibleData.buildPromotions != null;
        for (BuildPromotion build : compatibleData.buildPromotions) {
          Map<SBuildAgent, CompatibilityResult> agentCompatibilities = build.getCompatibilityMap(allAgents);
          for (Map.Entry<SBuildAgent, CompatibilityResult> compatibility : agentCompatibilities.entrySet()) {
            if (!canActuallyRun(compatibility.getKey(), build, compatibility.getValue())) {
                result.add(compatibility.getKey());
            }
          }
        }
      }
      return getItemHolder(result);
    }

    TreeSet<SBuildAgent> result = createContainerSet();

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
    if (locator.isSingleValue()) {
      return;
    }

    final Boolean defaultFiltering = locator.getSingleDimensionValueAsBoolean(DEFAULT_FILTERING);
    if (defaultFiltering != null && !defaultFiltering) {
      return;
    }

    if (defaultFiltering == null && locator.isAnyPresent(DIMENSION_ID, NAME)){
      return;
    }

    if (defaultFiltering == null && locator.isAnyPresent(BUILD, POOL, IP, PROTOCOL)) {
      return;
    }

    locator.setDimensionIfNotPresent(AUTHORIZED, "true");
  }

  /**
   * @return found agent pool or null if cannot extract agent pool from the locator
   */
  @Nullable
  public static AgentPool getAgentPoolFromLocator(@NotNull final String locatorText, @NotNull final AgentPoolFinder agentPoolFinder) {
    final Locator locator = new Locator(locatorText);
    final String poolDimension = locator.getSingleDimensionValue(POOL);
    if (poolDimension != null) {
      return agentPoolFinder.getItem(poolDimension);
    }
    return null; //reporting no errors by method contract
  }

  public static SAgentType getAgentType(@NotNull final String agentTypeLocator, @NotNull final AgentTypeFinder agentTypeFinder) {
    //support only int single value for now
    Integer agentTypeId = null;
    try {
      agentTypeId = Integer.valueOf(agentTypeLocator);
    } catch (NumberFormatException e) {
      throw new BadRequestException("Bad agent type id '" + agentTypeLocator + "': should be a number");
    }
    SAgentType agentType = agentTypeFinder.findAgentType(agentTypeId); //make AgentFinder finding agent types in the future
    if (agentType == null) {
      throw new NotFoundException("No agent type is found by id '" + agentTypeId + "'");
    }
    return agentType;
  }
}
