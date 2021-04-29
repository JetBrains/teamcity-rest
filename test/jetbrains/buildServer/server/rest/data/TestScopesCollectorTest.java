/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScope;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopesCollector;
import jetbrains.buildServer.serverSide.CurrentProblemsManager;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.TestName2IndexImpl;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManagerImpl;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestScopesCollectorTest extends BaseServerTestCase {
  private PermissionChecker myPermissionChecker;
  private ProjectFinder myProjectFinder;
  private AgentFinder myAgentFinder;
  private BuildTypeFinder myBuildTypeFinder;
  private BuildFinder myBuildFinder;
  private TestOccurrenceFinder myTestOccurrenceFinder;
  private TestFinder myTestFinder;
  private BranchFinder myBranchFinder;
  private BuildPromotionFinder myBuildPromotionFinder;
  private VcsRootFinder myVcsRootFinder;
  private TimeCondition myTimeCondition;
  private UserGroupFinder myGroupFinder;
  private UserFinder myUserFinder;


  private TestScopesCollector myCollector;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myTimeCondition = new TimeCondition(myFixture);
    myFixture.addService(myTimeCondition);

    myPermissionChecker = new PermissionChecker(myServer.getSecurityContext(), myProjectManager);
    myFixture.addService(myPermissionChecker);

    myProjectFinder = new ProjectFinder(myProjectManager, myPermissionChecker, myServer);
    myFixture.addService(myProjectFinder);

    myGroupFinder = new UserGroupFinder(getUserGroupManager());
    myFixture.addService(myGroupFinder);
    myUserFinder = new UserFinder(getUserModelEx(), myGroupFinder, myProjectFinder, myTimeCondition,
                                  myFixture.getRolesManager(), myPermissionChecker, myServer.getSecurityContext(), myServer);

    myAgentFinder = new AgentFinder(myAgentManager, myFixture);
    myFixture.addService(myAgentFinder);


    myBuildTypeFinder = new BuildTypeFinder(myProjectManager, myProjectFinder, myAgentFinder, myPermissionChecker, myFixture);

    myBranchFinder = new BranchFinder(myBuildTypeFinder, myFixture);

    final VcsRootIdentifiersManagerImpl vcsRootIdentifiersManager = myFixture.getSingletonService(VcsRootIdentifiersManagerImpl.class);

    myVcsRootFinder = new VcsRootFinder(myFixture.getVcsManager(), myProjectFinder, myBuildTypeFinder, myProjectManager, vcsRootIdentifiersManager, myPermissionChecker);
    myFixture.addService(myVcsRootFinder);

    myBuildPromotionFinder = new BuildPromotionFinder(myFixture.getBuildPromotionManager(), myFixture.getBuildQueue(), myServer, myVcsRootFinder, myProjectFinder,
                                                      myBuildTypeFinder, myUserFinder, myAgentFinder, myBranchFinder, myTimeCondition, myPermissionChecker, null, myFixture);
    myFixture.addService(myBuildPromotionFinder);

    myBuildFinder = new BuildFinder(myFixture, myBuildTypeFinder, myProjectFinder, myUserFinder, myBuildPromotionFinder, myAgentFinder);


    final TestName2IndexImpl testName2Index = myFixture.getSingletonService(TestName2IndexImpl.class);
    final ProblemMutingService problemMutingService = myFixture.getSingletonService(ProblemMutingService.class);
    myTestFinder = new TestFinder(myProjectFinder, myBuildTypeFinder, myBuildPromotionFinder,
                                  myFixture.getTestManager(), testName2Index, myFixture.getCurrentProblemsManager(), problemMutingService);
    myFixture.addService(myTestFinder);

    final CurrentProblemsManager currentProblemsManager = myServer.getSingletonService(CurrentProblemsManager.class);
    myTestOccurrenceFinder = new TestOccurrenceFinder(myTestFinder, myBuildFinder, myBuildTypeFinder, myProjectFinder, myFixture.getTestsHistory(), currentProblemsManager, myBranchFinder);
    myFixture.addService(myTestOccurrenceFinder);


    myCollector = new TestScopesCollector(myTestOccurrenceFinder);
  }

  @Test
  public void testCanGetPackages() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("package1.class1.aaa", true)
                                          .withTest("package2.class2.bbb", true)
                                          .withTest("package3.class1.ccc", true)
                                          .withTest("package4.class2.ddd", true)
                                          .finish();
    String locator = "scopeType:package,testOccurrences:(build:(id:" + build10.getBuildId() + "))";
    PagedSearchResult<TestScope> result = myCollector.getPagedItems(Locator.locator(locator));

    assertEquals(4, result.myEntries.size());

    Set<String> packages = result.myEntries.stream()
                                           .peek(scope -> assertEquals(1, scope.getTestRuns().size()))
                                           .peek(scope -> scope.getName().equals(scope.getPackage()))
                                           .map(scope -> scope.getPackage())
                                           .collect(Collectors.toSet());

    for(int i = 1; i <= 4; i++) {
      assertTrue(packages.contains("package" + i));
    }
  }

  @Test
  public void testCanGetClasses() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest("packageA.class1.aaa", true)
                                          .withTest("packageA.class2.bbb", true)
                                          .withTest("packageB.class1.ccc", true)
                                          .withTest("packageB.class2.ddd", true)
                                          .finish();

    String locator = "scopeType:class,testOccurrences:(build:(id:" + build10.getBuildId() + "))";
    PagedSearchResult<TestScope> result = myCollector.getPagedItems(Locator.locator(locator));

    assertEquals("Although there are only class1 and class 2, packageA.classX and packageB.classX are expected to be different", 4, result.myEntries.size());

    Set<String> classes = result.myEntries.stream()
                                           .peek(scope -> assertEquals(1, scope.getTestRuns().size()))
                                           .peek(scope -> scope.getName().equals(scope.getClass1()))
                                           .map(scope -> scope.getClass1())
                                           .collect(Collectors.toSet());

    assertContains(classes, "class1", "class2");
  }

  @Test
  public void testCanGetSuites() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    final SFinishedBuild build10 = build().in(buildType)
                                          .startSuite("suite1")
                                            .withTest("packageA.class1.aaa", true)
                                            .withTest("packageA.class2.bbb", true)
                                          .endSuite()
                                          .startSuite("suite2")
                                            .withTest("packageB.class1.ccc", true)
                                            .withTest("packageB.class2.ddd", true)
                                          .endSuite()
                                          .finish();

    PagedSearchResult<TestScope> result = myCollector.getPagedItems(Locator.createPotentiallyEmptyLocator("testOccurrences:(build:(id:" + build10.getBuildId() + ")),scopeType:suite"));
    for(TestScope scope : result.myEntries) {
      assertEquals(2, scope.getTestRuns().size());
    }

    Set<String> suites = result.myEntries.stream()
                                         .peek(scope -> assertEquals(2, scope.getTestRuns().size()))
                                         .peek(scope -> scope.getName().equals(scope.getSuite()))
                                         .map(scope -> scope.getSuite())
                                         .collect(Collectors.toSet());

    assertEquals(2, suites.size());
    assertContains(suites, "suite1: ", "suite2: ");
  }

  @Test
  public void testCanFilterSuites() {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");

    final SFinishedBuild build10 = build().in(buildType)
                                          .startSuite("suite1")
                                          .withTest("packageA.class1.aaa", true)
                                          .withTest("packageA.class2.bbb", true)
                                          .endSuite()
                                          .startSuite("suite2")
                                          .withTest("packageB.class1.ccc", true)
                                          .withTest("packageB.class2.ddd", true)
                                          .endSuite()
                                          .finish();

    // $base64:c3VpdGUxOiA= is a base64 representation of a string 'suite1: '
    PagedSearchResult<TestScope> result = myCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(id:" + build10.getBuildId() + ")),scopeType:suite,suite:(value:($base64:c3VpdGUxOiA=),matchType:equals)"
    ));
    assertEquals(1, result.myEntries.size());
    assertEquals("suite1: ", result.myEntries.get(0).getName());
  }

  @Test
  public void testReturnsCorrectTree() {

    final BuildTypeImpl buildType1 = registerBuildType("buildConf1", "project");

    final SFinishedBuild build1 = build().in(buildType1)
                                         .startSuite("suite1")
                                         .withTest("packageA.class1.aaa", true)
                                         .withTest("packageA.class2.aaa", true)
                                         .withTest("packageB.class1.aaa", true)
                                         .endSuite()
                                         .finish();

    final BuildTypeImpl buildType2 = registerBuildType("buildConf2", "project");

    final SFinishedBuild build2 = build().in(buildType2)
                                         .startSuite("suite1")
                                         .withTest("packageA.class1.aaa", true)
                                         .withTest("packageA.class2.aaa", true)
                                         .withTest("packageB.class1.aaa", true)
                                         .endSuite()
                                         .finish();

  }

  private void buildTree() {
    /* Builds a following tree:

        aaa  bbb    aaa       aaa                     aaa      aaa
          \   |      |         |                      |        |
          class1  class2    class1                     class1  class2
              \    /          |                       \    /
              packageA     packageB                                 suite2
                    \      /                |             |
                     suite1             buildConf2
                       |                    |
                  buildConf1            subproject
                          \              /
                              project
                                |
                             _Root
     */

    ProjectEx project = myFixture.getProject();
    ProjectEx subproject = myFixture.createProject("subproject", project);

  }
}
