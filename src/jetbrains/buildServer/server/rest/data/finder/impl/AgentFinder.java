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

import com.google.common.collect.ComparisonChain;
import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.AgentRestrictor;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.parameters.impl.MapParametersProviderImpl;
import jetbrains.buildServer.server.rest.data.CloudInstanceData;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.ParameterCondition;
import jetbrains.buildServer.server.rest.data.finder.AbstractFinder;
import jetbrains.buildServer.server.rest.data.finder.FinderImpl;
import jetbrains.buildServer.server.rest.data.util.*;
import jetbrains.buildServer.server.rest.data.util.itemholder.ItemHolder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.jersey.provider.annotated.JerseyContextSingleton;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.Compatibility;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorDimension;
import jetbrains.buildServer.server.rest.swagger.annotations.LocatorResource;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorDimensionDataType;
import jetbrains.buildServer.server.rest.swagger.constants.LocatorName;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentTypes.AgentType;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeFinder;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.impl.buildDistribution.restrictors.SingleAgentRestrictor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * @author Yegor.Yarko
 *         Date: 25.12.13
 */
@LocatorResource(value = LocatorName.AGENT,
    extraDimensions = {FinderImpl.DIMENSION_ID, PagerData.START, PagerData.COUNT, AbstractFinder.DIMENSION_ITEM},
    baseEntity = "Agent",
    examples = {
        "`name:DefaultAgent` - find agent with `DefaultAgent` name.",
        "`pool:(<agentPoolLocator>),connected:true` - find all connected agents in a pool found by `agentPoolLocator`."
    }
)
@JerseyContextSingleton
@Component("restAgentFinder")
public class AgentFinder extends AbstractFinder<SBuildAgent> {
  private static final Logger LOG = Logger.getInstance(AgentFinder.class.getName());

