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

package jetbrains.buildServer.server.rest.model;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.BuildProblemTypes;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.BuildFinderTestBase;
import jetbrains.buildServer.server.rest.data.change.SVcsModificationOrChangeDescriptor;
import jetbrains.buildServer.server.rest.model.change.ChangeStatus;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.StandardProperties;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRootEx;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;

@Test
public class ChangeStatusTest extends BaseFinderTest<SVcsModificationOrChangeDescriptor> {
  private MockVcsSupport myVcs;
  private SUser myUser;
  private SUser myAnotherUser;
  private ProjectEx myTestProject;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setFinder(myChangeFinder);

    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);

    myVcs = new MockVcsSupport("vcs");
    myVcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(myVcs);
    myTestProject = createProject("someproject");

    myUser = createUser("testuser");
    myUser.addRole(RoleScope.projectScope(myTestProject.getProjectId()), getTestRoles().getProjectViewerRole());

    myAnotherUser = createUser("anothertestuser");
  }

  public void testSuccessfulFinished() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    build().in(buildConf).onModifications(m20).finish();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getRunning());
    assertEquals(1, (int) status.getSuccessful());
  }

  @Test(dataProvider = "allBooleans")
  public void testSuccessfulFinishedPersonal(boolean viewPersonalBuilds) throws Throwable {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", myTestProject, "Ant");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    build().in(buildConf).onModifications(m20).personalForUser(myAnotherUser.getUsername()).finish();

    if(viewPersonalBuilds) {
      myUser.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "true");
    } else {
      myUser.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "false");
    }

    ChangeStatus status = myFixture.getSecurityContext().runAs(myUser, () -> new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    ));

    if(viewPersonalBuilds) {
      assertEquals(1, (int)status.getFinished());
      assertEquals(0, (int)status.getRunning());
      assertEquals(1, (int)status.getSuccessful());
    } else {
      assertEquals(0, (int)status.getFinished());
      assertEquals(0, (int)status.getRunning());
      assertEquals(0, (int)status.getSuccessful());
    }
  }

  public void testSuccessfulRunning() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    RunningBuildEx runningBuildEx = build().in(buildConf).onModifications(m20).run();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    myFixture.finishBuild(runningBuildEx, false);

    assertEquals(0, (int) status.getFinished());
    assertEquals(1, (int) status.getRunning());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getRunningSuccessfuly());
  }

  @Test(dataProvider = "allBooleans")
  public void testSuccessfulRunningPersonal(boolean viewPersonalBuilds) throws Throwable {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", myTestProject, "Ant");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    RunningBuildEx runningBuildEx = build().in(buildConf).personalForUser(myAnotherUser.getUsername()).onModifications(m20).run();

    if(viewPersonalBuilds) {
      myUser.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "true");
    } else {
      myUser.setUserProperty(StandardProperties.SHOW_ALL_PERSONAL_BUILDS, "false");
    }

    jetbrains.buildServer.vcs.ChangeStatus mergedStatus = myFixture.getChangeStatusProvider().getMergedChangeStatus(m20);
    ChangeStatus status = myFixture.getSecurityContext().runAs(myUser, () -> new ChangeStatus(
      mergedStatus,
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    ));

    myFixture.finishBuild(runningBuildEx, false);

    if(viewPersonalBuilds) {
      assertEquals(0, (int) status.getFinished());
      assertEquals(1, (int) status.getRunning());
      assertEquals(0, (int) status.getSuccessful());
      assertEquals(1, (int) status.getRunningSuccessfuly());
    } else {
      assertEquals(0, (int) status.getFinished());
      assertEquals(0, (int) status.getRunning());
      assertEquals(0, (int) status.getSuccessful());
      assertEquals(0, (int) status.getRunningSuccessfuly());
    }
  }

  public void testFailedFinished() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    RunningBuildEx runningBuildEx = build().in(buildConf).onModifications(m20).run();
    myFixture.finishBuild(runningBuildEx, true);

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );


    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getFailed());
    assertEquals(0, (int) status.getRunning());
  }

  public void testFailedRunning() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    RunningBuildEx runningBuildEx = build().in(buildConf).onModifications(m20).run();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    myFixture.finishBuild(runningBuildEx, true);

    assertEquals(0, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(0, (int) status.getFailed());
    assertEquals(1, (int) status.getRunning());
  }

  public void testQueued() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    SQueuedBuild queuedBuild = build().in(buildConf).onModifications(m20).addToQueue();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    queuedBuild.removeFromQueue(myUser, "comment");

    assertEquals(0, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(0, (int) status.getFailed());
    assertEquals(0, (int) status.getPendingBuildTypes());
    assertEquals(1, (int) status.getQueuedBuildsCount());
  }

  public void testCompilationErrorBuilds() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    SFinishedBuild finishedBuild = build().in(buildConf).onModifications(m20)
                                        .withProblem(BuildProblemData.createBuildProblem("problem", BuildProblemTypes.TC_COMPILATION_ERROR_TYPE, "can't compile"))
                                        .finish();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getFailed());
    assertEquals(1, (int) status.getCompilationErrorBuilds().count);
    assertEquals(finishedBuild.getBuildId(), (long) status.getCompilationErrorBuilds().builds.get(0).getId());
  }

  public void testCriticalErrorBuilds() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    SFinishedBuild finishedBuild = build().in(buildConf).onModifications(m20)
                                          .withProblem(BuildProblemData.createBuildProblem("problem", BuildProblemTypes.TC_JVM_CRASH_TYPE, "wow! such critical"))
                                          .finish();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getFailed());
    assertEquals(1, (int) status.getCriticalBuilds().count);
    assertEquals(finishedBuild.getBuildId(), (long) status.getCriticalBuilds().builds.get(0).getId());
  }

  public void testNonCriticalErrorBuilds() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    SFinishedBuild finishedBuild = build().in(buildConf).onModifications(m20)
                                          .withProblem(BuildProblemData.createBuildProblem("problem", BuildProblemTypes.TC_JVM_CRASH_TYPE, "wow! such critical"))
                                          .finish();

    buildConf.setResponsible(myUser, null, null);

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getFailed());
    assertEquals(0, (int) status.getCriticalBuilds().count);
    assertEquals(1, (int) status.getNonCriticalBuilds().count);
    assertEquals(finishedBuild.getBuildId(), (long) status.getNonCriticalBuilds().builds.get(0).getId());
  }

  public void testNewTestsFailedBuilds() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    SFinishedBuild finishedBuild = build().in(buildConf).onModifications(m20)
                                          .withFailedTests("yay")
                                          .finish();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getFailed());
    assertEquals(0, (int) status.getCriticalBuilds().count);
    assertEquals(0, (int) status.getNonCriticalBuilds().count);
    assertEquals(1, (int) status.getNewTestsFailedBuilds().count);
    assertEquals(finishedBuild.getBuildId(), (long) status.getNewTestsFailedBuilds().builds.get(0).getId());
  }

  public void testNewFailedTests() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    build().in(buildConf).onModifications(m20).withFailedTests("failedTestName", "secondFailedTesName").finish();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getFailed());
    assertEquals(2, (int) status.getNewFailedTests());
    assertEquals(1, (int) status.getNewTestsFailedBuilds().count);
  }

  public void testNoNewFailedTests() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));
    SVcsModification m30 = myFixture.addModification(modification().in(root1).version("30").parentVersions("20"));

    build().in(buildConf).onModifications(m20).withFailedTests("failedTestName").finish();
    build().in(buildConf).onModifications(m30).withFailedTests("failedTestName").finish(); // same test failed

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m30),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getFailed());
    assertEquals(0, (int) status.getNewFailedTests());
    assertEquals(1, (int) status.getOtherFailedTests());
    assertEquals(0, (int) status.getNewTestsFailedBuilds().count);
  }

  public void testSomeNewFailedTests() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));
    SVcsModification m30 = myFixture.addModification(modification().in(root1).version("30").parentVersions("20"));

    build().in(buildConf).onModifications(m20).withFailedTests("failedTestName").finish();
    build().in(buildConf).onModifications(m30).withFailedTests("failedTestName", "newFailedTestName").finish(); // same test failed

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m30),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(1, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(1, (int) status.getFailed());
    assertEquals(1, (int) status.getNewFailedTests());
    assertEquals(1, (int) status.getOtherFailedTests());
    assertEquals(1, (int) status.getNewTestsFailedBuilds().count);
  }

  public void testPendingConfiguration() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));
    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(0, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(0, (int) status.getFailed());
    assertEquals(1, (int) status.getPendingBuildTypes());
    assertEquals(0, (int) status.getQueuedBuildsCount());
  }

  @Test(enabled = false, description = "Cancelled builds are not counted at all, as MergedChangeStatus does not contain them for some reason.")
  public void testCancelled() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    VcsRootInstance root1 = prepareSingleVscRoot(buildConf);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));
    SUser user = createUser("testuser");
    RunningBuildEx runningBuildEx = build().in(buildConf).onModifications(m20).withFailedTests("failedTestName").run();
    runningBuildEx.stop(user, "Stopped manually");
    finishBuild(runningBuildEx, false);

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m20),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(0, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(0, (int) status.getFailed());
    assertEquals(1, (int) status.getCancelled());
    assertEquals(1, (int) status.getPendingBuildTypes());
  }

  private VcsRootInstance prepareSingleVscRoot(@NotNull BuildTypeImpl buildConf) {
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(myVcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;

    setBranchSpec(root1,"+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    myVcs.setCollectChangesPolicy(changesPolicy);

    return root1;
  }

  @org.testng.annotations.DataProvider(name = "allBooleans")
  public static Object[] allBooleans() {
    return new Object[] {true, false};
  }
}
