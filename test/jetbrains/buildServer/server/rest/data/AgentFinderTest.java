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

import java.util.List;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.util.TestFor;
import org.jetbrains.annotations.NotNull;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 17.10.2014
 */
@Test
public class AgentFinderTest extends BaseFinderTest<SBuildAgent> {
  private MockBuildAgent myAgent1;
  private MockBuildAgent myAgent2;
  private MockBuildAgent myAgent3;
  private MockBuildAgent myAgent4;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    setFinder(myAgentFinder);

    final List<BuildAgentEx> currentAgents = myAgentManager.getAllAgents(true);
    assertEquals(1, currentAgents.size());

    myAgent1 = (MockBuildAgent)currentAgents.get(0);          //connected, authorized

    myAgent2 = myFixture.createEnabledAgent("agent2", "Ant"); //disconnected
    myAgent2.setIsAvailable(false);
    myAgentManager.unregisterAgent(myAgent2.getId());

    myAgent3 = myFixture.createEnabledAgent("agent3", "Ant"); //connected, unauthorized
    myAgent3.setAuthorized(false, null, "test");

    myAgent4 = myFixture.createEnabledAgent("agent4", "Ant"); //disconnected, unauthorized
    myAgent4.setIsAvailable(false);
    myAgentManager.unregisterAgent(myAgent4.getId());
    myAgent4.setAuthorized(false, null, "test");
  }

  @Test
  public void testEmptyLocator() {
    checkAgents(null, myAgent1, myAgent2);
  }

  @Test
  public void testLocatorConnected() {
    checkAgents("connected:true", myAgent1);
    checkAgents("connected:true,authorized:any", myAgent1, myAgent3);
    checkAgents("connected:false", myAgent2);
    checkAgents("connected:any", myAgent1, myAgent2);
    checkAgents("connected:any,authorized:any", myAgent1, myAgent2, myAgent3, myAgent4);
  }

  @Test
  public void testLocatorAuthorized() {
    checkAgents("authorized:true", myAgent1, myAgent2);
    checkAgents("authorized:false", myAgent3, myAgent4);
    checkAgents("authorized:any", myAgent1, myAgent2, myAgent3, myAgent4);
  }

  @Test
  public void testLocatorMixed1() {
    checkAgents("connected:true,authorized:true", myAgent1);
    checkAgents("connected:false,authorized:true", myAgent2);
    checkAgents("connected:true,authorized:false", myAgent3);
    checkAgents("connected:false,authorized:false", myAgent4);

    checkAgents("enabled:true,connected:true,authorized:true", myAgent1);
    myAgent1.setEnabled(false, null, "");
    checkAgents("enabled:true,connected:true,authorized:true");
    checkAgents("enabled:any,connected:true,authorized:true", myAgent1);
    checkAgents("enabled:false,connected:true,authorized:true", myAgent1);
  }

  @Test
  public void testMisc() {
    myAgent1.addConfigParameter("x", "1");
    myAgent1.pushAgentTypeData();

    MockBuildAgent agent10 = myFixture.createEnabledAgent("agent10", "Ant");
    agent10.addConfigParameter("a", "b");
    agent10.addConfigParameter("x", "1");
    agent10.pushAgentTypeData();

    checkAgents(null, myAgent1, myAgent2, agent10);
    checkAgents("name:" + myAgent1.getName(), myAgent1);
    checkAgents("name:" + myAgent3.getName(), myAgent3);
    checkAgents("id:" + myAgent1.getId(), myAgent1);
    checkAgents("id:" + myAgent3.getId(), myAgent3);

    checkAgents("parameter:(name:a)", agent10);
    checkAgents("parameter:(name:zzz,value:1,matchType:does-not-equal,matchScope:all)", myAgent1, myAgent2, agent10);

    myAgent2.addConfigParameter("x", "3");
    myAgent2.pushAgentTypeData();

    checkAgents("parameter:(name:(matchType:any),value:1,matchType:does-not-equal,matchScope:all)", myAgent2);
  }

  @Test
  public void testLocatorPool() throws Exception {
    final int poolId1 = myFixture.getAgentPoolManager().createNewAgentPool("pool1").getAgentPoolId();
    myFixture.getAgentPoolManager().moveAgentTypesToPool(poolId1, createSet(myAgent3.getId()));

    checkAgents("pool:(id:" + poolId1 + "),defaultFilter:false", myAgent3);
    checkAgents("pool:(id:0),defaultFilter:false", myAgent1, myAgent2, myAgent4);
  }

  @Test
  public void testLocatorCompatible3AgentLimit() throws Exception {
    ProjectEx project10 = createProject("project10", "project 10");
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");
    bt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("a", null, RequirementType.EXISTS));
    BuildTypeEx bt20 = project10.createBuildType("bt20", "bt 20");
    BuildTypeEx bt30 = project10.createBuildType("bt30", "bt 30");
    bt30.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("x", "1", RequirementType.EQUALS));

    myAgent1.addConfigParameter("x", "1");
    myAgent1.pushAgentTypeData();

    myAgent1.setAuthorized(false, null, "");
    myAgent2.setAuthorized(false, null, "");

    MockBuildAgent agent10 = myFixture.createEnabledAgent("agent10", "Ant");
    agent10.addConfigParameter("a", "b");
    agent10.addConfigParameter("x", "1");
    agent10.pushAgentTypeData();
    MockBuildAgent agent15 = myFixture.createEnabledAgent("agent15", "Ant");
    agent15.addConfigParameter("a", "b");
    agent15.pushAgentTypeData();
    MockBuildAgent agent20 = myFixture.createEnabledAgent("agent20", "Ant");
    agent20.addConfigParameter("a", "b");
    agent20.pushAgentTypeData();
    agent20.setAuthorized(false, null, "");
    final int poolId1 = myFixture.getAgentPoolManager().createNewAgentPool("pool1").getAgentPoolId();
    myFixture.getAgentPoolManager().moveAgentTypesToPool(poolId1, createSet(agent20.getId()));

    MockBuildAgent agent30 = myFixture.createEnabledAgent("agent30", "Ant");
    agent30.addConfigParameter("a", "b");
    agent30.pushAgentTypeData();
    agent30.setAuthorized(false, null, "");
    myFixture.getAgentTypeManager().setRunConfigurationPolicy(agent30.getAgentTypeId(), BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS);
    myFixture.getAgentTypeManager().excludeRunConfigurationsFromAllowed(agent30.getAgentTypeId(), new String[]{bt10.getInternalId(), bt20.getInternalId()});

    MockBuildAgent agent40 = myFixture.createEnabledAgent("agent40", "Ant");
    agent40.setAuthorized(false, null, "");

    checkAgents("defaultFilter:false", myAgent1, myAgent2, myAgent3, myAgent4, agent10, agent15, agent20, agent30, agent40);
    checkAgents(null, agent10, agent15);
    checkAgents("authorized:any", myAgent1, myAgent2, myAgent3, myAgent4, agent10, agent15, agent20, agent30, agent40);
    checkAgents("compatible:(buildType:(id:" + bt10.getExternalId() + ")),authorized:any", agent10, agent15);
    checkAgents("compatible:(buildType:(id:" + bt30.getExternalId() + ")),authorized:any", myAgent1, agent10);
    checkAgents("compatible:(buildType:(item:(id:" + bt10.getExternalId() + "),item:(id:" + bt30.getExternalId() + "))),authorized:any", myAgent1, agent10, agent15);

    checkAgents("incompatible:(buildType:(id:" + bt10.getExternalId() + ")),authorized:any", myAgent1, myAgent2, myAgent3, myAgent4, agent20, agent30, agent40);
    checkAgents("incompatible:(buildType:(item:(id:" + bt10.getExternalId() + "),item:(id:" + bt30.getExternalId() + "))),authorized:any",
                myAgent1, myAgent2, myAgent3, myAgent4, agent15, agent20, agent30, agent40);

    checkAgents("compatible:(buildType:(id:" + bt30.getExternalId() + ")),incompatible:(buildType:(id:" + bt10.getExternalId() + ")),authorized:any", myAgent1);
  }

  @Test
  public void testLocatorCompatible() throws Exception {
    ProjectEx project10 = createProject("project10", "project 10");
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");
    bt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("a", null, RequirementType.EXISTS));
    BuildTypeEx bt20 = project10.createBuildType("bt20", "bt 20");
    BuildTypeEx bt30 = project10.createBuildType("bt30", "bt 30");
    bt30.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("x", "1", RequirementType.EQUALS));

    myAgent1.addConfigParameter("x", "1");
    myAgent1.pushAgentTypeData();

    MockBuildAgent agent10 = myFixture.createEnabledAgent("agent10", "Ant");
    agent10.addConfigParameter("a", "b");
    agent10.addConfigParameter("x", "1");
    agent10.pushAgentTypeData();

    if (myServer.getLicensingPolicy().getMaxNumberOfAuthorizedAgents() < 4){
      throw new SkipException("Cannot execute test logic when there is not enough agent licenses (only works in internal dev environment tests)");
    }

    MockBuildAgent agent15 = myFixture.createEnabledAgent("agent15", "Ant");
    agent15.addConfigParameter("a", "b");
    agent15.pushAgentTypeData();
    MockBuildAgent agent20 = myFixture.createEnabledAgent("agent20", "Ant");
    agent20.addConfigParameter("a", "b");
    agent20.pushAgentTypeData();
    final int poolId1 = myFixture.getAgentPoolManager().createNewAgentPool("pool1").getAgentPoolId();
    myFixture.getAgentPoolManager().moveAgentTypesToPool(poolId1, createSet(agent20.getId()));

    MockBuildAgent agent30 = myFixture.createEnabledAgent("agent30", "Ant");
    agent30.addConfigParameter("a", "b");
    agent30.pushAgentTypeData();
    myFixture.getAgentTypeManager().setRunConfigurationPolicy(agent30.getAgentTypeId(), BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS);
    myFixture.getAgentTypeManager().excludeRunConfigurationsFromAllowed(agent30.getAgentTypeId(), new String[]{bt10.getInternalId(), bt20.getInternalId()});

    MockBuildAgent agent40 = myFixture.createEnabledAgent("agent40", "Ant");

    MockBuildAgent agent50 = myFixture.createEnabledAgent("agent50", "Ant");
    agent50.addConfigParameter("a", "b");
    agent50.pushAgentTypeData();
    agent50.setEnabled(false, null, "");

    checkAgents("defaultFilter:false", myAgent1, myAgent2, myAgent3, myAgent4, agent10, agent15, agent20, agent30, agent40, agent50);
    checkAgents(null, myAgent1, myAgent2, agent10, agent15, agent20, agent30, agent40, agent50);
    checkAgents("compatible:(buildType:(id:" + bt10.getExternalId() + "))", agent10, agent15);
    checkAgents("enabled:any,compatible:(buildType:(id:" + bt10.getExternalId() + "))", agent10, agent15, agent50);
    checkAgents("compatible:(buildType:(id:" + bt30.getExternalId() + "))", myAgent1, agent10);
    checkAgents("compatible:(buildType:(item:(id:" + bt10.getExternalId() + "),item:(id:" + bt30.getExternalId() + ")))", myAgent1, agent10, agent15);
    checkAgents("enabled:any,compatible:(buildType:(item:(id:" + bt10.getExternalId() + "),item:(id:" + bt30.getExternalId() + ")))", myAgent1, agent10, agent15, agent50);
