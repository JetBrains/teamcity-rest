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

import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.data.problem.TestOccurrenceFinder;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.CurrentProblemsManager;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.TestName2IndexImpl;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManagerImpl;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 16/12/2015
 */
public class TestOccurrenceFinderTest extends BaseFinderTest<STestRun> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myProject.remove();
    final PermissionChecker permissionChecker = new PermissionChecker(myServer.getSecurityContext());
    myFixture.addService(permissionChecker);


    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager, permissionChecker, myServer);
    final UserFinder userFinder = new UserFinder(myFixture);
    final TestName2IndexImpl testName2Index = myFixture.getSingletonService(TestName2IndexImpl.class);
    final ProblemMutingService problemMutingService = myFixture.getSingletonService(ProblemMutingService.class);
    final TestFinder testFinder = new TestFinder(projectFinder, myFixture.getTestManager(), testName2Index, myFixture.getCurrentProblemsManager(), problemMutingService);
    myFixture.addService(testFinder);

    final AgentFinder agentFinder = new AgentFinder(myAgentManager, myFixture);
    final BuildTypeFinder buildTypeFinder = new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, permissionChecker, myServer);
    final VcsRootFinder vcsRootFinder = new VcsRootFinder(myFixture.getVcsManager(), projectFinder, buildTypeFinder, myProjectManager,
                                                          myFixture.getSingletonService(VcsRootIdentifiersManagerImpl.class), permissionChecker);
    final BuildPromotionFinder buildPromotionFinder = new BuildPromotionFinder(myFixture.getBuildPromotionManager(), myFixture.getBuildQueue(), myServer, vcsRootFinder,
                                                                               projectFinder, buildTypeFinder, userFinder, agentFinder);

    final BuildFinder buildFinder = new BuildFinder(myServer, buildTypeFinder, projectFinder, userFinder, buildPromotionFinder, agentFinder);
    setFinder(new TestOccurrenceFinder(testFinder, buildFinder, buildTypeFinder, projectFinder, myServer.getHistory(), myServer.getSingletonService(CurrentProblemsManager.class)));
  }

  @Test
  public void testBasic() throws Exception {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType).withTest("aaa", true).finish();
//todo: why error?    final SFinishedBuild build10 = build().in(myBuildType).withTest("aaa", true).finish();

    checkExceptionOnItemSearch(BadRequestException.class, "No_match");
    checkExceptionOnItemsSearch(BadRequestException.class, "No_match");
    check("build:(id:" + build10.getBuildId() + ")", build10.getFullStatistics().getAllTests().get(0));
  }
}
