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

package jetbrains.buildServer.server.rest.data;

import java.util.Date;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.FileChange;
import jetbrains.buildServer.server.rest.model.change.FileChanges;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.MockVcsModification;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.util.Util;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;

/**
 * @author Yegor.Yarko
 *         Date: 25/01/2016
 */
public class ChangeFinderTest extends BaseFinderTest<SVcsModification> {

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

    buildConf.forceCheckingForChanges();
    myFixture.getVcsModificationChecker().ensureModificationChecksComplete();

    check(null, m70, m60, m50, m40, m30, m20);
    String btLocator = "buildType:(id:" + buildConf.getExternalId() + ")";
    check(btLocator, m70, m60, m50, m40, m30, m20);
    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(aaa:bbb)");
    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(default:true)"); //no buildType is not supported
    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(name:branch1)");
    checkExceptionOnItemsSearch(BadRequestException.class, btLocator + ",branch:(name:master,aaa:bbb)");
    check(btLocator + ",branch:(name:master,default:false)");  //no branches match here
    check(btLocator + ",branch:(name:master)", m30, m20);
    check(btLocator + ",branch:(name:<default>)", m30, m20);
    checkExceptionOnItemsSearch(BadRequestException.class, "branch:(branch1)");
    check(btLocator + ",branch:(master)", m30, m20);
    check(btLocator + ",branch:(<default>)", m30, m20);
    check(btLocator + ",branch:(name:aaa)", m70);
    check(btLocator + ",branch:(aaa)", m70);
    check(btLocator + ",branch:(name:<any>)", m70, m60, m50, m40, m30, m20);
    check(btLocator + ",branch:(default:any)", m70, m60, m50, m40, m30, m20);
    check(btLocator + ",branch:(<any>)", m70, m60, m50, m40, m30, m20);
    check(btLocator + ",branch:(name:bbb)");
    check(btLocator + ",branch:(prefix/aaa)");
    check(btLocator + ",branch:(name:branch1)", m40);

    check(btLocator + ",branch:(default:true)", m30, m20);
    check(btLocator + ",branch:(default:false)", m70, m40, m50, m40, m60);

    check(btLocator + ",branch:(default:false),unique:true", m70, m40, m50, m60);

    //test pending

    check(btLocator + ",branch:(name:master),pending:true", m30, m20);
    check(btLocator + ",branch:(name:<default>),pending:true", m30, m20);
    check(btLocator + ",branch:(name:branch1),pending:true", m40);
    check(btLocator + ",branch:(name:master),pending:false");
    check(btLocator + ",branch:(name:<default>),pending:false");
    check(btLocator + ",branch:(name:branch1),pending:false");
    check(btLocator + ",branch:(name:branch1),pending:any", m40);

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

    buildConf.forceCheckingForChanges();
    myFixture.getVcsModificationChecker().ensureModificationChecksComplete();

    check(null,  m90, m80, m70, m60, m50, m40, m30, m20);
    check(btLocator + ",branch:(name:master)", m80, m30, m20);
    check(btLocator + ",branch:(name:<default>)", m80, m30, m20);
    check(btLocator + ",branch:(name:branch1)", m90, m40);

    check(btLocator + ",branch:(name:master),pending:true", m80);
    check(btLocator + ",branch:(name:<default>),pending:true", m80);
    check(btLocator + ",branch:(name:branch1),pending:true", m90);
    check(btLocator + ",branch:(name:master),pending:false", m30, m20);
    check(btLocator + ",branch:(name:<default>),pending:false", m30, m20);
    check(btLocator + ",branch:(name:branch1),pending:false", m40);
    check(btLocator + ",branch:(name:branch1),pending:any", m90, m40);
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

  private void check(final FileChange fileChangeToCheck, final String type, final String typeComment, final Boolean isDirectory, final String filePath, final String relativePath) {
    assertEquals(type, fileChangeToCheck.changeType);
    assertEquals(typeComment, fileChangeToCheck.changeTypeComment);
    assertEquals(isDirectory, fileChangeToCheck.directory);
    assertEquals(filePath, fileChangeToCheck.fileName);
    assertEquals(relativePath, fileChangeToCheck.relativeFileName);
  }
}
