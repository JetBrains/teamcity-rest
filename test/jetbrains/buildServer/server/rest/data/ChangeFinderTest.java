/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.change.Change;
import jetbrains.buildServer.server.rest.model.change.FileChange;
import jetbrains.buildServer.server.rest.model.change.FileChanges;
import jetbrains.buildServer.serverSide.impl.MockVcsModification;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsChange;
import jetbrains.buildServer.vcs.VcsChangeInfo;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 25/01/2016
 */
public class ChangeFinderTest extends BaseFinderTest<SVcsModification> {



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
