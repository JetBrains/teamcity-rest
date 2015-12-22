/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.PathTransformer;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.buildType.BuildType;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 20/10/2015
 */
public class BuildTypeTest extends BaseFinderTest<BuildTypeOrTemplate> {

  private BeanContext myBeanContext;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    final ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(new PathTransformer() {
      public String transform(final String path) {
        return path;
      }
    });
    final BeanFactory beanFactory = new BeanFactory(null);

    myBeanContext = new BeanContext(beanFactory, myServer, apiUrlBuilder);
  }

  @Test
  public void testSimple() {
    final BuildTypeEx bt = getRootProject().createProject("Project1", "Project test 1").createBuildType("testBT", "My test build type");
    final BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt), Fields.LONG, myBeanContext);
    assertEquals(bt.getName(), buildType.getName());
    assertEquals(bt.getProjectExternalId(), buildType.getProjectId());
    assertEquals(bt.getProjectName(), buildType.getProjectName());
    assertEquals(new Integer(0), buildType.getParameters().count);

    final Investigations investigations = buildType.getInvestigations();
    assertEquals(null, investigations.count);
    assertEquals("/app/rest/investigations?locator=buildType:(id:testBT)", investigations.href);
  }

  @Test
  public void testInvestigations() {
    final SUser user = createUser("user");
    final BuildTypeEx bt = getRootProject().createProject("Project1", "Project test 1").createBuildType("testBT", "My test build type");
    myFixture.getResponsibilityFacadeEx().setBuildTypeResponsibility(bt, createRespEntry(ResponsibilityEntry.State.TAKEN, user));
    BuildType buildType = new BuildType(new BuildTypeOrTemplate(bt), Fields.LONG, myBeanContext);

    Investigations investigations = buildType.getInvestigations();
    assertEquals(null, investigations.count);
    assertEquals("/app/rest/investigations?locator=buildType:(id:testBT)", investigations.href);
    assertEquals(null, investigations.items);

    buildType = new BuildType(new BuildTypeOrTemplate(bt), new Fields("investigations($long)"), myBeanContext);
    investigations = buildType.getInvestigations();

    assertEquals(new Integer(1), investigations.count);
    assertEquals("/app/rest/investigations?locator=buildType:(id:testBT)", investigations.href);
    assertEquals(1, investigations.items.size());
    assertEquals(ResponsibilityEntry.State.TAKEN.name(), investigations.items.get(0).state);

    buildType = new BuildType(new BuildTypeOrTemplate(bt), new Fields("investigations($long,$locator(assignee(id:" + user.getId() + ")))"), myBeanContext);
    investigations = buildType.getInvestigations();

    assertEquals(new Integer(1), investigations.count);
    assertEquals("/app/rest/investigations?locator=assignee:(id:" + user.getId() + "),buildType:(id:testBT)", investigations.href);
    assertEquals(1, investigations.items.size());
    assertEquals(ResponsibilityEntry.State.TAKEN.name(), investigations.items.get(0).state);

    final SUser user2 = createUser("user2");
    buildType = new BuildType(new BuildTypeOrTemplate(bt), new Fields("investigations($long,count,$locator(assignee(id:" + user2.getId() + ")))"), myBeanContext);
    investigations = buildType.getInvestigations();

    assertEquals(new Integer(0), investigations.count);
    assertEquals("/app/rest/investigations?locator=assignee:(id:" + user2.getId() + "),buildType:(id:testBT)", investigations.href);
    assertNull(investigations.items);
  }
}
