/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.model.Comment;
import jetbrains.buildServer.server.rest.model.buildType.Investigation;
import jetbrains.buildServer.server.rest.model.buildType.ProblemScope;
import jetbrains.buildServer.server.rest.model.buildType.ProblemTarget;
import jetbrains.buildServer.server.rest.model.problem.Resolution;
import jetbrains.buildServer.server.rest.model.problem.Tests;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.user.User;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 * Date: 31/07/2017
 */
public class InvestigationRequestTest extends BaseFinderTest<InvestigationWrapper> {
  private InvestigationRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = new InvestigationRequest();
    myRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));
  }

  @Test
  void testAssignInvestigation() throws Throwable {
    final SUser user2 = createUser("user2");

    Investigation investigation = new Investigation();
    investigation.state = "taken";
    investigation.assignee = new User();
    investigation.assignee.setId(user2.getId());
    investigation.assignment = new Comment();
    investigation.assignment.text = "comment here";
    investigation.scope = new ProblemScope();
    investigation.scope.project = new Project();
    investigation.scope.project.id = myProject.getExternalId();
    investigation.target = new ProblemTarget();
    investigation.target.tests = new Tests();
    jetbrains.buildServer.server.rest.model.problem.Test test = new jetbrains.buildServer.server.rest.model.problem.Test();
    test.name = "testname";
    investigation.target.tests.items = Collections.singletonList(test);
    investigation.resolution = new Resolution();
    investigation.resolution.type = Resolution.ResolutionType.manually;
    investigation.resolution.time = "20900512T163700";

    assertEmpty(myInvestigationFinder.getItems(null).myEntries);

    createBuildWithFailedTest("testname");

    Investigation result = myRequest.createInstance(investigation, "$long");

    assertEquals("testname", result.target.tests.items.get(0).name);

    List<InvestigationWrapper> currentInvestigations = myInvestigationFinder.getItems(null).myEntries;
    assertEquals(1, currentInvestigations.size());

    InvestigationWrapper investigationWrapper = currentInvestigations.get(0);

    assertEquals(ResponsibilityEntry.State.TAKEN, investigationWrapper.getState());
    assertEquals(user2.getId(), investigationWrapper.getResponsibleUser().getId());
    assertEquals("comment here", investigationWrapper.getComment());
    assertEquals(null, investigationWrapper.getProblemRE());
    assertEquals( myProject.getProjectId(), investigationWrapper.getTestRE().getProjectId());
    assertEquals( "testname", investigationWrapper.getTestRE().getTestName().getAsString());
    assertEquals(myProject.getProjectId(), investigationWrapper.getAssignmentProject().getProjectId());

    myRequest.deleteInstance(investigationWrapper.getId());

    assertEmpty(myInvestigationFinder.getItems(null).myEntries);
  }
}
