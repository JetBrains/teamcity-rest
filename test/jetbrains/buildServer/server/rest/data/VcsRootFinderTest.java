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

import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManagerImpl;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.projects.ProjectManagerImpl;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.impl.VcsManagerImpl;
import org.junit.Ignore;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 29.07.13
 */
@Test
public class VcsRootFinderTest extends BaseServerTestCase {

  private VcsRootFinder myVcsRootFinder;
  private ProjectManagerImpl myProjectManager;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final VcsManagerImpl vcsManager = myFixture.getVcsManager();
    myProjectManager = myFixture.getProjectManager();
    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager);
    final AgentFinder agentFinder = new AgentFinder(myAgentManager);
    final BuildTypeFinder buildTypeFinder = new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, myServer);
    final PermissionChecker permissionChecker = new PermissionChecker(myServer.getSecurityContext());
    myVcsRootFinder = new VcsRootFinder(vcsManager, projectFinder, buildTypeFinder, myProjectManager,
                                        myFixture.getSingletonService(VcsRootIdentifiersManagerImpl.class),
                                        permissionChecker);
  }

  private SVcsRoot createRoots() {
    return createRoots("svn");
  }

  private SVcsRoot createRoots(final String type) {
    myFixture.registerVcsSupport("svn");
    myFixture.registerVcsSupport("cvs");
    if (!"svn".equals(type) && !"cvs".equals(type)) myFixture.registerVcsSupport(type);
    final ProjectEx rootProject = myProjectManager.getRootProject();
    final ProjectEx project1 = rootProject.createProject("project1", "Project name");
    rootProject.createVcsRoot("svn", "id1", "VCS root 1 name");
    final SVcsRoot actualVcsRoot = project1.createVcsRoot(type, "id2", "VCS root 2 name");
    project1.createVcsRoot("cvs", "id3", "VCS root 3 name");
    rootProject.createVcsRoot("svn", "id4", "VCS root 4 name");

    return actualVcsRoot;
  }


  @Test(enabled = false, description = "Need to disable permission checking or run tests with due authorized user")
  public void testNoLocator() throws Exception {
    createRoots();

    final PagedSearchResult<SVcsRoot> foundVcsRoots = myVcsRootFinder.getVcsRoots(null);
    assertEquals(4, foundVcsRoots.myEntries.size());
  }

  @Test
  @Ignore("Need to disable permission checking or run tests with due authorized user")
  public void testNoDimensionLocator() throws Exception {
    final SVcsRoot actualVcsRoot = createRoots();

    checkRootIsFoundBy(actualVcsRoot, "id2");
    checkNoRootIsFoundBy("id_2");
  }

  @Test
  public void testIdsLocator() throws Exception {
    final SVcsRoot actualVcsRoot = createRoots();

    checkRootIsFoundBy(actualVcsRoot, "id:id2");
    checkNoRootIsFoundBy("id:id 2");
  }

  @Test
  public void testInternalIdsLocator() throws Exception {
    final SVcsRoot actualVcsRoot = createRoots();

    checkRootIsFoundBy(actualVcsRoot, "internalId:" + actualVcsRoot.getId());
    checkNoRootIsFoundBy("internalId:" + actualVcsRoot.getId() + 10);
  }

  @Test
  public void testNameLocator() throws Exception {
    final SVcsRoot actualVcsRoot = createRoots();

    checkRootIsFoundBy(actualVcsRoot, "name:VCS root 2 name");
    checkNoRootIsFoundBy("name:VCS root 2 name1");
  }

  @Test
  public void testTypeLocator() throws Exception {
    final SVcsRoot actualVcsRoot = createRoots("customType");

    checkRootIsFoundBy(actualVcsRoot, "type:customType");
    checkNoRootIsFoundBy("type:custom Type");
  }

  @Test
  public void testProjectLocator() throws Exception {
    final SVcsRoot actualVcsRoot = createRoots();

    checkRootIsFoundBy(actualVcsRoot, "project:(id:project1),type:svn");
    checkNoRootIsFoundBy("project:(id:project_missing)");
  }

  private void checkRootIsFoundBy(final SVcsRoot actualVcsRoot, final String locatorText) {
    SVcsRoot foundVcsRoot = myVcsRootFinder.getVcsRoot(locatorText);
    assertNotNull(foundVcsRoot);
    assertEquals(actualVcsRoot, foundVcsRoot);

    final PagedSearchResult<SVcsRoot> foundVcsRoots = myVcsRootFinder.getVcsRoots(VcsRootFinder.createVcsRootLocator("name:VCS root 2 name"));
    assertEquals(1, foundVcsRoots.myEntries.size());
    foundVcsRoot = foundVcsRoots.myEntries.get(0);
    assertNotNull(foundVcsRoot);
    assertEquals(actualVcsRoot, foundVcsRoot);
  }

  private void checkNoRootIsFoundBy(final String locatorText) {
    try {
      myVcsRootFinder.getVcsRoot(locatorText);
      assertTrue("Exception should be thrown", false);
    } catch (NotFoundException e) {
      return;
    }
    assertTrue("NotFoundException exception should be thrown", false);
  }

}
