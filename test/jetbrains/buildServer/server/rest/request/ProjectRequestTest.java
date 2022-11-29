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

import com.intellij.openapi.util.Pair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.build.Branch;
import jetbrains.buildServer.server.rest.model.build.Branches;
import jetbrains.buildServer.server.rest.model.project.Projects;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.ProjectFeatureDescriptorFactory;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Option;
import jetbrains.buildServer.vcs.OperationRequestor;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.util.ResourceUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.server.rest.request.ProjectRequestTest.PredicateMatcher.predicate;
import static jetbrains.buildServer.util.Util.map;
import static jetbrains.buildServer.vcs.RepositoryStateData.createVersionState;

/**
 * @author Yegor.Yarko
 * Date: 22/07/2016
 */
public class ProjectRequestTest extends BaseFinderTest<SProject> {
  private ProjectRequest myRequest;
  private BeanContext myBeanContext;
  private ServerSshKeyManager myServerSshKeyManagerMock;
  private ConfigActionFactory myConfigActionFactoryMock;
  private DataProvider myDataProviderMock;
  private ApiUrlBuilder myApiUrlBuilderMock;
  private ServiceLocator myServiceLocator;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myBeanContext = getBeanContext(myServer);
    myServerSshKeyManagerMock = Mockito.mock(ServerSshKeyManager.class);
    myConfigActionFactoryMock = Mockito.mock(ConfigActionFactory.class);
    myDataProviderMock = Mockito.mock(DataProvider.class);
    myApiUrlBuilderMock = Mockito.mock(ApiUrlBuilder.class);
    myRequest = createProjectRequest();
  }

  @Test
  void testProjectFeaturesParameters() {
    ProjectEx project10 = createProject("project10", "project10");
    SProjectFeatureDescriptor feature10 =
      myFixture.getSingletonService(ProjectFeatureDescriptorFactory.class).createNewProjectFeature("feature_type", CollectionsUtil.asMap("a", "b"), project10.getProjectId());
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
      assertEquals(null, project10.findFeatureById(feature10.getId()).getParameters().get("b"));
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

    final ProjectRequest request = myRequest;

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

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,default:true", null);
    assertBranchesEquals(branches.branches,
                         "master", true, null);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,default:false", null);
    assertBranchesEquals(branches.branches,
                         "branch10", null, null,
                         "branch20", null, null,
                         "branch30", null, null);


    setCurrentBranches(bt2, collectChangesPolicy, "master2", map("branch10", "2", "master2", "4", "branch20", "3"));

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null,
                         "branch10", null, null,
                         "branch20", null, null,
                         "branch30", null, null);

    //no default branch option test
    bt1.setOption(Option.fromKey("branchFilter"), "+:*\n-:<default>");
    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES", null);
    assertBranchesEquals(branches.branches,
                         "master2", true, null,
                         "branch10", null, null,
                         "branch20", null, null,
                         "branch30", null, null);
    bt2.setOption(Option.fromKey("branchFilter"), "+:*\n-:<default>");
    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES", null);
    assertBranchesEquals(branches.branches,
                         "branch10", null, null,
                         "branch20", null, null,
                         "branch30", null, null);
    //revert
    bt1.setOption(Option.fromKey("branchFilter"), "+:*");
    bt2.setOption(Option.fromKey("branchFilter"), "+:*");


    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,default:true", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null);

    setCurrentBranches(bt2, collectChangesPolicy, "master2", map("master", "1", "branch10", "2", "master2", "4", "branch20", "3"));

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null,
                         "branch10", null, null,
                         "branch20", null, null,
                         "branch30", null, null,
                         "master", null, null);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,default:true", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,default:false", null);
    assertBranchesEquals(branches.branches,
                         "branch10", null, null,
                         "branch20", null, null,
                         "branch30", null, null,
                         "master", null, null);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,buildType:(id:" + bt1.getExternalId() + ")", null);
    assertBranchesEquals(branches.branches,
                         "master", true, null,
                         "branch20", null, null,
                         "branch30", null, null);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,buildType:(id:" + bt2.getExternalId() + ")", null);
    assertBranchesEquals(branches.branches,
                         "master2", true, null,
                         "branch10", null, null,
                         "branch20", null, null,
                         "master", null, null);


    //subproject
    ProjectEx project1_1 = project1.createProject("Project1_1", "Project test 1.1");
    final BuildTypeEx bt11 = project1_1.createBuildType("testBT1_1", "My test build type 1");
    final BuildTypeEx bt12 = project1_1.createBuildType("testBT1_2", "My test build type 2");
    final BuildTypeEx bt13 = project1_1.createBuildType("testBT1_3", "My test build type 3");

    setCurrentBranches(bt11, collectChangesPolicy, "master", map("master", "1", "branch30", "3", "branch120", "2"));
    setCurrentBranches(bt12, collectChangesPolicy, "master", map("master", "1", "branch10", "2", "master2", "4", "branch120", "3"));
    setCurrentBranches(bt13, collectChangesPolicy, "master", map("master", "1", "branch10", "2", "master2", "4", "branch120", "3"));

    bt13.addVcsRoot(bt13.getProject().createVcsRoot("vcs", "extId13_2", "name13_2"));

    final VcsRootInstance vcsRootInstance2 = bt13.getVcsRootInstances().get(1);
    collectChangesPolicy.setCurrentState(vcsRootInstance2, createVersionState("master3", map("branch10", "2", "master3", "2", "branch2", "3")));
    setBranchSpec(vcsRootInstance2, "+:*");
    myFixture.getVcsModificationChecker().checkForModifications(bt13.getVcsRootInstances(), OperationRequestor.UNKNOWN);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null,
                         "branch10", null, null,
                         "branch20", null, null,
                         "branch30", null, null,
                         "master", null, null);

    branches = request.getBranches("id:" + project1_1.getExternalId(), "policy:ALL_BRANCHES", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null,
                         "branch10", null, null,
                         "branch120", null, null,
                         "branch2", null, null,
                         "branch30", null, null,
                         "master2", null, null);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,buildType:(affectedProject:(id:" + prjId + "))", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null,
                         "branch10", null, null,
                         "branch120", null, null,
                         "branch2", null, null,
                         "branch20", null, null,
                         "branch30", null, null,
                         "master", null, null,
                         "master2", null, null);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,buildType:(affectedProject:(id:" + prjId + "))", Fields.ALL_NESTED.getFieldsSpec());
    assertBranchesEquals(branches.branches,
                         "<default>", true, false,
                         "branch10", false, false,
                         "branch120", false, false,
                         "branch2", false, false,
                         "branch20", false, false,
                         "branch30", false, false,
                         "master", false, false,
                         "master2", false, false);

    branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES,buildType:(id:" + bt13.getExternalId() + ")", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null,
                         "branch10", null, null,
                         "branch120", null, null,
                         "branch2", null, null,
                         "master2", null, null);

    //not quite valid, but works for now
    branches = request.getBranches("id:" + project1_1.getExternalId(), "policy:ALL_BRANCHES,buildType:(id:" + bt1Id + ")", null);
    assertBranchesEquals(branches.branches,
                         "master", true, null,
                         "branch20", null, null,
                         "branch30", null, null);
  }

  @Test
  public void testBranchesDiffInCaseOnly() {
    String prjId = "Project1";
    ProjectEx project1 = getRootProject().createProject(prjId, "Project test 1");
    String bt1Id = "testBT1";
    String bt2Id = "testBT2";
    final BuildTypeEx bt1 = project1.createBuildType(bt1Id, "My test build type 1");
    final BuildTypeEx bt2 = project1.createBuildType(bt2Id, "My test build type 2");

    final ProjectRequest request = myRequest;

    MockVcsSupport vcs = vcsSupport().withName("vcs").dagBased(true).register();

    BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy = new BuildFinderTestBase.MockCollectRepositoryChangesPolicy();
    vcs.setCollectChangesPolicy(collectChangesPolicy);

    setCurrentBranches(bt1, collectChangesPolicy, "master", map("master", "1", "aaa", "2", "bbb", "3", "Aaa", "3"));
    setCurrentBranches(bt2, collectChangesPolicy, "Master", map("Master", "1", "bBb", "2", "ccc", "3"));


    Branches branches = request.getBranches("id:" + prjId, "policy:ALL_BRANCHES", null);
    assertBranchesEquals(branches.branches,
                         "<default>", true, null,
                         "Aaa", null, null,
                         "aaa", null, null,
                         "bBb", null, null,
                         "bbb", null, null,
                         "ccc", null, null);
  }

  // TODO @vshefer
  @Test
  public void testAddSshKey() throws IOException {
    String prjId = "Project1";
    getRootProject().createProject(prjId, "Project test 1");

    Path keyFilePath = ResourceUtils.getFile("classpath:rest/sshKeys/id_rsa").toPath();
    HttpServletRequest mockRequest = createRequest(keyFilePath, MediaType.TEXT_PLAIN);

    myRequest.addSshKey("id:" + prjId, "testprivatekey", mockRequest);

    /*
    Mockito.verify(myServerSshKeyManagerMock).addKey(
      Mockito.argThat(predicate(it -> it.getExternalId().equals(prjId))),
      Mockito.same("testprivatekey"),
      AdditionalMatchers.aryEq(Files.readAllBytes(keyFilePath)),
      Mockito.isNull(ConfigAction.class)
    );
     */
  }

  @Test
  public void testAddSshKey_empty() {
    String prjId = "Project1";
    getRootProject().createProject(prjId, "Project test 1");

    // TODO @vshefer
    //HttpServletRequest mockRequest = new MockHttpServletRequest();
    HttpServletRequest mockRequest = null;

    //assertExceptionThrown(() -> myRequest.addSshKey("id:" + prjId, "testprivatekey", mockRequest), BadRequestException.class);
  }

  @NotNull
  private static HttpServletRequest createRequest(
    Path bodyPath,
    String contentType
  ) throws IOException {
    // TODO @vshefer
    //HttpServletRequest mockHttpServletRequest = new FakeHttpServletRequest();

    //mockHttpServletRequest.setContent(Files.readAllBytes(bodyPath));
    //mockHttpServletRequest.setContentType(contentType);
    //return mockHttpServletRequest;
    return null;
  }

  static class PredicateMatcher<T> extends ArgumentMatcher<T> {


    private final Predicate<T> predicate;

    PredicateMatcher(Predicate<T> predicate) {
      this.predicate = predicate;
    }

    @Override
    public boolean matches(Object argument) {
      //noinspection unchecked
      return predicate.test((T)argument);
    }

    public static <T1> PredicateMatcher<T1> predicate(Predicate<T1> predicate) {
      return new PredicateMatcher<>(predicate);
    }

  }

  //@Test
  public void memoryTest() throws InterruptedException {
    final ProjectRequest request = myRequest;

    final String locator = "archived:false,affectedProject:_Root";
    final String fields =
      "count,project(id,internalId,name,parentProjectId,archived,readOnlyUI,buildTypes(buildType(id,paused,internalId,projectId,name,type,description)),description)";

    Queue<Pair<Integer, ProjectEx>> q = new ArrayDeque<>();
    q.add(new Pair<>(0, myProject));
    final int max = 2;
    final int children = 4;

    int counter = 0;
    while (!q.isEmpty()) {
      Pair<Integer, ProjectEx> p = q.poll();

      String prefix = "Bt" + StringUtils.repeat('a', 10 * p.getFirst());
      for (int i = 0; i < children; i++) {
        ProjectEx c = myFixture.createProject("z-" + p.first + "-" + counter++, p.second);
        if (p.first < max) {
          q.add(new Pair<>(p.first + 1, c));
        }

        for (int j = 0; j < children * 4; j++) {
          c.createBuildType(prefix + j);
        }
      }
    }

    System.out.println(counter + " projects created.");

    Thread[] ts = new Thread[100];
    for (int i = 0; i < ts.length; i++) {
      final int threadIdx = i;
      ts[i] = new Thread(() -> {
        new RestContext(z -> null).run(() -> {
          for (int j = 0; j < 100; j++) {
            final PagedSearchResult<SProject> result = myProjectFinder.getItems(locator);
            Projects projects = new Projects(result.myEntries, null, new Fields(fields), myBeanContext);

            projects.projects.stream()
                             .flatMap(p -> p.buildTypes.buildTypes.stream())
                             .forEach(bt -> {
                               bt.getId();
                               bt.isPaused();
                               bt.getInternalId();
                               bt.getProjectId();
                               bt.getName();
                               bt.getType();
                               bt.getDescription();
                             });
            System.out.println(String.format("Finished %d requests in thread %d", j, threadIdx));
          }
          return null;
        });
      });
      ts[i].start();
    }

    for (Thread t : ts) t.join();
  }

  @NotNull
  private ProjectRequest createProjectRequest() {
    return new ProjectRequest(myBeanContext, myServiceLocator, myDataProviderMock, myBuildFinder, myBuildTypeFinder, myProjectFinder, myAgentPoolFinder, myBranchFinder,
                              myApiUrlBuilderMock, myPermissionChecker, myConfigActionFactoryMock, myServerSshKeyManagerMock);
  }

  private void setCurrentBranches(final BuildTypeEx bt,
                                  final BuildFinderTestBase.MockCollectRepositoryChangesPolicy collectChangesPolicy,
                                  final String defaultBranchName, final Map<String, String> state) {
    if (bt.getVcsRoots().isEmpty()) {
      final SVcsRoot vcsRoot = bt.getProject().createVcsRoot("vcs", "extId_for_" + bt.getId(), "name_for_" + bt.getId());
      bt.addVcsRoot(vcsRoot);
    }

    final VcsRootInstance vcsRootInstance = bt.getVcsRootInstances().get(0);
    collectChangesPolicy.setCurrentState(vcsRootInstance, createVersionState(defaultBranchName, state));
    setBranchSpec(vcsRootInstance, "+:*");
    myFixture.getVcsModificationChecker().checkForModifications(bt.getVcsRootInstances(), OperationRequestor.UNKNOWN);
  }

  private static final DescriptionProvider<Branch> BRANCH_DESCRIPTION_PROVIDER =
    b -> b.getName() + "/" + b.isDefault() + "/" + b.isUnspecified();

  public static void assertBranchesEquals(final List<Branch> branches, Object... values) {
    String actualBranches = getDescription(branches, BRANCH_DESCRIPTION_PROVIDER);
    if (branches == null) {
      if (values.length == 0) return;
      fail("Less branches are returned than expected: " + actualBranches);
    }
    Iterator<Branch> branchIt = branches.iterator();
    int i = 0;
    for (Iterator it = Arrays.asList(values).iterator(); it.hasNext(); ) {
      if (!branchIt.hasNext()) {
        fail("Less branches are returned than expected: " + actualBranches);
      }
      Branch branch = branchIt.next();
      assertEquals("Name does not match for branch #" + i + ": " + actualBranches, (String)it.next(), branch.getName());
      assertEquals("isDefault does not match for branch " + branch.getName() + ": " + actualBranches, (Boolean)it.next(), branch.isDefault());
      assertEquals("isUnspecified does not match for branch " + branch.getName() + ": " + actualBranches, (Boolean)it.next(), branch.isUnspecified());
      i++;
    }
    assertFalse("More branches are returned than expected: " + actualBranches, branchIt.hasNext());
  }
}
