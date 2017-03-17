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

package jetbrains.buildServer.server.rest.model;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.artifacts.RevisionRules;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.requirements.RequirementType;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.BuildFinderTestBase;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.build.Branches;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.model.buildType.PropEntity;
import jetbrains.buildServer.server.rest.model.buildType.VcsRootEntry;
import jetbrains.buildServer.server.rest.request.BuildTypeRequest;
import jetbrains.buildServer.server.rest.request.ProjectRequestTest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.SArtifactDependency;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.impl.MockBuildAgent;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;

/**
 * @author Yegor.Yarko
 *         Date: 20/10/2015
 */
public class BuildTypeTest extends BaseFinderTest<BuildTypeOrTemplate> {

  private BeanContext myBeanContext;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myBeanContext = getBeanContext(myServer);
  }

  @Test
  public void testSimple() {
    final BuildTypeEx bt = getRootProject().createProject("Project1", "Project test 1").createBuildType("testBT", "My test build type");
    final BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt), Fields.LONG, myBeanContext);
    assertEquals(bt.getName(), buildType.getName());
    assertEquals(bt.getProjectExternalId(), buildType.getProjectId());
    assertEquals(bt.getProjectName(), buildType.getProjectName());
    assertEquals(new Integer(0), buildType.getParameters().count);

    final Investigations investigations = buildType.getInvestigations();
    assertEquals(null, investigations.count);
    assertEquals("/app/rest/investigations?locator=buildType:(id:testBT)", investigations.href);
  }

  @Test
  public void testInvestigations() {
    final SUser user = createUser("user");
    final BuildTypeEx bt = getRootProject().createProject("Project1", "Project test 1").createBuildType("testBT", "My test build type");
    myFixture.getResponsibilityFacadeEx().setBuildTypeResponsibility(bt, createRespEntry(ResponsibilityEntry.State.TAKEN, user));
    BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt), Fields.LONG, myBeanContext);

    Investigations investigations = buildType.getInvestigations();
    assertEquals(null, investigations.count);
    assertEquals("/app/rest/investigations?locator=buildType:(id:testBT)", investigations.href);
    assertEquals(null, investigations.items);

    buildType = new BuildType(new BuildTypeOrTemplate(bt), new Fields("investigations($long)"), myBeanContext);
    investigations = buildType.getInvestigations();

    assertEquals(new Integer(1), investigations.count);
    assertEquals("/app/rest/investigations?locator=buildType:(id:testBT)", investigations.href);
    assertEquals(1, investigations.items.size());
    assertEquals(ResponsibilityEntry.State.TAKEN.name(), investigations.items.get(0).state);

    buildType = new BuildType(new BuildTypeOrTemplate(bt), new Fields("investigations($long,$locator(assignee(id:" + user.getId() + ")))"), myBeanContext);
    investigations = buildType.getInvestigations();

    assertEquals(new Integer(1), investigations.count);
    assertEquals("/app/rest/investigations?locator=assignee:(id:" + user.getId() + "),buildType:(id:testBT)", investigations.href);
    assertEquals(1, investigations.items.size());
    assertEquals(ResponsibilityEntry.State.TAKEN.name(), investigations.items.get(0).state);

    final SUser user2 = createUser("user2");
    buildType = new BuildType(new BuildTypeOrTemplate(bt), new Fields("investigations($long,count,$locator(assignee(id:" + user2.getId() + ")))"), myBeanContext);
    investigations = buildType.getInvestigations();

    assertEquals(new Integer(0), investigations.count);
    assertEquals("/app/rest/investigations?locator=assignee:(id:" + user2.getId() + "),buildType:(id:testBT)", investigations.href);
    assertNull(investigations.items);
  }

  @Test
  public void testBranches() {
    final BuildTypeEx bt = getRootProject().createProject("Project1", "Project test 1").createBuildType("testBT", "My test build type");

    final BuildTypeRequest buildTypeRequest = new BuildTypeRequest();
    buildTypeRequest.setInTests(myBuildTypeFinder, myBranchFinder);

    Branches branches = buildTypeRequest.serveBranches("id:testBT", null, null);
    ProjectRequestTest.assertBranchesEquals(branches.branches, "<default>", true, null);

    branches = buildTypeRequest.serveBranches("id:testBT", null, Fields.ALL_NESTED.getFieldsSpec());
    ProjectRequestTest.assertBranchesEquals(branches.branches, "<default>", true, false);


    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    final SVcsRoot vcsRoot = bt.getProject().createVcsRoot("vcs", "extId", "name");
    bt.addVcsRoot(vcsRoot);

    final VcsRootInstance vcsRootInstance = bt.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState("master", map("master", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance, "+:*");

    branches = buildTypeRequest.serveBranches("id:testBT", null, null);
    ProjectRequestTest.assertBranchesEquals(branches.branches, "<default>", true, null); // why default before checking for changes???

    bt.forceCheckingForChanges();
    myFixture.getVcsModificationChecker().ensureModificationChecksComplete();

    branches = buildTypeRequest.serveBranches("id:testBT", null, null);
    ProjectRequestTest.assertBranchesEquals(branches.branches, "master", true, null);

    branches = buildTypeRequest.serveBranches("id:testBT", "policy:ALL_BRANCHES", null);
    ProjectRequestTest.assertBranchesEquals(branches.branches,
                                            "master", true, null,
                                            "branch1", null, null,
                                            "branch2", null, null);
    assertNull(branches.branches.get(0).getInternalName());

    branches = buildTypeRequest.serveBranches("id:testBT", "policy:ALL_BRANCHES", Fields.ALL_NESTED.getFieldsSpec());
    assertEquals("<default>", branches.branches.get(0).getInternalName());
    assertEquals("branch1", branches.branches.get(1).getInternalName());
    assertEquals("branch2", branches.branches.get(2).getInternalName());

    branches = buildTypeRequest.serveBranches("id:testBT", "policy:all_branches", null);
    ProjectRequestTest.assertBranchesEquals(branches.branches,
                                            "master", true, null,
                                            "branch1", null, null,
                                            "branch2", null, null);

    branches = buildTypeRequest.serveBranches("id:testBT", "default:true", null);
    ProjectRequestTest.assertBranchesEquals(branches.branches,"master", true, null);

    bt.addVcsRoot(bt.getProject().createVcsRoot("vcs", "extId2", "name2"));

    final VcsRootInstance vcsRootInstance2 = bt.getVcsRootInstances().get(1);
    collectChangesPolicy.setCurrentState(vcsRootInstance2, createVersionState("master2", map("master2", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance2, "+:*");
    bt.forceCheckingForChanges();
    myFixture.getVcsModificationChecker().ensureModificationChecksComplete();

    branches = buildTypeRequest.serveBranches("id:testBT", "policy:ALL_BRANCHES", Fields.ALL_NESTED.getFieldsSpec());
    ProjectRequestTest.assertBranchesEquals(branches.branches,
                                            "<default>", true, false,
                                            "branch1", false, false,
                                            "branch2", false, false);
    assertEquals("<default>", branches.branches.get(0).getInternalName());


    checkException(BadRequestException.class, new Runnable() {
      public void run() {
        buildTypeRequest.serveBranches("id:testBT", "changesFromDependencies:any", null);
      }
    }, "searching with wrong changesFromDependencies");

    checkException(BadRequestException.class, new Runnable() {
      public void run() {
        buildTypeRequest.serveBranches("id:testBT", "policy:INVALID_POLICY", null);
      }
    }, "searching with wrong changesFromDependencies");

    //can also add test for branches from builds
    //can also add test for "changesFromDependencies:true" locator and several defaults in different branches
  }

  @Test
  public void testLinks() {
    final BuildTypeEx bt = getRootProject().createProject("Project1", "Project test 1").createBuildType("testBT", "My test build type");
    WebLinks webLinks = getWebLinks(myServer.getRootUrl());
    RelativeWebLinks relativeWebLinks = new RelativeWebLinks();
    assertEquals("http://localhost/viewType.html?buildTypeId=testBT", webLinks.getConfigurationHomePageUrl(bt));
    assertEquals("/viewType.html?buildTypeId=testBT", relativeWebLinks.getConfigurationHomePageUrl(bt));

    BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt), Fields.SHORT, myBeanContext);

    assertEquals(webLinks.getConfigurationHomePageUrl(bt), buildType.getWebUrl());
    assertNull(buildType.getLinks());

    buildType = new BuildType(new BuildTypeOrTemplate(bt), Fields.LONG, myBeanContext);
    assertEquals(webLinks.getConfigurationHomePageUrl(bt), buildType.getWebUrl());
    assertNull(buildType.getLinks()); //not present until explicitly requested

    buildType = new BuildType(new BuildTypeOrTemplate(bt), new Fields("links"), myBeanContext);
    assertNotNull(buildType.getLinks());
    assertEquals(Integer.valueOf(2), buildType.getLinks().count);
    assertNotNull(buildType.getLinks().links);
    assertEquals("webView", buildType.getLinks().links.get(0).type);
    assertEquals(webLinks.getConfigurationHomePageUrl(bt), buildType.getLinks().links.get(0).url);
    assertEquals(relativeWebLinks.getConfigurationHomePageUrl(bt), buildType.getLinks().links.get(0).relativeUrl);
    assertEquals("webEdit", buildType.getLinks().links.get(1).type);
    assertEquals(webLinks.getEditConfigurationPageUrl(bt.getExternalId()), buildType.getLinks().links.get(1).url);
    assertEquals(relativeWebLinks.getEditConfigurationPageUrl(bt.getExternalId()), buildType.getLinks().links.get(1).relativeUrl);
  }

  @Test
  public void testAgents() {
    myBuildAgent.setAuthorized(false, null, ""); //we will need the agent license

    ProjectEx project10 = createProject("project10", "project 10");
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");
    bt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("a", null, RequirementType.EXISTS));
    BuildTypeEx bt20 = project10.createBuildType("bt20", "bt 20");

    MockBuildAgent agent10 = myFixture.createEnabledAgent("agent10", "Ant"); // not compatible
    MockBuildAgent agent20 = myFixture.createEnabledAgent("agent20", "Ant"); //compatible
    agent20.addConfigParameter("a", "b");
    agent20.pushAgentTypeData();

    MockBuildAgent agent30 = myFixture.createEnabledAgent("agent30", "Ant"); //compatible
    agent30.addConfigParameter("a", "b");
    agent30.pushAgentTypeData();
    agent30.setAuthorized(false, null, "");

    {
      BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), Fields.LONG, myBeanContext);
      assertNotNull(buildType.getCompatibleAgents());
      assertNull(buildType.getCompatibleAgents().count);
      assertNotNull(buildType.getCompatibleAgents().href);
      assertContains(buildType.getCompatibleAgents().href, "compatible:(buildType:(id:" + bt10.getExternalId() + "))");
      assertNull(buildType.getCompatibleAgents().agents);
    }

    {
      BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("compatibleAgents($long)"), myBeanContext);
      assertNotNull(buildType.getCompatibleAgents());
      assertEquals(Integer.valueOf(1), buildType.getCompatibleAgents().count);
      assertContains(buildType.getCompatibleAgents().href, "compatible:(buildType:(id:" + bt10.getExternalId() + "))");
      assertNotNull(buildType.getCompatibleAgents().agents);
      assertEquals(1, buildType.getCompatibleAgents().agents.size());
    }

    {
      BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("compatibleAgents($long,$locator(authorized:any))"), myBeanContext);
      assertNotNull(buildType.getCompatibleAgents());
      assertEquals(Integer.valueOf(2), buildType.getCompatibleAgents().count);
