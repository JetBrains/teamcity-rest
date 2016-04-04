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

import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Branch;
import jetbrains.buildServer.server.rest.model.build.Branches;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.request.BuildTypeRequest;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.RelativeWebLinks;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
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
    assertEquals(1, branches.branches.size());
    Branch branch = branches.branches.get(0);
    assertEquals("<default>", branch.getName());
    assertEquals(Boolean.TRUE, branch.isDefault());
    assertEquals(null, branch.isUnspecified());

    branches = buildTypeRequest.serveBranches("id:testBT", null, Fields.ALL_NESTED.getFieldsSpec());
    assertEquals(1, branches.branches.size());
    branch = branches.branches.get(0);
    assertEquals("<default>", branch.getName());
    assertEquals(Boolean.TRUE, branch.isDefault());
    assertEquals(Boolean.FALSE, branch.isUnspecified());


    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    final SVcsRoot vcsRoot = bt.getProject().createVcsRoot("vcs", "extId", "name");
    bt.addVcsRoot(vcsRoot);

    final VcsRootInstance vcsRootInstance = bt.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState("master", map("master", "1", "branch1", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance, "+:*");

    branches = buildTypeRequest.serveBranches("id:testBT", null, null);
    assertEquals(1, branches.branches.size());
    branch = branches.branches.get(0);
    assertEquals("<default>", branch.getName()); // why default before checking for changes???
    assertEquals(Boolean.TRUE, branch.isDefault());
    assertEquals(null, branch.isUnspecified());

    bt.forceCheckingForChanges();
    myFixture.getVcsModificationChecker().ensureModificationChecksComplete();

    branches = buildTypeRequest.serveBranches("id:testBT", null, null);
    assertEquals(1, branches.branches.size());
    branch = branches.branches.get(0);
    assertEquals("master", branch.getName());
    assertEquals(Boolean.TRUE, branch.isDefault());
    assertEquals(null, branch.isUnspecified());

    branches = buildTypeRequest.serveBranches("id:testBT", "policy:ALL_BRANCHES", null);
    assertEquals(3, branches.branches.size());
    branch = branches.branches.get(0);
    assertEquals("master", branch.getName());
    assertEquals(Boolean.TRUE, branch.isDefault());
    assertEquals(null, branch.isUnspecified());
    branch = branches.branches.get(1);
    assertEquals("branch1", branch.getName());
    assertEquals(null, branch.isDefault());
    assertEquals(null, branch.isUnspecified());
    branch = branches.branches.get(2);
    assertEquals("branch2", branch.getName());
    assertEquals(null, branch.isDefault());
    assertEquals(null, branch.isUnspecified());

    branches = buildTypeRequest.serveBranches("id:testBT", "policy:all_branches", null);
    assertEquals(3, branches.branches.size());

    branches = buildTypeRequest.serveBranches("id:testBT", "default:true", null);
    assertEquals(1, branches.branches.size());
    branch = branches.branches.get(0);
    assertEquals("master", branch.getName());
    assertEquals(Boolean.TRUE, branch.isDefault());
    assertEquals(null, branch.isUnspecified());

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
