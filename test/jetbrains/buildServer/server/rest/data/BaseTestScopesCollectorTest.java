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

package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.server.rest.data.finder.impl.*;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopeFilterProducer;
import jetbrains.buildServer.server.rest.data.problem.scope.TestScopesCollector;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManagerImpl;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import org.testng.annotations.BeforeMethod;

public class BaseTestScopesCollectorTest extends BaseServerTestCase {
  private PermissionChecker myPermissionChecker;
  private ProjectFinder myProjectFinder;
  private AgentFinder myAgentFinder;
  private BuildTypeFinder myBuildTypeFinder;
  protected TestOccurrenceFinder myTestOccurrenceFinder;
  private TestFinder myTestFinder;
  private BranchFinder myBranchFinder;
  private BuildPromotionFinder myBuildPromotionFinder;
  private VcsRootFinder myVcsRootFinder;
  private TimeCondition myTimeCondition;
  private UserGroupFinder myGroupFinder;
  private UserFinder myUserFinder;

  protected TestScopesCollector myTestScopesCollector;

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

    myFixture.addService(new BranchGroupsService(myServer));
    myBranchFinder = new BranchFinder(myBuildTypeFinder, myFixture);

    final VcsRootIdentifiersManagerImpl vcsRootIdentifiersManager = myFixture.getSingletonService(VcsRootIdentifiersManagerImpl.class);

    myVcsRootFinder = new VcsRootFinder(myFixture.getVcsManager(), myProjectFinder, myBuildTypeFinder, myProjectManager, vcsRootIdentifiersManager, myPermissionChecker);
    myFixture.addService(myVcsRootFinder);

    myBuildPromotionFinder = new BuildPromotionFinder(myFixture.getBuildPromotionManager(), myFixture.getBuildQueue(), myServer, myVcsRootFinder, myProjectFinder,
                                                      myBuildTypeFinder, myUserFinder, myAgentFinder, myBranchFinder, myTimeCondition, myPermissionChecker, null, myFixture);
    myFixture.addService(myBuildPromotionFinder);


    final TestName2Index testName2Index = myFixture.getSingletonService(TestName2Index.class);
    final ProblemMutingService problemMutingService = myFixture.getSingletonService(ProblemMutingService.class);
    myTestFinder = new TestFinder(myProjectFinder, myBuildTypeFinder, myBuildPromotionFinder,
                                  myFixture.getTestManager(), testName2Index, myFixture.getCurrentProblemsManager(), problemMutingService);
    myFixture.addService(myTestFinder);

    TestScopeFilterProducer testScopesFilterProducer = new TestScopeFilterProducer(myBuildTypeFinder);
    final CurrentProblemsManager currentProblemsManager = myServer.getSingletonService(CurrentProblemsManager.class);
    myTestOccurrenceFinder = new TestOccurrenceFinder(
      myServer.getSecurityContext(),
      myTestFinder,
      myBuildPromotionFinder,
      myBuildTypeFinder,
      myProjectFinder,
      myFixture.getTestsHistory(),
      currentProblemsManager,
      myBranchFinder,
      testScopesFilterProducer
    );
    myFixture.addService(myTestOccurrenceFinder);


    myTestScopesCollector = new TestScopesCollector(myTestOccurrenceFinder, testScopesFilterProducer);
  }

  protected void buildTree() {
    /* Builds a following tree:

                                 project
                               /        \
                       project1          project2
                      /        \                \
                buildconf1   subproject11    subproject21
                    |               \                    \
                  suite1          buildconf2            buildconf1
                 /      \             \             /        |    \
            packageA    packageB    suite2       suite1  suite2   suite0
             /     \        \          \           |         |         \
         class1   class2    class1   packageA   packageB   packageC    packageZ
         / | \       |         \        \          |         |            \
        b  a  c      a          b     class3    class1       class2      classZ
                                         \         |       / | | | | \      \
                                          c        a      h  g f e d  c      z
     */

    ProjectEx project = myFixture.createProject("project", "project");

    ProjectEx project1 = project.createProject("project1", "project1");
    ProjectEx project2 = project.createProject("project2", "project2");

    ProjectEx subproject11 = project1.createProject("subproject11", "subproject11");
    ProjectEx subproject21 = project2.createProject("subproject21", "subproject21");

    BuildTypeEx buildconf1 = project1.createBuildType("buildconf1");
    BuildTypeEx buildconf2 = subproject11.createBuildType("buildconf2");
    BuildTypeEx buildconf11 = subproject21.createBuildType("buildconf1");

    final SFinishedBuild build1 = build().in(buildconf1)
                                         .startSuite("suite1")
                                         .withTest("packageA.class1.b", false)
                                         .withTest("packageA.class1.a", false)
                                         .withTest("packageB.class1.b", false)
                                         .withTest("packageA.class2.a", false)
                                         .withTest("packageA.class1.c", false)
                                         .endSuite()
                                         .finish();

    final SFinishedBuild build2 = build().in(buildconf2)
                                         .startSuite("suite2")
                                         .withTest("packageA.class3.c", false)
                                         .endSuite()
                                         .finish();

    final SFinishedBuild build3 = build().in(buildconf11)
                                         .startSuite("suite1")
                                         .withTest("packageB.class1.a", false)
                                         .endSuite()
                                         .startSuite("suite2")
                                         .withTest("packageC.class2.h", false)
                                         .withTest("packageC.class2.g", false)
                                         .withTest("packageC.class2.f", false)
                                         .withTest("packageC.class2.e", false)
                                         .withTest("packageC.class2.d", false)
                                         .withTest("packageC.class2.c", false)
                                         .endSuite()
                                         .startSuite("suite0")
                                         .withTest("packageZ.classZ.z", false)
                                         .endSuite()
                                         .finish();
  }
}
