/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.pages.problems;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.Locator;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.server.rest.data.pages.problems.TestFailuresProblemEntriesLocatorDefinition.*;

@Test
public class TestFailuresProblemEntriesCollectorTest extends BaseFinderTest {
  private SUser myUser;
  private TestFailuresProblemEntriesCollector myCollector;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myUser = createUser("test_user");
    myCollector = new TestFailuresProblemEntriesCollector(
      myTestOccurrenceFinder,
      myProjectFinder,
      myUserFinder,
      myFixture.getResponsibilityFacadeEx(),
      myFixture.getSingletonService(ProblemMutingService.class),
      myFixture.getTestManager()
    );
  }

  public void basicTest() {
    startBuild(myBuildType);
    testFailed("failed.basic");
    testFailed("failed.investigated");
    finishBuild(true);

    investigate("failed.investigated", myProject, ResponsibilityEntry.State.TAKEN);
    investigate("failed.non_current", myProject, ResponsibilityEntry.State.TAKEN);

    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test without investigation, one test with investigation and one investigation without test must be included", 3, result.size());
  }

  public void onlyInvestigated() {
    startBuild(myBuildType);
    testFailed("failed.basic");
    testFailed("failed.investigated");
    finishBuild(true);

    investigate("failed.investigated", myProject, ResponsibilityEntry.State.TAKEN);
    investigate("failed.non_current", myProject, ResponsibilityEntry.State.TAKEN);

    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    locator.setDimension(CURRENTLY_INVESTIGATED, "true");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test with investigation and one investigation without test must be included", 2, result.size());
  }

  public void onlyNotInvestigated() {
    startBuild(myBuildType);
    testFailed("failed.basic");
    testFailed("failed.investigated");
    finishBuild(true);

    investigate("failed.investigated", myProject, ResponsibilityEntry.State.TAKEN);
    investigate("failed.non_current", myProject, ResponsibilityEntry.State.TAKEN);

    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    locator.setDimension(CURRENTLY_INVESTIGATED, "false");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test without investigation must be included", 1, result.size());
  }

  public void onlyNotInvestigatedEvenWhenMuted() {
    startBuild(myBuildType);
    runTestsInRunningBuild(
      new String[0],
      new String[]{
        "failed.basic",
        "failed.investigated_and_muted",
      },
      new String[0]
    );
    finishBuild(true);

    investigate("failed.investigated_and_muted", myProject, ResponsibilityEntry.State.TAKEN);

    final STest mutedTest = myFixture.getTestManager().createTest(new TestName("failed.investigated_and_muted"), myProject.getProjectId());
    mute(mutedTest, myProject, "");


    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    locator.setDimension(CURRENTLY_INVESTIGATED, "false");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test without investigation must be included", 1, result.size());
  }

  public void onlyMuted() {
    startBuild(myBuildType);
    testFailed("failed.basic");
    testFailed("failed.muted");
    finishBuild(true);

    investigate("failed.non_current", myProject, ResponsibilityEntry.State.TAKEN);

    final STest mutedTest = myFixture.getTestManager().createTest(new TestName("failed.muted"), myProject.getProjectId());
    mute(mutedTest, myProject, "");


    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    locator.setDimension(CURRENTLY_MUTED, "true");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One muted test must be included", 1, result.size());
  }

  public void onlyNotMuted() {
    startBuild(myBuildType);
    testFailed("failed.basic");
    testFailed("failed.muted");
    finishBuild(true);

    investigate("failed.non_current", myProject, ResponsibilityEntry.State.TAKEN);

    final STest mutedTest = myFixture.getTestManager().createTest(new TestName("failed.muted"), myProject.getProjectId());
    mute(mutedTest, myProject, "");


    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    locator.setDimension(CURRENTLY_MUTED, "false");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("Two non-muted test must be included", 2, result.size());
  }

  public void onlyNotMutedEvenWhenInvestigated() {
    startBuild(myBuildType);
    testFailed("failed.basic");
    testFailed("failed.investigated_and_muted");
    finishBuild(true);

    investigate("failed.investigated_and_muted", myProject, ResponsibilityEntry.State.TAKEN);

    final STest mutedTest = myFixture.getTestManager().createTest(new TestName("failed.investigated_and_muted"), myProject.getProjectId());
    mute(mutedTest, myProject, "");


    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    locator.setDimension(CURRENTLY_MUTED, "false");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test without investigation must be included", 1, result.size());
  }

  public void notMutedNotInvestigated() {
    startBuild(myBuildType);
    testFailed("failed.basic");
    testFailed("failed.muted");
    finishBuild(true);

    investigate("failed.non_current", myProject, ResponsibilityEntry.State.TAKEN);

    final STest mutedTest = myFixture.getTestManager().createTest(new TestName("failed.muted"), myProject.getProjectId());
    mute(mutedTest, myProject, "");


    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    locator.setDimension(CURRENTLY_MUTED, "false");
    locator.setDimension(CURRENTLY_INVESTIGATED, "false");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test must be included", 1, result.size());
  }

  public void onlyActivelyInvestigated() {
    startBuild(myBuildType);
    testFailed("failed.investigated.fixed");
    testFailed("failed.investigated.given_up");
    testFailed("failed.investigated.taken");
    finishBuild(true);

    investigate("failed.investigated.fixed", myProject, ResponsibilityEntry.State.FIXED);
    investigate("failed.investigated.given_up", myProject, ResponsibilityEntry.State.GIVEN_UP);
    investigate("failed.investigated.taken", myProject, ResponsibilityEntry.State.TAKEN);


    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, myProject.getExternalId());
    locator.setDimension(CURRENTLY_INVESTIGATED, "true");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test with taken investigation must be included", 1, result.size());
    assertEquals(ResponsibilityEntry.State.TAKEN, result.get(0).getInvestigations().get(0).getState());
  }

  public void shouldReturnInvestigationsForParentProjectsAndSubprojects() {
    ProjectEx parentProject = createProject("Parent");
    ProjectEx middleProject = parentProject.createProject("Parent_Middle", "Middle");
    ProjectEx siblingProject = parentProject.createProject("Parent_Sibling", "Sibling");

    ProjectEx childProject = middleProject.createProject("Parent_Middle_Child", "Child");
    BuildTypeEx bt = childProject.createBuildType("my_bt");

    startBuild(bt);
    testFailed("failed.investigated");
    finishBuild(true);

    investigate("failed.investigated", parentProject, ResponsibilityEntry.State.TAKEN);
    investigate("failed.investigated", childProject, ResponsibilityEntry.State.TAKEN);
    investigate("failed.investigated", siblingProject, ResponsibilityEntry.State.TAKEN);

    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, middleProject.getExternalId());
    locator.setDimension(CURRENTLY_INVESTIGATED, "true");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test is investigated", 1, result.size());

    List<InvestigationWrapper> investigations = result.get(0).getInvestigations();
    assertEquals("Investigation in parent and child projects must be included", 2, investigations.size());

    List<String> investigatedProjects = investigations.stream().map(iw -> iw.getTestRE().getProjectId()).collect(Collectors.toList());
    assertContains(investigatedProjects, childProject.getProjectId(), parentProject.getProjectId());
  }

  public void shouldNotReturnUnrelatedInvestigationsFromParentProjects() {
    ProjectEx parentProject = createProject("Parent");
    ProjectEx childProject = parentProject.createProject("Parent_Child", "Child");
    BuildTypeEx bt = childProject.createBuildType("my_bt");

    startBuild(bt);
    testFailed("failed.investigated");
    finishBuild(true);

    investigate("failed.investigated", childProject, ResponsibilityEntry.State.TAKEN);
    investigate("unrelated", parentProject, ResponsibilityEntry.State.TAKEN);

    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, childProject.getExternalId());
    locator.setDimension(CURRENTLY_INVESTIGATED, "true");
    locator.setDimension(CURRENTLY_FAILING, "true");

    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();
    assertEquals("Only one test is investigated in the child project", 1, result.size());

    List<InvestigationWrapper> investigations = result.get(0).getInvestigations();
    assertEquals("Only investigation in the child project must be included", 1, investigations.size());
    assertEquals("failed.investigated", investigations.get(0).getTestRE().getTestName().getAsString());
  }

  public void shouldReturnMutesForParentProjectsAndSubprojects() {
    ProjectEx parentProject = createProject("Parent");
    ProjectEx middleProject = parentProject.createProject("Parent_Middle", "Middle");
    ProjectEx siblingProject = parentProject.createProject("Parent_Sibling", "Sibling");

    ProjectEx childProject = middleProject.createProject("Parent_Middle_Child", "Child");
    BuildTypeEx bt = childProject.createBuildType("my_bt");

    startBuild(bt);
    testFailed("failed.muted");
    final STest mutedTest = myFixture.getTestManager().createTest(new TestName("failed.muted"), myProject.getProjectId());


    finishBuild(true);

    mute(mutedTest, parentProject, "Muted in the parent project, should be visible.");
    mute(mutedTest, siblingProject, "Muted in the sibling project, should NOT be visible.");
    mute(mutedTest, childProject, "Muted in the child project, should be visible.");


    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, middleProject.getExternalId());
    locator.setDimension(CURRENTLY_MUTED, "true");
    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();

    assertEquals("One test is muted", 1, result.size());

    List<SingleTestMuteInfoView> mutes = result.get(0).getMutes();
    assertEquals("Investigation in parent and child projects must be included", 2, mutes.size());

    List<String> mutedProjects = mutes.stream().map(mi -> mi.getMuteInfo().getProject().getProjectId()).collect(Collectors.toList());
    assertContains(mutedProjects, childProject.getProjectId(), parentProject.getProjectId());
  }

  public void shouldNotReturnUnrelatedMutesFromParentProjects() {
    ProjectEx parentProject = createProject("Parent");
    ProjectEx childProject = parentProject.createProject("Parent_Child", "Child");
    BuildTypeEx bt = childProject.createBuildType("my_bt");

    startBuild(bt);
    testFailed("failed.muted");
    finishBuild(true);

    final STest unrelatedTest = myFixture.getTestManager().createTest(new TestName("unrelated"), parentProject.getProjectId());
    final STest mutedTest = myFixture.getTestManager().createTest(new TestName("failed.muted"), childProject.getProjectId());

    mute(unrelatedTest, parentProject, "Unrelated mute in the parent project, should NOT be visible.");
    mute(mutedTest, childProject, "Muted in the child project, should be visible.");


    Locator locator = Locator.createEmptyLocator();
    locator.setDimension(AFFECTED_PROJECT, childProject.getExternalId());
    locator.setDimension(CURRENTLY_MUTED, "true");
    locator.setDimension(CURRENTLY_FAILING, "true");

    List<TestFailuresProblemEntry> result = myCollector.getItems(locator).getEntries();
    assertEquals("Only one test is muted in the child project", 1, result.size());

    List<SingleTestMuteInfoView> mutes = result.get(0).getMutes();
    assertEquals("Only mute in the child project must be included", 1, mutes.size());
    assertEquals(mutedTest.getTestNameId(), mutes.get(0).getTestNameId());
  }

  private void investigate(String investigatedTest, ProjectEx project, ResponsibilityEntry.State state) {
    TestName investigatedTestName = new TestName(investigatedTest);
    myFixture.getResponsibilityFacadeEx().setTestNameResponsibility(investigatedTestName, project.getProjectId(), createRespEntry(state, myUser));
  }

  private void mute(STest test, ProjectEx project, String note) {
    myFixture.getSingletonService(ProblemMutingService.class)
             .muteTestsInProject(myUser, note, false, null, project, Collections.singleton(test));
  }
}
