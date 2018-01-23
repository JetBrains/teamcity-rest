/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.problem.Mutes;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.mute.ProblemMutingService;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 * Date: 22/01/2018
 */
public class MuteRequestTest extends BaseFinderTest<MuteInfo> {
  private MuteRequest myRequest;
  private ProblemMutingService myMutingService;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = new MuteRequest();
    myRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));

    myMutingService = myFixture.getSingletonService(ProblemMutingService.class);
  }

  @Test
  public void test_unmuteTest() {
    final ProjectEx project = createProject("Project", "Project");
    final BuildTypeEx bt1 = project.createBuildType("bt1");
    final BuildTypeEx bt2 = project.createBuildType("bt2");

    //just a child project
    final ProjectEx projectChild = project.createProject("Project2", "Project2");
    final BuildTypeEx bt21 = projectChild.createBuildType("bt21");

    createBuild(bt1, new String[0], new String[]{"test1", "test2", "test3"});
    createBuild(bt2, new String[0], new String[]{"test1", "test2", "test3", "test4"});
    createBuild(bt21, new String[0], new String[]{"test1", "test2", "test3", "test4"});

    final STest test1 = myFixture.getTestManager().createTest(new TestName("test1"), project.getProjectId());
    final STest test2 = myFixture.getTestManager().createTest(new TestName("test2"), project.getProjectId());

    SUser user = createUser("user");

    {
      Mutes mutes = myRequest.getMutes(null, null, null, null);
//      assertEquals(Integer.valueOf(0), mutes.count); //todo: fix
      assertEmpty(mutes.items);
    }

    myMutingService.muteTestsInBuildTypes(user, null, false, null, Arrays.asList(bt1, bt2), Arrays.asList(test1, test2), false);

    {
      Mutes mutes = myRequest.getMutes(null, null, null, null);
      assertEquals(Integer.valueOf(1), mutes.count);
      assertEquals(1, mutes.items.size());
      assertNull(mutes.items.get(0).scope.project);
      assertNotNull(mutes.items.get(0).scope.buildTypes);
      assertEquals(2, mutes.items.get(0).scope.buildTypes.buildTypes.size());
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt1.getExternalId().equals(buildType.getId())));
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt2.getExternalId().equals(buildType.getId())));

      assertNull(mutes.items.get(0).target.problems);
      assertEquals(2, mutes.items.get(0).target.tests.items.size());
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test1.getTestNameId()).equals(test.id)));
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test2.getTestNameId()).equals(test.id)));
    }

    myMutingService.unmuteTests(user, null, bt1, Arrays.asList(test1));

    {
      //this is the current behavior, but actually something should change, see TW-53393
      Mutes mutes = myRequest.getMutes(null, null, null, null);
      assertEquals(Integer.valueOf(1), mutes.count);
      assertEquals(1, mutes.items.size());
      assertNull(mutes.items.get(0).scope.project);
      assertNotNull(mutes.items.get(0).scope.buildTypes);
      assertEquals(2, mutes.items.get(0).scope.buildTypes.buildTypes.size());
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt1.getExternalId().equals(buildType.getId())));
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt2.getExternalId().equals(buildType.getId())));

      assertNull(mutes.items.get(0).target.problems);
      assertEquals(2, mutes.items.get(0).target.tests.items.size());
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test1.getTestNameId()).equals(test.id)));
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test2.getTestNameId()).equals(test.id)));
    }
    
    myMutingService.unmuteTests(user, null, bt2, Arrays.asList(test1));

    {
      Mutes mutes = myRequest.getMutes(null, null, null, null);
      assertEquals(Integer.valueOf(1), mutes.count);
      assertEquals(1, mutes.items.size());
      assertNull(mutes.items.get(0).scope.project);
      assertNotNull(mutes.items.get(0).scope.buildTypes);
      assertEquals(2, mutes.items.get(0).scope.buildTypes.buildTypes.size());
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt1.getExternalId().equals(buildType.getId())));
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt2.getExternalId().equals(buildType.getId())));

      assertNull(mutes.items.get(0).target.problems);
      assertEquals(1, mutes.items.get(0).target.tests.items.size());
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test2.getTestNameId()).equals(test.id)));
    }

    {
      Mutes mutes = myRequest.getMutes(null, "$long,mute(target(tests(test(mutes(mute(target(tests($long))))))))", null, null);
      assertEquals(1, mutes.items.get(0).target.tests.items.get(0).mutes.items.get(0).target.tests.items.size());
    }
  }

  @Test
  public void test_unmuteBuildType() {
    final ProjectEx project = createProject("Project", "Project");
    final BuildTypeEx bt1 = project.createBuildType("bt1");
    final BuildTypeEx bt2 = project.createBuildType("bt2");

    //just a child project
    final ProjectEx projectChild = project.createProject("Project2", "Project2");
    final BuildTypeEx bt21 = projectChild.createBuildType("bt21");

    createBuild(bt1, new String[0], new String[]{"test1", "test2", "test3"});
    createBuild(bt2, new String[0], new String[]{"test1", "test2", "test3", "test4"});
    createBuild(bt21, new String[0], new String[]{"test1", "test2", "test3", "test4"});

    final STest test1 = myFixture.getTestManager().createTest(new TestName("test1"), project.getProjectId());
    final STest test2 = myFixture.getTestManager().createTest(new TestName("test2"), project.getProjectId());

    SUser user = createUser("user");

    {
      Mutes mutes = myRequest.getMutes(null, null, null, null);
//      assertEquals(Integer.valueOf(0), mutes.count); //todo: fix
      assertEmpty(mutes.items);
    }

    myMutingService.muteTestsInBuildTypes(user, null, false, null, Arrays.asList(bt1, bt2), Arrays.asList(test1, test2), false);

    {
      Mutes mutes = myRequest.getMutes(null, null, null, null);
      assertEquals(Integer.valueOf(1), mutes.count);
      assertEquals(1, mutes.items.size());
      assertNull(mutes.items.get(0).scope.project);
      assertNotNull(mutes.items.get(0).scope.buildTypes);
      assertEquals(2, mutes.items.get(0).scope.buildTypes.buildTypes.size());
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt1.getExternalId().equals(buildType.getId())));
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt2.getExternalId().equals(buildType.getId())));

      assertNull(mutes.items.get(0).target.problems);
      assertEquals(2, mutes.items.get(0).target.tests.items.size());
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test1.getTestNameId()).equals(test.id)));
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test2.getTestNameId()).equals(test.id)));
    }

    myMutingService.unmuteTests(user, null, bt1, Arrays.asList(test1, test2));
    {
      Mutes mutes = myRequest.getMutes(null, null, null, null);
      assertEquals(Integer.valueOf(1), mutes.count);
      assertEquals(1, mutes.items.size());
      assertNull(mutes.items.get(0).scope.project);
      assertNotNull(mutes.items.get(0).scope.buildTypes);
      assertEquals(1, mutes.items.get(0).scope.buildTypes.buildTypes.size());
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt2.getExternalId().equals(buildType.getId())));

      assertNull(mutes.items.get(0).target.problems);
      assertEquals(2, mutes.items.get(0).target.tests.items.size());
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test1.getTestNameId()).equals(test.id)));
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test2.getTestNameId()).equals(test.id)));
    }

    {
      Mutes mutes = myRequest.getMutes(null, "$long,mute(target(tests(test(mutes(mute(scope(buildTypes($long))))))))", null, null);
      assertEquals(1, mutes.items.get(0).target.tests.items.get(0).mutes.items.get(0).scope.buildTypes.buildTypes.size());
    }
  }
  
  @Test
  public void test_severalMutes() {
    final ProjectEx project = createProject("Project", "Project");
    final BuildTypeEx bt1 = project.createBuildType("bt1");
    final BuildTypeEx bt2 = project.createBuildType("bt2");

    final ProjectEx project2 = createProject("Project2", "Project2");
    final BuildTypeEx bt22 = project.createBuildType("bt3");

    createBuild(bt1, new String[0], new String[]{"test1", "test2", "test3"});
    createBuild(bt22, new String[0], new String[]{"test1", "test2", "test3", "test4"});

    final STest test1 = myFixture.getTestManager().createTest(new TestName("test1"), project.getProjectId());
    final STest test2 = myFixture.getTestManager().createTest(new TestName("test2"), project.getProjectId());

    SUser user = createUser("user");

    myMutingService.muteTestsInBuildTypes(user, null, false, null, Arrays.asList(bt1, bt2), Arrays.asList(test1, test2), false);
    myMutingService.muteTestsInProject(user, null, false, null, project2, Arrays.asList(test1));

    int mute1Id, mute2Id;
    {
      Mutes mutes = myRequest.getMutes(null, null, null, null);
      assertEquals(Integer.valueOf(2), mutes.count);
      assertEquals(2, mutes.items.size());
      mute1Id = mutes.items.get(0).id;
      assertNull(mutes.items.get(0).scope.project);
      assertNotNull(mutes.items.get(0).scope.buildTypes);
      assertEquals(2, mutes.items.get(0).scope.buildTypes.buildTypes.size());
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt1.getExternalId().equals(buildType.getId())));
      assertTrue(mutes.items.get(0).scope.buildTypes.buildTypes.stream().anyMatch(buildType -> bt2.getExternalId().equals(buildType.getId())));

      assertNull(mutes.items.get(0).target.problems);
      assertEquals(2, mutes.items.get(0).target.tests.items.size());
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test1.getTestNameId()).equals(test.id)));
      assertTrue(mutes.items.get(0).target.tests.items.stream().anyMatch(test -> String.valueOf(test2.getTestNameId()).equals(test.id)));

      
      mute2Id = mutes.items.get(1).id;
      assertNull(mutes.items.get(1).scope.buildTypes);
      assertNotNull(mutes.items.get(1).scope.project);
      assertEquals(project2.getExternalId(), mutes.items.get(1).scope.project.id);

      assertNull(mutes.items.get(1).target.problems);
      assertEquals(1, mutes.items.get(1).target.tests.items.size());
      assertTrue(mutes.items.get(1).target.tests.items.stream().anyMatch(test -> String.valueOf(test1.getTestNameId()).equals(test.id)));
    }

    {
      Mutes mutes = myRequest.getMutes("id:" + mute2Id, "$long,mute(target(tests(test(mutes(mute(target(tests(test($long))),scope(buildTypes(buildType($long)))))))))", null, null);
      assertEquals(2, mutes.items.get(0).target.tests.items.get(0).mutes.items.size());
      assertEquals(2, mutes.items.get(0).target.tests.items.get(0).mutes.items.get(0).target.tests.items.size());
      assertEquals(2, mutes.items.get(0).target.tests.items.get(0).mutes.items.get(0).scope.buildTypes.buildTypes.size());
    }
  }
}