//      assertContains(buildType.getCompatibleAgents().href, "compatible:(buildType:(id:" + bt10.getExternalId() + ")),authorized:any");
      assertNotNull(buildType.getCompatibleAgents().agents);
      assertEquals(2, buildType.getCompatibleAgents().agents.size());
    }
  }

  @Test
  public void testInheritance() {
    //see also alike setup in BuildTypeRequestTest.testCreatingWithTemplate()
    ProjectEx project10 = createProject("project10", "project 10");
    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();
    final SVcsRoot vcsRoot10 = project10.createVcsRoot("vcs", "extId10", "name10");
    final SVcsRoot vcsRoot20 = project10.createVcsRoot("vcs", "extId20", "name20");
    final SVcsRoot vcsRoot30 = project10.createVcsRoot("vcs", "extId30", "name30");

    project10.addParameter(new SimpleParameter("p", "v"));

    BuildTypeEx bt100 = project10.createBuildType("bt100", "bt 100");
    BuildTypeEx bt110 = project10.createBuildType("bt110", "bt 110");
    BuildTypeEx bt120 = project10.createBuildType("bt120", "bt 120");


    // TEMPLATE
    BuildTypeTemplate t10 = project10.createBuildTypeTemplate("t10", "bt 10");

    t10.setArtifactPaths("aaaaa");
    t10.setBuildNumberPattern("pattern");
    t10.setOption(BuildTypeOptions.BT_ALLOW_EXTERNAL_STATUS, true);
    t10.setOption(BuildTypeOptions.BT_CHECKOUT_DIR, "checkout_t");
    t10.setOption(BuildTypeOptions.BT_CHECKOUT_MODE, "ON_AGENT");
    t10.setOption(BuildTypeOptions.BT_FAIL_ON_ANY_ERROR_MESSAGE, true);
    t10.setOption(BuildTypeOptions.BT_EXECUTION_TIMEOUT, 11);


    t10.addVcsRoot(vcsRoot10);
    t10.addVcsRoot(vcsRoot20);
    t10.setCheckoutRules(vcsRoot20, new CheckoutRules("a=>b"));

    BuildRunnerDescriptorFactory runnerDescriptorFactory = myFixture.getSingletonService(BuildRunnerDescriptorFactory.class);
    t10.addBuildRunner(runnerDescriptorFactory.createBuildRunner(project10, "run10", "name10", "Ant1", map("a", "b")));
    t10.addBuildRunner(runnerDescriptorFactory.createBuildRunner(project10, "run20", "name20", "Ant2", map("a", "b")));

    BuildTriggerDescriptor trigger10 = t10.addBuildTrigger("Type", map("a", "b"));
    BuildTriggerDescriptor trigger20 = t10.addBuildTrigger("Type", map("a", "b"));

    t10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f10", "type", map("a", "b")));
    t10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f20", "type", map("a", "b")));
    t10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f30", "type", map("a", "b")));

    ArtifactDependencyFactory artifactDependencyFactory = myFixture.getSingletonService(ArtifactDependencyFactory.class);
    ArrayList<SArtifactDependency> artifactDeps = new ArrayList<>();
    artifactDeps.add(artifactDependencyFactory.createArtifactDependency("art10", bt100.getExternalId(), "path1", RevisionRules.LAST_PINNED_RULE));
    artifactDeps.add(artifactDependencyFactory.createArtifactDependency("art20", bt100.getExternalId(), "path2", RevisionRules.LAST_PINNED_RULE));
    artifactDeps.add(artifactDependencyFactory.createArtifactDependency("art30", bt100.getExternalId(), "path3", RevisionRules.LAST_PINNED_RULE));
    t10.setArtifactDependencies(artifactDeps);

    t10.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(bt100));
    t10.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(bt110));

    t10.addParameter(new SimpleParameter("a10", "b"));
    t10.addParameter(new SimpleParameter("a20", "b"));

    t10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req10", "a", null, RequirementType.EXISTS));
    t10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req20", "b", null, RequirementType.EXISTS));
    t10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req30", "c", null, RequirementType.EXISTS));

    // BUILD TYPE
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");
    bt10.attachToTemplate(t10);

    bt10.setArtifactPaths("bbbb"); //todo: test w/o override
    bt10.setOption(BuildTypeOptions.BT_ALLOW_EXTERNAL_STATUS, false);
    bt10.setOption(BuildTypeOptions.BT_CHECKOUT_DIR, "checkout_bt");
    bt10.setOption(BuildTypeOptions.BT_CHECKOUT_MODE, "ON_AGENT");
    bt10.setOption(BuildTypeOptions.BT_EXECUTION_TIMEOUT, 17);

    bt10.addVcsRoot(vcsRoot20);
    bt10.setCheckoutRules(vcsRoot20, new CheckoutRules("x=>y"));
    bt10.addVcsRoot(vcsRoot30);

    bt10.setEnabled("run20", false);
    bt10.addBuildRunner(runnerDescriptorFactory.createBuildRunner(project10, "run30", "name30", "Ant30", map("a", "b")));

    bt10.setEnabled(trigger20.getId(), false);
    BuildTriggerDescriptor trigger30 = bt10.addBuildTrigger("Type", map("a", "b"));

    bt10.setEnabled("f20", false);
    bt10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f30", "type_bt", map("a", "b")));
    bt10.addBuildFeature(myFixture.getBuildFeatureDescriptorFactory().createBuildFeature("f40", "type", map("a", "b")));

    ArrayList<SArtifactDependency> artifactDepsBt = new ArrayList<>();
    artifactDepsBt.add(artifactDependencyFactory.createArtifactDependency("art30", bt100.getExternalId(), "path30", RevisionRules.LAST_FINISHED_RULE));
    artifactDepsBt.add(artifactDependencyFactory.createArtifactDependency("art40", bt100.getExternalId(), "path4", RevisionRules.LAST_PINNED_RULE));
    bt10.setArtifactDependencies(artifactDepsBt);
    bt10.setEnabled("art20", false);
    bt10.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(bt110));
    bt10.addDependency(myFixture.getSingletonService(DependencyFactory.class).createDependency(bt120));

    bt10.addParameter(new SimpleParameter("a20", "x"));
    bt10.addParameter(new SimpleParameter("a30", "x"));

    bt10.setEnabled("req20", false);
    bt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req30", "x", null, RequirementType.EQUALS));
    bt10.addRequirement(myFixture.findSingletonService(RequirementFactory.class).createRequirement("req40", "y", null, RequirementType.EXISTS));


    // NOW, TEST TIME!

    BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long"), myBeanContext);

    parameterEquals(find(buildType.getSettings().properties, "artifactRules"), "artifactRules", "bbbb", null);
    parameterEquals(find(buildType.getSettings().properties, "buildNumberPattern"), "buildNumberPattern", "pattern", true);
    parameterEquals(find(buildType.getSettings().properties, "allowExternalStatus"), "allowExternalStatus", "false", null);
    parameterEquals(find(buildType.getSettings().properties, "checkoutDirectory"), "checkoutDirectory", "checkout_bt", null);
