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

import java.util.*;
import jetbrains.BuildServerCreator;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.BuildProblemTypes;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildFinderTestBase;
import jetbrains.buildServer.server.rest.model.change.ChangeStatus;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.serverSide.impl.*;
import jetbrains.buildServer.serverSide.impl.projects.ProjectImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.StandardProperties;
import jetbrains.buildServer.util.Option;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;

@Test
public class ChangeStatusTest extends BaseServerTestCase {
  private MockVcsSupport myVcs;
  private SUser myUser;
  private SUser myAnotherUser;
  private ProjectEx myTestProject;
  private BuildFinderTestBase.MockCollectRepositoryChangesPolicy myChangesPolicy;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);

    myVcs = new MockVcsSupport("vcs");
    myVcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(myVcs);
    myChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    myVcs.setCollectChangesPolicy(myChangesPolicy);

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

  public void testCompositeWithUnrelatedFailure() {
    ProjectEx project = createProject("testProject", "testProject");
    final BuildTypeEx targetBt = project.createBuildType("targetBt");
    final BuildTypeEx unrelatedBt = project.createBuildType("unrelated");
    final BuildTypeEx compositeBt = project.createBuildType("composite");
    BuildServerCreator.makeComposite(compositeBt);

    List<VcsRootInstance> roots = prepareMultipleVscRoots(targetBt, compositeBt);

    addDependency(compositeBt, unrelatedBt);
    addDependency(compositeBt, targetBt);

    Date now = new Date();
    SVcsModification m0 = myFixture.addModification(modification().in(roots.get(0)).by("user").at(now).version("20"));
    SVcsModification m1 = myFixture.addModification(modification().in(roots.get(1)).by("user").at(now).version("20"));

    myChangesPolicy.setCurrentState(roots.get(0), RepositoryStateData.createVersionState("master", Util.map("master", "20")));
    myChangesPolicy.setCurrentState(roots.get(1), RepositoryStateData.createVersionState("master", Util.map("master", "20")));
    myFixture.getVcsModificationChecker().checkForModifications(targetBt.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    myFixture.getVcsModificationChecker().checkForModifications(compositeBt.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    SFinishedBuild unrelatedBuild = build().in(unrelatedBt).withFailedTests("Unrealated.failedTest").finish();
    SFinishedBuild targetBuild = build().in(targetBt).withFailedTests("Target.failed").onModifications(m0).finish();
    SFinishedBuild headBuild = build().in(compositeBt).snapshotDepends(unrelatedBuild.getBuildPromotion(), targetBuild.getBuildPromotion()).onModifications(m1).finish();
    ((BuildPromotionImpl) headBuild.getBuildPromotion()).setAttribute("teamcity.internal.composite", "true");

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(m0),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    // Failure of unrelated build is not related to this change, so shouldn't be counted anywhere.
    assertEquals(2, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(2, (int) status.getFailed());
    assertEquals(0, (int) status.getPendingBuildTypes());
    assertEquals(1, (int) status.getNewFailedTests());
    assertEquals(0, (int) status.getOtherFailedTests());
  }

  public void testTwoIdenticalComposites() {
    ProjectEx project = createProject("testProject", "testProject");
    final BuildTypeEx dep1Bt = project.createBuildType("dep1Bt");
    final BuildTypeEx composite1Bt = project.createBuildType("composite1");

    final BuildTypeEx dep2Bt = project.createBuildType("dep2Bt");
    final BuildTypeEx composite2Bt = project.createBuildType("composite2");

    BuildServerCreator.makeComposite(composite1Bt);
    BuildServerCreator.makeComposite(composite2Bt);

    List<VcsRootInstance> roots = prepareMultipleVscRoots(dep1Bt, composite1Bt, dep2Bt, composite2Bt);

    addDependency(composite1Bt, dep1Bt);
    addDependency(composite2Bt, dep2Bt);

    Date now = new Date();
    SVcsModification mDep1 = myFixture.addModification(modification().in(roots.get(0)).by("user").at(now).version("20"));
    SVcsModification mComposite1 = myFixture.addModification(modification().in(roots.get(1)).by("user").at(now).version("20"));
    SVcsModification mDep2 = myFixture.addModification(modification().in(roots.get(2)).by("user").at(now).version("20"));
    SVcsModification mComposite2 = myFixture.addModification(modification().in(roots.get(3)).by("user").at(now).version("20"));

    myChangesPolicy.setCurrentState(roots.get(0), RepositoryStateData.createVersionState("master", Util.map("master", "20")));
    myChangesPolicy.setCurrentState(roots.get(1), RepositoryStateData.createVersionState("master", Util.map("master", "20")));
    myChangesPolicy.setCurrentState(roots.get(2), RepositoryStateData.createVersionState("master", Util.map("master", "20")));
    myChangesPolicy.setCurrentState(roots.get(3), RepositoryStateData.createVersionState("master", Util.map("master", "20")));
    myFixture.getVcsModificationChecker().checkForModifications(dep1Bt.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    myFixture.getVcsModificationChecker().checkForModifications(composite1Bt.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    myFixture.getVcsModificationChecker().checkForModifications(dep2Bt.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    myFixture.getVcsModificationChecker().checkForModifications(composite2Bt.getVcsRootInstances(), OperationRequestor.UNKNOWN);


    composite1Bt.addToQueue("test");
    RunningBuildEx composite1Build = null;
    RunningBuildEx dep1Build = null;
    for(RunningBuildEx b : myFixture.flushQueueAndWaitN(2)) {
      if(b.getBuildPromotion().isCompositeBuild()) {
        composite1Build = b;
      } else {
        dep1Build = b;
      }
    }
    assertNotNull("Test setup failure, unable to find dependent build.", dep1Build);

    myFixture.doTestFailed(dep1Build, "Split.failed1");
    myFixture.doTestFailed(dep1Build, "Split.failed2");

    myFixture.finishBuild(dep1Build, true);
    myFixture.finishBuild(composite1Build, true);


    composite2Bt.addToQueue("test");
    RunningBuildEx composite2Build = null;
    RunningBuildEx dep2Build = null;
    for(RunningBuildEx b : myFixture.flushQueueAndWaitN(2)) {
      if(b.getBuildPromotion().isCompositeBuild()) {
        composite2Build = b;
      } else {
        dep2Build = b;
      }
    }
    assertNotNull("Test setup failure, unable to find dependent build.", dep2Build);

    myFixture.doTestFailed(dep2Build, "Split.failed1");
    myFixture.doTestFailed(dep2Build, "Split.failed2");

    myFixture.finishBuild(dep2Build, true);
    myFixture.finishBuild(composite2Build, true);


    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(mComposite2),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    // Failure of unrelated build is not related to this change, so shouldn't be counted anywhere.
    assertEquals(4, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(4, (int) status.getFailed());
    assertEquals(0, (int) status.getPendingBuildTypes());
    assertEquals(
      "Each test is expected to be counted as failed twice: 1 in the first composite and 1 in the second one.",
      4, (int) status.getNewFailedTests()
    );
    assertEquals(0, (int) status.getOtherFailedTests());
  }

  public void testFailuresInParallelizedBuild() {
    ProjectEx project = createProject("testProject", "testProject");
    ProjectEx virtualProject = project.createProject("virtual", "virtual");
    virtualProject.setArchived(true, null);
    virtualProject.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "true"));

    final BuildTypeEx virtualBt1 = virtualProject.createBuildType("virtualBt1");
    final BuildTypeEx virtualBt2 = virtualProject.createBuildType("virtualBt2");
    final BuildTypeEx splitBt = project.createBuildType("splitBt");
    // this is an important part: we pretend that the parallel builds feature is enabled.
    splitBt.addBuildFeature("parallelTests", Collections.singletonMap("numberOfBatches", "2"));

    List<VcsRootInstance> roots = prepareMultipleVscRoots(virtualBt1, virtualBt2, splitBt);
    List<SVcsModification> mods = prepareModificationInMultipleRoots(roots, "master", "20");

    // add two agents, so we can run all three builds at the same time.
    myFixture.createEnabledAgent("x");
    myFixture.createEnabledAgent("x");

    SQueuedBuild splitBuild    = build().in(splitBt).onModifications(mods.get(2)).addToQueue();
    SQueuedBuild virtual1Build = build().in(virtualBt1).onModifications(mods.get(0)).addToQueue();
    SQueuedBuild virtual2Build = build().in(virtualBt2).onModifications(mods.get(1)).addToQueue();
    myFixture.flushQueueAndWaitN(3);

    // that is not a composite configuration, so we add deps manually
    ((BuildPromotionEx)splitBuild.getBuildPromotion()).addDependency((BuildPromotionEx) virtual1Build.getBuildPromotion(), NULL_OPTIONS);
    ((BuildPromotionEx)splitBuild.getBuildPromotion()).addDependency((BuildPromotionEx) virtual2Build.getBuildPromotion(), NULL_OPTIONS);

    ((BuildPromotionImpl) splitBuild.getBuildPromotion()).setAttribute("teamcity.build.composite", "true");

    RunningBuildEx splitRunning = build().run(splitBuild);
    RunningBuildEx virtual1running = build().withFailedTests("Split.failed1", "Split.failed2").run(virtual1Build);
    RunningBuildEx virtual2running = build().withFailedTests("Split.failed1", "Split.failed2").run(virtual2Build);
    splitRunning.updateBuild();

    myFixture.finishBuild(virtual1running, true);
    myFixture.finishBuild(virtual2running, true);
    myFixture.finishBuild(splitRunning, true);

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(mods.get(2)),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(
      "Failed tests are expected to be combined into multi run tests in a case of a virtual build.",
      2, (int) status.getNewFailedTests()
    );

    assertEquals(0, (int) status.getOtherFailedTests());
  }

  public void testFailureInDependency() {
    ProjectEx project = createProject("testProject", "testProject");
    final BuildTypeEx depBt = project.createBuildType("dependencyBt");

    final BuildTypeEx mainBt = project.createBuildType("mainBt");
    BuildServerCreator.makeComposite(mainBt);
    addDependency(mainBt, depBt);

    List<VcsRootInstance> roots = prepareMultipleVscRoots(mainBt, depBt);
    List<SVcsModification> mods = prepareModificationInMultipleRoots(roots, "main", "version");

    SQueuedBuild mainBuild = mainBt.addToQueue("test");
    SQueuedBuild depBuild = null;
    for(SQueuedBuild q : myFixture.getBuildQueue().getItems()) {
      if(q.getBuildPromotion().isCompositeBuild()) continue;

      depBuild = q;
    }
    assertNotNull("Test setup failure, unable to find composite build.", mainBuild);
    assertNotNull("Test setup failure, unable to find dependency build.", depBuild);

    myFixture.flushQueueAndWaitN(2);


    build().withFailedTests("depFail").run(depBuild).finish();
    RunningBuildEx mainRunning = build().run(mainBuild);
    mainRunning.updateBuild(); mainRunning.finish();

    ChangeStatus status = new ChangeStatus(
      myFixture.getChangeStatusProvider().getMergedChangeStatus(mods.get(1)),
      Fields.ALL_NESTED,
      getBeanContext(myFixture)
    );

    assertEquals(2, (int) status.getFinished());
    assertEquals(0, (int) status.getSuccessful());
    assertEquals(2, (int) status.getFailed());
    assertEquals(0, (int) status.getPendingBuildTypes());
    assertEquals("Failure in dependency must be counted once.", 1, (int) status.getNewFailedTests());
    assertEquals(0, (int) status.getOtherFailedTests());
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

  private VcsRootInstance prepareSingleVscRoot(@NotNull BuildTypeEx buildConf) {
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(myVcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;

    setBranchSpec(root1,"+:*");

    return root1;
  }

  private List<VcsRootInstance> prepareMultipleVscRoots(@NotNull BuildTypeEx... buildConfs) {
    List<VcsRootInstance> result = new ArrayList<>();

    for(BuildTypeEx buildConf : buildConfs) {
      SVcsRootEx parentRoot1 = myFixture.addVcsRoot(myVcs.getName(), "", buildConf);
      VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
      assert root1 != null;

      setBranchSpec(root1, "+:*");
      result.add(root1);
    }
    return result;
  }

  private List<SVcsModification> prepareModificationInMultipleRoots(@NotNull List<VcsRootInstance> roots, @NotNull String branch, @NotNull String v) {
    Date now = new Date();

    List<SVcsModification> result = new ArrayList<>(roots.size());
    for(VcsRootInstance root : roots) {
      SVcsModification m = myFixture.addModification(modification().in(root).by("user").at(now).version(v));
      myChangesPolicy.setCurrentState(root, RepositoryStateData.createVersionState(branch, Util.map(branch, v)));
      result.add(m);
    }

    myFixture.getVcsModificationChecker().checkForModifications(roots, OperationRequestor.UNKNOWN);

    return result;
  }

  static public BeanContext getBeanContext(final ServiceLocator serviceLocator) {
    final ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(path -> path);
    final BeanFactory beanFactory = new BeanFactory(null);

    return new BeanContext(beanFactory, serviceLocator, apiUrlBuilder);
  }

  @org.testng.annotations.DataProvider(name = "allBooleans")
  public static Object[][] allBooleans() {
    return new Object[][] {{true}, {false}};
  }

  private final DependencyOptions NULL_OPTIONS = new DependencyOptions() {
    @Override
    @NotNull
    public Object getOption(@NotNull final Option option) {
      return new Object();
    }

    @Override
    public <T> void setOption(@NotNull final Option<T> option, @NotNull final T value) {
    }

    @Override
    @NotNull
    public Collection<Option> getOwnOptions() {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public Collection<Option> getOptions() {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public Option[] getChangedOptions() {
      return new Option[0];
    }

    @Override
    @NotNull
    public <T> T getOptionDefaultValue(@NotNull final Option<T> option) {
      return option.getDefaultValue();
    }

    @Nullable
    @Override
    public <T> T getDeclaredOption(final Option<T> option) {
      return null;
    }
  };
}
