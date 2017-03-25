/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.google.common.collect.Ordering;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.LicenseTestUtil;
import jetbrains.buildServer.AgentRestrictorType;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.model.BuildTest;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.Agents;
import jetbrains.buildServer.server.rest.model.agent.Compatibilities;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.BuildTypes;
import jetbrains.buildServer.server.rest.request.AgentRequest;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolCannotBeRenamedException;
import jetbrains.buildServer.serverSide.agentPools.NoSuchAgentPoolException;
import jetbrains.buildServer.serverSide.agentPools.PoolQuotaExceededException;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.google.common.collect.Ordering.natural;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

/**
 * @author Yegor.Yarko
 *         Date: 13/07/2016
 */
public class BuildCompatibleAgentsTest extends BaseFinderTest<BuildPromotion> {

  private ProjectEx myProject10;
  private ProjectEx myProject20;
  private BuildTypeEx myBt10;
  private BuildTypeEx myBt40;
  private BuildTypeEx myBt50;
  private BuildTypeEx myBt60;

  private MockBuildAgent myAgent10;
  private MockBuildAgent myAgent15;
  private MockBuildAgent myAgent20;
  private MockBuildAgent myAgent30;
  private MockBuildAgent myAgent40;
  private MockBuildAgent myAgent50;
  private MockBuildAgent myAgent60;
  private MockBuildAgent myAgent70;
  private int myPoolId20;
  private BuildPromotion myBuild10;
  private BuildPromotion myBuild20;
  private BuildPromotion myBuild30;
  private BuildPromotion myBuild40;
  private BuildPromotion myBuild50;
  private BuildTypeEx myBt30;

  private ExpectedCompatibilities myExpected;
  private BuildPromotion myBuild110;
  private BuildPromotion myBuild100;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setFinder(myBuildPromotionFinder);

    if (!LicenseTestUtil.hasLicenseGenerator() && myServer.getLicensingPolicy().getMaxNumberOfAuthorizedAgents() < 6) {
      throw new SkipException("Cannot execute test logic when there is not enough agent licenses (only works in internal dev environment tests)");
    }

