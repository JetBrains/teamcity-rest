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

package jetbrains.buildServer.server.rest.request;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.BuildFinderTestBase;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Branch;
import jetbrains.buildServer.server.rest.model.build.Branches;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.ProjectFeatureDescriptorFactory;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;

/**
 * @author Yegor.Yarko
 *         Date: 22/07/2016
 */
public class ProjectRequestTest extends BaseFinderTest<SProject> {
  private ProjectRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = ProjectRequest.createForTests(BaseFinderTest.getBeanContext(myFixture));
  }

  @Test
  void testProjectFeaturesParameters() {
    ProjectEx project10 = createProject("project10", "project10");
    SProjectFeatureDescriptor feature10 =
      myFixture.getSingletonService(ProjectFeatureDescriptorFactory.class).createNewProjectFeature("feature_type", CollectionsUtil.asMap("a", "b"), project10);
    project10.addFeature(feature10);
    {
      String newValue = myRequest.getFeatures("id:" + project10.getExternalId()).getParametersSubResource(feature10.getId(), "$long").setParameterValue("a", "B");
      assertEquals("B", newValue);
      assertEquals(1, project10.getAvailableFeatures().size());
      assertEquals("B", project10.findFeatureById(feature10.getId()).getParameters().get("a"));
    }
    {
      String newValue = myRequest.getFeatures("id:" + project10.getExternalId()).getParametersSubResource("id:" + feature10.getId(), "$long").setParameterValue("a", "X");
      assertEquals("X", newValue);
      assertEquals(1, project10.getAvailableFeatures().size());
      assertEquals("X", project10.findFeatureById(feature10.getId()).getParameters().get("a"));
      assertEquals(1, project10.findFeatureById(feature10.getId()).getParameters().size());
    }
    {
      String newValue = myRequest.getFeatures("id:" + project10.getExternalId()).getParametersSubResource("id:" + feature10.getId(), "$long").setParameterValue("b", "Y");
      assertEquals("Y", newValue);
      assertEquals(1, project10.getAvailableFeatures().size());
      assertEquals(2, project10.findFeatureById(feature10.getId()).getParameters().size());
      assertEquals("Y", project10.findFeatureById(feature10.getId()).getParameters().get("b"));
    }
    {
      myRequest.getFeatures("id:" + project10.getExternalId()).getParametersSubResource("id:" + feature10.getId(), "$long").deleteParameter("b");
      assertEquals(1, project10.getAvailableFeatures().size());
      assertEquals(1, project10.findFeatureById(feature10.getId()).getParameters().size());
      assertEquals(null , project10.findFeatureById(feature10.getId()).getParameters().get("b"));
      assertEquals("X", project10.findFeatureById(feature10.getId()).getParameters().get("a"));
    }
  }

  @Test
  public void testBranches() {
    String prjId = "Project1";
    ProjectEx project1 = getRootProject().createProject(prjId, "Project test 1");
    String bt1Id = "testBT1";
    String bt2Id = "testBT2";
    final BuildTypeEx bt1 = project1.createBuildType(bt1Id, "My test build type 1");
    final BuildTypeEx bt2 = project1.createBuildType(bt2Id, "My test build type 2");

    final ProjectRequest request = new ProjectRequest();
    request.setInTests(myProjectFinder, myBranchFinder);

    Branches branches = request.getBranches("id:" + prjId, null, null);
    assertBranchesEquals(branches.branches, "<default>", true, null);

    branches = request.getBranches("id:" + prjId, null, Fields.ALL_NESTED.getFieldsSpec());
    assertBranchesEquals(branches.branches, "<default>", true, false);


    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    setCurrentBranches(bt1, collectChangesPolicy, "master", map("master", "1", "branch20", "2", "branch30", "3"));
    setCurrentBranches(bt2, collectChangesPolicy, "master", map("master", "1", "branch10", "2", "branch20", "3"));


    branches = request.getBranches("id:" + prjId, null, null);
    assertBranchesEquals(branches.branches, "master", true, null);  //active branches by default

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES", null);
    assertBranchesEquals(branches.branches,
                         "master", true, null,
                         "branch10", null, null,
                         "branch20", null, null,
                         "branch30", null, null);
    //todo add subproject
    //todo add different default branches
    //todo add default branch which maos to <default>
    //todo add filtering by build types
    //todo: add test for branches from builds
    //todo: add test for "default:true" locator and several defaults in different branches
    //todo: add test for "changesFromDependencies:true" locator and several defaults in different branches
  }

  private void setCurrentBranches(final BuildTypeEx bt,
                                  final BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy,
                                  final String defaultBranchName, final Map<String, String> state) {
    final SVcsRoot vcsRoot = bt.getProject().createVcsRoot("vcs", "extId_for_" + bt.getId(), "name_for_" + bt.getId());
    bt.addVcsRoot(vcsRoot);

    final VcsRootInstance vcsRootInstance = bt.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState(defaultBranchName, state));
    setBranchSpec(vcsRootInstance, "+:*");
    bt.forceCheckingForChanges();
    myFixture.getVcsModificationChecker().ensureModificationChecksComplete();
  }

  private static final DescriptionProvider<Branch> BRANCH_DESCRIPTION_PROVIDER =
    b -> b.getName() + "/" + b.isDefault() + "/" + b.isUnspecified();

  private void assertBranchesEquals(final List<Branch> branches, Object... values) {
    String actualBranches = getDescription(branches, BRANCH_DESCRIPTION_PROVIDER);
    Iterator<Branch> branchIt = branches.iterator();
    int i = 0;
    for (Iterator it = Arrays.asList(values).iterator(); it.hasNext(); ) {
      if (!branchIt.hasNext()) {
        fail("Less branches are returned than expected: " + actualBranches);
      }
      Branch branch = branchIt.next();
      assertEquals("Name does not match for branch #" + i +": "+ actualBranches, (String)it.next(), branch.getName());
      assertEquals("isDefault does not match for branch " +  branch.getName() +": "+ actualBranches, (Boolean)it.next(), branch.isDefault());
      assertEquals("isUnspecified does not match for branch " +  branch.getName() +": "+ actualBranches, (Boolean)it.next(), branch.isUnspecified());
      i++;
    }
    assertFalse("More branches are returned than expected: " + actualBranches, branchIt.hasNext());
  }
}
