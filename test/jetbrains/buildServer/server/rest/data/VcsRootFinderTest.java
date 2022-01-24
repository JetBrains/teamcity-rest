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

import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 29.07.13
 */
@Test
public class VcsRootFinderTest extends BaseFinderTest<SVcsRoot> {

  private SVcsRoot myRoot10;
  private SVcsRoot myRoot20;
  private SVcsRoot myRoot30;
  private SVcsRoot myRoot40;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    setFinder(myVcsRootFinder);

    myFixture.registerVcsSupport("svn");
    myFixture.registerVcsSupport("cvs");
    final ProjectEx rootProject = myProjectManager.getRootProject();
    myProject = rootProject.createProject("project1", "Project name");
    myRoot10 = rootProject.createVcsRoot("svn", "id1", "VCS root 1 name");
    myRoot20 = myProject.createVcsRoot("svn", "id2", "VCS root 2 name");
    myRoot30 = myProject.createVcsRoot("cvs", "id3", "VCS root 3 name");
    myRoot40 = rootProject.createVcsRoot("svn", "id4", "VCS root 4 name");
  }


  @Test
  public void testNoLocator() throws Exception {
    check(null, myRoot10, myRoot20, myRoot30, myRoot40);
  }

  @Test
  public void testNoDimensionLocator() throws Exception {
    check("id2", myRoot20);
    check("id_2");
  }

  @Test
  public void testIdsLocator() throws Exception {
    check("id:id2", myRoot20);
    check("id:id 2");
  }

  @Test
  public void testInternalIdsLocator() throws Exception {
    check("internalId:" + myRoot20.getId(), myRoot20);
    check("internalId:" + myRoot20.getId() + 10);
  }

  @Test
  public void testNameLocator() throws Exception {
    check("name:VCS root 2 name", myRoot20);
    check("name:VCS root 2 name1");
  }

  @Test
  public void testTypeLocator() throws Exception {
    myFixture.registerVcsSupport("customType");
    myRoot20 = myProject.createVcsRoot("customType", "custom_id1", "VCS root custom 1 name");

    check("type:customType", myRoot20);
    check("type:custom Type");
  }

  @Test
  public void testProjectLocator() throws Exception {
    check("project:(id:project1),type:svn", myRoot20);
    checkExceptionOnItemsSearch(NotFoundException.class, "project:(id:project_missing)");
  }

  @Test
  public void testPropertyLocator() throws Exception {
    myRoot20.setProperties(CollectionsUtil.asMap("aaa", "bbb"));
    check("project:(id:project1),property:(name:aaa)", myRoot20);
    checkExceptionOnItemsSearch(NotFoundException.class, "project:(id:project_missing)");
  }
}
