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

import java.util.Date;
import jetbrains.BuildServerCreator;
import jetbrains.buildServer.responsibility.BuildProblemResponsibilityEntry;
import jetbrains.buildServer.responsibility.BuildTypeResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntryEx;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.PathTransformer;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.data.problem.TestFinder;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.Investigation;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.TestName2IndexImpl;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.problems.BuildProblemInfoImpl;
import jetbrains.buildServer.serverSide.impl.projects.ProjectManagerImpl;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 11.11.13
 */
@Test
public class InvestigationFinderTest extends BaseServerTestCase {
  public static final String FAIL_TEST2_NAME = "fail.test2";
  public static final String PROBLEM_IDENTITY = "myUniqueProblem";
  private InvestigationFinder myInvestigationFinder;
  private ProjectManagerImpl myProjectManager;
  private BuildTypeEx myBuildType;
  private SUser myUser;
  private TestName2IndexImpl myTestName2Index;
  private PermissionChecker myPermissionChecker;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myProjectManager = myFixture.getProjectManager();
    myPermissionChecker = new PermissionChecker(myServer.getSecurityContext());
    myFixture.addService(myPermissionChecker);
    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager, myPermissionChecker, myServer);
    final UserFinder userFinder = new UserFinder(myFixture);
    myTestName2Index = myFixture.getSingletonService(TestName2IndexImpl.class);
    final TestFinder testFinder = new TestFinder(projectFinder, myFixture.getTestManager(), myTestName2Index,
                                                 myFixture.getCurrentProblemsManager(), myFixture.getSingletonService(ProblemMutingService.class));
    myFixture.addService(testFinder);
    myFixture.addService(new PermissionChecker(myServer.getSecurityContext()));
    myInvestigationFinder = new InvestigationFinder(projectFinder, null, null, testFinder, userFinder, myFixture.getResponsibilityFacadeEx(), myFixture.getResponsibilityFacadeEx(),
                                                    myFixture.getResponsibilityFacadeEx());
  }

  @Test
  public void testBuildTypeInvestigation() throws Exception {
    createFailingBuild();
    myFixture.getResponsibilityFacadeEx().setBuildTypeResponsibility(myBuildType, createRespEntry(ResponsibilityEntry.State.TAKEN, myUser));


    final PagedSearchResult<InvestigationWrapper> result = myInvestigationFinder.getItems((String)null);
    assertEquals(1, result.myEntries.size());
    final InvestigationWrapper investigation1 = result.myEntries.get(0);
    assertEquals(true, investigation1.isBuildType());
    assertEquals(false, investigation1.isProblem());
    assertEquals(false, investigation1.isTest());
    assertEquals("anyProblem", investigation1.getType());

    final BuildTypeResponsibilityEntry buildTypeRE = investigation1.getBuildTypeRE();
    assertEquals(true, buildTypeRE != null);

    assertEquals(myUser, investigation1.getResponsibleUser());
    assertEquals(ResponsibilityEntry.State.TAKEN, investigation1.getState());
  }

  @Test
  public void testBuildTypeInvestigationModel() throws Exception {
    createFailingBuild();
    myFixture.getResponsibilityFacadeEx().setBuildTypeResponsibility(myBuildType, createRespEntry(ResponsibilityEntry.State.TAKEN, myUser));

    final PagedSearchResult<InvestigationWrapper> ivestigationWrappers = myInvestigationFinder.getItems((String)null);
    final ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(new PathTransformer() {
      public String transform(final String path) {
        return path;
      }
    });
    final BeanFactory beanFactory = new BeanFactory(null);

    final Investigations investigations = new Investigations(ivestigationWrappers.myEntries, null, Fields.ALL_NESTED, new BeanContext(beanFactory, myServer, apiUrlBuilder));

    assertEquals(1, investigations.count.longValue());
    final Investigation investigation = investigations.items.get(0);
    assertEquals("buildType:(id:" + myBuildType.getExternalId() + ")", investigation.id);
    assertEquals("TAKEN", investigation.state);
    assertEquals((Long)myUser.getId(), investigation.assignee.getId());
    assertEquals("The comment", investigation.assignment.text);
    assertEquals(Boolean.TRUE, investigation.target.anyProblem);
    assertEquals(null, investigation.target.problems);
    assertEquals(null, investigation.target.tests);
    assertEquals(myBuildType.getExternalId(), investigation.scope.buildTypes.buildTypes.get(0).getId());
    assertEquals(null, investigation.scope.project);
  }

  @Test
  public void testTestInvestigationModel() throws Exception {
    createFailingBuild();

    final TestName testName = new TestName(FAIL_TEST2_NAME);
    myFixture.getResponsibilityFacadeEx().setTestNameResponsibility(testName, myProject.getProjectId(),
                                                                    createRespEntry(ResponsibilityEntry.State.TAKEN, myUser));

    final PagedSearchResult<InvestigationWrapper> ivestigationWrappers = myInvestigationFinder.getItems((String)null);
    ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(new PathTransformer() {
      public String transform(final String path) {
        return path;
      }
    });
    final BeanFactory beanFactory = new BeanFactory(null);
    registerBuildTypeFinder();

    final Investigations investigations = new Investigations(ivestigationWrappers.myEntries, null, Fields.LONG, new BeanContext(beanFactory, myServer, apiUrlBuilder));

    assertEquals(1, investigations.count.longValue());
    final Investigation investigation = investigations.items.get(0);
    assertEquals("test:(id:" + myTestName2Index.findTestNameId(testName) + "),assignmentProject:(id:" + myProject.getExternalId() + ")", investigation.id);
    assertEquals("TAKEN", investigation.state);
    assertEquals((Long)myUser.getId(), investigation.assignee.getId());
    assertEquals("The comment", investigation.assignment.text);
    assertEquals(null, investigation.target.anyProblem);
    assertEquals(null, investigation.target.problems);

    assertEquals(FAIL_TEST2_NAME, investigation.target.tests.items.get(0).name);
    assertEquals(null, investigation.scope.buildTypes);
    assertEquals(myProject.getExternalId(), investigation.scope.project.id);
  }

  @Test
  public void testProblemInvestigationModel() throws Exception {
    createFailingBuild();

    final BuildProblemInfoImpl buildProblem = new BuildProblemInfoImpl(myProject.getProjectId(), getProblemId(PROBLEM_IDENTITY), null);
    myFixture.getResponsibilityFacadeEx().setBuildProblemResponsibility(buildProblem, myProject.getProjectId(), createRespEntry(ResponsibilityEntry.State.TAKEN, myUser));

    final BuildProblemResponsibilityEntry buildProblemResponsibility = myFixture.getResponsibilityFacadeEx().findBuildProblemResponsibility(buildProblem, myProject.getProjectId());
    assert buildProblemResponsibility != null;

    final PagedSearchResult<InvestigationWrapper> ivestigationWrappers = myInvestigationFinder.getItems((String)null);
    ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(new PathTransformer() {
      public String transform(final String path) {
        return path;
      }
    });
    final BeanFactory beanFactory = new BeanFactory(null);
    registerBuildTypeFinder();

    final Investigations investigations = new Investigations(ivestigationWrappers.myEntries, null, Fields.LONG, new BeanContext(beanFactory, myServer, apiUrlBuilder));

    assertEquals(1, investigations.count.longValue());
    final Investigation investigation = investigations.items.get(0);
    assertEquals("assignmentProject:(id:" + myProject.getExternalId() + "),problem:(id:" + buildProblemResponsibility.getBuildProblemInfo().getId() + ")", investigation.id);
    assertEquals("TAKEN", investigation.state);
    assertEquals((Long)myUser.getId(), investigation.assignee.getId());
    assertEquals("The comment", investigation.assignment.text);
    assertEquals(null, investigation.target.anyProblem);
    assertEquals(null, investigation.target.tests);

    assertEquals(PROBLEM_IDENTITY, investigation.target.problems.items.get(0).identity);
    assertEquals(myProject.getExternalId(), investigation.scope.project.id);
  }

  private void registerBuildTypeFinder() {
    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager, myPermissionChecker, myServer);
    final AgentFinder agentFinder = new AgentFinder(myAgentManager, myFixture);
    myFixture.addService(new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, myPermissionChecker, myServer));
  }

  @Override
  protected ResponsibilityEntryEx createRespEntry(ResponsibilityEntry.State state, SUser user) {
    return new ResponsibilityEntryEx(state, user, user, new Date(), "The comment", ResponsibilityEntry.RemoveMethod.WHEN_FIXED);
  }

  private void createFailingBuild() {
    final ProjectEx rootProject = myProjectManager.getRootProject();
    final ProjectEx project1 = rootProject.createProject("project1", "Project name");
    myBuildType = project1.createBuildType("extId", "bt name");

    startBuild(myBuildType);
    runTestsInRunningBuild(new String[]{"pass.test1"}, new String[]{FAIL_TEST2_NAME}, new String[0]);
    BuildServerCreator.doBuildProblem(getRunningBuild(), PROBLEM_IDENTITY);
    final SFinishedBuild build =  finishBuild(true);

    myUser = createUser("user");
  }
}
