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

import java.util.*;
import jetbrains.buildServer.server.rest.data.change.SVcsModificationOrChangeDescriptor;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.finder.impl.BuildFinderTestBase;
import jetbrains.buildServer.server.rest.data.util.ItemFilter;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.FileChange;
import jetbrains.buildServer.server.rest.model.change.FileChanges;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.MockVcsModification;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.RepositoryStateManager;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;
import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateFactory.createRepositoryState;

/**
 * @author Yegor.Yarko
 *         Date: 25/01/2016
 */
public class ChangeFinderTest extends BaseFinderTest<SVcsModificationOrChangeDescriptor> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setFinder(myChangeFinder);
  }

  @Test
  public void testBranches1() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;

    setBranchSpec(root1,
                  "+:*\n" +
                  "+:prefix/*"
    );

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));
    SVcsModification m30 = myFixture.addModification(modification().in(root1).version("30").parentVersions("20"));
    SVcsModification m40 = myFixture.addModification(modification().in(root1).version("40").parentVersions("10"));
    SVcsModification m50 = myFixture.addModification(modification().in(root1).version("50").parentVersions("40"));
    SVcsModification m60 = myFixture.addModification(modification().in(root1).version("60").parentVersions("15"));
    SVcsModification m70 = myFixture.addModification(modification().in(root1).version("70").parentVersions("10"));

    changesPolicy.setCurrentState(root1, RepositoryStateData.createVersionState("master", Util.map("master", "30",
                                                                                                   "branch1", "40",
                                                                                                   "branch2", "50",
                                                                                                   "branch3", "60",
                                                                                                   "prefix/aaa", "70",
                                                                                                   "branch10", "100")));

    myFixture.getVcsModificationChecker().checkForModifications(buildConf.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    /* Repository state, root1

    Invisible to TC # Visible to TC
                    #
             15 ----#-- 60 (branch3)
             10 ----#-- 20 -- 30 (master)
              |     #
              +-----#-- 40 (branch1)
              |     #   |
              |     #   +---- 50 (branch2)
              |     #
              +-----#-------- 70 (prefix/aaa)
                    #
                    #        100 (branch10)
                    #
     */

    checkWithMessage(
      "When locator is empty, all changes known to TC should be returned, sorted from the newest to the oldest",
      null,
      m70, m60, m50, m40, m30, m20
    );
    setInternalProperty("rest.request.changes.legacyChangesInAllBranches", "true");

    final String btLocator = "buildType:(id:" + buildConf.getExternalId() + ")";
    checkWithMessage(
      "When locator has no branch set all changes known to TC should be returned, sorted from the newest to the oldest (current behaviour).\n" +
      "Logically, we should return changes from default branch instead (m30, m20).",
      btLocator,
      m70, m60, m50, m40, m30, m20
    ); //documenting current behavior, should be check(btLocator, m30, m20);

    checkWithMessage(
      "When branch set to any, all changes known to TC should be returned, sorted from the newest to the oldest.",
      btLocator + ",branch:(default:any)",
      m70, m60, m50, m40, m30, m20
    );

    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(aaa:bbb)");
    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(default:true)"); //no buildType is not supported
    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(name:branch1)");
    checkExceptionOnItemsSearch(BadRequestException.class, btLocator + ",branch:(name:master,aaa:bbb)");

    checkWithMessage(
      "Branch master is default, so no branches match => no changes.",
      btLocator + ",branch:(name:master,default:false)",
      new SVcsModificationOrChangeDescriptor[0]
    );
    checkWithMessage(
      "Changes from the master branch are expected.",
      btLocator + ",branch:(name:master)",
      m30, m20
    );
    checkWithMessage(
      "Changes from the master branch are expected.",
      btLocator + ",branch:(name:<default>)",
      m30, m20
    );
    checkWithMessage(
      "Changes from the branch are expected, prefix in a branch name should be ignored according to a branch spec.",
      btLocator + ",branch:(name:aaa)",
      m70
    );
    checkWithMessage(
      "Only changes from the branchwith specified name are expected.",
      btLocator + ",branch:(name:branch1)",
      m40
    );
    checkWithMessage(
      "No changes shoud be found in the non existing branch.",
      btLocator + ",branch:(name:bbb)",
      new SVcsModificationOrChangeDescriptor[0]
    );
    checkWithMessage(
      "All changes are expected when wildcard branch name specified.",
      btLocator + ",branch:(name:<any>)",
      m70, m60, m50, m40, m30, m20
    );

    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(branch1)");

    checkWithMessage(
      "All changes are expected when wildcard branch name specified, singleValue branch locator.",
      btLocator + ",branch:(<any>)",
      m70, m60, m50, m40, m30, m20
    );
    checkWithMessage(
      "Changes from the master branch are expected, singleValue branch locator.",
      btLocator + ",branch:(master)",
      m30, m20
    );
    checkWithMessage(
      "Changes from the master branch are expected, singleValue branch locator.",
      btLocator + ",branch:(<default>)",
      m30, m20
    );
    checkWithMessage(
      "Changes from the branch with specified name are expected, singleValue branch locator, prefix in a branch name should be ignored according to a branch spec.",
      btLocator + ",branch:(aaa)",
      m70
    );
    checkWithMessage(
      "Prefix in a branch name should be ignored according to a branch spec, so TC doesn't know about prefix in the branch name.",
      btLocator + ",branch:(prefix/aaa)",
      new SVcsModificationOrChangeDescriptor[0]
    );
    checkWithMessage(
      "Only changes from the default branch are expected.",
      btLocator + ",branch:(default:true)",
      m30, m20
    );
    checkWithMessage(
      "Only changes NOT from the default branch are expected.",
      btLocator + ",branch:(default:false)",
      m70, m60, m50, m40
    );
    checkWithMessage(
      "Only changes NOT from the default branch are expected, all of them are unique, no duplicates should be filtered out.",
      btLocator + ",branch:(default:false),unique:true",
      m70, m60, m50, m40
    );

    //test pending

    checkWithMessage(
      "Before any build was run, all changes are considered pending in default branch",
      btLocator + ",branch:(name:<default>),pending:true",
      m30, m20
    );
    checkWithMessage(
      "Before any build was run, all changes are considered pending in a branch with specific name",
      btLocator + ",branch:(name:master),pending:true",
      m30, m20
    );
    checkWithMessage(
      "Pending changes in a branch should not include changes that are not in that branch (m50 should not be present)",
      btLocator + ",branch:(name:branch1),pending:true",
      m40
    );
    checkWithMessage(
      "All changes in master should be considred pending if there was no build.",
      btLocator + ",branch:(name:master),pending:false",
      new SVcsModificationOrChangeDescriptor[0]
    );
    checkWithMessage(
      "All changes in default branch should be considred pending if there was no build.",
      btLocator + ",branch:(name:<default>),pending:false",
      new SVcsModificationOrChangeDescriptor[0]
    );
    checkWithMessage(
      "All changes are pending in a branch1.",
      btLocator + ",branch:(name:branch1),pending:false",
      new SVcsModificationOrChangeDescriptor[0]
    );
    checkWithMessage(
      "Both pending and non-pending changes should be returned.",
      btLocator + ",branch:(name:branch1),pending:any",
      m40
    );

    changesPolicy.setCurrentState(root2, RepositoryStateData.createVersionState("master", Util.map("master", "11")));
    build().in(buildConf).withDefaultBranch().finish();
    build().in(buildConf).withBranch("branch1").finish();

    SVcsModification m80 = myFixture.addModification(modification().in(root1).version("80").parentVersions("30"));
    SVcsModification m90 = myFixture.addModification(modification().in(root1).version("90").parentVersions("40"));

    changesPolicy.setCurrentState(root1, RepositoryStateData.createVersionState("master", Util.map("master", "80",
                                                                                                   "branch1", "90",
                                                                                                   "branch2", "50",
                                                                                                   "branch3", "60",
                                                                                                   "prefix/aaa", "70",
                                                                                                   "branch10", "100")));

    myFixture.getVcsModificationChecker().checkForModifications(buildConf.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    /* Repository state, root1

    Invisible to TC # Visible to TC
                    #
             15 ----#-- 60 (branch3)
             10 ----#-- 20 --- 30 [build] --- 80 (master)
              |     #
              +-----#-- 40 [build] ---------- 90 (branch1)
              |     #   |
              |     #   +----- 50 (branch2)
              |     #
              +-----#--------- 70 (prefix/aaa)
                    #
                    #         100 (branch10)
                    #
     */

    checkWithMessage(
      "All changes are expected when empty locator is given, including new ones.",
      null,
      m90, m80, m70, m60, m50, m40, m30, m20
    );
    checkWithMessage(
      "All changes in master are expected, including new ones.",
      btLocator + ",branch:(name:master)",
      m80, m30, m20
    );
    checkWithMessage(
      "All changes in default branch are expected, including new ones.",
      btLocator + ",branch:(name:<default>)",
      m80, m30, m20
    );
    checkWithMessage(
      "All changes in specified branch are expected, including new ones.",
      btLocator + ",branch:(name:branch1)",
      m90, m40
    );
    checkWithMessage(
      "There is only one pending change in a master after build.",
      btLocator + ",branch:(name:master),pending:true",
      m80
    );
    checkWithMessage(
      "There is only one pening change in default branch after build.",
      btLocator + ",branch:(name:<default>),pending:true",
      m80
    );
    checkWithMessage(
      "There is only one pending change in a branch1 after build.",
      btLocator + ",branch:(name:branch1),pending:true",
      m90
    );
    checkWithMessage(
      "Changes in a master included in a build are not pending.",
      btLocator + ",branch:(name:master),pending:false",
      m30, m20
    );
    checkWithMessage(
      "Changes in default branch included in a build are not pending.",
      btLocator + ",branch:(name:<default>),pending:false",
      m30, m20
    );
    checkWithMessage(
      "Changes in branch1 included in a build are not pending.",
      btLocator + ",branch:(name:branch1),pending:false",
      m40
    );
    checkWithMessage(
      "All changes in specified branch are expected, including new ones.",
      btLocator + ",branch:(name:branch1),pending:any",
      m90, m40
    );
  }

  @Test
  public void testBranchFromBranch() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;

    setBranchSpec(root1,"+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));

    build().in(buildConf).onModifications(m20).finish();

    SVcsModification m30 = myFixture.addModification(modification().in(root1).version("30").parentVersions("20"));
    SVcsModification m40 = myFixture.addModification(modification().in(root1).version("40").parentVersions("30"));
    SVcsModification m45 = myFixture.addModification(modification().in(root1).version("45").parentVersions("30"));
    SVcsModification m60 = myFixture.addModification(modification().in(root1).version("60").parentVersions("20"));

    changesPolicy.setCurrentState(root1, RepositoryStateData.createVersionState("master", Util.map("master", "60",
                                                                                                   "branch1", "40",
                                                                                                   "branch2", "45")));

    myFixture.getVcsModificationChecker().checkForModifications(buildConf.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    /* Repository state

    Invisible to TC # Visible to TC
                    #
               10 --#-- 20 [build] -- 60 (master)
                    #    |
                    #    +--- 30 --- 40 (branch1)
                    #         |
                    #         +----- 45 (branch2)
                    #
    */

    checkWithMessage(
      "Empty locator should return all changes.",
      null,
      m60, m45, m40, m30, m20
    );

    final String btLocator = "buildType:(id:" + buildConf.getExternalId() + ")";
    checkWithMessage(
      "Locator without branch should return all changes as of now (current behaviour).\n" +
      "Logically, only changes from default branch should be included (m60, m20)",
      btLocator,
      m60, m45, m40, m30, m20
    );
    checkWithMessage(
      "All branches are selected by locator, so we should return all changes.",
      btLocator + ",branch:(default:any)",
      m60, m45, m40, m30, m20
    );
    checkWithMessage(
      "When pending:true and branch locator is not specified, only pending changes from default branch should be included.",
      btLocator + ",pending:true",
      m60
    );
    checkWithMessage(
      "When pending:true and branch is given, only pending changes from given branch should be included.",
      btLocator + ",pending:true,branch:branch1",
      m40, m30
    );
    checkWithMessage(
      "When pending:false and non default branch, pending changes from all branches except default should be included.",
      btLocator + ",pending:true,branch:(default:false)",
      m45, m40, m30
    );
    checkWithMessage(
      "When pending:true and all branches are selected, pending changes from all branches should be included.",
      btLocator + ",pending:true,branch:(default:any)",
      m60, m45, m40, m30
    );
  }

  @Test
  public void testChangedVcsRootAndDAG() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;

    setBranchSpec(root1,"+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).version("20").parentVersions("10"));
    SVcsModification m30 = myFixture.addModification(modification().in(root1).version("30").parentVersions("20"));
    SVcsModification m40 = myFixture.addModification(modification().in(root1).version("40").parentVersions("10"));
    SVcsModification m45 = myFixture.addModification(modification().in(root1).version("45").parentVersions("40"));
    SVcsModification m50 = myFixture.addModification(modification().in(root1).version("50").parentVersions("10"));
    SVcsModification m60 = myFixture.addModification(modification().in(root1).version("60").parentVersions("20"));

    changesPolicy.setCurrentState(root1, RepositoryStateData.createVersionState("master", Util.map("master", "30",
                                                                                                   "branch1", "45",
                                                                                                   "branch2", "50",
                                                                                                   "branch3", "60")));
    myFixture.getVcsModificationChecker().checkForModifications(buildConf.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    build().in(buildConf).onModifications(m20).finish();
    build().in(buildConf).withBranch("branch1").onModifications(m40).finish();

    /* Repository state, root1

    Invisible to TC # Visible to TC
                    #
               10 --#-- 20 [build] --- 30 (master)
                |   #        |
                |   #        +-------- 60 (branch3)
                |   #
                +---#-- 40 [build] --- 45 (branch1)
                |   #
                +---#-- 50 (branch2)

    */

    checkWithMessage(
      "Empty locator should return all changes.",
      null,
      m60, m50, m45, m40, m30, m20
    );

    final String btLocator = "buildType:(id:" + buildConf.getExternalId() + ")";
    checkWithMessage(
      "As of now, all changes are returned when no branch is specified.\n" +
      "Logically, we should return only changes coming from default branch instead (m30, m20).",
      btLocator,
      m60, m50, m45, m40, m30, m20
    ); //documenting current behavior should be check(btLocator, m30, m20);
    checkWithMessage(
      "Locator matching any branch should return all changes.",
      btLocator + ",branch:(default:any)",
      m60, m50, m45, m40, m30, m20
    );
    checkWithMessage(
      "When pending:true and branch is not specified, only pending changes from default branch should be returned.",
      btLocator + ",pending:true",
      m30
    );
    checkWithMessage(
      "When pending:true and branch is specified, return pending changes from specified branch.",
      btLocator + ",pending:true,branch:branch1",
      m45
    );
    checkWithMessage(
      "When pending:true and all non-default branches are specified, return pending changes from all branches except default.",
      btLocator + ",pending:true,branch:(default:false)",
      m60, m50, m45
    );
    checkWithMessage(
      "When pending:true and all branches are specified, return all pending changes.",
      btLocator + ",pending:true,branch:(default:any)",
      m60, m50, m45, m30
    );

    buildConf.removeVcsRoot(parentRoot1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root2 = buildConf.getVcsRootInstanceForParent(parentRoot2);
    assert root2 != null;

    setBranchSpec(root2,"+:*");

    SVcsModification m200 = myFixture.addModification(modification().in(root2).version("200").parentVersions("199"));
    SVcsModification m210 = myFixture.addModification(modification().in(root2).version("210").parentVersions("199"));

    changesPolicy.setCurrentState(root2, RepositoryStateData.createVersionState("master", Util.map("master", "210",
                                                                                                   "branch1", "200")));
    myFixture.getVcsModificationChecker().checkForModifications(buildConf.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    /* Repository state, root2

    Invisible to TC # Visible to TC
                    #
              199 --#-- 210 (master)
                |   #
                + --#-- 200 (branch1)

    */

    checkWithMessage(
      "When locator is not specififed, we should return all changes from old and new vcs roots.",
      null,
      m210, m200, m60, m50, m45, m40, m30, m20
    );
    checkWithMessage(
      "As of now, all changes are returned when no branch is specified.\n" +
      "Logically, we should return only changes coming from default branch instead (m210, m30, m20).",
      btLocator,
      m210, m200, m60, m50, m45, m40, m30, m20
    );
    checkWithMessage(
      "When locator specififes all branches, we should return all changes from old and new vcs roots.",
      btLocator + ",branch:(default:any)",
      m210, m200, m60, m50, m45, m40, m30, m20
    );
    checkWithMessage(
      "When pending:true and no branch specified, we should return pending changes from default branch only. 'Pending' changes from old vcsRoot shpuld be ignored.",
      btLocator + ",pending:true",
      m210
    );
    checkWithMessage(
      "When pending:true and branch is specified specified, we should return pending changes from specified branch only. 'Pending' changes from old vcsRoot shpuld be ignored.",
      btLocator + ",pending:true,branch:branch1",
      m200
    );
    checkWithMessage(
      "When pending:true and only default branch is specified, we should return pending changes from default branch only. 'Pending' changes from old vcsRoot shpuld be ignored.",
      btLocator + ",pending:true,branch:(default:true)",
      m210
    );
    checkWithMessage(
      "When pending:true and only non-default branches are specified, we should return pending changes from non-default branches only. 'Pending' changes from old vcsRoot shpuld be ignored.",
      btLocator + ",pending:true,branch:(default:false)",
      m200
    );
    checkWithMessage(
      "When pending:true and all branches are specified, we should return all pending changes. 'Pending' changes from old vcsRoot shpuld be ignored.",
      btLocator + ",pending:true,branch:(default:any)",
      m210, m200
    );
  }

  @Test
  public void testChangesFromDependenciesNoDAG() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    createDependencyChain(buildConf2, buildConf1);

    SVcsRoot vcsRoot1 = myFixture.addVcsRoot("svn", "", buildConf1);
    SVcsModification m10 = myFixture.addModification(ModificationDataForTest.forTests("descr1", "user1", vcsRoot1, "ver1"));

    SVcsRoot vcsRoot2 = myFixture.addVcsRoot("svn", "", buildConf2);
    SVcsModification m20 = myFixture.addModification(ModificationDataForTest.forTests("descr2", "user2", vcsRoot2, "ver2"));


    check(null, m20, m10);

    String btLocator1 = "buildType:(id:" + buildConf1.getExternalId() + ")";
    String btLocator2 = "buildType:(id:" + buildConf2.getExternalId() + ")";

    check(btLocator1, m10);
    check(btLocator1 + ",pending:true", m10);

    check(btLocator2, m20);
    check(btLocator2 + ",pending:true", m20);

    buildConf2.setOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES, true);

    check(btLocator2, m20, m10);
    check(btLocator2 + ",pending:true", m20, m10);
  }

  @Test
  public void testChangesFromDependenciesDAG() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    createDependencyChain(buildConf2, buildConf1);

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf2);
    VcsRootInstance root1 = buildConf1.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf2.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;

    setBranchSpec(root1, "+:*");
    setBranchSpec(root2, "+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    SVcsModification m120 = myFixture.addModification(modification().in(root1).version("120").parentVersions("10"));
    SVcsModification m250 = myFixture.addModification(modification().in(root2).version("250").parentVersions("10"));

    changesPolicy.setCurrentState(root1, RepositoryStateData.createVersionState("master", Util.map("master", "120")));
    changesPolicy.setCurrentState(root2, RepositoryStateData.createVersionState("master", Util.map("master", "250")));
    myFixture.getVcsModificationChecker().checkForModifications(buildConf1.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    myFixture.getVcsModificationChecker().checkForModifications(buildConf2.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    String btLocator1 = "buildType:(id:" + buildConf1.getExternalId() + ")";
    String btLocator2 = "buildType:(id:" + buildConf2.getExternalId() + ")";

    check(null, m250, m120);
    check(btLocator1, m120);
    check(btLocator1 + ",branch:(default:true)", m120);
    check(btLocator1 + ",branch:(name:master)", m120);
    check(btLocator1 + ",branch:(name:branch1)");

    check(btLocator1 + ",pending:true", m120);
    check(btLocator1 + ",pending:true,branch:(default:true)", m120);
    check(btLocator1 + ",pending:true,branch:(default:any)", m120);
    check(btLocator1 + ",pending:true,branch:(name:branch1)");
    check(btLocator1 + ",pending:true,branch:(policy:ACTIVE_VCS_BRANCHES)", m120);

    check(btLocator2, m250);
    check(btLocator2 + ",branch:(default:true)", m250);
    check(btLocator2 + ",branch:(name:master)", m250);
    check(btLocator2 + ",branch:(name:branch1)");

    check(btLocator2 + ",pending:true", m250);
    check(btLocator2 + ",pending:true,branch:(default:true)", m250);
    check(btLocator2 + ",pending:true,branch:(default:any)", m250);
    check(btLocator2 + ",pending:true,branch:(name:branch1)");
    check(btLocator2 + ",pending:true,branch:(policy:ACTIVE_VCS_BRANCHES)", m250);


    build().in(buildConf1).onModifications(m120).finish();
    SFinishedBuild build20 = build().in(buildConf2).onModifications(m250).finish();
    assertEquals("120", build20.getBuildPromotion().getDependencies().iterator().next().getDependOn().getRevisions().get(0).getRevision());

    SVcsModification m130 = myFixture.addModification(modification().in(root1).version("130").parentVersions("120"));
    SVcsModification m140 = myFixture.addModification(modification().in(root1).version("140").parentVersions("10"));
    SVcsModification m150 = myFixture.addModification(modification().in(root1).version("150").parentVersions("140"));

    SVcsModification m260 = myFixture.addModification(modification().in(root2).version("260").parentVersions("250"));
    SVcsModification m270 = myFixture.addModification(modification().in(root2).version("270").parentVersions("10"));

    changesPolicy.setCurrentState(root1, RepositoryStateData.createVersionState("master", Util.map("master", "130", "branch1", "150")));
    changesPolicy.setCurrentState(root2, RepositoryStateData.createVersionState("master", Util.map("master", "260", "branch1", "270")));

    myFixture.getVcsModificationChecker().checkForModifications(buildConf1.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    myFixture.getVcsModificationChecker().checkForModifications(buildConf2.getVcsRootInstances(), OperationRequestor.UNKNOWN);


    check(null, m270, m260, m150, m140, m130, m250, m120);

    check(btLocator1, m150, m140, m130, m120); //documenting current behavior, should be check(btLocator1, m130, m120);
    check(btLocator1 + ",branch:(default:true)", m130, m120);
    check(btLocator1 + ",branch:(default:any)", m150, m140, m130, m120);
    check(btLocator1 + ",branch:(name:master)", m130, m120);
    check(btLocator1 + ",branch:(name:branch1)", m150, m140);

    check(btLocator1 + ",pending:true", m130);
    check(btLocator1 + ",pending:true,branch:(default:true)", m130);
    check(btLocator1 + ",pending:true,branch:(default:any)", m150, m140, m130);
    check(btLocator1 + ",pending:true,branch:(name:branch1)", m150, m140);
    check(btLocator1 + ",pending:true,branch:(policy:ACTIVE_VCS_BRANCHES)", m150, m140, m130);

    check(btLocator2, m270, m260, m250); //documenting current behavior should be check(btLocator2, m260, m250);
    check(btLocator2 + ",branch:(default:true)", m260, m250);
    check(btLocator2 + ",branch:(name:master)", m260, m250);
    check(btLocator2 + ",branch:(name:branch1)", m270);

    check(btLocator2 + ",pending:true", m260);
    check(btLocator2 + ",pending:true,branch:(default:true)", m260);
    check(btLocator2 + ",pending:true,branch:(default:any)", m270, m260);
    check(btLocator2 + ",pending:true,branch:(name:branch1)", m270);
    check(btLocator2 + ",pending:true,branch:(policy:ACTIVE_VCS_BRANCHES)", m270, m260);

    check("build:(" + build20.getBuildId()+ ")", m250);

    buildConf2.setOption(BuildTypeOptions.BT_SHOW_DEPS_CHANGES, true);

    check(btLocator2, m260, m130, m250, m120);
    check(btLocator2 + ",branch:(default:true)", m260, m130, m250, m120);
    check(btLocator2 + ",branch:(name:master)", m260, m130, m250, m120);
    check(btLocator2 + ",branch:(name:branch1)", m270, m150, m140);

    check(btLocator2 + ",pending:true", m260, m130);
    check(btLocator2 + ",pending:true,branch:(default:true)", m260, m130);
    check(btLocator2 + ",pending:true,branch:(default:any)", m270, m260, m150, m140, m130);
    check(btLocator2 + ",pending:true,branch:(name:branch1)", m270, m150, m140);
    check(btLocator2 + ",pending:true,branch:(policy:ACTIVE_VCS_BRANCHES)", m270, m260, m150, m140, m130);

    check("build:(" + build20.getBuildId()+ ")", m250, m120);
  }

  @Test
  @TestFor(issues = "TW-60774")
  public void testChangesByBuildIdFromDependenciesHaveDescriptor() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    createDependencyChain(buildConf2, buildConf1);

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf2);
    VcsRootInstance root1 = buildConf1.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf2.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;

    setBranchSpec(root1, "+:*");
    setBranchSpec(root2, "+:*");

    SVcsModification m1 = myFixture.addModification(modification().in(root1).version("1").parentVersions("0"));
    SVcsModification m2 = myFixture.addModification(modification().in(root2).version("2").parentVersions("0"));

    SQueuedBuild qb1 = build().in(buildConf1).onModifications(m1).addToQueue();
    SQueuedBuild qb2 = build().in(buildConf2).onModifications(m2).snapshotDepends(qb1.getBuildPromotion()).addToQueue();

    SFinishedBuild build1 = finishBuild(myFixture.flushQueueAndWait(),false);
    SFinishedBuild build2 = finishBuild(myFixture.flushQueueAndWait(),false);

    List<SVcsModificationOrChangeDescriptor> items = getFinder()
      .getItems("build:" + build2.getBuildId() + ",changesFromDependencies:true,vcsRoot:(id:" + root1.getExternalId() + ")")
      .myEntries;
    assertEquals("There is exactly one change coming from dependency.", 1, items.size());

    ChangeDescriptor descriptor = items.get(0).getChangeDescriptor();
    assertNotNull("Change descriptor must be present when looking for changes using build id.", descriptor);
    assertEquals(ChangeDescriptorConstants.SNAPSHOT_DEPENDENCY_VCS_CHANGE, descriptor.getType());

    SVcsModification modification = items.get(0).getSVcsModification();
    assertEquals("1", modification.getDisplayVersion());
  }

  @Test
  @TestFor(issues = "TW-72448")
  public void testChangesByBuildTypeFromDependenciesHaveDescriptor() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    createDependencyChain(buildConf2, buildConf1);

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf2);
    VcsRootInstance root1 = buildConf1.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf2.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;

    setBranchSpec(root1, "+:*");
    setBranchSpec(root2, "+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    myFixture.addModification(modification().in(root1).version("1").parentVersions("0"));
    myFixture.addModification(modification().in(root2).version("2").parentVersions("0"));

    List<SVcsModificationOrChangeDescriptor> items = getFinder()
      .getItems("buildType:buildConf2,changesFromDependencies:true,vcsRoot:(id:" + root1.getExternalId() + ")")
      .myEntries;
    assertEquals("There is exactly one pending change coming from dependency.", 1, items.size());

    ChangeDescriptor descriptor = items.get(0).getChangeDescriptor();
    assertNotNull("Depency change descriptor must be present when looking for changes using build type.", descriptor);
    assertEquals(ChangeDescriptorConstants.SNAPSHOT_DEPENDENCY_VCS_CHANGE, descriptor.getType());

    SVcsModification modification = items.get(0).getSVcsModification();
    assertEquals("1", modification.getDisplayVersion());
  }

  @Test
  public void testBuildTypeDimensionInFilter() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");
    createDependencyChain(buildConf2, buildConf1);

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    vcs.setDAGBased(true);
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf2);
    VcsRootInstance root1 = buildConf1.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf2.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null;
    assert root2 != null;

    setBranchSpec(root1, "+:*");
    setBranchSpec(root2, "+:*");

    final BuildFinderTestBase.MockCollectRepositoryChangesPolicy changesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(changesPolicy);

    SVcsModification mod1 = myFixture.addModification(modification().in(root1).version("1").parentVersions("0"));
    SVcsModification mod2 = myFixture.addModification(modification().in(root2).version("2").parentVersions("0"));

    ItemFilter<SVcsModificationOrChangeDescriptor> filter = getFinder().getFilter("buildType:buildConf2");

    assertFalse(filter.isIncluded(new SVcsModificationOrChangeDescriptor(mod1)));
    assertTrue(filter.isIncluded(new SVcsModificationOrChangeDescriptor(mod2)));
  }

  @Test
  public void testChangeBean() {

    MockVcsSupport vcsSupport = new MockVcsSupport("svn");
    myFixture.getVcsManager().registerVcsSupport(vcsSupport);
    SVcsRootImpl vcsRoot = myFixture.addVcsRoot(vcsSupport.getName(), "", myBuildType);
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(vcsRoot);

    MockVcsModification modification10 = MockVcsModification.createWithoutFiles("user1", "descr1", new Date());
    modification10.addChange(new VcsChange(VcsChangeInfo.Type.ADDED, "root/a/file.txt", "a/file.txt", "9", "10"));
    modification10.addChange(new VcsChange(VcsChangeInfo.Type.CHANGED, "root/a/file2.txt", "a/file2.txt", null, null));
    modification10.addChange(new VcsChange(VcsChangeInfo.Type.REMOVED, "root/b/file.txt", "b/file.txt", null, null));
    modification10.addChange(new VcsChange(VcsChangeInfo.Type.NOT_CHANGED, "root/b/file3.txt", "b/file3.txt", null, null));

    modification10.addChange(new VcsChange(VcsChangeInfo.Type.DIRECTORY_ADDED, "root/c", "c", null, "after"));
    modification10.addChange(new VcsChange(VcsChangeInfo.Type.DIRECTORY_CHANGED, "root/c1", "c1", null, "after"));
    modification10.addChange(new VcsChange(VcsChangeInfo.Type.DIRECTORY_REMOVED, "root/d", "d", "before", null));
    modification10.addChange(new VcsChange(VcsChangeInfo.Type.DIRECTORY_COPIED, "root/e", "e", "before", "after"));
    vcsSupport.addChange(vcsRootInstance, modification10);

    Change change10 = new Change(modification10, Fields.ALL, getBeanContext(myServer));
    FileChanges fileChanges10 = change10.getFileChanges();
    assertEquals(Integer.valueOf(8), fileChanges10.count);
    //type names are part of API
    check(fileChanges10.files.get(0), "added", null, null, "root/a/file.txt", "a/file.txt");
    check(fileChanges10.files.get(1), "edited", null, null, "root/a/file2.txt", "a/file2.txt");
    check(fileChanges10.files.get(2), "removed", null, null, "root/b/file.txt", "b/file.txt");
    check(fileChanges10.files.get(3), "unchanged", null, null, "root/b/file3.txt", "b/file3.txt");

    check(fileChanges10.files.get(4), "added", null, true, "root/c", "c");
    check(fileChanges10.files.get(5), "edited", null, true, "root/c1", "c1");
    check(fileChanges10.files.get(6), "removed", null, true, "root/d", "d");
    check(fileChanges10.files.get(7), "copied", null, true, "root/e", "e");
  }

  @Test
  public void testLimitedProcessing() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;

    SVcsModification m20 = myFixture.addModification(modification().in(root1).by("user1").version("20").parentVersions("10"));
    SVcsModification m30 = myFixture.addModification(modification().in(root1).version("30").parentVersions("20"));
    SVcsModification m40 = myFixture.addModification(modification().in(root1).version("40").parentVersions("10"));
    SVcsModification m50 = myFixture.addModification(modification().in(root1).by("user1").version("50").parentVersions("40"));
    SVcsModification m60 = myFixture.addModification(modification().in(root1).version("60").parentVersions("15"));
    SVcsModification m70 = myFixture.addModification(modification().in(root1).version("70").parentVersions("10"));

    myFixture.getVcsModificationChecker().checkForModifications(buildConf.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    check(null, m70, m60, m50, m40, m30, m20);
    checkCounts("count:3", 3, 4);
    checkCounts("lookupLimit:3", 3, 4);
    checkCounts("username:user1", 2, 6);
    checkCounts("buildType:(id:" + buildConf2.getExternalId() + ")", 0, 0);
    checkCounts("version:50", 1, 6);
  }

  @Test
  public void testVersionedSettings() {
    ProjectEx project = getRootProject().createProject("project", "project");
    project.persist();

    final BuildTypeEx buildConf = project.createBuildType("buildConf1");
    buildConf.persist();

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;

    SVcsRoot vsRoot = project.createVcsRoot(vcsSupport().withName("vcs1").dagBased(true).register().getName(), "Settings Root", map());
    VcsRootInstance vsInstance = resolveInProject(vsRoot, project);

    myFixture.getSingletonService(RepositoryStateManager.class).setRepositoryState(vsInstance, createRepositoryState(map("default", "vs10"), "default"));

    VersionedSettingsConfig vsConfig = new VersionedSettingsConfig();
    vsConfig.setVcsRootExternalId(vsRoot.getExternalId());
    vsConfig.setEnabled(true);
    vsConfig.setShowSettingsChanges(true);
    vsConfig.setBuildSettingsMode(VersionedSettingsConfig.BuildSettingsMode.PREFER_VCS);
    myFixture.writeVersionedSettingsConfig(project, vsConfig);

    SVcsModification m20 = myFixture.addModification(modification().in(root1).by("user1").version("m20").parentVersions("m10"));
    SVcsModification vs_m20 = myFixture.addModification(modification().in(vsInstance).version("vs20").parentVersions("vs10"), buildConf, RelationType.SETTINGS_AFFECT_BUILDS);

    myFixture.getSingletonService(RepositoryStateManager.class).setRepositoryState(vsInstance, createRepositoryState(map("default", "vs20"), "default"));

    //myFixture.getVcsModificationChecker().checkForModifications(buildConf.getVcsRootInstances(), OperationRequestor.UNKNOWN);
    //myFixture.getVcsModificationChecker().checkForModifications(Collections.singleton(vsInstance), OperationRequestor.UNKNOWN);

    check(null, vs_m20, m20);
    String bt = "buildType:(" + buildConf.getExternalId() + ")";
    check(bt, vs_m20, m20);
    check(bt + ",versionedSettings:true", vs_m20);
    check(bt + ",versionedSettings:false", m20);
    check(bt + ",versionedSettings:any", vs_m20, m20);
  }

  @Test
  public void testCommentDimensionLegacy() {
    VcsRootInstance root1 = buildVcsRootInstance();

    final String description1 = "Description made by user with a string 'hello'";
    final String description2 = "Description made by user without a magic string";

    myFixture.addModification(modification().in(root1).description(description1).by("user1").version("1"));
    myFixture.addModification(modification().in(root1).description(description2).by("user1").version("2").parentVersions("1"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("comment:contains:hello").myEntries;
    assertEquals(1, result.size());
    assertEquals(description1, result.get(0).getSVcsModification().getDescription());
  }

  @Test
  public void testCommentDimension() {
    VcsRootInstance root1 = buildVcsRootInstance();

    final String description1 = "Description made by user with a string 'hello'";
    final String description2 = "Description made by user without a magic string";

    myFixture.addModification(modification().in(root1).description(description1).by("user1").version("1"));
    myFixture.addModification(modification().in(root1).description(description2).by("user1").version("2").parentVersions("1"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("comment:(value:HELLO,ignoreCase:true,matchType:contains)").myEntries;
    assertEquals(1, result.size());
    assertEquals(description1, result.get(0).getSVcsModification().getDescription());
  }

  @Test
  public void testCommentDimensionExactMatch() {
    VcsRootInstance root1 = buildVcsRootInstance();

    final String description1 = "Description made by user with a string 'hello'";
    final String description2 = "Description made by user without a magic string";

    myFixture.addModification(modification().in(root1).description(description1).by("user1").version("1"));
    myFixture.addModification(modification().in(root1).description(description2).by("user1").version("2").parentVersions("1"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("comment:" + description1).myEntries;
    assertEquals(1, result.size());
    assertEquals(description1, result.get(0).getSVcsModification().getDescription());
  }

  @Test
  public void testCommentDimensionExactMatch2() {
    VcsRootInstance root1 = buildVcsRootInstance();

    // Let's check that the word 'contains' also works as an exact match.
    // It's important because 'contains' is also a dimension in a legacy approach.
    final String description1 = "contains";
    final String description2 = "Description made by user without a magic string";

    myFixture.addModification(modification().in(root1).description(description1).by("user1").version("1"));
    myFixture.addModification(modification().in(root1).description(description2).by("user1").version("2").parentVersions("1"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("comment:" + description1).myEntries;
    assertEquals(1, result.size());
    assertEquals(description1, result.get(0).getSVcsModification().getDescription());
  }

  @Test
  public void testFilePathLegacyDimension() {
    VcsRootInstance root1 = buildVcsRootInstance();

    final String changedFile1 = "FileA";
    final String changedFile2 = "FileB";

    myFixture.addModification(modification().in(root1).by("user1").withChangedFile(changedFile1).version("1"));
    myFixture.addModification(modification().in(root1).by("user1").withChangedFile(changedFile2).version("2").parentVersions("1"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("file:path:contains:A").myEntries;
    assertEquals(1, result.size());
    assertEquals(changedFile1, result.get(0).getSVcsModification().getChanges().get(0).getFileName());
  }

  @Test
  public void testFilePathDimension() {
    VcsRootInstance root1 = buildVcsRootInstance();

    final String changedFile1 = "FileA";
    final String changedFile2 = "FileB";

    myFixture.addModification(modification().in(root1).by("user1").withChangedFile(changedFile1).version("1"));
    myFixture.addModification(modification().in(root1).by("user1").withChangedFile(changedFile2).version("2").parentVersions("1"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("file:path:(value:ILEa,ignoreCase:true,matchType:contains)").myEntries;
    assertEquals(1, result.size());
    assertEquals(changedFile1, result.get(0).getSVcsModification().getChanges().get(0).getFileName());
  }

  @Test
  public void testFilePathDimensionExactMatch() {
    VcsRootInstance root1 = buildVcsRootInstance();

    final String changedFile1 = "FileA";
    final String changedFile2 = "FileB";

    myFixture.addModification(modification().in(root1).by("user1").withChangedFile(changedFile1).version("1"));
    myFixture.addModification(modification().in(root1).by("user1").withChangedFile(changedFile2).version("2").parentVersions("1"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("file:path:" + changedFile1).myEntries;
    assertEquals(1, result.size());
    assertEquals(changedFile1, result.get(0).getSVcsModification().getChanges().get(0).getFileName());
  }

  @Test
  public void testFilePathDimensionExactMatch2() {
    VcsRootInstance root1 = buildVcsRootInstance();

    // Let's check that the word 'contains' also works as an exact match.
    // It's important because 'contains' is also a dimension in a legacy approach.
    final String changedFile1 = "contains";
    final String changedFile2 = "FileB";

    myFixture.addModification(modification().in(root1).by("user1").withChangedFile(changedFile1).version("1"));
    myFixture.addModification(modification().in(root1).by("user1").withChangedFile(changedFile2).version("2").parentVersions("1"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("file:path:" + changedFile1).myEntries;
    assertEquals(1, result.size());
    assertEquals(changedFile1, result.get(0).getSVcsModification().getChanges().get(0).getFileName());
  }

  @Test
  public void testVersionDimension() {
    VcsRootInstance root1 = buildVcsRootInstance();

    final String version1 = "12345";
    final String version2 = "98765";

    myFixture.addModification(modification().in(root1).by("user1").version(version1));
    myFixture.addModification(modification().in(root1).by("user1").version(version2).parentVersions(version1));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("version:(value:3,ignoreCase:false,matchType:contains)").myEntries;
    assertEquals(1, result.size());
    assertEquals(version1, result.get(0).getSVcsModification().getVersion());
  }

  @Test
  public void testVersionDimensionExactMatch() {
    VcsRootInstance root1 = buildVcsRootInstance();

    final String version1 = "12345";
    final String version2 = "98765";

    myFixture.addModification(modification().in(root1).by("user1").version(version1));
    myFixture.addModification(modification().in(root1).by("user1").version(version2).parentVersions(version1));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("version:" + version1).myEntries;
    assertEquals(1, result.size());
    assertEquals(version1, result.get(0).getSVcsModification().getVersion());
  }

  @Test
  public void testDuplicate() {
    final BuildTypeImpl buildConf1 = registerBuildType("buildConf1", "project");
    final BuildTypeImpl buildConf2 = registerBuildType("buildConf2", "project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf2);
    VcsRootInstance root1 = buildConf1.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf2.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null && root2 != null;

    final String version = "12345";

    myFixture.addModification(modification().in(root1).by("user1").version(version));
    myFixture.addModification(modification().in(root2).by("user1").version(version));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("unique:true").myEntries;
    assertEquals(1, result.size());

    List<SVcsModificationOrChangeDescriptor> resultWithDuplicate = myChangeFinder.getItems("count:10").myEntries;
    assertEquals( 2, resultWithDuplicate.size());
  }

  @Test
  public void testDuplicatesOverrideEachOther() {
    final BuildTypeEx buildConf1 = createProject("project1").createBuildType("buildConf1");
    final BuildTypeEx buildConf2 = createProject("project2").createBuildType("buildConf2");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", buildConf2);
    VcsRootInstance root1 = buildConf1.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = buildConf2.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null && root2 != null;

    SUser user1 = createUser("user1");
    final String version = "12345";
    SVcsModification m1 = myFixture.addModification(modification().in(root1).by("user1").version(version));
    SVcsModification m2 = myFixture.addModification(modification().in(root2).by("user1").version(version));

    // Include user dimension to the locator to ensure that we filter by project and not retrieve items by project.
    // Check both projects to ensure that we find what we want for any order we obtain modifications
    assertEquals(1, myChangeFinder.getItems("unique:true,user:user1,project:project1").myEntries.size());
    assertEquals(1, myChangeFinder.getItems("unique:true,user:user1,project:project2").myEntries.size());
  }

  @Test
  public void testPermissionsToViewCommit() throws Throwable {
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);

    SUser user1 = createUser("user1");

    ProjectEx project1 = createProject("project1");
    ProjectEx project2 = createProject("project2");
    BuildTypeEx bt1 = project1.createBuildType("bt1");
    BuildTypeEx bt2 = project2.createBuildType("bt2");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", bt1);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", bt2);
    VcsRootInstance root1 = bt1.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = bt2.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null && root2 != null;

    myFixture.addModification(modification().in(root1).by("user1").version("12345"));
    myFixture.addModification(modification().in(root2).by("user1").version("12345"));

    user1.addRole(RoleScope.projectScope(project2.getProjectId()), getTestRoles().getProjectDevRole());

    List<SVcsModificationOrChangeDescriptor> result = myFixture.getSecurityContext().runAs(
      user1,
      () -> myChangeFinder.getItems("username:user1").myEntries
    );
    assertEquals("Only one change is visible to the user.",1, result.size());
  }

  @Test
  public void testProjectDimension() throws Throwable {
    ProjectEx project = createProject("project1");
    BuildTypeEx parentBt = project.createBuildType("parentBt");

    ProjectEx subproject = project.createProject("subproject", "subproject");
    BuildTypeEx childBt = subproject.createBuildType("childBt");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", parentBt);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", childBt);
    VcsRootInstance root1 = parentBt.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = childBt.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null && root2 != null;

    SVcsModification modInParent = myFixture.addModification(modification().in(root1).version("12345"));
    SVcsModification modInChild = myFixture.addModification(modification().in(root2).version("12345"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("project:" + project.getExternalId()).myEntries;
    assertEquals("Only one change is in build configuration directly in parent project.", 1, result.size());
    assertEquals("Only change from parentBt should be visible.", modInParent.getId(), result.get(0).getSVcsModification().getId());
  }

  @Test
  public void testAffectedProjectDimension() throws Throwable {
    ProjectEx project = createProject("project1");
    BuildTypeEx parentBt = project.createBuildType("parentBt");

    ProjectEx subproject = project.createProject("subproject", "subproject");
    BuildTypeEx childBt = subproject.createBuildType("childBt");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", parentBt);
    SVcsRootEx parentRoot2 = myFixture.addVcsRoot(vcs.getName(), "", childBt);
    VcsRootInstance root1 = parentBt.getVcsRootInstanceForParent(parentRoot1);
    VcsRootInstance root2 = childBt.getVcsRootInstanceForParent(parentRoot2);
    assert root1 != null && root2 != null;

    SVcsModification modInParent = myFixture.addModification(modification().in(root1).version("12345"));
    SVcsModification modInChild = myFixture.addModification(modification().in(root2).version("12345"));

    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("affectedProject:" + project.getExternalId()).myEntries;
    assertEquals("Changes from all build configurations (direct and indirect) in parent project should be visible.", 2, result.size());
  }

  @TestFor(issues = "TW-76056")
  @Test
  public void testByUserChecksAllRoots() throws Throwable {
    SUser user1 = createUser("user1");
    ProjectEx project = createProject("project1");
    ProjectEx subproject = project.createProject("subproject", "subproject");
    BuildTypeEx childBt = subproject.createBuildType("childBt");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRoot root = project.createVcsRoot("vcs", "vcs_external_id", "vcs");
    childBt.addVcsRoot(root);
    childBt.setCheckoutRules(root, new CheckoutRules(""));
    childBt.persist();

    VcsRootInstance rootInstance = childBt.getVcsRootInstanceForParent(root);
    assert rootInstance != null;

    SVcsModification mod = myFixture.addModification(modification().in(root).by("user1").version("12345"));

    List<SVcsModificationOrChangeDescriptor> result;

    result = myChangeFinder.getItems("username:user1,project:" + subproject.getExternalId()).myEntries;
    assertEquals("Change from VcsRoot root in parent project should be visible.", 1, result.size());

    result = myChangeFinder.getItems("user:(id:" + user1.getId() + "),project:" + subproject.getExternalId()).myEntries;
    assertEquals("Change from VcsRoot root in parent project should be visible.", 1, result.size());
  }

  @Test
  public void changesFromDependenciesAreIncludedWhenFilteringByUsername() {
    createUser("user1");
    ProjectEx project = createProject("project1");
    BuildTypeEx btHead = project.createBuildType("btHead");
    BuildTypeEx btDep = project.createBuildType("btDep");
    addDependency(btHead, btDep);

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRoot root = project.createVcsRoot("vcs", "vcs_external_id", "vcs");
    btDep.addVcsRoot(root);
    btDep.setCheckoutRules(root, new CheckoutRules(""));
    btDep.persist();


    myFixture.addModification(modification().in(root).by("user1").version("12345"));


    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems("username:user1,buildType:" + btHead.getExternalId()+ ",pending:true,changesFromDependencies:true").myEntries;
    assertEquals("Change from VcsRoot in dependent buildType should be visible.", 1, result.size());
  }

  @Test
  public void changesFromDependenciesAreIncludedWhenFilteringByUser() {
    SUser user = createUser("user1");
    ProjectEx project = createProject("project1");
    BuildTypeEx btHead = project.createBuildType("btHead");
    BuildTypeEx btDep = project.createBuildType("btDep");
    addDependency(btHead, btDep);

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRoot root = project.createVcsRoot("vcs", "vcs_external_id", "vcs");
    btDep.addVcsRoot(root);
    btDep.setCheckoutRules(root, new CheckoutRules(""));
    btDep.persist();


    myFixture.addModification(modification().in(root).by("user1").version("12345"));


    String locator = String.format("user:(id:%d),buildType:%s,pending:true,changesFromDependencies:true", user.getId(), btHead.getExternalId());
    List<SVcsModificationOrChangeDescriptor> result = myChangeFinder.getItems(locator).myEntries;
    assertEquals("Change from VcsRoot in dependent buildType should be visible.", 1, result.size());
  }

  @NotNull
  private VcsRootInstance buildVcsRootInstance() {
    final BuildTypeImpl buildConf = registerBuildType("buildConf1", "project");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRootEx parentRoot1 = myFixture.addVcsRoot(vcs.getName(), "", buildConf);
    VcsRootInstance root1 = buildConf.getVcsRootInstanceForParent(parentRoot1);
    assert root1 != null;
    return root1;
  }

  private void check(final FileChange fileChangeToCheck, final String type, final String typeComment, final Boolean isDirectory, final String filePath, final String relativePath) {
    assertEquals(type, fileChangeToCheck.changeType);
    assertEquals(typeComment, fileChangeToCheck.changeTypeComment);
    assertEquals(isDirectory, fileChangeToCheck.directory);
    assertEquals(filePath, fileChangeToCheck.fileName);
    assertEquals(relativePath, fileChangeToCheck.relativeFileName);
  }

  private void check(String locator) {
    check(locator, new SVcsModificationOrChangeDescriptor[0]);
  }

  private void check(@Nullable final String locator,
                     @NotNull final SVcsModification... modifications) {
    SVcsModificationOrChangeDescriptor[] wrapped = new SVcsModificationOrChangeDescriptor[modifications.length];
    for(int i = 0; i < modifications.length; i++) {
      wrapped[i] = new SVcsModificationOrChangeDescriptor(modifications[i]);
    }

    check(locator, (m1, m2) -> m1.getSVcsModification().equals(m2.getSVcsModification()), wrapped);
  }

  private void checkWithMessage(@NotNull final String message,
                                @Nullable final String locator,
                                @NotNull final SVcsModification... modifications) {
    SVcsModificationOrChangeDescriptor[] wrapped = new SVcsModificationOrChangeDescriptor[modifications.length];
    for(int i = 0; i < modifications.length; i++) {
      wrapped[i] = new SVcsModificationOrChangeDescriptor(modifications[i]);
    }

    checkWithMessage(message, locator, (m1, m2) -> m1.getSVcsModification().equals(m2.getSVcsModification()), wrapped);
  }
}