//    checkAgents("compatible:(buildType:(id:" + bt10.getExternalId() + ")),compatible:(buildType:(id:" + bt30.getExternalId() + "))", agent10);

    checkAgents("incompatible:(buildType:(id:" + bt10.getExternalId() + "))", myAgent1, myAgent2, agent20, agent30, agent40);
    checkAgents("incompatible:(buildType:(item:(id:" + bt10.getExternalId() + "),item:(id:" + bt30.getExternalId() + ")))",
                myAgent1, myAgent2, agent15, agent20, agent30, agent40, agent50);

    checkAgents("compatible:(buildType:(id:" + bt30.getExternalId() + ")),incompatible:(buildType:(id:" + bt10.getExternalId() + "))", myAgent1);
  }

  @Test
  public void testLocatorCompatibleForBuildWithoutPrefilter() throws Exception {
    setInternalProperty("rest.request.agents.compatibilityPrefilter", "false"); //non-default, pre-2017.1.2 value
    testLocatorCompatibleForBuild();
  }

  @Test
  public void testLocatorCompatibleForBuild() throws Exception {
    ProjectEx project10 = createProject("project10", "project 10");
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");
    bt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("a", null, RequirementType.EXISTS));
    BuildTypeEx bt20 = project10.createBuildType("bt20", "bt 20");

    MockBuildAgent agent10 = myFixture.createEnabledAgent("agent10", "Ant");
    agent10.addConfigParameter("a", "b");
    agent10.pushAgentTypeData();

    if (myServer.getLicensingPolicy().getMaxNumberOfAuthorizedAgents() < 4){
      throw new SkipException("Cannot execute test logic when there is not enough agent licenses (only works in internal dev environment tests)");
    }

    MockBuildAgent agent20 = myFixture.createEnabledAgent("agent20", "Ant");
    agent20.addConfigParameter("a", "b");
    agent20.pushAgentTypeData();
    final int poolId1 = myFixture.getAgentPoolManager().createNewAgentPool("pool1").getAgentPoolId();
    myFixture.getAgentPoolManager().moveAgentTypesToPool(poolId1, createSet(agent20.getId()));

    MockBuildAgent agent30 = myFixture.createEnabledAgent("agent30", "Ant");
    agent30.addConfigParameter("a", "b");
    agent30.pushAgentTypeData();
    myFixture.getAgentTypeManager().setRunConfigurationPolicy(agent30.getAgentTypeId(), BuildAgentManager.RunConfigurationPolicy.SELECTED_COMPATIBLE_CONFIGURATIONS);
    myFixture.getAgentTypeManager().excludeRunConfigurationsFromAllowed(agent30.getAgentTypeId(), new String[]{bt20.getInternalId()});

    MockBuildAgent agent40 = myFixture.createEnabledAgent("agent40", "Ant");

    MockBuildAgent agent50 = myFixture.createEnabledAgent("agent50", "Ant");
    agent50.addConfigParameter("a", "b");
    agent50.pushAgentTypeData();
    agent50.setEnabled(false, null, "");


    checkAgents("defaultFilter:false,connected:true,authorized:true,enabled:true", myAgent1, agent10, agent20, agent30, agent40);

    SQueuedBuild build1 = build().in(bt10).addToQueue();
    checkAgents("compatible:(build:(id:" + build1.getBuildPromotion().getId() + "))", agent10);

    SQueuedBuild build2 = build().in(bt20).addToQueue();
    checkAgents("compatible:(build:(id:" + build2.getBuildPromotion().getId() + "))", myAgent1, agent10, agent40);

    checkAgents("compatible:(build:(item:(id:" + build2.getBuildPromotion().getId() + "),item:(id:"+ build1.getBuildPromotion().getId() +")))", myAgent1, agent10, agent40);

    SFinishedBuild build3 = build().in(bt10).finish();
    checkAgents("compatible:(build:(id:" + build3.getBuildPromotion().getId() + "))", agent10, agent50); //agent50 should probably not be here

    SFinishedBuild build4 = build().in(bt20).finish();
    checkAgents("compatible:(build:(id:" + build4.getBuildPromotion().getId() + "))", myAgent1, agent10, agent40, agent50);  //agent50 should probably not be here
  }

  @Test
  @TestFor(issues = {"TW-49934"})
  public void testLocatorCompatibleBuildSpecific() throws Exception {
    ProjectEx project10 = createProject("project10", "project 10");
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");
    bt10.addParameter(new SimpleParameter("a", "%b%"));

    checkAgents(null, myAgent1, myAgent2);
    checkAgents("compatible:(buildType:(id:" + bt10.getExternalId() + "))");

    {
      SQueuedBuild queuedBuild = build().in(bt10).addToQueue();
      checkAgents("compatible:(build:(id:" + queuedBuild.getItemId() + "))");
      queuedBuild.removeFromQueue(null, null);
    }

    {
      SQueuedBuild queuedBuild = build().in(bt10).parameter("b", "value").addToQueue();
      checkAgents("compatible:(build:(id:" + queuedBuild.getItemId() + "))", myAgent1);
    }
  }

  @Test
  @TestFor(issues = {"TW-49934"})
  public void testLocatorCompatibleSnapshotDep() throws Exception {
    ProjectEx project10 = createProject("project10", "project 10");
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");
    BuildTypeEx bt20 = project10.createBuildType("bt20", "bt 20");
    createDependencyChain(bt20, bt10);

    checkAgents(null, myAgent1, myAgent2);
    checkAgents("compatible:(buildType:(id:" + bt20.getExternalId() + "))", myAgent1);

    SQueuedBuild queuedBuild = build().in(bt20).addToQueue();
    checkAgents("compatible:(build:(id:" + queuedBuild.getItemId() + "))", myAgent1); //queued snapshot dependency does not affect the compatibility
    myServer.flushQueue();
    final RunningBuildEx build = myFixture.waitForQueuedBuildToStart(queuedBuild.getBuildPromotion().getDependencies().iterator().next().getDependOn());
    checkAgents("compatible:(build:(id:" + queuedBuild.getItemId() + "))", myAgent1);
    finishBuild();
    checkAgents("compatible:(build:(id:" + queuedBuild.getItemId() + "))", myAgent1);
  }


  private void checkAgents(final String locatorText, final SBuildAgent... agents) {
    check(locatorText, new Matcher<SBuildAgent, SBuildAgent>() {
      @Override
      public boolean matches(@NotNull final SBuildAgent sBuildAgent, @NotNull final SBuildAgent sBuildAgent2) {
        return sBuildAgent.getId() == sBuildAgent2.getId();
      }
    }, agents);
  }
}
