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

package jetbrains.buildServer.server.rest.request;

import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.change.SVcsModificationOrChangeDescriptor;
import jetbrains.buildServer.server.rest.model.change.FileChanges;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder.modification;


public class ChangeRequestTest extends BaseFinderTest<SVcsModificationOrChangeDescriptor> {
  private ChangeRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setFinder(myChangeFinder);
    myRequest = new ChangeRequest();
    BeanContext ctx = getBeanContext(myFixture);
    myRequest.initForTests(myFixture, ctx, myChangeFinder, myBuildTypeFinder, null, null);
  }

  @Test
  public void filteredFilesRespectCheckoutRules() {
    ProjectEx project = createProject("testproject");
    BuildTypeEx bt = project.createBuildType("bt");

    MockVcsSupport vcs = new MockVcsSupport("vcs");
    myFixture.getVcsManager().registerVcsSupport(vcs);
    SVcsRoot root = project.createVcsRoot("vcs", "vcs_external_id", "vcs");
    bt.addVcsRoot(root);
    bt.setCheckoutRules(root, new CheckoutRules("-:x"));
    bt.persist();

    SVcsModification mod = myFixture.addModification(modification().in(root).by("user1").version("12345").withChangedFiles("x", "y"));

    FileChanges result = myRequest.getFilteredFiles("id:" + mod.getId(), bt.getExternalId(), "count,file(file)");

    assertEquals("1 out of 2 files must be filtered via checkout rules", new Integer(1), result.count);
    assertEquals("File 'y' must not be filtered", "y", result.files.get(0).fileName);
  }
}