    myProject10 = createProject("project10", "project 10");
    myBt10 = myProject10.createBuildType("bt10", "bt 10");
    myBt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("a", null, RequirementType.EXISTS));
    myBt30 = myProject10.createBuildType("bt30", "bt 30");

    myBt40 = myProject10.createBuildType("bt40", "bt 40");
    myBt40.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("x", "1", RequirementType.EQUALS));
    myBt50 = myProject10.createBuildType("bt50", "bt 50");
    Dependency dependency = myFixture.getSingletonService(DependencyFactory.class).createDependency(myBt40);
    dependency.setOption(DependencyOptions.RUN_BUILD_ON_THE_SAME_AGENT, true);
    myBt50.addDependency(dependency);


    // not compatible
    myAgent10 = myFixture.createEnabledAgent("agent10", "Ant");
    myAgent10.addConfigParameter("x", "1");
    myAgent10.pushAgentTypeData();
    //semi-compatible
    myAgent15 = myFixture.createEnabledAgent("agent15", "Ant");
    myAgent15.addConfigParameter("a", "b");
    myAgent15.pushAgentTypeData();
    //compatible
    myAgent20 = myFixture.createEnabledAgent("agent20", "Ant");
    myAgent20.addConfigParameter("a", "b");
    myAgent20.addConfigParameter("x", "1");
    myAgent20.pushAgentTypeData();

    //compatible, but in another pool
    myAgent30 = myFixture.createEnabledAgent("agent30", "Ant");
    myAgent30.addConfigParameter("a", "b");
    myAgent30.addConfigParameter("x", "2");
    myAgent30.pushAgentTypeData();

    final int poolId1 = myFixture.getAgentPoolManager().createNewAgentPool("pool1").getAgentPoolId(); //pool without projects
    myFixture.getAgentPoolManager().moveAgentTypesToPool(poolId1, createSet(myAgent30.getId()));

    //compatible, but excluded by policy - can only run myBt30
    myAgent40 = myFixture.createEnabledAgent("agent40", "Ant");
    myAgent40.addConfigParameter("a", "b");
    myAgent40.addConfigParameter("x", "1");
    myAgent40.pushAgentTypeData();
    myFixture.getAgentTypeManager().setRunConfigurationPolicy(myAgent40.getAgentTypeId(), BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS);
    myFixture.getAgentTypeManager().includeRunConfigurationsToAllowed(myAgent40.getAgentTypeId(), new String[]{myBt30.getInternalId()});
    myFixture.getAgentTypeManager().excludeRunConfigurationsFromAllowed(myAgent40.getAgentTypeId(), new String[]{myBt10.getInternalId()});

    //compatible, but unauthorized
    myAgent50 = myFixture.createEnabledAgent("agent50", "Ant");
    myAgent50.addConfigParameter("a", "b");
    myAgent50.addConfigParameter("x", "2");
    myAgent50.pushAgentTypeData();
    myAgent50.setAuthorized(false, null, "");

    //compatible, but disabled
    myAgent60 = myFixture.createEnabledAgent("agent60", "Ant");
    myAgent60.addConfigParameter("a", "b");
    myAgent60.addConfigParameter("x", "2");
    myAgent60.pushAgentTypeData();
    myAgent60.setEnabled(false, null, "");

    //compatible, but disconnected
    myAgent70 = myFixture.createEnabledAgent("agent70", "Ant");
    myAgent70.addConfigParameter("a", "b");
    myAgent70.addConfigParameter("x", "2");
    myAgent70.pushAgentTypeData();
    myAgent70.setIsAvailable(false);
    myAgent70.setUnregistrationComment("comment");
    myAgentManager.unregisterAgent(myAgent70.getId());


    myPoolId20 = myFixture.getAgentPoolManager().createNewAgentPool("pool20").getAgentPoolId();  //pool without agents
    myFixture.getAgentPoolManager().associateProjectsWithPool(myPoolId20, new HashSet<String>(Arrays.asList(String.valueOf(myProject10.getProjectId()))));

    myProject20 = createProject("project20", "project 20");
    myBt60 = myProject20.createBuildType("bt60", "bt 60");
    int poolId30 = myFixture.getAgentPoolManager().createNewAgentPool("pool30").getAgentPoolId();  //pool without agents
    myFixture.getAgentPoolManager().associateProjectsWithPool(poolId30, new HashSet<String>(Arrays.asList(String.valueOf(myProject20.getProjectId()))));
    myFixture.getAgentPoolManager().dissociateProjectsFromOtherPools(poolId30, new HashSet<String>(Arrays.asList(String.valueOf(myProject20.getProjectId()))));


    myBuild10 = build().in(myBt10).addToQueue().getBuildPromotion();
    myBuild20 = build().in(myBt10).parameter("param1", "%x%").addToQueue().getBuildPromotion();
    myBuild30 = build().in(myBt10).on(myAgent60).addToQueue().getBuildPromotion();
    myBuild40 = build().in(myBt10).parameter("param1", "%abra%").addToQueue().getBuildPromotion();

    final BuildCustomizer customizer = myFixture.getSingletonService(BuildCustomizerFactory.class).createBuildCustomizer(myBt10, null);
    SAgentRestrictor agentRestrictor = myFixture.getSingletonService(AgentRestrictorFactory.class).createFor(AgentRestrictorType.AGENT_POOL, myPoolId20);
    myBuild50 = myBt10.addToQueue(agentRestrictor, (BuildPromotionEx)customizer.createPromotion(), new TriggeredByBuilder().toString()).getBuildPromotion();

    myExpected = new ExpectedCompatibilities();
    myExpected.setAllBuildTypes(myBuildType, myBt10, myBt30, myBt40, myBt50, myBt60);
    myExpected.setAllBuilds(myBuild10, myBuild20, myBuild30, myBuild40, myBuild50);

