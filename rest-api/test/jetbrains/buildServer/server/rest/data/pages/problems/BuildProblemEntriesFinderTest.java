package jetbrains.buildServer.server.rest.data.pages.problems;

import java.util.Collections;
import java.util.Objects;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.BuildProblemTypes;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BuildProblemEntriesFinderTest extends BaseFinderTest<BuildProblemEntry> {
  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    setFinder(new BuildProblemEntriesFinder(myFixture.getCurrentProblemsManager(), myFixture.getSingletonService(ProblemMutingService.class), myFixture));
  }

  @Test
  public void findsProblemsByAffectedProject() {
    ProjectEx topProject = createProject("top", "top");
    BuildTypeEx topBt = topProject.createBuildType("topBt");
    BuildProblemEntry topProblem = runBuildAndCreateProblem(topBt, "topProblem");

    ProjectEx leftProject = topProject.createProject("top_left", "left");
    BuildTypeEx leftBt = leftProject.createBuildType("leftBt");
    BuildProblemEntry leftProblem = runBuildAndCreateProblem(leftBt, "leftProblem");

    ProjectEx rightProject = topProject.createProject("top_right", "right");
    BuildTypeEx rightBt = rightProject.createBuildType("rightBt");
    BuildProblemEntry rightProblem = runBuildAndCreateProblem(rightBt, "rightProblem");

    ProjectEx rightDeepProject = rightProject.createProject("top_right_deep", "right_deep");
    BuildTypeEx rightDeepBt = rightDeepProject.createBuildType("rightDeepBt");
    BuildProblemEntry rightDeepProblem = runBuildAndCreateProblem(rightDeepBt, "rightDeepProblem");

    check("affectedProject:" + topProject.getExternalId(), PROBLEM_BASIC_MATCHER, topProblem, leftProblem, rightProblem, rightDeepProblem);
    check("affectedProject:" + leftProject.getExternalId(), PROBLEM_BASIC_MATCHER, leftProblem);
    check("affectedProject:" + rightProject.getExternalId(), PROBLEM_BASIC_MATCHER, rightProblem, rightDeepProblem);
  }

  @Test
  public void findsProblemsByBuildType() {
    ProjectEx topProject = createProject("top", "top");
    BuildTypeEx topBt = topProject.createBuildType("topBt");
    BuildProblemData problemData1 = BuildProblemData.createBuildProblem("problem1", BuildProblemTypes.TC_EXIT_CODE_TYPE, "problem1");
    BuildProblemData problemData2 = BuildProblemData.createBuildProblem("problem2", BuildProblemTypes.TC_USER_PROVIDED_TYPE, "problem2");
    BuildProblemData problemData3 = BuildProblemData.createBuildProblem("problem3", BuildProblemTypes.TC_COMPILATION_ERROR_TYPE, "problem3");
    SFinishedBuild build = build().in(topBt)
                                  .withProblem(problemData1)
                                  .withProblem(problemData2)
                                  .withProblem(problemData3)
                                  .finish();

    BuildProblem problem1 = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(0);
    BuildProblem problem2 = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(1);
    BuildProblem problem3 = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(2);


    ProjectEx leftProject = createProject("left", "left");
    BuildTypeEx leftBt = leftProject.createBuildType("leftBt");
    BuildProblemEntry leftProblem = runBuildAndCreateProblem(leftBt, "leftProblem");

    check("buildType:topBt", PROBLEM_BASIC_MATCHER,
          new BuildProblemEntry(problem1, null),
          new BuildProblemEntry(problem2, null),
          new BuildProblemEntry(problem3, null)
    );
    check("buildType:leftBt", PROBLEM_BASIC_MATCHER, leftProblem);
  }

  @Test
  public void findsProblemsByAssignee() {
    SUser user1 = createUser("user1");
    SUser user2 = createUser("user2");

    BuildProblemData problemData1 = BuildProblemData.createBuildProblem("problem1", BuildProblemTypes.TC_EXIT_CODE_TYPE, "problem1");
    BuildProblemData problemData2 = BuildProblemData.createBuildProblem("problem2", BuildProblemTypes.TC_EXIT_CODE_TYPE, "problem2");
    SFinishedBuild build = build().in(myBuildType)
                                  .withProblem(problemData1)
                                  .withProblem(problemData2)
                                  .finish();

    BuildProblem problem1 = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(0);
    BuildProblem problem2 = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(1);

    myFixture.getResponsibilityFacadeEx()
             .setBuildProblemResponsibility(problem1, myProject.getProjectId(), createRespEntry(ResponsibilityEntry.State.TAKEN, user1));

    myFixture.getResponsibilityFacadeEx()
             .setBuildProblemResponsibility(problem2, myProject.getProjectId(), createRespEntry(ResponsibilityEntry.State.TAKEN, user2));


    check("affectedProject:" + myProject.getExternalId() + ",assignee:user1", PROBLEM_BASIC_MATCHER, new BuildProblemEntry(problem1, null));
    check("affectedProject:" + myProject.getExternalId() + ",assignee:user2", PROBLEM_BASIC_MATCHER, new BuildProblemEntry(problem2, null));
  }

  @Test
  public void findsProblemsByInvestigated() {
    SUser user = createUser("user");

    BuildProblemData investigatedProblemData = BuildProblemData.createBuildProblem("problem1", BuildProblemTypes.TC_EXIT_CODE_TYPE, "problem1");
    BuildProblemData problemDataWithoutInvestigation = BuildProblemData.createBuildProblem("problem2", BuildProblemTypes.TC_EXIT_CODE_TYPE, "problem2");
    SFinishedBuild build = build().in(myBuildType)
                                  .withProblem(investigatedProblemData)
                                  .withProblem(problemDataWithoutInvestigation)
                                  .finish();

    BuildProblem investigatedProblem = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(0);
    BuildProblem problemWithoutInvestigation = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(1);

    myFixture.getResponsibilityFacadeEx()
             .setBuildProblemResponsibility(investigatedProblem, myProject.getProjectId(), createRespEntry(ResponsibilityEntry.State.TAKEN, user));


    check("affectedProject:" + myProject.getExternalId() + ",currentlyInvestigated:true", PROBLEM_BASIC_MATCHER, new BuildProblemEntry(investigatedProblem, null));
    check("affectedProject:" + myProject.getExternalId() + ",currentlyInvestigated:false", PROBLEM_BASIC_MATCHER, new BuildProblemEntry(problemWithoutInvestigation, null));
    check("affectedProject:" + myProject.getExternalId() + ",currentlyInvestigated:any", PROBLEM_BASIC_MATCHER,
          new BuildProblemEntry(investigatedProblem, null),
          new BuildProblemEntry(problemWithoutInvestigation, null)
    );
  }

  @Test
  public void findsProblemsByMuted() {
    SUser user = createUser("test_user");
    BuildProblemData mutedProblemData = BuildProblemData.createBuildProblem("mutedProblem", BuildProblemTypes.TC_EXIT_CODE_TYPE, "mutedProblem");
    BuildProblemData problemData = BuildProblemData.createBuildProblem("problem", BuildProblemTypes.TC_EXIT_CODE_TYPE, "problem");
    SFinishedBuild build = build().in(myBuildType)
                                  .withProblem(mutedProblemData)
                                  .withProblem(problemData)
                                  .finish();

    BuildProblem mutedProblem = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(0);
    BuildProblem problem = ((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(1);

    myFixture.getSingletonService(ProblemMutingService.class)
             .muteProblemsInProject(user, null, true, null, myProject, Collections.singletonList(mutedProblem));

    MuteInfo muteInfo = myFixture.getSingletonService(ProblemMutingService.class)
                                 .getBuildProblemsCurrentMuteInfo(myProject).values().iterator().next().getProjectsMuteInfo().get(myProject);

    check("affectedProject:" + myProject.getExternalId() + ",currentlyMuted:true", PROBLEM_BASIC_MATCHER, new BuildProblemEntry(mutedProblem, Collections.singleton(muteInfo)));
    check("affectedProject:" + myProject.getExternalId() + ",currentlyMuted:false", PROBLEM_BASIC_MATCHER, new BuildProblemEntry(problem, null));
    check("affectedProject:" + myProject.getExternalId() + ",currentlyMuted:any", PROBLEM_BASIC_MATCHER,
          new BuildProblemEntry(mutedProblem, Collections.singletonList(muteInfo)),
          new BuildProblemEntry(problem, null))
    ;
  }


  @NotNull
  private BuildProblemEntry runBuildAndCreateProblem(@NotNull BuildTypeEx bt, @NotNull String problemText) {
    BuildProblemData problem = BuildProblemData.createBuildProblem(problemText, BuildProblemTypes.TC_EXIT_CODE_TYPE, problemText);
    SFinishedBuild build = build().in(bt).withProblem(problem).finish();

    return new BuildProblemEntry(((BuildPromotionEx) build.getBuildPromotion()).getBuildProblems().get(0), null);
  }

  private static final Matcher<BuildProblemEntry, BuildProblemEntry> PROBLEM_BASIC_MATCHER = (l, r) -> {
    return Objects.equals(l.getBuildPromotion().getId(), r.getBuildPromotion().getId()) &&
           Objects.equals(l.getProblem().getBuildProblemData().getDescription(), r.getProblem().getBuildProblemData().getDescription()) &&
           Objects.equals(l.getProblem().getBuildProblemData().getType(), r.getProblem().getBuildProblemData().getType()) &&
           Objects.equals(l.getProblem().getBuildProblemData().getIdentity(), r.getProblem().getBuildProblemData().getIdentity()) &&
           ((l.getMuteInfos() == null && r.getMuteInfos() == null) || Objects.equals(l.getMuteInfos().size(), r.getMuteInfos().size()));
  };
}
