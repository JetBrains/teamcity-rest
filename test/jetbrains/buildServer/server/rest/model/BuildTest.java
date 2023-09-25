/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model;

import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import jetbrains.BuildServerCreator;
import jetbrains.buildServer.AgentRestrictorType;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.artifacts.ArtifactDependency;
import jetbrains.buildServer.artifacts.RevisionRule;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.requirements.Requirement;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildFinderTestBase;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.agent.Agent;
import jetbrains.buildServer.server.rest.model.agent.AgentPool;
import jetbrains.buildServer.server.rest.model.build.Build;
import jetbrains.buildServer.server.rest.model.build.Builds;
import jetbrains.buildServer.server.rest.model.build.downloadedArtifacts.ArtifactDownloadInfo;
import jetbrains.buildServer.server.rest.model.build.downloadedArtifacts.BuildArtifactsDownloadInfo;
import jetbrains.buildServer.server.rest.model.buildType.*;
import jetbrains.buildServer.server.rest.model.change.Changes;
import jetbrains.buildServer.server.rest.model.change.Revision;
import jetbrains.buildServer.server.rest.model.change.Revisions;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolCannotBeRenamedException;
import jetbrains.buildServer.serverSide.agentPools.NoSuchAgentPoolException;
import jetbrains.buildServer.serverSide.agentPools.PoolQuotaExceededException;
import jetbrains.buildServer.serverSide.agentTypes.AgentTypeKey;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.*;
import jetbrains.buildServer.serverSide.impl.timeEstimation.CachingBuildEstimator;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;
import static jetbrains.buildServer.serverSide.parameters.ParameterFactoryImpl.OUR_PASSWORD_REPLACE;
import static jetbrains.buildServer.util.Util.map;

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
  public void testBuildNodeAttributes() {
    {
      final BuildCustomizer customizer = myFixture.getSingletonService(BuildCustomizerFactory.class).createBuildCustomizer(myBuildType, getOrCreateUser("user"));
      SQueuedBuild queuedBuild = myBuildType.addToQueue((BuildPromotionEx)customizer.createPromotion(), "");
      Build buildNode = new Build(queuedBuild.getBuildPromotion(), Fields.LONG, getBeanContext(myFixture));
      assertNull(buildNode.getAttributes());
      queuedBuild.removeFromQueue(null, "");
    }

    {
      final BuildCustomizer customizer = myFixture.getSingletonService(BuildCustomizerFactory.class).createBuildCustomizer(myBuildType, getOrCreateUser("user"));
      customizer.setCleanSources(true);
      SQueuedBuild queuedBuild = myBuildType.addToQueue((BuildPromotionEx)customizer.createPromotion(), "");
      Build buildNode = new Build(queuedBuild.getBuildPromotion(), Fields.LONG, getBeanContext(myFixture));
      assertNotNull(buildNode.getAttributes());
      assertNotNull(buildNode.getAttributes().entries);
      assertEquals(1, buildNode.getAttributes().entries.size());
      assertEquals("teamcity.cleanSources", buildNode.getAttributes().entries.get(0).name);
      assertEquals("true", buildNode.getAttributes().entries.get(0).value);
      queuedBuild.removeFromQueue(null, "");
    }

    {
      final BuildCustomizer customizer = myFixture.getSingletonService(BuildCustomizerFactory.class).createBuildCustomizer(myBuildType, getOrCreateUser("user"));
      customizer.setAttributes(map("a", "b"));
      SQueuedBuild queuedBuild = myBuildType.addToQueue((BuildPromotionEx)customizer.createPromotion(), "");
      Build buildNode = new Build(queuedBuild.getBuildPromotion(), Fields.LONG, getBeanContext(myFixture));
      assertNull(buildNode.getAttributes());
      queuedBuild.removeFromQueue(null, "");
    }

    {
      final BuildCustomizer customizer = myFixture.getSingletonService(BuildCustomizerFactory.class).createBuildCustomizer(myBuildType, getOrCreateUser("user"));
      customizer.setAttributes(map("a", "b", "c", "d"));
      SQueuedBuild queuedBuild = myBuildType.addToQueue((BuildPromotionEx)customizer.createPromotion(), "");
      Build buildNode = new Build(queuedBuild.getBuildPromotion(), new Fields("attributes($long,$locator(name:$any))"), getBeanContext(myFixture));
      assertNotNull(buildNode.getAttributes());
      assertNotNull(buildNode.getAttributes().entries);
      assertTrue(buildNode.getAttributes().entries.size() >= 2); //there are also some always present attributes
      assertEquals("b", buildNode.getAttributes().entries.stream().filter(entry -> "a".equals(entry.name)).findFirst().get().value);
      queuedBuild.removeFromQueue(null, "");
    }
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
  public void testBuildTriggeringTriggeredBy() throws Throwable {
    final Build build = new Build();
    final BuildType buildType = new BuildType();
    buildType.setId(myBuildType.getExternalId());
    build.setBuildType(buildType);
    final SUser triggeringUser = getOrCreateUser("userName");

    {
      final SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
      TriggeredBy triggeredBy = queuedBuild.getTriggeredBy();
      assertContains(triggeredBy.getParameters(),
                     CollectionsUtil.asMap("userId", String.valueOf(triggeringUser.getId()), "type", "user", "origin", "rest"));
      myServer.getQueue().removeAllFromQueue();
    }

    myFixture.getSecurityContext().runAsSystem(() -> {
      final SQueuedBuild queuedBuild = build.triggerBuild(null, myFixture, new HashMap<Long, Long>());
      TriggeredBy triggeredBy = queuedBuild.getTriggeredBy();
      assertContains(triggeredBy.getParameters(),
                     CollectionsUtil.asMap("type", "request", "origin", "rest"));
      assertNull(triggeredBy.getParameters().get("userId"));
      myServer.getQueue().removeAllFromQueue();
    });

    {
      jetbrains.buildServer.server.rest.model.build.TriggeredBy submittedTriggeredBy = new jetbrains.buildServer.server.rest.model.build.TriggeredBy();
      build.setTriggered(submittedTriggeredBy);
      submittedTriggeredBy.type = "idePlugin";
      submittedTriggeredBy.details = "IntelliJ IDEA";

      final SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
      TriggeredBy triggeredBy = queuedBuild.getTriggeredBy();
      assertContains(triggeredBy.getParameters(),
                     CollectionsUtil.asMap("userId", String.valueOf(triggeringUser.getId()), "type", "xmlRpc", "IDEPlugin", "IntelliJ IDEA", "origin", "rest"));
      myServer.getQueue().removeAllFromQueue();
    }

    {
      jetbrains.buildServer.server.rest.model.build.TriggeredBy submittedTriggeredBy = new jetbrains.buildServer.server.rest.model.build.TriggeredBy();
      build.setTriggered(submittedTriggeredBy);
      submittedTriggeredBy.type = "build";
      submittedTriggeredBy.build = new Build();
      SFinishedBuild aFinishedBuild = build().in(myBuildType).finish();
      submittedTriggeredBy.build.setId(aFinishedBuild.getBuildId());

      final SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
      TriggeredBy triggeredBy = queuedBuild.getTriggeredBy();
      assertContains(triggeredBy.getParameters(),
                     CollectionsUtil.asMap("userId", String.valueOf(triggeringUser.getId()), "type", "user", "buildId", String.valueOf(aFinishedBuild.getBuildId()), "origin", "rest"));
      myServer.getQueue().removeAllFromQueue();
    }
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
  public void testBuildOnAgentPoolTriggering() throws Exception {

    final MockBuildAgent agent2 = myFixture.createEnabledAgent("agent2", "Ant");
    final int poolId1 = myFixture.getAgentPoolManager().createNewAgentPool("pool1").getAgentPoolId();
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
    assertEquals(AgentRestrictorType.SINGLE_AGENT, queuedBuild.getAgentRestrictor().getType());
    assertEquals(agent2.getId(), queuedBuild.getAgentRestrictor().getId());


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
  public void testBuildOnCloudAgentTriggering() throws NoSuchAgentPoolException, AgentPoolCannotBeRenamedException, PoolQuotaExceededException {
    int cloudAgentTypeId = myFixture.getAgentTypeManager().getOrCreateAgentTypeId(new AgentTypeKey("Cloud", "Profile", "Image"));

    final SUser triggeringUser = getOrCreateUser("user");
    {
      final Build build = new Build();
      final BuildType buildType = new BuildType();
      buildType.setId(myBuildType.getExternalId());
      build.setBuildType(buildType);

      Agent submittedAgent = new Agent();
      submittedAgent.typeId = cloudAgentTypeId;
      build.setAgent(submittedAgent);

      SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
      assertNotNull(queuedBuild.getAgentRestrictor());
      assertEquals(AgentRestrictorType.CLOUD_IMAGE, queuedBuild.getAgentRestrictor().getType());
      assertEquals(cloudAgentTypeId, queuedBuild.getAgentRestrictor().getId());
    }

    {
      final Build build = new Build();
      final BuildType buildType = new BuildType();
      buildType.setId(myBuildType.getExternalId());
      build.setBuildType(buildType);

      final MockBuildAgent agent2 = myFixture.createEnabledAgent("agent2", "Ant");

      Agent submittedAgent = new Agent();
      submittedAgent.locator = "id:" + agent2.getId();
      submittedAgent.typeId = cloudAgentTypeId;
      build.setAgent(submittedAgent);

      SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
      assertNotNull(queuedBuild.getAgentRestrictor());
      assertEquals(AgentRestrictorType.SINGLE_AGENT, queuedBuild.getAgentRestrictor().getType());
      assertEquals(agent2.getId(), queuedBuild.getAgentRestrictor().getId());
    }

    {
      final Build build = new Build();
      final BuildType buildType = new BuildType();
      buildType.setId(myBuildType.getExternalId());
      build.setBuildType(buildType);

      Agent submittedAgent = new Agent();
      submittedAgent.typeId = cloudAgentTypeId + 10;
      build.setAgent(submittedAgent);

      checkException(NotFoundException.class, () -> build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>()), "triggering build on not existent agent type");
    }
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
    builds1.setBuilds(Arrays.asList(build1));
    build.setBuildArtifactDependencies(builds1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(buildType3.getId(), result.getBuildPromotion().getArtifactDependencies().get(0).getSourceBuildTypeId());
    assertEquals(build3_2.getBuildId() + ".tcbuildid", result.getBuildPromotion().getArtifactDependencies().get(0).getRevisionRule().getRevision());

    Builds builds2 = new Builds();
    Build build2 = new Build();
    build2.setLocator("buildType:(id:" + buildType4.getExternalId() + "),number:" + build4_1.getBuildNumber());
    builds2.setBuilds(Arrays.asList(build2));
    build.setBuildArtifactDependencies(builds2);
    checkException(BadRequestException.class,
                   () -> build.triggerBuild(user, myFixture, new HashMap<Long, Long>()),
                   "triggering build with artifact dependency not in default artifact dependencies");

    build.setBuildArtifactDependencies(builds1);

    PropEntitiesArtifactDep propEntitiesArtifactDep1 = new PropEntitiesArtifactDep();
    propEntitiesArtifactDep1.setReplace("false");
    build.setCustomBuildArtifactDependencies(propEntitiesArtifactDep1);
    result = build.triggerBuild(user, myFixture, new HashMap<>());
    assertEquals(2, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(buildType3.getId(), result.getBuildPromotion().getArtifactDependencies().get(0).getSourceBuildTypeId());
    assertEquals(build3_2.getBuildId() + ".tcbuildid", result.getBuildPromotion().getArtifactDependencies().get(0).getRevisionRule().getRevision());
    assertEquals(buildType2.getId(), result.getBuildPromotion().getArtifactDependencies().get(1).getSourceBuildTypeId());
    assertEquals("latest.lastFinished", result.getBuildPromotion().getArtifactDependencies().get(1).getRevisionRule().getRevision());

    PropEntitiesArtifactDep propEntitiesArtifactDep2 = new PropEntitiesArtifactDep();
    PropEntityArtifactDep propEntityArtifactDep1 = new PropEntityArtifactDep();
    propEntityArtifactDep1.properties =
      new Properties(createMap("revisionName", "buildId", "revisionValue", "1000", "pathRules", "path3", "cleanDestinationDirectory", "true"), null, Fields.ALL_NESTED, getBeanContext(myFixture));
    propEntityArtifactDep1.sourceBuildType = new BuildType();
    propEntityArtifactDep1.sourceBuildType.setId(buildType4.getExternalId());
    propEntityArtifactDep1.type = "artifact_dependency";
    propEntitiesArtifactDep2.propEntities = Arrays.asList(propEntityArtifactDep1);
    build.setCustomBuildArtifactDependencies(propEntitiesArtifactDep2);

    checkException(BadRequestException.class,
                   () -> build.triggerBuild(user, myFixture, new HashMap<Long, Long>()),
                   "triggering build with artifact dependency not in posted custom-artifact-dependnecies");

    build.setBuildArtifactDependencies(null);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(buildType4.getId(), result.getBuildPromotion().getArtifactDependencies().get(0).getSourceBuildTypeId());
    assertEquals("1000" + ".tcbuildid", result.getBuildPromotion().getArtifactDependencies().get(0).getRevisionRule().getRevision());
    assertEquals("path3", result.getBuildPromotion().getArtifactDependencies().get(0).getSourcePaths());
    assertTrue(result.getBuildPromotion().getArtifactDependencies().get(0).isCleanDestinationFolder());

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

  /**
   * This test documents current behaviour, i.e. if we do not provide default template steps
   * in the custom steps, they still occur in the custom build settings.
   */
  @Test
  public void testBuildTriggeringWithCustomStepReplacingStepsFromDefaultTemplate() {
    BuildTypeImpl bt = registerBuildType("buildType1", "projectName");
    ProjectEx project = bt.getProject();
    BuildTypeTemplate tpl = project.createBuildTypeTemplate("tpl");

    project.setDefaultTemplate(tpl);

    tpl.addBuildRunner("t1", "runner1", Collections.emptyMap()).getId();
    tpl.addBuildRunner("t2", "runner2", Collections.emptyMap()).getId();
    bt.addBuildRunner("b1", "runner3", Collections.emptyMap()).getId();

    project.setDefaultTemplate(tpl);
    persist(project, tpl, bt);
    assertEquals(Arrays.asList("t1", "t2", "b1"), bt.getBuildRunners().stream().map(r -> r.getName()).collect(Collectors.toList()));

    final SUser user = getOrCreateUser("user");

    final Build build = new Build();
    final BuildType buildTypeEntity = new BuildType();
    buildTypeEntity.setId(bt.getExternalId());
    buildTypeEntity.getSettings();
    PropEntitiesStep steps = new PropEntitiesStep();
    PropEntityStep step = new PropEntityStep();
    step.type = "runner2";
    step.name = "custom";
    step.properties = new Properties(createMap("x", "y"), null, Fields.ALL, getBeanContext(myFixture));
    steps.propEntities = Arrays.asList(step);
    buildTypeEntity.setSteps(steps);
    build.setBuildType(buildTypeEntity);

    SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    assertEquals(Arrays.asList("t1", "t2", "custom"), ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildRunners().stream().map(r -> r.getName()).collect(Collectors.toList()));
  }


  @Test
  public void testBuildTriggeringWithCustomSteps() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addBuildRunner("stepName", "runner", createMap("a", "b"));

    final SUser user = getOrCreateUser("user");

    // end of setup

    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      build.setBuildType(buildTypeEntity);

      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      //noinspection ConstantConditions
      assertEquals(1, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildRunners().size());
    }

    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      PropEntitiesStep steps = new PropEntitiesStep();
      PropEntityStep step = new PropEntityStep();
      step.type = "runner2";
      step.properties = new Properties(createMap("x", "y"), null, Fields.ALL, getBeanContext(myFixture));
      steps.propEntities = Arrays.asList(step);
      buildTypeEntity.setSteps(steps);
      build.setBuildType(buildTypeEntity);

      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      //noinspection ConstantConditions
      Collection<? extends SBuildStepDescriptor> actualSteps = ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildRunners();
      assertEquals(1, actualSteps.size());
      assertEquals("runner2", actualSteps.iterator().next().getType());
    }

    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      PropEntitiesStep steps = new PropEntitiesStep();
      PropEntityStep step = new PropEntityStep();
      step.type = "runner2";
      step.properties = new Properties(createMap("x", "y"), null, Fields.ALL, getBeanContext(myFixture));
      steps.propEntities = Arrays.asList(step);
      step.disabled = true;
      buildTypeEntity.setSteps(steps);
      build.setBuildType(buildTypeEntity);

      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      //noinspection ConstantConditions
      assertEquals(0, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildRunners().size());
    }
  }

  @Test
  public void testBuildTriggeringWithCustomFeatures() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addBuildFeature("featureType", createMap("a", "b"));

    //add other settings: requirements, features and check that they can be reset

    final SUser user = getOrCreateUser("user");

    // end of setup

    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      build.setBuildType(buildTypeEntity);

      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      //noinspection ConstantConditions
      assertEquals(1, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildFeatures().size());
    }

    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      PropEntitiesFeature features = new PropEntitiesFeature();
      PropEntityFeature feature = new PropEntityFeature();
      feature.type = "feature2";
      feature.properties = new Properties(createMap("x", "y"), null, Fields.ALL, getBeanContext(myFixture));
      features.propEntities = Arrays.asList(feature);
      buildTypeEntity.setFeatures(features);
      build.setBuildType(buildTypeEntity);

      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      //noinspection ConstantConditions
      Collection<? extends SBuildFeatureDescriptor> actualFeatures = ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildFeatures();
      assertEquals(1, actualFeatures.size());
      assertEquals("feature2", actualFeatures.iterator().next().getType());
    }
  }

  @Test
  public void testBuildTriggeringWithCustomRequirements() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addRequirement(new Requirement("id1", "propName", "value", RequirementType.EQUALS));

    final SUser user = getOrCreateUser("user");

    // end of setup

    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      build.setBuildType(buildTypeEntity);

      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      //noinspection ConstantConditions
      assertEquals(1, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getRequirements().size());
    }

    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      PropEntitiesAgentRequirement agentRequirements = new PropEntitiesAgentRequirement();
      PropEntityAgentRequirement agentRequirement = new PropEntityAgentRequirement();
      agentRequirement.type = RequirementType.EQUALS.getName();
      agentRequirement.properties = new Properties(createMap("property-name", "propName2", "property-value", "value2"), null, Fields.ALL, getBeanContext(myFixture));
      agentRequirements.propEntities = Arrays.asList(agentRequirement);
      buildTypeEntity.setAgentRequirements(agentRequirements);
      build.setBuildType(buildTypeEntity);
      build.setProperties(new Properties(createMap("disableBuildMerging", "See TW-44714"), null, Fields.ALL, getBeanContext(myFixture))); //this line can be removed after TW-44714 fix
      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      List<Requirement> actualAgentRequirements = ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getRequirements();
      assertEquals(1, actualAgentRequirements.size());
      assertEquals("propName2", actualAgentRequirements.iterator().next().getPropertyName());
    }
  }

  @Test
  public void testBuildTriggeringWithEmptySets() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");
    BuildTypeImpl buildType2 = registerBuildType("buildType2", "projectName");

    ArtifactDependencyFactory depsFactory = myFixture.getSingletonService(ArtifactDependencyFactory.class);
    SArtifactDependency dep2 = depsFactory.createArtifactDependency(buildType2, "path", RevisionRules.LAST_FINISHED_RULE);
    buildType1.setArtifactDependencies(Arrays.asList(dep2));

    buildType1.addVcsRoot(createVcsRoot("aaa", null));
    buildType1.addBuildRunner("stepName", "runner", createMap("a", "b"));
    buildType1.addBuildFeature("featureType", createMap("a", "b"));
    buildType1.addParameter(new SimpleParameter("name", "value"));

    final SUser user = getOrCreateUser("user");

    // end of setup

    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      build.setBuildType(buildTypeEntity);

      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
      assertEquals(1, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getVcsRootEntries().size());
      assertEquals(1, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildRunners().size());
      assertEquals(1, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildFeatures().size());
      assertEquals(1, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getParameters().size());
    }

    //this test will actually test something only with option to copy the current settings from the build type. As so far empty build type is created, these checks check nothing
    {
      final Build build = new Build();
      final BuildType buildTypeEntity = new BuildType();
      buildTypeEntity.setId(buildType1.getExternalId());
      buildTypeEntity.setArtifactDependencies(new PropEntitiesArtifactDep());
      buildTypeEntity.setVcsRootEntries(new VcsRootEntries());
      buildTypeEntity.setSteps(new PropEntitiesStep());
      buildTypeEntity.setFeatures(new PropEntitiesFeature());
      buildTypeEntity.setParameters(new Properties());
      build.setBuildType(buildTypeEntity);

      SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
      assertEquals(0, result.getBuildPromotion().getArtifactDependencies().size());
      assertEquals(0, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getVcsRootEntries().size());
      assertEquals(0, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildRunners().size());
      assertEquals(0, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getBuildFeatures().size());
      assertEquals(0, ((BuildPromotionEx)result.getBuildPromotion()).getBuildSettings().getParameters().size());
    }
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
    assertEquals(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildNumberRule(build2_2.getBuildNumber())),
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.LAST_FINISHED_RULE),
                 new TestArtifactDep(buildType3.getBuildTypeId(), "path3=>b", false, RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber())));

    Builds builds1 = new Builds();
    Build build1 = new Build();
    build1.setLocator("id:" + build3_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build1));
    build.setBuildArtifactDependencies(builds1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                 new TestArtifactDep(buildType3.getBuildTypeId(), "path3=>b", false, RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber())));

    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build1));
    build.setBuildArtifactDependencies(builds1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(1, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())));

    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build1));
    Build build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    builds1.setBuilds(Arrays.asList(build1, build2));
    build.setBuildArtifactDependencies(builds1);
    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(2, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())),
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.newBuildIdRule(build2_2.getBuildId(), build2_2.getBuildNumber())));

    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build1));
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    builds1.setBuilds(Arrays.asList(build2, build1));
    build.setBuildArtifactDependencies(builds1);

    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(2, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_2.getBuildId(), build2_2.getBuildNumber())),
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())));

    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build1));
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    Build build3 = new Build();
    build3.setLocator("id:" + build2_3.getBuildId());
    builds1.setBuilds(Arrays.asList(build2, build3, build1));
    build.setBuildArtifactDependencies(builds1);

    checkException(BadRequestException.class,
                   () -> build.triggerBuild(user, myFixture, new HashMap<Long, Long>()),
                   "triggering build with more builds in artifact dependencies then there are default artifact dependencies");


    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build1));
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    build3 = new Build();
    build3.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build2, build3, build1));
    build.setBuildArtifactDependencies(builds1);

    PropEntitiesArtifactDep customDeps = new PropEntitiesArtifactDep();
    PropEntityArtifactDep dep1 = new PropEntityArtifactDep();
    dep1.properties =
      new Properties(createMap("revisionName", "buildId", "revisionValue", "1000", "pathRules", "path3=>x", "cleanDestinationDirectory", "true"), null, Fields.ALL_NESTED, getBeanContext(myFixture));
    dep1.sourceBuildType = new BuildType();
    dep1.sourceBuildType.setId(buildType2.getExternalId());
    dep1.type = "artifact_dependency";
    customDeps.propEntities = Arrays.asList(dep1);
    build.setCustomBuildArtifactDependencies(customDeps);

    checkException(BadRequestException.class,
                   () -> build.triggerBuild(user, myFixture, new HashMap<Long, Long>()),
                   "triggering build with more builds in artifact dependencies then there are in custom artifact dependencies");


    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build1));
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    builds1.setBuilds(Arrays.asList(build1, build2));
    build.setBuildArtifactDependencies(builds1);

    customDeps = new PropEntitiesArtifactDep();
    customDeps.setReplace("false");
    build.setCustomBuildArtifactDependencies(customDeps);

    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(3, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())),
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.newBuildIdRule(build2_2.getBuildId(), build2_2.getBuildNumber())),
                 new TestArtifactDep(buildType3.getBuildTypeId(), "path3=>b", false, RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber())));


    builds1 = new Builds();
    build1 = new Build();
    build1.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build1));
    build2 = new Build();
    build2.setLocator("id:" + build2_2.getBuildId());
    build3 = new Build();
    build3.setLocator("id:" + build2_1.getBuildId());
    builds1.setBuilds(Arrays.asList(build2, build3, build1));
    build.setBuildArtifactDependencies(builds1);

    customDeps = new PropEntitiesArtifactDep();
    dep1 = new PropEntityArtifactDep();
    dep1.properties =
      new Properties(createMap("revisionName", "buildId", "revisionValue", "1000", "pathRules", "path3=>x", "cleanDestinationDirectory", "true"), null, Fields.ALL_NESTED, getBeanContext(myFixture));
    dep1.sourceBuildType = new BuildType();
    dep1.sourceBuildType.setId(buildType2.getExternalId());
    dep1.type = "artifact_dependency";
    customDeps.propEntities = Arrays.asList(dep1);
    customDeps.setReplace("false");
    build.setCustomBuildArtifactDependencies(customDeps);

    result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals(4, result.getBuildPromotion().getArtifactDependencies().size());
    assertEquals(result.getBuildPromotion().getArtifactDependencies(), EQUALS_TEST,
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_1", true, RevisionRules.newBuildIdRule(build2_2.getBuildId(), build2_2.getBuildNumber())),
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path2_2=>a", false, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())),
                 new TestArtifactDep(buildType2.getBuildTypeId(), "path3=>x", true, RevisionRules.newBuildIdRule(build2_1.getBuildId(), build2_1.getBuildNumber())),
                 new TestArtifactDep(buildType3.getBuildTypeId(), "path3=>b", false, RevisionRules.newBuildIdRule(build3_1.getBuildId(), build3_1.getBuildNumber())));
  }

  @Test
  @TestFor(issues = "TW-21036")
  public void testDownloadedArtifacts() {
    DownloadedArtifactsLoggerImpl artifactsLogger = myFixture.getSingletonService(DownloadedArtifactsLoggerImpl.class);
    SBuildType bt1 = registerBuildType("bt1", "project1", "Ant");
    SBuild build1 = createBuild(bt1, Status.NORMAL);

    SBuildType bt2 = registerBuildType("bt2", "project2", "Ant");
    SBuild build2 = createBuild(bt2, Status.NORMAL);

    artifactsLogger.logArtifactDownload(build2.getBuildId(), build1.getBuildId(), "path1");

    artifactsLogger.waitForQueuePersisting();

    Build build2model = new Build(build2, new Fields("downloadedArtifacts(unfilteredCount,count,downloadInfo(count,build(id),artifactInfo(**)))"), getBeanContext(myFixture));
    jetbrains.buildServer.server.rest.model.build.downloadedArtifacts.DownloadedArtifacts build2Artifacts = build2model.getDownloadedArtifacts();
    assertEquals("There is 1 build as a source of artifacts", 1, (int) build2Artifacts.getUnfilteredCount());
    assertEquals("There is 1 build as a source of artifacts", 1, (int) build2Artifacts.getCount());
    assertEquals("There is 1 build as a source of artifacts", 1, build2Artifacts.getDownloadInfo().size());

    BuildArtifactsDownloadInfo build2downloadInfo = build2Artifacts.getDownloadInfo().get(0);
    assertEquals("There is 1 downloaded artifact", 1, (int) build2downloadInfo.getCount());
    assertEquals(
      "Build(" + build2.getBuildId() + ") downloaded artifact from build(" + build1.getBuildId() + ")",
      build1.getBuildId(), (long) build2downloadInfo.getBuild().getId()
    );

    DownloadedArtifacts realDownloaded = build2.getDownloadedArtifacts();
    ArtifactDownloadInfo artifactInfo = build2downloadInfo.getArtifactInfo().get(0);
    assertEquals("path1", artifactInfo.getArtifactPath());
    assertEquals(
      Util.formatTime(realDownloaded.getArtifacts().get(build1).get(0).getDownloadTimestamp()),
      artifactInfo.getDownloadTimestamp()
    );
  }

  @Test
  @TestFor(issues = "TW-21036")
  public void testDownloadedArtifactsPermission() throws Throwable {
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);

    DownloadedArtifactsLoggerImpl artifactsLogger = myFixture.getSingletonService(DownloadedArtifactsLoggerImpl.class);
    SBuildType bt0 = registerBuildType("bt0", "project0", "Ant");
    SBuild build0 = createBuild(bt0, Status.NORMAL);

    SBuildType bt1 = registerBuildType("bt1", "project1", "Ant");
    SBuild build1 = createBuild(bt1, Status.NORMAL);

    SBuildType bt2 = registerBuildType("bt2", "project2", "Ant");
    SBuild build2 = createBuild(bt2, Status.NORMAL);

    final SUser user = createUser("user1");
    myFixture.getSecurityContext().runAsSystem(() -> user.addRole(RoleScope.projectScope(build1.getProjectId()), getTestRoles().getProjectViewerRole()));

    artifactsLogger.logArtifactDownload(build2.getBuildId(), build1.getBuildId(), "path1");
    artifactsLogger.logArtifactDownload(build2.getBuildId(), build0.getBuildId(), "path0");
    artifactsLogger.waitForQueuePersisting();

    myFixture.getSecurityContext().setAuthorityHolder(user);

    Build build2model = new Build(build2, new Fields("downloadedArtifacts(unfilteredCount,count,downloadInfo)"), getBeanContext(myFixture));

    jetbrains.buildServer.server.rest.model.build.downloadedArtifacts.DownloadedArtifacts build2Artifacts = build2model.getDownloadedArtifacts();
    assertEquals("There are 2 builds as a source of artifacts", 2, (int) build2Artifacts.getUnfilteredCount());
    assertEquals("There is only 1 visible build as a source of artifacts", 1, (int) build2Artifacts.getCount());
    assertEquals("There is only 1 visible build as a source of artifacts", 1, build2Artifacts.getDownloadInfo().size());
  }

  @Test
  @TestFor(issues = "TW-50824")
  public void testBuildEstimates() throws Exception {
    BuildTypeImpl bt1 = registerBuildType("buildType1", "projectName");

    //~setting build estimate to 10 minutes
    Date tenMinutesAgo = Date.from(Instant.now().minus(10, ChronoUnit.MINUTES));
    createBuildWithBuildDurationStatistic(bt1, Status.NORMAL, tenMinutesAgo, tenMinutesAgo, myBuildAgent);

    build().in(bt1).addToQueue();
    build().in(bt1).parameter("a", "prevent merging").addToQueue();

    BuildTypeImpl bt2 = registerBuildType("buildType2", "projectName");
    bt2.addRequirement(myFixture.getSingletonService(RequirementFactory.class).createRequirement("missing", "some", RequirementType.EQUALS));
    build().in(bt2).addToQueue();

    myFixture.getSingletonService(CachingBuildEstimator.class).invalidate(true);

    TimeInterval timeInterval = myServer.getQueue().getItems().get(1).getBuildEstimates().getTimeInterval();
    long diff = timeInterval.getStartPoint().getAbsoluteTime().getTime() - Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)).getTime();
    assertTrue(timeInterval.getStartPoint().getAbsoluteTime() + " -- " + Dates.now() + ", diff: " + diff, diff < 60000);

    Build build1 = new Build(myServer.getQueue().getItems().get(1).getBuildPromotion(), Fields.LONG, getBeanContext(myFixture));
    assertEquals(Util.formatTime(myServer.getQueue().getItems().get(1).getBuildEstimates().getTimeInterval().getStartPoint().getAbsoluteTime()), build1.getStartEstimate());
    assertEquals(Util.formatTime(myServer.getQueue().getItems().get(1).getBuildEstimates().getTimeInterval().getEndPoint().getAbsoluteTime()), build1.getFinishEstimate());

    build1 = new Build(myServer.getQueue().getItems().get(2).getBuildPromotion(), Fields.LONG, getBeanContext(myFixture));
    assertNull(build1.getStartEstimate());
  }

  @Test
  @TestFor(issues = "TW-51092")
  public void testBuildArtifactsHrefForVersionedUrls() throws IOException {
    SFinishedBuild finishedBuild = build().in(myBuildType).finish();
    final File artifactsDir = finishedBuild.getArtifactsDirectory();
    //noinspection ResultOfMethodCallIgnored
    artifactsDir.mkdirs();
    File dir = new File(artifactsDir, "dir");
    //noinspection ResultOfMethodCallIgnored
    dir.mkdirs();
    File file = new File(artifactsDir, "file.txt");
     //noinspection ResultOfMethodCallIgnored
     file.createNewFile();

    final ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(path -> StringUtil.replace(path, "/app/rest/", "/app/rest/version/"));

    final Build build = new Build(finishedBuild, new Fields("href,artifacts(href,file(children,content))"), new BeanContext(new BeanFactory(null), myFixture, apiUrlBuilder));

    assertEquals("/app/rest/version/builds/id:1", build.getHref());
    assertEquals("/app/rest/version/builds/id:1/artifacts/children/", build.getArtifacts().href);
    //noinspection ConstantConditions
    assertEquals("/app/rest/version/builds/id:1/artifacts/children/dir", build.getArtifacts().files.get(0).getChildren().href);
    //noinspection ConstantConditions
    assertEquals("/app/rest/version/builds/id:1/artifacts/content/file.txt", build.getArtifacts().files.get(1).getContent().href);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Test
  @TestFor(issues = "TW-27205")
  public void testBuildArtifactsHrefWithSpecialSymbols() throws IOException {
    SFinishedBuild finishedBuild = build().in(myBuildType).finish();
    final File artifactsDir = finishedBuild.getArtifactsDirectory();
    artifactsDir.mkdirs();
    String specialCharacters = "~!@#$%^&()_+=-`][{}'; .,\u044B%61";

    File dir = new File(artifactsDir, specialCharacters);
    dir.mkdirs();
    File file = new File(dir, specialCharacters);
    file.createNewFile();

    final ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(path -> StringUtil.replace(path, "/app/rest/", "/app/rest/version/"));

    final Build build = new Build(finishedBuild, new Fields("href,artifacts($locator(recursive:true),href,file(name,fullName,href,children($long,$locator(pattern(+:**,+:%))),content))"), new BeanContext(new BeanFactory(null), myFixture, apiUrlBuilder));

    assertEquals("/app/rest/version/builds/id:1", build.getHref());
    //noinspection ConstantConditions
    jetbrains.buildServer.server.rest.model.files.File artifact1 = build.getArtifacts().files.get(0);
    jetbrains.buildServer.server.rest.model.files.File artifact2 = build.getArtifacts().files.get(1);

    String specialCharacters_escaped;
    if (!specialCharacters.equals(artifact1.name)) {
      System.out.println("File system does not seem to support some characters. Was creating file \"" + specialCharacters + "\" but got \"" + artifact1.name + "\"");
      specialCharacters_escaped = WebUtil.encode(artifact1.name);
    } else {
      specialCharacters_escaped = WebUtil.encode(specialCharacters);
      assertEquals(specialCharacters, artifact2.name);
      assertEquals(specialCharacters + "/" + specialCharacters, artifact2.fullName);
    }
    assertEquals("/app/rest/version/builds/id:1/artifacts/metadata/" + specialCharacters_escaped, artifact1.href);
    //noinspection ConstantConditions
    assertEquals("/app/rest/version/builds/id:1/artifacts/children/" + specialCharacters_escaped + "?locator=pattern(%2B:**,%2B:%25)", artifact1.getChildren().href);

    assertEquals("/app/rest/version/builds/id:1/artifacts/metadata/" + specialCharacters_escaped + "/" + specialCharacters_escaped, artifact2.href);
    //noinspection ConstantConditions
    assertEquals("/app/rest/version/builds/id:1/artifacts/content/" + specialCharacters_escaped + "/" + specialCharacters_escaped, artifact2.getContent().href);
  }

  @Test
  public void testBuildArtifactsCheapOperation() throws IOException {
    SFinishedBuild finishedBuild10 = build().in(myBuildType).finish();
    {
      final File artifactsDir = finishedBuild10.getArtifactsDirectory();
      //noinspection ResultOfMethodCallIgnored
      artifactsDir.mkdirs();
      File dir = new File(artifactsDir, "dir");
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
      File file = new File(artifactsDir, "file.txt");
      //noinspection ResultOfMethodCallIgnored
      file.createNewFile();
    }

    {
      final Build build = new Build(finishedBuild10, new Fields("$long"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNotNull(build.getArtifacts().href);
      assertNull(build.getArtifacts().count); //not calculated until requested
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNotNull(build.getArtifacts().href);
      assertNull(build.getArtifacts().count); //not calculated until requested
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild10.getBuildPromotion()).hasComputedArtifactsState()); //for the test to check what it needs to, this should not be yet calculated
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count); //still not yet calculated
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild10.getBuildPromotion()).hasComputedArtifactsState()); //check that the request did not trigger this to be calculated
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional),$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count); //still not yet calculated
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts($short)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild10.getBuildPromotion()).hasComputedArtifactsState()); //check that the request did not trigger this to be calculated

    ((BuildPromotionEx)finishedBuild10.getBuildPromotion()).getArtifactStateInfo(); //ensure this is calculated
    assertTrue(((BuildPromotionEx)finishedBuild10.getBuildPromotion()).hasComputedArtifactsState());
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional),$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(1), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional),$locator(count:2))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count); //still not yet calculated
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count); //still not yet calculated
      assertNull(build.getArtifacts().files);
    }
    assertTrue(((BuildPromotionEx)finishedBuild10.getBuildPromotion()).hasComputedArtifactsState());

    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(2), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts($short)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts($short,$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(1), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts($long)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(2), build.getArtifacts().count);
      assertNotNull(build.getArtifacts().files);
      assertEquals(2, build.getArtifacts().files.size());
    }
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional),$locator(hidden:any))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count);
    }
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional),$locator(hidden:true))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count);
    }
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional),$locator(modified:-1d))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count);
    }
    {
      final Build build = new Build(finishedBuild10, new Fields("artifacts(count($optional),$locator(aaa))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count);
    }


    SFinishedBuild finishedBuild20 = build().in(myBuildType).finish(); //build without artifacts

    {
      final Build build = new Build(finishedBuild20, new Fields("$long"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNotNull(build.getArtifacts().href);
      assertNull(build.getArtifacts().count); //not calculated until requested
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNotNull(build.getArtifacts().href);
      assertNull(build.getArtifacts().count); //not calculated until requested
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild20.getBuildPromotion()).hasComputedArtifactsState()); //for the test to check what it needs to, this should not be yet calculated
    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts(count($optional))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count); //still not yet calculated
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild20.getBuildPromotion()).hasComputedArtifactsState()); //check that the request did not trigger this to be calculated
    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts(count($optional),$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count); //still not yet calculated
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts($short)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild20.getBuildPromotion()).hasComputedArtifactsState()); //check that the request did not trigger this to be calculated

    ((BuildPromotionEx)finishedBuild20.getBuildPromotion()).getArtifactStateInfo(); //ensure this is calculated
    assertTrue(((BuildPromotionEx)finishedBuild20.getBuildPromotion()).hasComputedArtifactsState());
    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts(count($optional),$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts(count($optional),$locator(count:2))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts(count($optional))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }
    assertTrue(((BuildPromotionEx)finishedBuild20.getBuildPromotion()).hasComputedArtifactsState());

    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts(count)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts($short)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts($short,$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild20, new Fields("artifacts($long)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNotNull(build.getArtifacts().files);
      assertEquals(0, build.getArtifacts().files.size());
    }

    SFinishedBuild finishedBuild30 = build().in(myBuildType).finish();
    //build with hidden artifacts only
    {
      final File artifactsDir = finishedBuild30.getArtifactsDirectory();
      //noinspection ResultOfMethodCallIgnored
      artifactsDir.mkdirs();
      File dir = new File(artifactsDir, ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR);
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
      File file = new File(dir, "file.txt");
      //noinspection ResultOfMethodCallIgnored
      file.createNewFile();
    }

    {
      final Build build = new Build(finishedBuild30, new Fields("$long"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNotNull(build.getArtifacts().href);
      assertNull(build.getArtifacts().count); //not calculated until requested
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNotNull(build.getArtifacts().href);
      assertNull(build.getArtifacts().count); //not calculated until requested
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild30.getBuildPromotion()).hasComputedArtifactsState()); //for the test to check what it needs to, this should not be yet calculated
    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts(count($optional))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count); //still not yet calculated
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild30.getBuildPromotion()).hasComputedArtifactsState()); //check that the request did not trigger this to be calculated
    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts(count($optional),$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count); //still not yet calculated
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts($short)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertNull(build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }
    assertFalse(((BuildPromotionEx)finishedBuild30.getBuildPromotion()).hasComputedArtifactsState()); //check that the request did not trigger this to be calculated

    ((BuildPromotionEx)finishedBuild30.getBuildPromotion()).getArtifactStateInfo(); //ensure this is calculated
    assertTrue(((BuildPromotionEx)finishedBuild30.getBuildPromotion()).hasComputedArtifactsState());
    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts(count($optional),$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts(count($optional),$locator(count:2))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }
    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts(count($optional))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }
    assertTrue(((BuildPromotionEx)finishedBuild30.getBuildPromotion()).hasComputedArtifactsState());

    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts(count)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts($short)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts($short,$locator(count:1))"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNull(build.getArtifacts().files);
    }

    {
      final Build build = new Build(finishedBuild30, new Fields("artifacts($long)"), getBeanContext(myFixture));
      assertNotNull(build.getArtifacts());
      assertEquals(Integer.valueOf(0), build.getArtifacts().count);
      assertNotNull(build.getArtifacts().files);
      assertEquals(0, build.getArtifacts().files.size());
    }
  }

  @Test
  public void testChanges() throws IOException, ExecutionException, InterruptedException {
    MockVcsSupport vcs = new MockVcsSupport("vcs");
//    vcs.setDAGBased(true); //see jetbrains.buildServer.server.rest.data.ChangeFinderTest.testBranches1() for branches setup  example
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", myBuildType);
    VcsRootInstance root1 = myBuildType.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    addChange(root1, 10, changesPolicy);
    addChange(root1, 20, changesPolicy);
    ensureChangesDetected();

    SFinishedBuild build1 = build().in(myBuildType).finish();

    addChange(root1, 30, changesPolicy);
    addChange(root1, 40, changesPolicy);
    ensureChangesDetected();

    SFinishedBuild build2 = build().in(myBuildType).finish();

    addChange(root1, 50, changesPolicy);
    addChange(root1, 60, changesPolicy);
    ensureChangesDetected();

    BuildPromotionEx bp = (BuildPromotionEx)build2.getBuildPromotion();
    bp.getDetectedChanges(SelectPrevBuildPolicy.SINCE_LAST_BUILD); // makes sure changes are cached and thus getCount will return not null value

    {
      Build build = new Build(build2, Fields.SHORT, getBeanContext(myFixture));
      assertNull(build.getChanges());
    }
    {
      Build build = new Build(build2, Fields.LONG, getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertEquals("/app/rest/changes?locator=build:(id:" + build2.getBuildId() + ")", changes.getHref());
      assertEquals(Integer.valueOf(2), changes.getCount()); //changes are cached
      assertNull(changes.getChanges());
      assertNull(changes.getNextHref());
    }
    {
      ((BuildPromotionEx)build2.getBuildPromotion()).resetChangesCache();
      Build build = new Build(build2, Fields.LONG, getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertEquals("/app/rest/changes?locator=build:(id:" + build2.getBuildId() + ")", changes.getHref());
      assertNull(changes.getCount()); //changes are cached
      assertNull(changes.getChanges());
      assertNull(changes.getNextHref());
    }
    {
      Build build = new Build(build2, new Fields("changes($long)"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertEquals(Integer.valueOf(2), changes.getCount());
      assertEquals(2, changes.getChanges().size());
      assertNull(changes.getNextHref());
    }
    {
      Build build = new Build(build2, new Fields("changes($long,$locator(count(1)))"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertEquals("/app/rest/changes?locator=build:(id:" + build2.getBuildId() + "),count:1", changes.getHref());
      assertEquals(Integer.valueOf(1), changes.getCount());
      assertEquals(1, changes.getChanges().size());
    }
    {
      setInternalProperty("rest.defaultPageSize", "1");
      Build build = new Build(build2, new Fields("changes($long)"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertEquals("/app/rest/changes?locator=build:(id:" + build2.getBuildId() + ")", changes.getHref());
      assertEquals(Integer.valueOf(2), changes.getCount()); //all the changes are present in the node
      assertEquals(2, changes.getChanges().size());
      removeInternalProperty("rest.defaultPageSize");
    }
  }

  @Test
  public void testChangesOptional() throws IOException, ExecutionException, InterruptedException {
    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", myBuildType);
    VcsRootInstance root1 = myBuildType.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);
    for (int i = 0; i < 10; i++) {
      addChange(root1, i, changesPolicy);
    }
    ensureChangesDetected();

    SFinishedBuild build1 = build().in(myBuildType).finish();

    ((BuildPromotionEx)build1.getBuildPromotion()).resetChangesCache();

    assertFalse(myChangeFinder.isCheap(build1.getBuildPromotion(), null));
    {
      Build build = new Build(build1, new Fields("changes"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertEquals("/app/rest/changes?locator=build:(id:" + build1.getBuildId() + ")", changes.getHref());
      assertNull(changes.getCount());
      assertNull(changes.getChanges());
    }
    {
      Build build = new Build(build1, new Fields("changes(change($optional))"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertNull(changes.getCount());
      assertNull(changes.getChanges());
    }
    {
      Build build = new Build(build1, new Fields("changes(change($optional),count($optional))"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertNull(changes.getCount());
      assertNull(changes.getChanges());
    }
    {
      Build build = new Build(build1, new Fields("changes(count($optional))"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertNull(changes.getCount());
      assertNull(changes.getChanges());
    }
    {
      Build build = new Build(build1, new Fields("changes($locator(count:2),count($optional))"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertNull(changes.getCount());
      assertNull(changes.getChanges());
    }
    {
      Build build = new Build(build1, new Fields("changes($locator(count:2))"), getBeanContext(myFixture)); //no fields requested
      Changes changes = build.getChanges();
      assertNull(changes.getChanges());
      assertNull(changes.getCount());
    }
    assertFalse(myChangeFinder.isCheap(build1.getBuildPromotion(), null)); //still not calculated
    {
      Build build = new Build(build1, new Fields("changes($locator(count:2),count)"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertNull(changes.getChanges());
      assertEquals(Integer.valueOf(2), changes.getCount());
    }
    assertFalse(myChangeFinder.isCheap(build1.getBuildPromotion(), null));
    assertTrue(myChangeFinder.isCheap(build1.getBuildPromotion(), "count:2"));
    {
      Build build = new Build(build1, new Fields("changes($locator(count:2),count($optional))"), getBeanContext(myFixture));
      Changes changes = build.getChanges();
      assertEquals(Integer.valueOf(2), changes.getCount());
    }
    {
      Build build = new Build(build1, new Fields("changes($locator(count:1),count($optional))"), getBeanContext(myFixture)); //less than cached
      Changes changes = build.getChanges();
      assertEquals(Integer.valueOf(1), changes.getCount());
    }
    {
      Build build = new Build(build1, new Fields("changes($locator(count:3),count($optional))"), getBeanContext(myFixture));  //morethan cached
      Changes changes = build.getChanges();
      assertNull(changes.getCount());
    }
    assertFalse(myChangeFinder.isCheap(build1.getBuildPromotion(), "count:3")); //still not calculated

    {
      Build build = new Build(build1, new Fields("changes($optional)"), getBeanContext(myFixture));  //$optional should not be included when not supported
      assertNull(build.getChanges());
    }
  }

  @Test
  public void testTriggerBuildWithCustomRevisions() {
    MockVcsSupport git = new MockVcsSupport("git");
    MockCollectRepositoryChangesPolicy policy = new MockCollectRepositoryChangesPolicy();
    git.setCollectChangesPolicy(policy);

    myFixture.getVcsManager().registerVcsSupport(git);
    SVcsRootEx root1 = myProject.createVcsRoot(git.getName(), null, Collections.singletonMap(VcsUtil.BRANCH_SPEC_PROP, "+:*"));
    SVcsRootEx root2 = myProject.createVcsRoot(git.getName(), null, Collections.emptyMap());
    myBuildType.addVcsRoot(root1);
    myBuildType.addVcsRoot(root2);
    myBuildType.setCheckoutRules(root2, new CheckoutRules(". => subdir"));
    VcsRootInstance rootInst1 = myBuildType.getVcsRootInstanceForParent(root1);
    VcsRootInstance rootInst2 = myBuildType.getVcsRootInstanceForParent(root2);

    policy.setCurrentState(rootInst1, RepositoryStateData.createVersionState("master", map("master", "r1_0")));
    policy.setCurrentState(rootInst2, RepositoryStateData.createVersionState("main", map("main", "r2_0")));
    myServer.checkForModifications();

    policy.setCurrentState(rootInst1, RepositoryStateData.createVersionState("master", map("master", "r1_2", "br1", "r1_2`")));
    policy.setChanges(rootInst1, modification().by("user1").withChangedFile().version("r1_1").parentVersions("r1_0"),
                      modification().by("user2").withChangedFile().version("r1_2").parentVersions("r1_1"),
                      modification().by("user3").withChangedFile().version("r1_1`").parentVersions("r1_0"),
                      modification().by("user4").withChangedFile().version("r1_2`").parentVersions("r1_1`"));

    policy.setCurrentState(rootInst2, RepositoryStateData.createVersionState("main", map("main", "r2_1")));
    policy.setChanges(rootInst2, modification().by("user1").withChangedFile().version("r2_1").parentVersions("r2_0"));
    myServer.checkForModifications();

    final Build build = new Build();
    final BuildType buildTypeEntity = new BuildType();
    buildTypeEntity.setId(myBuildType.getExternalId());
    build.setBuildType(buildTypeEntity);
    build.setBranchName("br1");

    Revisions revisions = new Revisions();
    Revision r1 = new Revision();
    r1.vcsRoot = new jetbrains.buildServer.server.rest.model.change.VcsRootInstance();
    r1.vcsRoot.vcsRootId = root1.getExternalId();
    r1.displayRevision = "r1_1`";
    r1.vcsBranchName = "br1";
    Revision r2 = new Revision();
    r2.vcsRoot = new jetbrains.buildServer.server.rest.model.change.VcsRootInstance();
    r2.vcsRoot.id = String.valueOf(rootInst2.getId());
    r2.displayRevision = "r2_0";
    r2.checkoutRules = ".=>subdir  \n";
    revisions.revisions = new ArrayList<>();
    revisions.revisions.add(r1);
    revisions.revisions.add(r2);
    build.setRevisions(revisions);

    final SUser user = getOrCreateUser("user");
    SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());
    BuildPromotionEx bp = (BuildPromotionEx)result.getBuildPromotion();
    assertEquals("br1", bp.getBranch().getName());
    assertFalse(bp.isChangeCollectingNeeded());
    assertEquals(2, bp.getRevisions().size());

    assertEquals("r1_1`", bp.getRevisions().get(0).getRevision());
    assertEquals("br1", bp.getRevisions().get(0).getRepositoryVersion().getVcsBranch());
    assertEquals(rootInst1, bp.getRevisions().get(0).getRoot());
    assertTrue(((BuildRevisionEx)bp.getRevisions().get(0)).getModificationId() > 0);

    assertEquals("r2_0", bp.getRevisions().get(1).getRevision());
    assertNull(bp.getRevisions().get(1).getRepositoryVersion().getVcsBranch());
    assertEquals(rootInst2, bp.getRevisions().get(1).getRoot());
    assertTrue(bp.getRevisions().get(1).getCheckoutRules().getAsString().contains("subdir"));
    assertEquals(-1L, ((BuildRevisionEx)bp.getRevisions().get(1)).getModificationId().longValue());
  }

  @Test
  public void testTriggerBuildWithCustomRevisions_RevisionsPassedToDependencies() {
    MockVcsSupport git = new MockVcsSupport("git");
    MockCollectRepositoryChangesPolicy policy = new MockCollectRepositoryChangesPolicy();
    git.setCollectChangesPolicy(policy);

    myFixture.getVcsManager().registerVcsSupport(git);
    SVcsRootEx root1 = myProject.createVcsRoot(git.getName(), null, Collections.singletonMap(VcsUtil.BRANCH_SPEC_PROP, "+:*"));
    SVcsRootEx root2 = myProject.createVcsRoot(git.getName(), null, Collections.emptyMap());

    SBuildType bt1 = myProject.createBuildType("bt1");
    SBuildType bt2 = myProject.createBuildType("bt2");
    SBuildType bt3 = myProject.createBuildType("bt3");
    addDependency(bt1, bt2);
    addDependency(bt2, bt3);

    bt1.addVcsRoot(root1);
    bt2.addVcsRoot(root2);
    bt2.setCheckoutRules(root2, new CheckoutRules("+:subdir"));
    bt3.addVcsRoot(root2);
    bt3.setCheckoutRules(root2, new CheckoutRules("+:other"));

    VcsRootInstance rootInst1 = bt1.getVcsRootInstanceForParent(root1);
    VcsRootInstance rootInst2 = bt2.getVcsRootInstanceForParent(root2);

    policy.setCurrentState(rootInst1, RepositoryStateData.createVersionState("master", map("master", "r1_0")));
    policy.setCurrentState(rootInst2, RepositoryStateData.createVersionState("main", map("main", "r2_0")));
    myServer.checkForModifications();

    policy.setCurrentState(rootInst1, RepositoryStateData.createVersionState("master", map("master", "r1_2", "br1", "r1_2`")));
    policy.setChanges(rootInst1, modification().by("user1").withChangedFile().version("r1_1").parentVersions("r1_0"),
                      modification().by("user2").withChangedFile().version("r1_2").parentVersions("r1_1"),
                      modification().by("user3").withChangedFile().version("r1_1`").parentVersions("r1_0"),
                      modification().by("user4").withChangedFile().version("r1_2`").parentVersions("r1_1`"));

    policy.setCurrentState(rootInst2, RepositoryStateData.createVersionState("main", map("main", "r2_1")));
    policy.setChanges(rootInst2, modification().by("user1").withChangedFile().version("r2_1").parentVersions("r2_0"));
    myServer.checkForModifications();

    final Build build = new Build();
    final BuildType buildTypeEntity = new BuildType();
    buildTypeEntity.setId(bt1.getExternalId());
    build.setBuildType(buildTypeEntity);
    build.setBranchName("br1");

    Revisions revisions = new Revisions();
    Revision r1 = new Revision();
    r1.vcsRoot = new jetbrains.buildServer.server.rest.model.change.VcsRootInstance();
    r1.vcsRoot.vcsRootId = root1.getExternalId();
    r1.displayRevision = "r1_1`";
    r1.vcsBranchName = "br1";
    Revision r2 = new Revision();
    r2.vcsRoot = new jetbrains.buildServer.server.rest.model.change.VcsRootInstance();
    r2.vcsRoot.id = String.valueOf(rootInst2.getId());
    r2.displayRevision = "r2_0";
    r2.checkoutRules = "+:subdir";
    Revision r3 = new Revision();
    r3.vcsRoot = new jetbrains.buildServer.server.rest.model.change.VcsRootInstance();
    r3.vcsRoot.id = String.valueOf(rootInst2.getId());
    r3.displayRevision = "r2_1";
    r3.checkoutRules = "+:other";
    revisions.revisions = new ArrayList<>();
    revisions.revisions.add(r1);
    revisions.revisions.add(r2);
    revisions.revisions.add(r3);
    build.setRevisions(revisions);

    final SUser user = getOrCreateUser("user");
    SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<>());
    BuildPromotionEx bp = (BuildPromotionEx)result.getBuildPromotion();
    assertEquals("br1", bp.getBranch().getName());
    assertFalse(bp.isChangeCollectingNeeded());
    assertEquals(1, bp.getRevisions().size());

    assertEquals("r1_1`", bp.getRevisions().get(0).getRevision());
    assertEquals("br1", bp.getRevisions().get(0).getRepositoryVersion().getVcsBranch());
    assertEquals(rootInst1, bp.getRevisions().get(0).getRoot());
    assertTrue(((BuildRevisionEx)bp.getRevisions().get(0)).getModificationId() > 0);

    BuildPromotionEx bt2Bp = bp.getDependencies().iterator().next().getDependOn();
    assertEquals(bt2, bt2Bp.getBuildType());
    List<BuildRevision> depRevisions = bt2Bp.getRevisions();
    assertEquals(1, depRevisions.size());

    assertEquals("r2_0", depRevisions.get(0).getRevision());
    assertNull(depRevisions.get(0).getRepositoryVersion().getVcsBranch());
    assertEquals(rootInst2, depRevisions.get(0).getRoot());
    assertTrue(depRevisions.get(0).getCheckoutRules().getAsString().contains("subdir"));
    assertEquals(-1L, ((BuildRevisionEx)depRevisions.get(0)).getModificationId().longValue());

    BuildPromotionEx bt3Bp = bt2Bp.getDependencies().iterator().next().getDependOn();
    assertEquals(bt3, bt3Bp.getBuildType());
    assertEquals("r2_1", bt3Bp.getRevisions().get(0).getRevision());
  }

  @Test
  public void testTriggerBuildWithCustomRevisions_RevisionsPassedToDependencies_RevisionsAreProvidedForSomeRootsOnly() {
    MockVcsSupport git = new MockVcsSupport("git");
    MockCollectRepositoryChangesPolicy policy = new MockCollectRepositoryChangesPolicy();
    git.setCollectChangesPolicy(policy);

    myFixture.getVcsManager().registerVcsSupport(git);
    SVcsRootEx root1 = myProject.createVcsRoot(git.getName(), null, Collections.emptyMap());
    SVcsRootEx root2 = myProject.createVcsRoot(git.getName(), null, Collections.emptyMap());
    SVcsRootEx root3 = myProject.createVcsRoot(git.getName(), null, Collections.emptyMap());

    SBuildType bt1 = myProject.createBuildType("bt1");
    SBuildType bt2 = myProject.createBuildType("bt2");
    addDependency(bt1, bt2);

    bt1.addVcsRoot(root1);
    bt2.addVcsRoot(root2);
    bt2.addVcsRoot(root3);

    VcsRootInstance rootInst1 = bt1.getVcsRootInstanceForParent(root1);
    VcsRootInstance rootInst2 = bt2.getVcsRootInstanceForParent(root2);
    VcsRootInstance rootInst3 = bt2.getVcsRootInstanceForParent(root3);

    policy.setCurrentState(rootInst1, RepositoryStateData.createVersionState("master", map("master", "10")));
    policy.setCurrentState(rootInst2, RepositoryStateData.createVersionState("master", map("master", "20")));
    policy.setCurrentState(rootInst3, RepositoryStateData.createVersionState("master", map("master", "30")));
    myServer.checkForModifications();

    policy.setCurrentState(rootInst1, RepositoryStateData.createVersionState("master", map("master", "11")));
    policy.setChanges(rootInst1, modification().by("user1").withChangedFile().version("11").parentVersions("10"));

    policy.setCurrentState(rootInst2, RepositoryStateData.createVersionState("master", map("master", "21")));
    policy.setChanges(rootInst2, modification().by("user1").withChangedFile().version("21").parentVersions("20"));
    myServer.checkForModifications();

    final Build build = new Build();
    final BuildType buildTypeEntity = new BuildType();
    buildTypeEntity.setId(bt1.getExternalId());
    build.setBuildType(buildTypeEntity);
    build.setBranchName("master");

    Revisions revisions = new Revisions();
    Revision r1 = new Revision();
    r1.vcsRoot = new jetbrains.buildServer.server.rest.model.change.VcsRootInstance();
    r1.vcsRoot.vcsRootId = root1.getExternalId();
    r1.displayRevision = "11";
    r1.vcsBranchName = "master";
    Revision r2 = new Revision();
    r2.vcsRoot = new jetbrains.buildServer.server.rest.model.change.VcsRootInstance();
    r2.vcsRoot.id = String.valueOf(rootInst2.getId());
    r2.displayRevision = "21";
    revisions.revisions = new ArrayList<>();
    revisions.revisions.add(r1);
    revisions.revisions.add(r2);
    build.setRevisions(revisions);

    final SUser user = getOrCreateUser("user");
    try {
      build.triggerBuild(user, myFixture, new HashMap<>());
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Missing revisions"));
      assertTrue(e.getMessage().contains(root3.getExternalId()));
    }

    revisions = new Revisions();
    revisions.failOnMissingRevisions = false;
    revisions.revisions = new ArrayList<>();
    revisions.revisions.add(r1);
    revisions.revisions.add(r2);
    build.setRevisions(revisions);

    SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<>());
    BuildPromotionEx bp = (BuildPromotionEx)result.getBuildPromotion();
    assertFalse(bp.isChangeCollectingNeeded());
    assertTrue(bp.isChangeCollectingNeeded(true));
    assertEquals("master", bp.getBranch().getName());
    assertEquals(1, bp.getRevisions().size());

    assertEquals("11", bp.getRevisions().get(0).getRevision());

    BuildPromotionEx bt2Bp = bp.getDependencies().iterator().next().getDependOn();
    assertEquals(bt2, bt2Bp.getBuildType());
    List<BuildRevision> depRevisions = bt2Bp.getRevisions();
    assertTrue(bt2Bp.isChangeCollectingNeeded());
    assertEquals(0, depRevisions.size());
  }

  @Test
  public void testBuildTriggerWithCustomPasswordParameters() {
    final Build build = new Build();
    final BuildType buildType = new BuildType();
    buildType.setId(myBuildType.getExternalId());
    build.setBuildType(buildType);

    final Property secretProp = new Property();
    secretProp.type = new ParameterType();
    secretProp.type.rawValue = "password";
    secretProp.name = "my.secret.prop";
    secretProp.value = "very_secret";
    final Properties properties = new Properties();
    properties.setProperties(Arrays.asList(secretProp));
    build.setProperties(properties);

    final SUser triggeringUser = getOrCreateUser("user");
    final SQueuedBuild queuedBuild = build.triggerBuild(triggeringUser, myFixture, new HashMap<Long, Long>());
    assertEquals(myBuildType, queuedBuild.getBuildPromotion().getBuildType());
    String paramFromBuild = queuedBuild.getBuildPromotion().getParameterValue("my.secret.prop");
    assertEquals(OUR_PASSWORD_REPLACE, paramFromBuild);
  }

  @Test
  @TestFor(issues = "TW-81244")
  public void testNoParametersForQueuedBuild() {
    SQueuedBuild queuedBuild = build().in(myBuildType).parameter("some_param", "value").addToQueue();

    Build model = new Build(queuedBuild.getBuildPromotion(), new Fields("resultingProperties(**),startProperties(**)"), getBeanContext(myFixture));

    assertEquals(new Integer(0), model.getResultingProperties().getCount());
    assertEquals(new Integer(0), model.getStartProperties().getCount());
  }

  @Test
  @TestFor(issues = "TW-81244")
  public void testNoParametersForRunningCompositeBuild() {
    BuildTypeEx bt = myProject.createBuildType("composite"); BuildServerCreator.makeComposite(bt);

    SRunningBuild runningBuild = build().in(bt).parameter("some_param", "value").run();

    Build model = new Build(runningBuild.getBuildPromotion(), new Fields("resultingProperties(**),startProperties(**)"), getBeanContext(myFixture));

    assertEquals(new Integer(0), model.getResultingProperties().getCount());
    assertEquals(new Integer(0), model.getStartProperties().getCount());
    assertFalse(runningBuild.isFinished());
  }

  private void ensureChangesDetected() {
    myFixture.getVcsModificationChecker().checkForModifications(myBuildType.getVcsRootInstances(), OperationRequestor.UNKNOWN);
  }

  @NotNull
  private SVcsModification addChange(@NotNull final VcsRootInstance root1, final int version, @NotNull final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy) {
    SVcsModification result = myFixture.addModification(modification().in(root1).version(String.valueOf(version)));
    changesPolicy.setCurrentState(root1, RepositoryStateData.createSingleVersionState(String.valueOf(version)));
    return result;
  }

  @Test(enabled = false)
  @TestFor(issues = "TW-48945")
  public void testBuildTriggeringWithBuildTypeAndCustomDefaultParameter() {
    BuildTypeImpl buildType1 = registerBuildType("buildType1", "projectName");

    buildType1.addParameter(new SimpleParameter("name", "value"));

    final SUser user = getOrCreateUser("user");

    // end of setup

    final Build build = new Build();
    final BuildType buildTypeEntity = new BuildType();
    buildTypeEntity.setId(buildType1.getExternalId());
    buildTypeEntity.setDescription("some description");
    build.setBuildType(buildTypeEntity);
    build.setProperties(new Properties(createMap("name", "value"), null, Fields.ALL, getBeanContext(myFixture)));
    SQueuedBuild result = build.triggerBuild(user, myFixture, new HashMap<Long, Long>());

    assertEquals("value", result.getBuildPromotion().getParameterValue("name"));
  }


  public static <E, A> void assertEquals(@Nullable final List<A> collection, EqualsTest<E, A> equalsTest, final E... items) {
    assertEquals(null, collection, equalsTest, (e) -> BaseFinderTest.getDescription(e), (e) -> BaseFinderTest.getDescription(e), items);
  }

  public static <E, A> void assertEquals(final String description, @Nullable final List<A> collection, EqualsTest<E, A> equalsTest,
                                         @NotNull DescriptionProvider<E> loggerExpected, @NotNull DescriptionProvider<A> loggerActual, final E... items) {
    final String expected = BaseFinderTest.getDescription(Arrays.asList(items), loggerExpected);
    final String actual = getDescription(collection, loggerActual);
    if (collection == null && items.length == 0) return;
    assertEquals("Expected and actual collection sizes are different for '" + description + "'\n" +
                 "Expected:\n" + expected + "\n\n" +
                 "Actual:\n" + actual,
                 items.length, collection == null ? 0 : collection.size());

    for (E item : items) {
      boolean ok = false;
      for (A t : collection) {
        if (equalsTest.equals(item, t)) {
          ok = true;
          break;
        }
      }
      if (!ok) {
        fail("Actual collection does not have item " + loggerExpected.describe(item) + " for '" + description + "'\n" +
             "Expected:\n" + expected + "\n\n" +
             "Actual:\n" + actual);
      }
    }

    for (A t : collection) {
      boolean ok = false;
      for (E item : items) {
        if (equalsTest.equals(item, t)) {
          ok = true;
          break;
        }
      }
      if (!ok) {
        fail("Actual collection does not have item " + loggerActual.describe(t) + " for '" + description + "'\n" +
             "Expected:\n" + expected + "\n\n" +
             "Actual:\n" + actual);
      }
    }
  }

  protected static final EqualsTest<SArtifactDependency, SArtifactDependency> EQUALS_TEST = (o1, o2) -> o1.isSimilarTo(o2);

  public interface EqualsTest<A, T> {
    boolean equals(@NotNull final A o1, @NotNull final T o2);
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