  @LocatorDimension("name") public static final String NAME = "name";
  protected static final String AGENT_TYPE_ID = "typeId";  //"imageId" might suiite better, but "typeId" is already used in Agent
  @LocatorDimension(value = "connected", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is the agent connected.")
  public static final String CONNECTED = "connected";
  @LocatorDimension(value = "authorized", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is the agent authorized.")
  public static final String AUTHORIZED = "authorized";
  @LocatorDimension("parameter") public static final String PARAMETER = "parameter";
  @LocatorDimension(value = "enabled", dataType = LocatorDimensionDataType.BOOLEAN, notes = "Is the agent enabled.")
  public static final String ENABLED = "enabled";
  @LocatorDimension("ip") protected static final String IP = "ip";
  protected static final String PROTOCOL = "protocol";
  protected static final String DEFAULT_FILTERING = "defaultFilter";
  @LocatorDimension(value = "pool", format = LocatorName.AGENT_POOL, notes = "Agent pool locator.")
  protected static final String POOL = "pool";
  @LocatorDimension(value = "build", format = LocatorName.BUILD, notes = "Build locator.")
  protected static final String BUILD = "build";
  @LocatorDimension(value = "compatible", format = LocatorName.BUILD_TYPE, notes = "Compatible build types locator.")
  protected static final String COMPATIBLE = "compatible";
  protected static final String INCOMPATIBLE = "incompatible";
  protected static final String COMPATIBLE_BUILD_TYPE = "buildType";
  protected static final String COMPATIBLE_BUILD = "build";
  protected static final String CLOUD_INSTANCE = "cloudInstance";

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

  @NotNull
  public static String getCompatibleAgentsLocator(final SBuildType buildType) {
    return Locator.getStringLocator(COMPATIBLE, Locator.getStringLocator(COMPATIBLE_BUILD_TYPE, BuildTypeFinder.getLocator(buildType)));
  }

  @NotNull
  public static String getCompatibleAgentsLocator(final BuildPromotion build) {
    return Locator.getStringLocator(COMPATIBLE, Locator.getStringLocator(COMPATIBLE_BUILD, BuildPromotionFinder.getLocator(build)));
  }

  @NotNull
  public static String getLocator(@NotNull final AgentPool pool) {
    return Locator.getStringLocator(POOL, AgentPoolFinder.getLocator(pool), DEFAULT_FILTERING, "false");
  }

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

    Long id = locator.getSingleDimensionValueAsLong(DIMENSION_ID);
    if (id != null) {
      result.add(item -> id == item.getId());
    }

    String name = locator.getSingleDimensionValue(NAME);
    if (name != null) {
      result.add(item -> name.equals(item.getName()));
    }

    final Boolean authorizedDimension = locator.getSingleDimensionValueAsBoolean(AUTHORIZED);
    if (authorizedDimension != null) {
      result.add(item -> FilterUtil.isIncludedByBooleanFilter(authorizedDimension, item.isAuthorized()));
    }

    final Boolean enabledDimension = locator.getSingleDimensionValueAsBoolean(ENABLED);
    if (enabledDimension != null) {
      result.add(item -> FilterUtil.isIncludedByBooleanFilter(enabledDimension, item.isEnabled()));
    }

    final Boolean connectedDimension = locator.getSingleDimensionValueAsBoolean(CONNECTED);
    if (connectedDimension != null) {
      result.add(item -> FilterUtil.isIncludedByBooleanFilter(connectedDimension, item.isRegistered()));
    }

    final String poolDimension = locator.getSingleDimensionValue(POOL); //see also AgentPoolsFinder.getPoolAgents()
    if (poolDimension != null) {
      AgentPoolFinder agentPoolFinder = myServiceLocator.getSingletonService(AgentPoolFinder.class);
      final AgentPool agentPool = agentPoolFinder.getItem(poolDimension); //get id here to support not existing pools?
      result.add(item -> ((BuildAgentEx)item).getAgentType().getAgentPoolId() == agentPool.getAgentPoolId()); //TeamCity API issue: cast
    }

    if (locator.isUnused(BUILD)) {
      final String buildDimension = locator.getSingleDimensionValue(BUILD);
      if (buildDimension != null) {
        Set<SBuildAgent> agents = getBuildRelatedAgents(buildDimension);
        result.add(item -> agents.contains(item));
      }
    }

    if (locator.isUnused(AGENT_TYPE_ID)) {
      final String agentTypeLocator = locator.getSingleDimensionValue(AGENT_TYPE_ID);
      if (agentTypeLocator != null) {
        int agentTypeId = getAgentType(agentTypeLocator, myServiceLocator.getSingletonService(AgentTypeFinder.class)).getAgentTypeId();
        result.add(item -> agentTypeId == item.getAgentTypeId());
      }
    }

    final String ipDimension = locator.getSingleDimensionValue(IP);
    if (ipDimension != null) {
      result.add(item -> ipDimension.equals(Agent.getFieldValue(item, "ip", myServiceLocator))); //name of the field, not locator dimension
    }

    final String protocolDimension = locator.getSingleDimensionValue(PROTOCOL);
    if (protocolDimension != null) {
      result.add(item -> protocolDimension.equals(Agent.getFieldValue(item, "protocol", myServiceLocator))); //name of the field, not locator dimension
    }

    final String parameterDimension = locator.getSingleDimensionValue(PARAMETER);
    if (parameterDimension != null) {
      final ParameterCondition parameterCondition = ParameterCondition.create(parameterDimension);
      result.add(item -> parameterCondition.matches(new MapParametersProviderImpl(item.getAvailableParameters())));
    }

    if (locator.isUnused(CLOUD_INSTANCE)) {
      final String cloudInstanceLocator = locator.getSingleDimensionValue(CLOUD_INSTANCE);
      if (cloudInstanceLocator != null) {
        List<CloudInstance> instances = myServiceLocator.getSingletonService(CloudInstanceFinder.class)
                                                        .getItems(cloudInstanceLocator).myEntries.stream().map(CloudInstanceData::getInstance).collect(Collectors.toList());
        result.add(a -> instances.stream().anyMatch(i -> i.containsAgent(a)));
        /* CloudInstance might not have equals/hashcode, if it does, it would be better to use in a set like below
        Set<CloudInstance> instances = myServiceLocator.getSingletonService(CloudInstanceFinder.class).getItems(cloudInstanceLocator).myEntries.stream().map(i -> i.getInstance()).collect(
          Collectors.toSet());
        CloudManager cloudManager = myServiceLocator.getSingletonService(CloudManager.class);
        result.add(a -> Util.resolveNull(cloudManager.findInstanceByAgent(a), pair -> instances.contains(pair.getSecond()), false));
        */
      }
    }

    if (locator.isUnused(COMPATIBLE)) {
      final String compatible = locator.getSingleDimensionValue(COMPATIBLE); //compatible with at least with one of the buildTypes
      if (compatible != null) {
        final CompatibleLocatorParseResult compatibleData = getBuildTypesFromCompatibleDimension(compatible);
        if (compatibleData.buildTypes != null) {
          result.add(item -> isCompatibleWithAny(item, compatibleData.buildTypes));
        } else {
          assert compatibleData.buildPromotions != null;
          result.add(item -> isCompatibleWithAnyBuild(item, compatibleData.buildPromotions));
        }
      }
    }

    final String incompatible = locator.getSingleDimensionValue(INCOMPATIBLE); //incompatible with at least with one of the buildTypes
    if (incompatible != null) {
      final CompatibleLocatorParseResult compatibleData = getBuildTypesFromCompatibleDimension(incompatible);
       if (compatibleData.buildTypes != null) {
         result.add(item -> !isCompatibleWithAll(item, compatibleData.buildTypes));
       } else {
         result.add(item -> {
           assert compatibleData.buildPromotions != null;
           return !isCompatibleWithAllBuild(item, compatibleData.buildPromotions);
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
    TreeSet<SBuildAgent> result = new TreeSet<>(AGENT_COMPARATOR);
    for (BuildPromotion build : builds) {
      SQueuedBuild queuedBuild = build.getQueuedBuild();
      if (queuedBuild != null && !build.isCompositeBuild()) { //isAgentLessBuild should be used here, but queued build does not have that so far
        result.addAll(queuedBuild.getCanRunOnAgents());
      } else {
        SBuild associatedBuild = build.getAssociatedBuild();
        if (associatedBuild != null && !associatedBuild.isAgentLessBuild()) {
          result.add(associatedBuild.getAgent());
        }
      }
    }
    return result;
  }

  protected static final Comparator<SBuildAgent> AGENT_COMPARATOR = (o1, o2) ->
    ComparisonChain.start()
                   .compare(o1.getId(), o2.getId())
                   .compare(o1.getAgentTypeId(), o2.getAgentTypeId())
                   .compare(o1.getName(), o2.getName())
                   .result();

  @Override
  @NotNull
  public DuplicateChecker<SBuildAgent> createDuplicateChecker() {
    return new ComparatorDuplicateChecker<>(AGENT_COMPARATOR);
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
    for (final SBuildType buildType : buildTypes) {
      if (canActuallyRun(agent, buildType)) return true;
    }
    return false;
  }

  private boolean isCompatibleWithAll(@NotNull final SBuildAgent agent, @NotNull final List<SBuildType> buildTypes) {
    for (final SBuildType buildType : buildTypes) {
      if (!canActuallyRun(agent, buildType)) return false;
    }
    return true;
  }

  private boolean isCompatibleWithAnyBuild(final @NotNull SBuildAgent agent, final List<BuildPromotion> buildPromotions) {
    for (BuildPromotion buildPromotion : buildPromotions) {
      if (canActuallyRun(agent, buildPromotion)) return true;
    }
    return false;
  }

  private boolean isCompatibleWithAllBuild(final @NotNull SBuildAgent agent, final List<BuildPromotion> buildPromotions) {
    for (BuildPromotion buildPromotion : buildPromotions) {
      if (!canActuallyRun(agent, buildPromotion)) return false;
    }
    return true;
  }

  public static boolean canActuallyRun(@NotNull final SBuildAgent agent, @NotNull final SBuildType buildType) {
    return getCompatibilityData(agent, buildType).isCompatible();
  }

  @NotNull
  public static List<Compatibility.AgentCompatibilityData> getIncompatible(final @NotNull SBuildAgent agent,
                                                                           final @Nullable List<SBuildType> buildTypes,
                                                                           final @NotNull ServiceLocator serviceLocator) {
    ArrayList<Compatibility.AgentCompatibilityData> result = new ArrayList<>();
    List<SBuildType> buildTypesToProcess = buildTypes != null ? buildTypes : serviceLocator.getSingletonService(ProjectManager.class).getAllBuildTypes();
    for (final SBuildType buildType : buildTypesToProcess) {
      Compatibility.AgentCompatibilityData compatibilityData = getCompatibilityData(agent, buildType);
      if (!compatibilityData.isCompatible()) {
        result.add(compatibilityData);
      }
    }
    return result;
  }

  private static Compatibility.AgentCompatibilityData getCompatibilityData(final @NotNull SBuildAgent agent, final @NotNull SBuildType buildType) {
    if (!getAgentType(agent).getPolicy().isBuildTypeAllowed(buildType.getBuildTypeId())) {
      return new Compatibility.BasicAgentCompatibilityData(agent, buildType, false, "Restricted by agent policy");
    }
    if (!agent.getAgentPool().containsProjectId(buildType.getProjectId())) {
      return new Compatibility.BasicAgentCompatibilityData(agent, buildType, false, "Agent belongs to the pool not associated with the project");
    }

    //if (!agent.isEnabled()) //considered compatible
    //if (!agent.isRegistered()) //considered compatible
    //if (!agent.isAuthorized()) //considered compatible

    AgentCompatibility compatibility = buildType.getAgentCompatibility(agent);
    if (compatibility == null) {
      return new Compatibility.BasicAgentCompatibilityData(agent, buildType, false, "No compatibility data found");
    }
    if (!compatibility.isActive()) {
      return new Compatibility.BasicAgentCompatibilityData(agent, buildType, false, "Agent belongs to the pool not associated with the project");
    }
    if (!compatibility.isCompatible()) {
      return new Compatibility.ActualAgentCompatibilityData(compatibility, agent);
    }
    return new Compatibility.ActualAgentCompatibilityData(compatibility, agent);
  }

  public boolean canActuallyRun(@NotNull final SBuildAgent agent, @NotNull final BuildPromotion build) {
    //consider passing checkEnabled flag from outside (from agent locator), so that one can find disabled agents compatible with a build
    if (!agent.isRegistered() || !agent.isAuthorized()) return false; //is this separate check necessary?
    if (build.getCanRunOnAgents(Collections.singletonList(agent)).isEmpty()) {
      return false;
    }
    SQueuedBuild queuedBuild = build.getQueuedBuild(); // doing this after  build.getCanRunOnAgents as this does not take agent as argument
    if (queuedBuild != null) {
      if (!isAgentRestrictorAllowed(agent, queuedBuild)) return false;
      return queuedBuild.getCanRunOnAgents().stream().anyMatch(a -> AGENT_COMPARATOR.compare(a, agent) == 0);
    }

    SBuildType buildType = build.getBuildType();
    if (buildType != null && !getCompatibilityData(agent, buildType).isCompatible()) {
      return false;  //todo: optimize, as this calculates compatibility second time
    }
    return true;
  }

  private static boolean isAgentRestrictorAllowed(final @NotNull SBuildAgent agent, final @NotNull SQueuedBuild queuedBuild) {
    AgentRestrictor agentRestrictor = queuedBuild.getAgentRestrictor();
    if (agentRestrictor == null) {
      return agent.isEnabled();
    }

    if (!agentRestrictor.accept(agent)) {
      return false;
    }

    return agent.isEnabled() || agentRestrictor instanceof SingleAgentRestrictor;
  }

  private Iterable<SBuildAgent> calculateCanActuallyRunAgents(@NotNull final List<BuildPromotion> builds, final @NotNull ServiceLocator serviceLocator) {
    TreeSet<SBuildAgent> result = new TreeSet<>(AGENT_COMPARATOR);
    for (BuildPromotion build : builds) {
      SQueuedBuild queuedBuild = build.getQueuedBuild();
      if (queuedBuild != null) {
        result.addAll(queuedBuild.getCanRunOnAgents().stream().filter(a -> a.isAuthorized() && a.isRegistered() && isAgentRestrictorAllowed(a, queuedBuild)).collect(Collectors.toList()));
      } else {
        SBuildType buildType = build.getBuildType();
        if (buildType != null) {
          final List<BuildAgentEx> agents = serviceLocator.getSingletonService(BuildAgentManagerEx.class).getAllAgents();
          result.addAll(
            agents.stream().filter(a -> a.isAuthorized() && a.isRegistered() && getCompatibilityData(a, buildType).isCompatible()).collect(Collectors.toList()));
        }
      }
    }
    return result;
  }

  private boolean canActuallyRun(@NotNull final SBuildAgent agent, @NotNull final BuildPromotion build, @NotNull final CompatibilityResult compatibilityResult) {
    if (getAgentType(agent).getPolicy().isBuildTypeAllowed(build.getBuildTypeId())) {
      if (compatibilityResult.isCompatible()) return true;
    }
    return false;
  }

  public static boolean canActuallyRun(@NotNull final AgentCompatibility compatibility) {
    if (getAgentType(compatibility).getPolicy().isBuildTypeAllowed(compatibility.getBuildType().getBuildTypeId())) {
      if (compatibility.isActive() && compatibility.isCompatible()) return true;
    }
    return false;
  }

  @NotNull
  public static AgentType getAgentType(@NotNull final AgentCompatibility compatibility) {
    AgentDescription agentDescription = compatibility.getAgentDescription();
    if (agentDescription instanceof AgentType) {
      return (AgentType)agentDescription;
    }
    if (agentDescription instanceof BuildAgentEx) {
      return ((BuildAgentEx)agentDescription).getAgentType();
    }
    throw new OperationException("Unsupported agent details of type: " + agentDescription.getClass());
  }

  @NotNull
  public static SAgentType getAgentType(@NotNull final SBuildAgent agent) {
    return ((BuildAgentEx)agent).getAgentType();
  }

  public static Set<String> getAssignedBuildTypes(@NotNull final SBuildAgent agent) {
    return getAgentType(agent).getPolicy().getAllowedBuildTypes();
  }

  @NotNull
  @Override
  public ItemHolder<SBuildAgent> getPrefilteredItems(@NotNull final Locator locator) {
    setLocatorDefaults(locator);

    final String buildDimension = locator.getSingleDimensionValue(BUILD);
    if (buildDimension != null) {
      return ItemHolder.of(getBuildRelatedAgents(buildDimension));
    }

    final String cloudInstanceLocator = locator.getSingleDimensionValue(CLOUD_INSTANCE);
    if (cloudInstanceLocator != null) {
      CloudInstanceFinder cloudInstanceFinder = myServiceLocator.getSingletonService(CloudInstanceFinder.class);
      Stream<SBuildAgent> agents = cloudInstanceFinder.getItems(cloudInstanceLocator).myEntries.stream().map(CloudInstanceData::getAgent).filter(Objects::nonNull).distinct();
      return ItemHolder.of(agents);
    }

    if (TeamCityProperties.getBooleanOrTrue("rest.request.agents.compatibilityPrefilter")) { //added just in case
      final String compatible = locator.getSingleDimensionValue(COMPATIBLE);
      if (compatible != null) {
        final CompatibleLocatorParseResult compatibleData = getBuildTypesFromCompatibleDimension(compatible);
        if (compatibleData.buildTypes != null) {
          //not supported yet, but might not contribute to performance much
          locator.markUnused(COMPATIBLE);
        } else {
          assert compatibleData.buildPromotions != null;
          return ItemHolder.of(calculateCanActuallyRunAgents(compatibleData.buildPromotions, myServiceLocator));
        }
      }
    }

    final Boolean authorizedDimension = locator.getSingleDimensionValueAsBoolean(AUTHORIZED);
    final boolean includeUnauthorized = authorizedDimension == null || !authorizedDimension;

    TreeSet<SBuildAgent> result = new TreeSet<>(AGENT_COMPARATOR);

    final Boolean connectedDimension = locator.getSingleDimensionValueAsBoolean(CONNECTED);
    if (connectedDimension == null || connectedDimension) {
      result.addAll(myAgentManager.getRegisteredAgents(includeUnauthorized));
    }

    if (connectedDimension == null || !connectedDimension) {
      result.addAll(((BuildAgentManagerEx)myAgentManager).getUnregisteredAgents(includeUnauthorized));  //TeamCIty API issue: cast
    }

    return ItemHolder.of(result);
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

    if ((defaultFiltering == null || defaultFiltering) && locator.isAnyPresent(COMPATIBLE)) {
      locator.setDimensionIfNotPresent(CONNECTED, "true");
      String compatible = locator.lookupSingleDimensionValue(COMPATIBLE);
      try {
        if (new Locator(compatible).getSingleDimensionValue(COMPATIBLE_BUILD_TYPE) != null) {
          locator.setDimensionIfNotPresent(ENABLED, "true");
        }
      } catch (LocatorProcessException e) {
        //ignore
      }
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
