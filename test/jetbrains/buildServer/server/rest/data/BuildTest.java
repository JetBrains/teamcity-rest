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

import java.util.HashMap;
import jetbrains.buildServer.AgentRestrictorType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.PathTransformer;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolCannotBeRenamedException;
import jetbrains.buildServer.serverSide.agentPools.NoSuchAgentPoolException;
import jetbrains.buildServer.serverSide.impl.AgentRestrictorFactoryImpl;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 24/07/2015
 */
public class BuildTest extends BaseFinderTest<SBuild> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testBuildTriggering1() {
    final Build build = new Build();
    final BuildType buildType = new BuildType();
    buildType.setId(myBuildType.getExternalId());
    build.setBuildType(buildType);

    final SUser triggeringUser = getOrCreateUser("user");
    final SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
    assertEquals(myBuildType, queuedBuild.getBuildPromotion().getBuildType());
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void testBuildTriggering2() {
    final Build build = new Build();
    final BuildType buildType = new BuildType();
    buildType.setId(myBuildType.getExternalId());
    buildType.setPaused(true); //this generates BadRequestException
    build.setBuildType(buildType);

    final SUser triggeringUser = getOrCreateUser("user");

    build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
  }

  @Test
  public void testBuildOnAgentTriggering() {
    myFixture.addService(new AgentRestrictorFactoryImpl());
    final MockBuildAgent agent2 = myFixture.createEnabledAgent("agent2", "Ant");

    final Build build = new Build();
    final BuildType buildType = new BuildType();
    buildType.setId(myBuildType.getExternalId());
    build.setBuildType(buildType);
    final Agent submittedAgent = new Agent();
    submittedAgent.id = agent2.getId();
    build.setAgent(submittedAgent);

    final SUser triggeringUser = getOrCreateUser("user");
    final SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
    assertEquals(Integer.valueOf(agent2.getId()), queuedBuild.getBuildAgentId());
  }

  @Test
  public void testBuildOnAgentPoolTriggering() throws NoSuchAgentPoolException, AgentPoolCannotBeRenamedException {

    final MockBuildAgent agent2 = myFixture.createEnabledAgent("agent2", "Ant");
    final int poolId1 = myFixture.getAgentPoolManager().createNewAgentPool("pool1");
    myFixture.getAgentPoolManager().moveAgentTypesToPool(poolId1, createSet(agent2.getId()));

    final Build build = new Build();
    final BuildType buildType = new BuildType();
    buildType.setId(myBuildType.getExternalId());
    build.setBuildType(buildType);
    final SUser triggeringUser = getOrCreateUser("user");

    Agent submittedAgent = new Agent();
    submittedAgent.locator = "pool:(id:" + poolId1+")";
    build.setAgent(submittedAgent);

    SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
    assertNotNull(queuedBuild.getAgentRestrictor());
    assertEquals(AgentRestrictorType.AGENT_POOL, queuedBuild.getAgentRestrictor().getType());
    assertEquals(poolId1, queuedBuild.getAgentRestrictor().getId());


    submittedAgent = new Agent();
    submittedAgent.pool = new AgentPool();
    submittedAgent.pool.id = poolId1;
    build.setAgent(submittedAgent);

    queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
    assertNotNull(queuedBuild.getAgentRestrictor());
    assertEquals(AgentRestrictorType.AGENT_POOL, queuedBuild.getAgentRestrictor().getType());
    assertEquals(poolId1, queuedBuild.getAgentRestrictor().getId());

    submittedAgent = new Agent();
    submittedAgent.pool = new AgentPool();
    submittedAgent.pool.locator = "id:" + poolId1;
    build.setAgent(submittedAgent);

    queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
    assertNotNull(queuedBuild.getAgentRestrictor());
    assertEquals(AgentRestrictorType.AGENT_POOL, queuedBuild.getAgentRestrictor().getType());
    assertEquals(poolId1, queuedBuild.getAgentRestrictor().getId());
  }

  @NotNull
  private BeanContext getBeanContext() {
    final ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(new PathTransformer() {
      public String transform(final String path) {
        return path;
      }
    });
    final BeanFactory beanFactory = new BeanFactory(null);
    return new BeanContext(beanFactory, myFixture, apiUrlBuilder);
  }
}