//    parameterEquals(find(buildType.getSettings().properties, "checkoutMode"), "checkoutMode", "ON_AGENT", null); //option set to the same value in bt - API does not make difference so far
    parameterEquals(find(buildType.getSettings().properties, "shouldFailBuildOnAnyErrorMessage"), "shouldFailBuildOnAnyErrorMessage", "true", true);
    parameterEquals(find(buildType.getSettings().properties, "executionTimeoutMin"), "executionTimeoutMin", "17", null);
    assertNull(find(buildType.getSettings().properties, "showDependenciesChanges")); //default value

    assertEquals(3, buildType.getVcsRootEntries().vcsRootAssignments.size());
    vcsRootEntryEquals(buildType.getVcsRootEntries().vcsRootAssignments.get(0), vcsRoot10.getExternalId(), "", true);
    vcsRootEntryEquals(buildType.getVcsRootEntries().vcsRootAssignments.get(1), vcsRoot20.getExternalId(), "a=>b", true); //bt modifications are ignored
    vcsRootEntryEquals(buildType.getVcsRootEntries().vcsRootAssignments.get(2), vcsRoot30.getExternalId(), "", null);

    assertEquals(3, buildType.getSteps().propEntities.size());
    stepsEquals(buildType.getSteps().propEntities.get(0), "run10", "Ant1", null, true);
    stepsEquals(buildType.getSteps().propEntities.get(1), "run20", "Ant2", false, true);
    stepsEquals(buildType.getSteps().propEntities.get(2), "run30", "Ant30", null, null);

    //TeamCity issue: order of some entities depends on where the trigger is defined (build type or template)

    assertEquals(3, buildType.getTriggers().propEntities.size());
    stepsEquals(buildType.getTriggers().propEntities.get(0), trigger30.getId(), "Type", null, null);
    stepsEquals(buildType.getTriggers().propEntities.get(1), trigger10.getId(), "Type", null, true);
    stepsEquals(buildType.getTriggers().propEntities.get(2), trigger20.getId(), "Type", false, true);

    assertEquals(4, buildType.getFeatures().propEntities.size());
    stepsEquals(buildType.getFeatures().propEntities.get(0), "f30", "type_bt", null, null);
    stepsEquals(buildType.getFeatures().propEntities.get(1), "f40", "type", null, null);
    stepsEquals(buildType.getFeatures().propEntities.get(2), "f10", "type", null, true);
    stepsEquals(buildType.getFeatures().propEntities.get(3), "f20", "type", false, true);

    assertEquals(4, buildType.getArtifactDependencies().propEntities.size());
    stepsEquals(buildType.getArtifactDependencies().propEntities.get(0), "art30", "artifact_dependency", null, null);
    stepsEquals(buildType.getArtifactDependencies().propEntities.get(1), "art40", "artifact_dependency", null, null);
    stepsEquals(buildType.getArtifactDependencies().propEntities.get(2), "art10", "artifact_dependency", null, true);
    stepsEquals(buildType.getArtifactDependencies().propEntities.get(3), "art20", "artifact_dependency", false, true);

    assertEquals(3, buildType.getSnapshotDependencies().propEntities.size());
    stepsEquals(buildType.getSnapshotDependencies().propEntities.get(0), bt100.getExternalId(), "snapshot_dependency", null, true);
    stepsEquals(buildType.getSnapshotDependencies().propEntities.get(1), bt110.getExternalId(), "snapshot_dependency", null, true);
    stepsEquals(buildType.getSnapshotDependencies().propEntities.get(2), bt120.getExternalId(), "snapshot_dependency", null, null);

    assertEquals(4, buildType.getParameters().properties.size());
    parameterEquals(buildType.getParameters().properties.get(0), "a10", "b", true);
    parameterEquals(buildType.getParameters().properties.get(1), "a20", "x", null);
    parameterEquals(buildType.getParameters().properties.get(2), "a30", "x", null);
    parameterEquals(buildType.getParameters().properties.get(3), "p", "v", true);

    assertEquals(4, buildType.getAgentRequirements().propEntities.size());
    stepsEquals(buildType.getAgentRequirements().propEntities.get(0), "req30", "equals", null, null);
    stepsEquals(buildType.getAgentRequirements().propEntities.get(1), "req40", "exists", null, null);
    stepsEquals(buildType.getAgentRequirements().propEntities.get(2), "req10", "exists", null, true);
    stepsEquals(buildType.getAgentRequirements().propEntities.get(3), "req20", "exists", false, true);
  }

  @Test
  public void testSettings() {
    ProjectEx project10 = createProject("project10", "project 10");
    BuildTypeEx bt10 = project10.createBuildType("bt10", "bt 10");

    {
      BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long"), myBeanContext);

      parameterEquals(find(buildType.getSettings().properties, "buildNumberCounter"), "buildNumberCounter", "1", null);
      assertEquals(1, buildType.getSettings().properties.size());
    }

    bt10.setArtifactPaths("bbbb");
    bt10.setOption(BuildTypeOptions.BT_ALLOW_EXTERNAL_STATUS, false);
    bt10.setOption(BuildTypeOptions.BT_CHECKOUT_DIR, "checkout_bt");
    bt10.setOption(BuildTypeOptions.BT_CHECKOUT_MODE, "ON_SERVER");
    bt10.setOption(BuildTypeOptions.BT_EXECUTION_TIMEOUT, 17);

    {
      BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long"), myBeanContext);

      parameterEquals(find(buildType.getSettings().properties, "artifactRules"), "artifactRules", "bbbb", null);
//    parameterEquals(find(buildType.getSettings().properties, "allowExternalStatus"), "allowExternalStatus", "false", null); //settings to default value does not set it in API...
      parameterEquals(find(buildType.getSettings().properties, "checkoutDirectory"), "checkoutDirectory", "checkout_bt", null);
      parameterEquals(find(buildType.getSettings().properties, "checkoutMode"), "checkoutMode", "ON_SERVER", null);
      parameterEquals(find(buildType.getSettings().properties, "executionTimeoutMin"), "executionTimeoutMin", "17", null);

      assertNull(find(buildType.getSettings().properties, "allowPersonalBuildTriggering"));
      assertNull(find(buildType.getSettings().properties, "buildNumberPattern"));
      assertNull(find(buildType.getSettings().properties, "shouldFailBuildOnAnyErrorMessage"));
      assertNull(find(buildType.getSettings().properties, "showDependenciesChanges"));
    }

    bt10.setOption(BuildTypeOptions.BT_ALLOW_PERSONAL_BUILD_TRIGGERING, false);
    bt10.setOption(BuildTypeOptions.BT_BUILD_NUMBER_PATTERN, "aaa");
    bt10.setOption(BuildTypeOptions.BT_FAIL_ON_ANY_ERROR_MESSAGE, true);
    bt10.setOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES, true);

    {
      BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long"), myBeanContext);

      parameterEquals(find(buildType.getSettings().properties, "allowPersonalBuildTriggering"), "allowPersonalBuildTriggering", "false", null);
      parameterEquals(find(buildType.getSettings().properties, "buildNumberPattern"), "buildNumberPattern", "aaa", null);
      parameterEquals(find(buildType.getSettings().properties, "shouldFailBuildOnAnyErrorMessage"), "shouldFailBuildOnAnyErrorMessage", "true", null);
      parameterEquals(find(buildType.getSettings().properties, "showDependenciesChanges"), "showDependenciesChanges", "true", null);
    }

    {
      BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long,settings($long,$locator(defaults:any))"), myBeanContext);

      assertEquals(19, buildType.getSettings().properties.size());
      parameterEquals(find(buildType.getSettings().properties, "buildNumberCounter"), "buildNumberCounter", "1", null);
      parameterEquals(find(buildType.getSettings().properties, "buildNumberPattern"), "buildNumberPattern", "aaa", null);
    }

    {
      BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt10), new Fields("$long,settings($long,$locator(defaults:any,name:buildNumberCounter))"), myBeanContext);

      assertEquals(1, buildType.getSettings().properties.size());
      parameterEquals(find(buildType.getSettings().properties, "buildNumberCounter"), "buildNumberCounter", "1", null);
    }
  }

  @Test
  public void testParametersCount() {
    final BuildTypeEx bt = getRootProject().createProject("Project1", "Project test 1").createBuildType("testBT", "My test build type");
    bt.addParameter(new SimpleParameter("a", "b"));
    {
      final BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt), Fields.LONG, myBeanContext);
      assertEquals(new Integer(1), buildType.getParameters().count);
      assertEquals(1, buildType.getParameters().properties.size());
      assertEquals("a", buildType.getParameters().properties.get(0).name);
      assertEquals("b", buildType.getParameters().properties.get(0).value);
    }
    {
      final BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt), new Fields("parameters($short)"), myBeanContext);
      assertEquals(new Integer(1), buildType.getParameters().count);
      assertNull(buildType.getParameters().properties);
    }
  }


  private static void stepsEquals(final PropEntity propEntity, final String id, final String type, final Boolean enabled, final Boolean inherited) {
    assertEquals(id, propEntity.id);
    assertEquals(type, propEntity.type);
    if (enabled == null) {
      assertNull(propEntity.disabled);
    } else {
      assertEquals(Boolean.valueOf(!enabled), propEntity.disabled);
    }
    assertEquals(inherited, propEntity.inherited);
  }

  @Nullable
  static private Property find(@NotNull final List<Property> properties, @NotNull final String propertyName) {
    return CollectionsUtil.findFirst(properties, data -> propertyName.equals(data.name));
  }

  private void vcsRootEntryEquals(final VcsRootEntry vcsRootEntry, final String id, final String checkout_rules, final Boolean inherited) {
    assertEquals(id, vcsRootEntry.id);
    assertEquals(checkout_rules, vcsRootEntry.checkoutRules);
    assertEquals(inherited, vcsRootEntry.inherited);
  }

  private void parameterEquals(final Property property, final String name, final String value, final Boolean inherited) {
    assertNotNull(property);
    assertEquals("name", name, property.name);
    assertEquals("value", value, property.value);
    assertEquals("inherited", inherited, property.inherited);
  }

  private static WebLinks getWebLinks(@NotNull final String rootUrl) {
    return new WebLinks(new RootUrlHolder() {
      @NotNull
      public String getRootUrl() {
        return rootUrl;
      }

      public void setRootUrl(@NotNull final String rootUrl) {}
    });
  }

}
