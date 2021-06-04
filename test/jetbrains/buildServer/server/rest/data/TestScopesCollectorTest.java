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
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeFilterProducer;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopesCollector;
import jetbrains.buildServer.serverSide.BuildTypeEx;
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

    TestScopeFilterProducer testScopesFilterProducer = new TestScopeFilterProducer(myProjectManager);
    final CurrentProblemsManager currentProblemsManager = myServer.getSingletonService(CurrentProblemsManager.class);
    myTestOccurrenceFinder = new TestOccurrenceFinder(myTestFinder, myBuildFinder, myBuildTypeFinder, myProjectFinder, myFixture.getTestsHistory(), currentProblemsManager, myBranchFinder, testScopesFilterProducer);
    myFixture.addService(myTestOccurrenceFinder);


    myCollector = new TestScopesCollector(myTestOccurrenceFinder, testScopesFilterProducer);
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
  public void testReturnsSuitesComplex() {
    buildTree();
    PagedSearchResult<TestScope> result = myCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:suite"
    ));

    assertEquals(2, result.myEntries.size());
    Set<String> resultNames = result.myEntries.stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "suite1: ");
    assertContains(resultNames, "suite2: ");
  }

  @Test
  public void testReturnsPackagesComplex() {
    buildTree();
    PagedSearchResult<TestScope> result = myCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:package"
    ));

    // suite1: packageA
    // suite1: packageB
    // suite2: packageC
    // suite2: packageA
    assertEquals(4, result.myEntries.size());
    Set<String> resultNames = result.myEntries.stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "packageA");
    assertContains(resultNames, "packageB");
    assertContains(resultNames, "packageC");
  }

  @Test
  public void testReturnsClassesComplex() {
    buildTree();
    PagedSearchResult<TestScope> result = myCollector.getPagedItems(Locator.createPotentiallyEmptyLocator(
      "testOccurrences:(build:(affectedProject:(name:project))),scopeType:class"
    ));

    // suite1: packageA.class1
    // suite1: packageA.class2
    // suite1: packageB.class1
    // suite2: packageC.class2
    // suite2: packageA.class3
    assertEquals(5, result.myEntries.size());
    Set<String> resultNames = result.myEntries.stream().map(s -> s.getName()).collect(Collectors.toSet());
    assertContains(resultNames, "class1");
    assertContains(resultNames, "class2");
    assertContains(resultNames, "class3");
  }

  private void buildTree() {
    /* Builds a following tree:

                                 project
                               /        \
                       project1          project2
                      /        \                \
                buildconf1   subproject11    subproject21
                    |               \               \
                  suite1          buildconf2     buildconf11
                 /      \             \             /     \
            packageA    packageB    suite2       suite1  suite2
             /     \        \          \           |        \
         class1   class2    class1   packageA   packageB   packageC
          /   \      |         \        \          |          \
         a     b     a          b     class3    class1       class2
                                         \         |           \
                                          c        a            c
     */

    ProjectEx project = myFixture.createProject("project", "project");

    ProjectEx project1 = project.createProject("project1", "project1");
    ProjectEx project2 = project.createProject("project2", "project2");

    ProjectEx subproject11 = project1.createProject("subproject11", "subproject11");
    ProjectEx subproject21 = project2.createProject("subproject21", "subproject21");

    BuildTypeEx buildconf1 = project1.createBuildType("buildConf1");
    BuildTypeEx buildconf2 = subproject11.createBuildType("buildconf2");
    BuildTypeEx buildconf11 = subproject21.createBuildType("buildconf11");

    final SFinishedBuild build1 = build().in(buildconf1)
                                         .startSuite("suite1")
                                         .withTest("packageA.class1.a", true)
                                         .withTest("packageA.class1.b", true)
                                         .withTest("packageA.class2.a", true)
                                         .withTest("packageB.class1.b", true)
                                         .endSuite()
                                         .finish();

    final SFinishedBuild build2 = build().in(buildconf2)
                                         .startSuite("suite2")
                                         .withTest("packageA.class3.c", true)
                                         .endSuite()
                                         .finish();

    final SFinishedBuild build3 = build().in(buildconf11)
                                         .startSuite("suite1")
                                         .withTest("packageB.class1.a", true)
                                         .endSuite()
                                         .startSuite("suite2")
                                         .withTest("packageC.class2.c", true)
                                         .endSuite()
                                         .finish();
  }
}