//should be    myExpected.add(ExpectedCompatibility.agent(myBuildAgent).buildTypes(myBuildType, myBt30).builds());
    myExpected.add(ExpectedCompatibility.agent(myBuildAgent).buildTypes(myBuildType, myBt30, myBt50).builds());  //current behavior: "run on the same agent" for snapshot dep is ignored
    myExpected.add(ExpectedCompatibility.agent(myAgent10).buildTypes(myBuildType, myBt30, myBt40, myBt50).builds());
    myExpected.add(ExpectedCompatibility.agent(myAgent15).buildTypes(myBuildType, myBt10, myBt30, myBt50).builds(myBuild10));
    myExpected.add(ExpectedCompatibility.agent(myAgent20).buildTypes(myBuildType, myBt10, myBt30, myBt40, myBt50).builds(myBuild10, myBuild20));
    myExpected.add(ExpectedCompatibility.agent(myAgent30).buildTypes().builds());
    myExpected.add(ExpectedCompatibility.agent(myAgent40).buildTypes(myBt30).builds());
    myExpected.add(ExpectedCompatibility.agent(myAgent50).buildTypes().builds());
    myExpected.add(ExpectedCompatibility.agent(myAgent60).buildTypes().builds(myBuild30));
    myExpected.add(ExpectedCompatibility.agent(myAgent70).buildTypes().builds());
  }

  private void patchExpectationsForAgentRequests() {
    //unauthorized, disconnected, disabled agents provide compatibility when requested
    myExpected.replace(ExpectedCompatibility.agent(myAgent50).buildTypes(myBuildType, myBt10, myBt30, myBt50));
    myExpected.replace(ExpectedCompatibility.agent(myAgent60).buildTypes(myBuildType, myBt10, myBt30, myBt50).builds(myBuild10, myBuild20, myBuild30));
    myExpected.replace(ExpectedCompatibility.agent(myAgent70).buildTypes(myBuildType, myBt10, myBt30, myBt50).builds(myBuild10, myBuild20, myBuild30));
  }

  private void addSnapshotBuilds() {
    myBuild110 = build().in(myBt50).addToQueue().getBuildPromotion();
    myBuild100 = myBuild110.getDependencies().iterator().next().getDependOn();

    myExpected.setAllBuilds(Stream.concat(myExpected.getAllBuilds().stream(), Stream.of(myBuild100, myBuild110)).toArray(BuildPromotion[]::new));

// should not be necessary
    myExpected.add(ExpectedCompatibility.agent(myBuildAgent).builds(myBuild110));  //current behavior: "run on the same agent" for snapshot dep is ignored
    myExpected.add(ExpectedCompatibility.agent(myAgent10).builds(myBuild100, myBuild110));
// should not be necessary
    myExpected.add(ExpectedCompatibility.agent(myAgent15).builds(myBuild110));  //current behavior: "run on the same agent" for snapshot dep is ignored
    myExpected.add(ExpectedCompatibility.agent(myAgent20).builds(myBuild100, myBuild110));
  }

  static class ExpectedCompatibilities {
    @NotNull private List<ExpectedCompatibility> myCompatibilities = new ArrayList<>();
    @NotNull private List<SBuildType> myAllBuildTypes = new ArrayList<>();
    @NotNull private List<BuildPromotion> myAllBuilds = new ArrayList<>();

    protected static final Ordering<SBuildAgent> AGENT_ORDERING = natural().reverse().nullsLast().onResultOf((SBuildAgent item) -> item.getId())
                                                                           .compound(natural().reverse().onResultOf((SBuildAgent item) -> item.getAgentTypeId()))
                                                                           .compound(natural().reverse().onResultOf((SBuildAgent item) -> item.getName()));

    public void add(@NotNull ExpectedCompatibility compatibility) {
      Optional<ExpectedCompatibility> first = myCompatibilities.stream().filter(c -> AGENT_ORDERING.compare(c.myAgent, compatibility.myAgent) == 0).findFirst();
      if (first.isPresent()) {
        first.get().buildTypes(compatibility.myBuildTypes).builds(compatibility.myBuilds);
      } else {
        myCompatibilities.add(compatibility);
      }
    }

    public void replace(@NotNull ExpectedCompatibility compatibility) {
      Optional<ExpectedCompatibility> first = myCompatibilities.stream().filter(c -> AGENT_ORDERING.compare(c.myAgent, compatibility.myAgent) == 0).findFirst();
      if (first.isPresent()) {
        first.get().reset().buildTypes(compatibility.myBuildTypes).builds(compatibility.myBuilds);
      } else {
        myCompatibilities.add(compatibility);
      }
    }


    public SBuildType[] compatibleBuildTypes(@NotNull SBuildAgent agent) {
      return myCompatibilities.stream().filter(c -> AGENT_ORDERING.compare(c.myAgent, agent) == 0).findFirst().get().myBuildTypes;
    }

    public SBuildType[] incompatibleBuildTypes(@NotNull SBuildAgent agent) {
      ArrayList<SBuildType> result = new ArrayList<>(getAllBuildTypes());
      result.removeAll(asList(compatibleBuildTypes(agent)));
      return result.toArray(new SBuildType[result.size()]);
    }

    public BuildPromotion[] compatibleBuilds(@NotNull SBuildAgent agent) {
      return myCompatibilities.stream().filter(c -> AGENT_ORDERING.compare(c.myAgent, agent) == 0).findFirst().get().myBuilds;
    }

    public BuildPromotion[] incompatibleBuilds(@NotNull SBuildAgent agent) {
      ArrayList<BuildPromotion> result = new ArrayList<>(getAllBuilds());
      result.removeAll(asList(compatibleBuilds(agent)));
      return result.toArray(new BuildPromotion[result.size()]);
    }

    public SBuildAgent[] compatibleAgents(@NotNull SBuildType buildType) {
      return myCompatibilities.stream().filter(c -> asList(c.myBuildTypes).contains(buildType)).map(c -> c.myAgent).toArray(size -> new SBuildAgent[size]);
    }

    public SBuildAgent[] incompatibleAgents(@NotNull SBuildType buildType) {
      ArrayList<SBuildAgent> result = new ArrayList<>(getAllAgents());
      result.removeAll(asList(compatibleAgents(buildType)));
      return result.toArray(new SBuildAgent[result.size()]);
    }

    public SBuildAgent[] compatibleAgents(@NotNull BuildPromotion build) {
      return myCompatibilities.stream().filter(c -> asList(c.myBuilds).contains(build)).map(c -> c.myAgent).toArray(size -> new SBuildAgent[size]);
    }

    public SBuildAgent[] incompatibleAgents(@NotNull BuildPromotion build) {
      ArrayList<SBuildAgent> result = new ArrayList<>(getAllAgents());
      result.removeAll(asList(compatibleAgents(build)));
      return result.toArray(new SBuildAgent[result.size()]);
    }

    @NotNull
    public List<SBuildType> getAllBuildTypes() {
      return myAllBuildTypes;
    }

    @NotNull
    public List<BuildPromotion> getAllBuilds() {
      return myAllBuilds;
    }

    @NotNull
    public List<SBuildAgent> getAllAgents() {
      return myCompatibilities.stream().map(c -> c.myAgent).collect(Collectors.toList());
    }

    public void setAllBuildTypes(@NotNull final SBuildType... buildTypes) {
      myAllBuildTypes = Arrays.asList(buildTypes);
    }

    public void setAllBuilds(@NotNull final BuildPromotion... builds) {
      myAllBuilds = Arrays.asList(builds);
    }
  }

  static class ExpectedCompatibility {
    @NotNull SBuildAgent myAgent;
    @NotNull SBuildType[] myBuildTypes = new SBuildType[0];
    @NotNull BuildPromotion[] myBuilds = new BuildPromotion[0];

    private ExpectedCompatibility() {
    }

    static ExpectedCompatibility agent(@NotNull SBuildAgent agent) {
      ExpectedCompatibility result = new ExpectedCompatibility();
      result.myAgent = agent;
      return result;
    }

    ExpectedCompatibility reset() {
      myBuildTypes = new SBuildType[0];
      myBuilds = new BuildPromotion[0];
      return this;
    }

    ExpectedCompatibility buildTypes(@NotNull final SBuildType... buildTypes) {
      if (myBuildTypes.length == 0) {
        myBuildTypes = buildTypes;
      } else {
        myBuildTypes = Stream.concat(Arrays.stream(myBuildTypes), Arrays.stream(buildTypes)).toArray(SBuildType[]::new);
      }
      return this;
    }

    ExpectedCompatibility builds(@NotNull final BuildPromotion... builds) {
      if (myBuilds.length == 0) {
        myBuilds = builds;
      } else {
        myBuilds = Stream.concat(Arrays.stream(myBuilds), Arrays.stream(builds)).toArray(BuildPromotion[]::new);
      }
      return this;
    }
  }

  @Test
  public void testBuildPromotionFinder() throws AgentPoolCannotBeRenamedException, PoolQuotaExceededException, NoSuchAgentPoolException {
    for (SBuildAgent agent : myExpected.getAllAgents()) {
      checkBuilds("compatibleAgent:(name:" + agent.getName() + "),state:queued", myExpected.compatibleBuilds(agent));
    }
    assertTrue(myExpected.compatibleBuilds(myAgent20).length > 0);
    checkBuilds("compatibleAgent:(name:" + myAgent20.getName() + ")"); //queued not included by default

    for (int i = 0; i < myExpected.getAllAgents().size() + 1; i++) {
      final int finalI = i;
      checkBuilds("compatibleAgentsCount:" + i + ",state:queued",
                  myExpected.getAllBuilds().stream().filter(b -> myExpected.compatibleAgents(b).length == finalI).toArray(s -> new BuildPromotion[s]));
    }
  }

  @Test
  public void testBuildPromotionFinder_QueuedBuildsWithSnapshotDep() throws AgentPoolCannotBeRenamedException, PoolQuotaExceededException, NoSuchAgentPoolException {
    addSnapshotBuilds();

    for (SBuildAgent agent : myExpected.getAllAgents()) {
      checkBuilds("compatibleAgent:(name:" + agent.getName() + "),state:queued", myExpected.compatibleBuilds(agent));
    }
  }

  @Test
  public void testBuildEntity() throws AgentPoolCannotBeRenamedException, PoolQuotaExceededException, NoSuchAgentPoolException {
    addSnapshotBuilds();

    for (BuildPromotion build : myExpected.getAllBuilds()) {
      assertCollectionEquals("build id: " + build.getId(),
                             new Build(build, new Fields("compatibleAgents($long)"), getBeanContext(myFixture)).getCompatibleAgents(), myExpected.compatibleAgents(build));
    }

    //test node presence,  not included by default
    assertNull(new Build(myBuild10, new Fields("$short"), getBeanContext(myFixture)).getCompatibleAgents());
    assertNotNull(new Build(myBuild10, new Fields("$long"), getBeanContext(myFixture)).getCompatibleAgents());
    assertNull(new Build(myBuild10, new Fields("$long"), getBeanContext(myFixture)).getCompatibleAgents().agents);
    assertNull(new Build(myBuild10, new Fields("$long"), getBeanContext(myFixture)).getCompatibleAgents().count);
    assertNotNull(new Build(myBuild10, new Fields("$long"), getBeanContext(myFixture)).getCompatibleAgents().href); //not included by default
    assertCollectionEquals(null, new Build(myBuild10, new Fields("compatibleAgents(agent)"), getBeanContext(myFixture)).getCompatibleAgents(),
                           myExpected.compatibleAgents(myBuild10));
    assertEquals(Integer.valueOf(myExpected.compatibleAgents(myBuild10).length),
                 new Build(myBuild10, new Fields("compatibleAgents(count)"), getBeanContext(myFixture)).getCompatibleAgents().count);
  }

  @Test
  public void testAgentFinder() throws AgentPoolCannotBeRenamedException, PoolQuotaExceededException, NoSuchAgentPoolException {
    addSnapshotBuilds();

    for (BuildPromotion build : myExpected.getAllBuilds()) {
      checkAgents("compatible:(build:(id:" + build.getId() + "))", myExpected.compatibleAgents(build));
    }

    for (BuildPromotion build : myExpected.getAllBuilds()) {
      checkAgents("authorized:any,incompatible:(build:(id:" + build.getId() + "))", myExpected.incompatibleAgents(build));
    }
    checkAgents("authorized:any,incompatible:(build:(id:" + myBuild10.getId() + "))", myBuildAgent, myAgent10, myAgent30, myAgent40, myAgent50, myAgent60, myAgent70);
    checkAgents("incompatible:(build:(id:" + myBuild10.getId() + "))", myBuildAgent, myAgent10, myAgent30, myAgent40, myAgent60, myAgent70);


    for (SBuildType buildType : myExpected.getAllBuildTypes()) {
      checkAgents("compatible:(buildType:(id:" + buildType.getExternalId() + "))", myExpected.compatibleAgents(buildType));
    }

    patchExpectationsForAgentRequests();

    checkAgents("compatible:(buildType:(id:" + myBt10.getExternalId() + "))", myAgent15, myAgent20);
    checkAgents("enabled:any,compatible:(buildType:(id:" + myBt10.getExternalId() + "))", myAgent15, myAgent20, myAgent60);
    checkAgents("authorized:any,compatible:(buildType:(id:" + myBt10.getExternalId() + "))", myAgent15, myAgent20, myAgent50);
    checkAgents("connected:any,authorized:any,compatible:(buildType:(id:" + myBt10.getExternalId() + "))", myAgent15, myAgent20, myAgent50, myAgent70);

    for (SBuildType buildType : myExpected.getAllBuildTypes()) {
      checkAgents("authorized:any,incompatible:(buildType:(id:" + buildType.getExternalId() + "))", myExpected.incompatibleAgents(buildType));
    }
    //unfortunately, there is no way to treat unauthorized and disconnected as not compatible for "incompatible"  query
    checkAgents("authorized:any,connected:any,incompatible:(buildType:(id:" + myBt10.getExternalId() + "))", myBuildAgent, myAgent10, myAgent30, myAgent40);
    checkAgents("incompatible:(buildType:(id:" + myBt10.getExternalId() + "))", myBuildAgent, myAgent10, myAgent30, myAgent40);

//    checkAgents("compatible:(buildType:(id:" + myBt10.getExternalId() + "),buildType:(id:" + myBt40.getExternalId() + "))", myAgent20); is not supported so far
    checkAgents("compatible:(buildType:(id:" + myBt10.getExternalId() + ")),and(compatible:(buildType:(id:" + myBt40.getExternalId() + ")))", myAgent20);
    checkAgents("compatible:(buildType:(item:(id:" + myBt10.getExternalId() + "),item:(id:" + myBt40.getExternalId() + ")))", myAgent10, myAgent15, myAgent20);

    checkAgents("compatible:(buildType:(id:" + myBt10.getExternalId() + ")),incompatible:(buildType:(id:" + myBt40.getExternalId() + "))", myAgent15);
  }

  @Test
  public void testBuildTypeEntity() throws Exception {
    for (SBuildType buildType : myExpected.getAllBuildTypes()) {
      assertCollectionEquals("build type id=" + buildType.getExternalId(),
                             new BuildType(new BuildTypeOrTemplate(buildType), new Fields("compatibleAgents($long)"), getBeanContext(myFixture)).getCompatibleAgents(),
                             myExpected.compatibleAgents(buildType));
    }

    myExpected.replace(ExpectedCompatibility.agent(myAgent50).buildTypes(myBuildType, myBt10, myBt30, myBt50));
    for (SBuildType buildType : myExpected.getAllBuildTypes()) {
      assertCollectionEquals("build type id=" + buildType.getExternalId(),
                             new BuildType(new BuildTypeOrTemplate(buildType), new Fields("compatibleAgents($long,$locator(authorized:any))"), getBeanContext(myFixture)).getCompatibleAgents(),
                             myExpected.compatibleAgents(buildType));
    }
  }

  @Test
  public void testAgentEntity() throws AgentPoolCannotBeRenamedException, PoolQuotaExceededException, NoSuchAgentPoolException {
    addSnapshotBuilds();
    patchExpectationsForAgentRequests();

    for (SBuildAgent agent : myExpected.getAllAgents()) {
      assertCollectionEquals(agent.getName(), new Agent(agent, myAgentPoolFinder, new Fields("compatibleBuildTypes($long)"), getBeanContext(myFixture)).compatibleBuildTypes,
                             myExpected.compatibleBuildTypes(agent));
    }

    //test node presence
    assertNull(new Agent(myBuildAgent, myAgentPoolFinder, new Fields("$short"), getBeanContext(myFixture)).compatibleBuildTypes); //not included by default
    assertNull(new Agent(myBuildAgent, myAgentPoolFinder, new Fields("$long"), getBeanContext(myFixture)).compatibleBuildTypes); //not included by default, todo: add href by default
    assertCollectionEquals(null, new Agent(myBuildAgent, myAgentPoolFinder, new Fields("compatibleBuildTypes(buildType)"), getBeanContext(myFixture)).compatibleBuildTypes,
                           myExpected.compatibleBuildTypes(myBuildAgent));
    assertEquals(Integer.valueOf(myExpected.compatibleBuildTypes(myBuildAgent).length),
                 new Agent(myBuildAgent, myAgentPoolFinder, new Fields("compatibleBuildTypes(count)"), getBeanContext(myFixture)).compatibleBuildTypes.count);

    for (SBuildAgent agent : myExpected.getAllAgents()) {
      assertCollectionEquals(agent.getName(), new Agent(agent, myAgentPoolFinder, new Fields("incompatibleBuildTypes($long)"), getBeanContext(myFixture)).incompatibleBuildTypes,
                             myExpected.incompatibleBuildTypes(agent));
    }

    assertNull(new Agent(myBuildAgent, myAgentPoolFinder, new Fields("$short"), getBeanContext(myFixture)).incompatibleBuildTypes); //not included by default
    assertNull(new Agent(myBuildAgent, myAgentPoolFinder, new Fields("$long"), getBeanContext(myFixture)).incompatibleBuildTypes); //not included by default
    assertCollectionEquals(null, new Agent(myBuildAgent, myAgentPoolFinder, new Fields("incompatibleBuildTypes(compatibility)"), getBeanContext(myFixture)).incompatibleBuildTypes,
                           myExpected.incompatibleBuildTypes(myBuildAgent));
    assertEquals(Integer.valueOf(myExpected.incompatibleBuildTypes(myBuildAgent).length),
                 new Agent(myBuildAgent, myAgentPoolFinder, new Fields("incompatibleBuildTypes(count)"), getBeanContext(myFixture)).incompatibleBuildTypes.count);
  }

  @Test
  public void testAgentRequest() {
    AgentRequest resource = AgentRequest.createForTests(getBeanContext(myFixture));

    patchExpectationsForAgentRequests();

    for (SBuildAgent agent : myExpected.getAllAgents()) {
      assertCollectionEquals(agent.getName(), resource.getCompatibleBuildTypes("id:" + agent.getId(), null), myExpected.compatibleBuildTypes(agent));
    }

    for (SBuildAgent agent : myExpected.getAllAgents()) {
      assertCollectionEquals(agent.getName(), resource.geIncompatibleBuildTypes("id:" + agent.getId(), null), myExpected.incompatibleBuildTypes(agent));
    }
  }

  @Test
  public void testBuildTypeFinder() throws Exception {
    BuildTypeEx bt90 = myProject10.createBuildType("bt90", "bt 90");
    bt90.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("abra", "1", RequirementType.EQUALS));
    myExpected.setAllBuildTypes(myBuildType, myBt10, myBt30, myBt40, myBt50, bt90, myBt60); //order is important here

    for (int i = 0; i < myExpected.getAllAgents().size() + 1; i++) {
      final int finalI = i;
      checkBuildTypes("compatibleAgentsCount:" + i + "",
                  myExpected.getAllBuildTypes().stream().filter(b -> myExpected.compatibleAgents(b).length == finalI).toArray(s -> new SBuildType[s]));
    }

    patchExpectationsForAgentRequests();

    for (SBuildAgent agent : myExpected.getAllAgents()) {
      checkBuildTypes("compatibleAgent:(name:" + agent.getName() + ")", myExpected.compatibleBuildTypes(agent));
    }

    checkBuildTypes("compatibleAgent:(item:(id:" + myAgent10.getId() + "),item:(id:" + myAgent15.getId() + "))", myBuildType, myBt10, myBt30, myBt40, myBt50);
  }

  //todo: tests to add
  // agentType with several agents, agentType has different compatibility
  // under user who does not have permissions to see all (regular tests, tests with "*Count")


  //==================================================

  public static <E, A> void assertCollectionEquals(final String description, @Nullable final Agents actual, final SBuildAgent... expected) {
    BuildTest.assertEquals(description, actual == null ? null : actual.agents, AGENTS_EQUALS, (e) -> e.describe(false),
                           (e) -> "id: " + String.valueOf(e.id) + ", name: " + String.valueOf(e.name), expected);
  }

  public static <E, A> void assertCollectionEquals(final String description, @Nullable final BuildTypes actual, final SBuildType... expected) {
    BuildTest
      .assertEquals(description, actual == null ? null : actual.buildTypes, BUILD_TYPES_EQUALS, (e) -> LogUtil.describe(e), (e) -> "id: " + String.valueOf(e.getId()), expected);
  }

  public static <E, A> void assertCollectionEquals(final String description, @Nullable Compatibilities actual, final SBuildType... expected) {
    BuildTest.assertEquals(description, actual == null ? null : actual.compatibilities.stream().map(t -> t.buildType).collect(toList()), BUILD_TYPES_EQUALS,
                           (e) -> "id: " + e.getExternalId(), (e) -> "id: " + String.valueOf(e.getId()), expected);
  }

  protected static final BuildTest.EqualsTest<SBuildAgent, Agent> AGENTS_EQUALS =
    (o1, o2) -> o1.getId() == o2.id && o1.getAgentTypeId() == o2.typeId && o1.getName().equals(o2.name);
  protected static final BuildTest.EqualsTest<SBuildType, BuildType> BUILD_TYPES_EQUALS = (o1, o2) -> o1.getExternalId() == o2.getId();

  public void checkBuilds(@Nullable final String locator, BuildPromotion... items) {
    check(locator, getEqualsMatcher(), (r) -> LogUtil.describe(r), (r) -> LogUtil.describe(r), myBuildPromotionFinder, items);
  }

  public void checkBuildTypes(@Nullable final String locator, SBuildType... items) {
    check(locator, (s, r) -> s.getExternalId().equals(r.getId()),
          (BuildTypeOrTemplate r) -> "id: " + r.getId(), (SBuildType r) -> "id: " + r.getExternalId(), myBuildTypeFinder, items);
  }

  private void checkAgents(final String locatorText, final SBuildAgent... agents) {
    check(locatorText, new Matcher<SBuildAgent, SBuildAgent>() {
      @Override
      public boolean matches(@NotNull final SBuildAgent sBuildAgent, @NotNull final SBuildAgent sBuildAgent2) {
        return sBuildAgent.getId() == sBuildAgent2.getId();
      }
    }, (r) -> r.describe(false), (r) -> r.describe(false), myAgentFinder, agents);
  }
}
