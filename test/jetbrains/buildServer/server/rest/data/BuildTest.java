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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import jetbrains.buildServer.AgentRestrictorType;
import jetbrains.buildServer.artifacts.ArtifactDependency;
import jetbrains.buildServer.artifacts.RevisionRule;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.PropEntitiesArtifactDep;
import jetbrains.buildServer.server.rest.model.buildType.PropEntityArtifactDep;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolCannotBeRenamedException;
import jetbrains.buildServer.serverSide.agentPools.NoSuchAgentPoolException;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.AgentRestrictorFactoryImpl;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  @Test
  public void testBuildTriggeringWithArtifactDeps() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");
    BuildTypeImpl buildType2 = registerBuildType("buildType2", "projectName");
    BuildTypeImpl buildType3 = registerBuildType("buildType3", "projectName");
    BuildTypeImpl buildType4 = registerBuildType("buildType4", "projectName");

    SFinishedBuild build2_1 = build().in(buildType2).finish();
    SFinishedBuild build2_2 = build().in(buildType2).finish();

    SFinishedBuild build3_1 = build().in(buildType3).finish();
    SFinishedBuild build3_2 = build().in(buildType3).finish();

    SFinishedBuild build4_1 = build().in(buildType4).finish();

    ArtifactDependencyFactory depsFactory = myFixture.getSingletonService(ArtifactDependencyFactory.class);
    SArtifactDependency dep2 = depsFactory.createArtifactDependency(buildType2, "path", RevisionRules.LAST_FINISHED_RULE);
    dep2.setCleanDestinationFolder(true);
    SArtifactDependency dep3 = depsFactory.createArtifactDependency(buildType3, "path2", RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber()));
    dep3.setCleanDestinationFolder(false);

    buildType1.setArtifactDependencies(Arrays.asList(dep2, dep3));

    final SUser user = getOrCreateUser("user");

    // end of setup

    final Build build = new Build();
    final BuildType buildTypeEntity = new BuildType();
    buildTypeEntity.setId(buildType1.getExternalId());
    build.setBuildType(buildTypeEntity);

    SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(2, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(buildType2.getId(), result.getBuildPromotion().getArtifactDependencies().get(0).getSourceBuildTypeId());
    assertEquals("latest.lastFinished", result.getBuildPromotion().getArtifactDependencies().get(0).getRevisionRule().getRevision());
    assertEquals(buildType3.getId(), result.getBuildPromotion().getArtifactDependencies().get(1).getSourceBuildTypeId());
    assertEquals(build3_1.getBuildId() + ".tcbuildid", result.getBuildPromotion().getArtifactDependencies().get(1).getRevisionRule().getRevision());

    Builds builds1 = new Builds();
    Build build1 = new Build();
    build1.setLocator("buildType:(id:" + buildType3.getExternalId() + "),number:" + build3_2.getBuildNumber());
    builds1.builds = Arrays.asList(build1);
    build.setBuildArtifactDependencies(builds1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(buildType3.getId(), result.getBuildPromotion().getArtifactDependencies().get(0).getSourceBuildTypeId());
    assertEquals(build3_2.getBuildId() + ".tcbuildid", result.getBuildPromotion().getArtifactDependencies().get(0).getRevisionRule().getRevision());

    Builds builds2 = new Builds();
    Build build2 = new Build();
    build2.setLocator("buildType:(id:" + buildType4.getExternalId() + "),number:" + build4_1.getBuildNumber());
    builds2.builds = Arrays.asList(build2);
    build.setBuildArtifactDependencies(builds2);
    BuildPromotionFinderTest.checkException(BadRequestException.class, new Runnable() {
      public void run() {
        build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      }
    }, "triggering build with artifact dependency not in default artifact dependencies");

    build.setBuildArtifactDependencies(builds1);

    PropEntitiesArtifactDep propEntitiesArtifactDep1 = new PropEntitiesArtifactDep();
    propEntitiesArtifactDep1.setReplace("false");
    build.setCustomBuildArtifactDependencies(propEntitiesArtifactDep1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(2, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(buildType3.getId(), result.getBuildPromotion().getArtifactDependencies().get(0).getSourceBuildTypeId());
    assertEquals(build3_2.getBuildId() + ".tcbuildid", result.getBuildPromotion().getArtifactDependencies().get(0).getRevisionRule().getRevision());
    assertEquals(buildType2.getId(), result.getBuildPromotion().getArtifactDependencies().get(1).getSourceBuildTypeId());
    assertEquals("latest.lastFinished", result.getBuildPromotion().getArtifactDependencies().get(1).getRevisionRule().getRevision());

    PropEntitiesArtifactDep propEntitiesArtifactDep2 = new PropEntitiesArtifactDep();
    PropEntityArtifactDep propEntityArtifactDep1 = new PropEntityArtifactDep();
    propEntityArtifactDep1.properties =
      new Properties(createMap("revisionName", "buildId", "revisionValue", "1000", "pathRules", "path3", "cleanDestinationDirectory", "true"), null, Fields.ALL_NESTED);
    propEntityArtifactDep1.sourceBuildType = new BuildType();
    propEntityArtifactDep1.sourceBuildType.setId(buildType4.getExternalId());
    propEntityArtifactDep1.type = "artifact_dependency";
    propEntitiesArtifactDep2.propEntities = Arrays.asList(propEntityArtifactDep1);
    build.setCustomBuildArtifactDependencies(propEntitiesArtifactDep2);

    BuildPromotionFinderTest.checkException(BadRequestException.class, new Runnable() {
      public void run() {
        build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      }
    }, "triggering build with artifact dependency not in posted custom-artifact-dependnecies");

    build.setBuildArtifactDependencies(null);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(buildType4.getId(), result.getBuildPromotion().getArtifactDependencies().get(0).getSourceBuildTypeId());
    assertEquals("1000" + ".tcbuildid", result.getBuildPromotion().getArtifactDependencies().get(0).getRevisionRule().getRevision());
    assertEquals("path3", result.getBuildPromotion().getArtifactDependencies().get(0).getSourcePaths());
    assertEquals(true, result.getBuildPromotion().getArtifactDependencies().get(0).isCleanDestinationFolder());

    build.setBuildArtifactDependencies(builds2);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(buildType4.getId(), result.getBuildPromotion().getArtifactDependencies().get(0).getSourceBuildTypeId());
    assertEquals(build4_1.getBuildId() + ".tcbuildid", result.getBuildPromotion().getArtifactDependencies().get(0).getRevisionRule().getRevision());

    propEntitiesArtifactDep2.setReplace("false");
    build.setCustomBuildArtifactDependencies(propEntitiesArtifactDep2);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(3, result.getBuildPromotion().getArtifactDependencies().size());
  }

  @Test
  public void testBuildTriggeringWithTwoArtifactDepsOnSameBuildType() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");
    BuildTypeImpl buildType2 = registerBuildType("buildType2", "projectName");
    BuildTypeImpl buildType3 = registerBuildType("buildType3", "projectName");
    BuildTypeImpl buildType4 = registerBuildType("buildType4", "projectName");

    SFinishedBuild build2_1 = build().in(buildType2).finish();
    SFinishedBuild build2_2 = build().in(buildType2).finish();
    SFinishedBuild build2_3 = build().in(buildType2).finish();

    SFinishedBuild build3_1 = build().in(buildType3).finish();
    SFinishedBuild build3_2 = build().in(buildType3).finish();
    SFinishedBuild build3_3 = build().in(buildType3).finish();

    SFinishedBuild build4_1 = build().in(buildType4).finish();

    ArtifactDependencyFactory depsFactory = myFixture.getSingletonService(ArtifactDependencyFactory.class);
    SArtifactDependency dep2_1 = depsFactory.createArtifactDependency(buildType2, "path2_1", RevisionRules.newBuildNumberRule(build2_2.getBuildNumber()));
    dep2_1.setCleanDestinationFolder(true);
    SArtifactDependency dep2_2 = depsFactory.createArtifactDependency(buildType2, "path2_2=>a", RevisionRules.LAST_FINISHED_RULE);
    dep2_2.setCleanDestinationFolder(false);
    SArtifactDependency dep3 = depsFactory.createArtifactDependency(buildType3, "path3=>b", RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber()));
    dep3.setCleanDestinationFolder(false);

    buildType1.setArtifactDependencies(Arrays.asList(dep2_1, dep2_2, dep3));

    final SUser user = getOrCreateUser("user");

    // end of setup

    final Build build = new Build();
    final BuildType buildTypeEntity = new BuildType();
    buildTypeEntity.setId(buildType1.getExternalId());
    build.setBuildType(buildTypeEntity);

    SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(3, result.getBuildPromotion().getArtifactDependencies().size());
    assertContains(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildNumberRule(build2_2.getBuildNumber())),
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.LAST_FINISHED_RULE),
                   new TestArtifactDep(buildType3.getBuildTypeId(), "path3=>b", false, RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber())));

    Builds builds1 = new Builds();
    Build build1 = new Build();
    build1.setLocator("id:" + build3_1.getBuildId());
    builds1.builds = Arrays.asList(build1);
    build.setBuildArtifactDependencies(builds1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertContains(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                   new TestArtifactDep(buildType3.getBuildTypeId(), "path3=>b", false, RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber())));

    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build1);
    build.setBuildArtifactDependencies(builds1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertContains(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())));

    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build1);
    Build build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    builds1.builds = Arrays.asList(build1, build2);
    build.setBuildArtifactDependencies(builds1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(2, result.getBuildPromotion().getArtifactDependencies().size());
    assertContains(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())),
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.newBuildIdRule(build2_2.getBuildId(), build2_2.getBuildNumber())));

    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build1);
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    builds1.builds = Arrays.asList(build2, build1);
    build.setBuildArtifactDependencies(builds1);

    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(2, result.getBuildPromotion().getArtifactDependencies().size());
    assertContains(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_2.getBuildId(), build2_2.getBuildNumber())),
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())));

    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build1);
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    Build build3 = new Build();
    build3.setLocator("id:" + build2_3.getBuildId());
    builds1.builds = Arrays.asList(build2, build3, build1);
    build.setBuildArtifactDependencies(builds1);

    BuildPromotionFinderTest.checkException(BadRequestException.class, new Runnable() {
      public void run() {
        build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      }
    }, "triggering build with more builds in artifact dependencies then there are default artifact dependencies");


    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build1);
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    build3 = new Build();
    build3.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build2, build3, build1);
    build.setBuildArtifactDependencies(builds1);

    PropEntitiesArtifactDep customDeps = new PropEntitiesArtifactDep();
    PropEntityArtifactDep dep1 = new PropEntityArtifactDep();
    dep1.properties =
      new Properties(createMap("revisionName", "buildId", "revisionValue", "1000", "pathRules", "path3=>x", "cleanDestinationDirectory", "true"), null, Fields.ALL_NESTED);
    dep1.sourceBuildType = new BuildType();
    dep1.sourceBuildType.setId(buildType2.getExternalId());
    dep1.type = "artifact_dependency";
    customDeps.propEntities = Arrays.asList(dep1);
    build.setCustomBuildArtifactDependencies(customDeps);

    BuildPromotionFinderTest.checkException(BadRequestException.class, new Runnable() {
      public void run() {
        build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      }
    }, "triggering build with more builds in artifact dependencies then there are in custom artifact dependencies");


    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build1);
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    builds1.builds = Arrays.asList(build1, build2);
    build.setBuildArtifactDependencies(builds1);

    customDeps = new PropEntitiesArtifactDep();
    customDeps.setReplace("false");
    build.setCustomBuildArtifactDependencies(customDeps);

    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(3, result.getBuildPromotion().getArtifactDependencies().size());
    assertContains(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())),
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.newBuildIdRule(build2_2.getBuildId(), build2_2.getBuildNumber())),
                   new TestArtifactDep(buildType3.getBuildTypeId(), "path3=>b", false, RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber())));


    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build1);
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    build3 = new Build();
    build3.setLocator("id:" + build2_1.getBuildId());
    builds1.builds = Arrays.asList(build2, build3, build1);
    build.setBuildArtifactDependencies(builds1);

    customDeps = new PropEntitiesArtifactDep();
    dep1 = new PropEntityArtifactDep();
    dep1.properties =
      new Properties(createMap("revisionName", "buildId", "revisionValue", "1000", "pathRules", "path3=>x", "cleanDestinationDirectory", "true"), null, Fields.ALL_NESTED);
    dep1.sourceBuildType = new BuildType();
    dep1.sourceBuildType.setId(buildType2.getExternalId());
    dep1.type = "artifact_dependency";
    customDeps.propEntities = Arrays.asList(dep1);
    customDeps.setReplace("false");
    build.setCustomBuildArtifactDependencies(customDeps);

    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(4, result.getBuildPromotion().getArtifactDependencies().size());
    assertContains(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_2.getBuildId(), build2_2.getBuildNumber())),
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())),
                   new TestArtifactDep(buildType2.getBuildTypeId(), "path3=>x", true, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())),
                   new TestArtifactDep(buildType3.getBuildTypeId(), "path3=>b", false, RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber())));
  }

  public static <T> void assertContains(@Nullable final List<T> collection, EqualsTest<T> equalsTest, final T... items) {
    if (collection == null || (collection.isEmpty() && items.length != 0)) {
      fail("Expected and actual collection sizes are different");
    }

    for (T item : items) {
      boolean ok = false;
      for (T t : collection) {
        if (equalsTest.equals(item, t)) {
          ok = true;
          break;
        }
      }
      if (!ok) {
        fail("Actual collection does not have item " + item + "\n" +
             "Collection: " + collection.toString());
      }
    }
  }

  protected static final EqualsTest<SArtifactDependency> EQUALS_TEST = new EqualsTest<SArtifactDependency>() {
    @Override
    public boolean equals(final SArtifactDependency o1, final SArtifactDependency o2) {
      return o1.isSimilarTo(o2);
    }
  };

  private interface EqualsTest<T> {
    public boolean equals(final T o1, final T o2);
  }

  private static class TestArtifactDep implements SArtifactDependency {

    private final String sourceBuildTypeId;
    private final String sourcePaths;
    private final boolean cleanDestination;
    private final RevisionRule revisionRule;

    public TestArtifactDep(final String sourceBuildTypeId, final String sourcePaths, final boolean cleanDestination, final RevisionRule revisionRule) {
      this.sourceBuildTypeId = sourceBuildTypeId;
      this.sourcePaths = sourcePaths;
      this.cleanDestination = cleanDestination;
      this.revisionRule = revisionRule;
    }

    @NotNull
    @Override
    public String getSourceBuildTypeId() {
      return sourceBuildTypeId;
    }

    @NotNull
    @Override
    public String getSourceExternalId() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String getSourceName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getSourcePaths() {
      return sourcePaths;
    }

    @Override
    public boolean isCleanDestinationFolder() {
      return cleanDestination;
    }

    @Override
    public RevisionRule getRevisionRule() {
      return revisionRule;
    }

    @Override
    public void setCleanDestinationFolder(final boolean cleanDestinationFolder) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setSourceBuildTypeId(@NotNull final String sourceBuildTypeInternalId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setRevisionRule(@NotNull final RevisionRule revisionRule) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setSourcePaths(@NotNull final String paths) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void replaceReferences(@NotNull final ValueResolver resolver) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSimilarTo(final ArtifactDependency dep) {
      return dep.isSimilarTo(this);
    }

    @Override
    public List<String> getReferences() {
      throw new UnsupportedOperationException();
    }

    @Override
    public SArtifactDependency createCopy() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public SBuildType getSourceBuildType() throws AccessDeniedException {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public SBuild resolveForBuild(@NotNull final SBuild targetBuild) throws BuildTypeNotFoundException, AccessDeniedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("TestArtifactDep{");
      sb.append("sourceBuildTypeId='").append(sourceBuildTypeId).append('\'');
      sb.append(", sourcePaths='").append(sourcePaths).append('\'');
      sb.append(", cleanDestination=").append(cleanDestination);
      sb.append(", revisionRule=").append(revisionRule);
      sb.append('}');
      return sb.toString();
    }
  }
}
