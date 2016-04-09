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

import java.util.Arrays;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2014
 */
public class ProjectFinderTest extends BaseFinderTest<SProject> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myProject.remove();

    setFinder(myProjectFinder);
  }

  @Test
  public void testSingleDimensions() throws Exception {
    final SProject project10 = createProject("p1");
    final SProject project20 = createProject("p2");
    final SProject project10_10 = project10.createProject("p10_10", "p1_child1");
    final SProject project10_20 = project10.createProject("p10_20", "xxx");
    final SProject project10_10_10 = project10_10.createProject("p10_10_10", "xxx");
    final SProject project30 = createProject(project10.getProjectId(), "p3");

    check(project20.getProjectId(), project20);
    check(project20.getExternalId(), project20);
    check(project20.getName(), project20);

    check("id:" + project10.getExternalId(), project10);
    check("id:" + project10.getExternalId().toUpperCase(), project10);
    check("internalId:" + project10.getProjectId(), project10);
    check("id:" + project30.getExternalId(), project30);
    check("id:XXXX");
    check(project30.getExternalId(), project30);
    check("name:(" + project10.getName() + ")", project10);
    check("name:(" + project10.getName().toUpperCase() + ")");
    check("uuid:(" + project10.getConfigId() + ")", project10);
    check("name:(" + project10_10.getName() + ")", project10_10);
    check("name:(" + project10_10.getName().toUpperCase() + ")");
    check("name:(" + "xxx" + ")", project10_10_10, project10_20);

    check(project10.getExternalId(), project10);
    check(project10.getName(), project10);
    check("No_match");
  }

  @Test
  public void testProjectDimensions() throws Exception {
    final SProject project10 = createProject("p1");
    final SProject project20 = createProject("p2");
    final SProject project20_10 = project20.createProject("p2", "xxx");
    final SProject project10_10 = project10.createProject("p10_10", "p1_child1");
    final SProject project10_20 = project10.createProject("p10_20", "xxx");
    project10_20.setArchived(true, null);
    final SProject project10_10_10 = project10_10.createProject("p10_10_10", "xxx");
    final SProject project10_10_10_10 = project10_10_10.createProject("p10_10_10_10", "xxx");
    final SProject project10_10_20 = project10_10.createProject("p10_10_20", "p1_child2_child2");
    project10_10_20.setArchived(true, null);
    final SProject project10_10_20_10 = project10_10_20.createProject("p10_10_20_10", "p1_child2_child2_child1");
    final SProject project30 = createProject("p30", "p3");
    final SProject project30_10 = project30.createProject("p30_10", "p3_p1");
    final SProject project30_10_10 = project30_10.createProject("p30_10_10", "xxx");
    final SProject project40 = createProject(project10.getProjectId(), "p4");

    //sequence is used as is, documenting the current behavior
    check(null, myProjectManager.getRootProject(), project10, project10_10, project10_10_10, project10_10_10_10, project10_10_20_10, project20, project20_10, project30, project30_10, project30_10_10,
          project40, project10_10_20, project10_20);

    check("name:(" + "xxx" + ")", project10_10_10, project10_10_10_10, project20_10, project30_10_10, project10_20);
    check("name:(" + "xxx" + "),affectedProject:(id:" + project10.getExternalId() + ")", project10_20, project10_10_10, project10_10_10_10);
    check("name:(" + "xxx" + "),affectedProject:(id:" + project20.getExternalId() + ")", project20_10);
    check("name:(" + "xxx" + "),affectedProject:(id:" + project10_10.getExternalId() + ")", project10_10_10, project10_10_10_10);
    check("name:(" + "xxx" + "),affectedProject:(id:" + project30.getExternalId() + ")", project30_10_10);
    check("name:(" + "xxx" + "),affectedProject:(id:" + project10_10_10.getExternalId() + ")", project10_10_10_10);

    check("name:(" + "xxx" + "),parentProject:(id:" + project10.getExternalId() + ")", project10_20, project10_10_10, project10_10_10_10);
    check("name:(" + "xxx" + "),parentProject:(id:" + project20.getExternalId() + ")", project20_10);
    check("name:(" + "xxx" + "),parentProject:(id:" + project10_10.getExternalId() + ")", project10_10_10, project10_10_10_10);
    check("name:(" + "xxx" + "),parentProject:(id:" + project30.getExternalId() + ")", project30_10_10);
    check("name:(" + "xxx" + "),parentProject:(id:" + project10_10_10.getExternalId() + ")", project10_10_10_10);

    check("name:(" + "xxx" + "),project:(id:" + project10.getExternalId() + ")", project10_20);
    check("name:(" + "xxx" + "),project:(id:" + project20.getExternalId() + ")", project20_10);
    check("name:(" + "xxx" + "),project:(id:" + project10_10_20.getExternalId() + ")");
    check("name:(" + "xxx" + "),project:(id:" + project30.getExternalId() + ")");
    check("name:(" + "xxx" + "),project:(id:" + project10_10_10.getExternalId() + ")", project10_10_10_10);

    check("affectedProject:(id:" + project10.getExternalId() + ")", project10_10, project10_20, project10_10_10, project10_10_20, project10_10_10_10, project10_10_20_10);
    check("affectedProject:(id:" + project20.getExternalId() + ")", project20_10);
    check("affectedProject:(id:" + project40.getExternalId() + ")");
    check("affectedProject:(id:" + project10_10.getExternalId() + ")", project10_10_10, project10_10_20, project10_10_10_10, project10_10_20_10);

    check("parentProject:(id:" + project10.getExternalId() + ")", project10_10, project10_20, project10_10_10, project10_10_20, project10_10_10_10, project10_10_20_10);
    check("parentProject:(id:" + project20.getExternalId() + ")", project20_10);
    check("parentProject:(id:" + project40.getExternalId() + ")");

    check("project:(id:" + project10.getExternalId() + ")", project10_10, project10_20);
    check("project:(id:" + project20.getExternalId() + ")", project20_10);
    check("project:(id:" + project40.getExternalId() + ")");

    check("affectedProject:(id:" + project10.getExternalId() + "),archived:false", project10_10, project10_10_10, project10_10_10_10, project10_10_20_10);
    check("affectedProject:(id:" + project20.getExternalId() + "),archived:false", project20_10);
    check("affectedProject:(id:" + project40.getExternalId() + "),archived:false");
    check("parentProject:(id:" + project10.getExternalId() + "),archived:false", project10_10, project10_10_10, project10_10_10_10, project10_10_20_10);
    check("parentProject:(id:" + project20.getExternalId() + "),archived:false", project20_10);
    check("parentProject:(id:" + project40.getExternalId() + "),archived:false");
    check("project:(id:" + project10.getExternalId() + "),archived:false", project10_10);
    check("project:(id:" + project20.getExternalId() + "),archived:false", project20_10);
    check("project:(id:" + project40.getExternalId() + "),archived:false");
  }

  @Test
  public void testUserSelectedDimension() throws Exception {
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);

    final SProject project10 = createProject("p10", "project 10");
    final SProject project20 = createProject("p20", "project 20");
    final SProject project10_10 = project10.createProject("p10_10", "p10 child1");
    final SProject project10_20 = project10.createProject("p10_20", "xxx");
    final SProject project10_10_10 = project10_10.createProject("p10_10_10", "xxx");
    final SProject project10_10_20 = project10_10.createProject("p10_10_20", "p10_10 child2");
    final SProject project10_10_30 = project10_10.createProject("p10_10_30", "p10_10 child3");
    final SProject project30 = createProject(project10.getProjectId(), "project 30");
    final SProject project40 = createProject("p40", "project 40");

    final SUser user2 = createUser("user2");
    user2.addRole(RoleScope.projectScope(project10.getProjectId()), getProjectViewerRole());
    //default sorting is hierarchy-based + name-based within the same level
    check("selectedByUser:(username:user2)", project10, project10_10, project10_10_20, project10_10_30, project10_10_10, project10_20);

    final SUser user1 = createUser("user1");
    user1.addRole(RoleScope.projectScope(project10.getProjectId()), getProjectViewerRole());
    user1.addRole(RoleScope.projectScope(project20.getProjectId()), getProjectViewerRole());
    user1.addRole(RoleScope.projectScope(project30.getProjectId()), getProjectViewerRole());


    user1.setVisibleProjects(Arrays.asList(project10.getProjectId(), project10_10_20.getProjectId(), project10_10_10.getProjectId(), project40.getProjectId(), project30.getProjectId()));
    user1.setProjectsOrder(Arrays.asList(project10.getProjectId(), project10_10_20.getProjectId(), project10_10_10.getProjectId(), project40.getProjectId(), project30.getProjectId()));
    check("selectedByUser:(username:user1)", project10, project10_10_20, project10_10_10, project30);
    check("selectedByUser:(username:user1),project:(id:_Root)", project10, project30);
    check("selectedByUser:(username:user1),project:(id:p10)");

    user1.setVisibleProjects(Arrays.asList(project30.getProjectId(), project10_10_20.getProjectId(), project10_10_10.getProjectId()));
    user1.setProjectsOrder(Arrays.asList(project30.getProjectId(), project10_10_20.getProjectId(), project10_10_10.getProjectId()));
    check("selectedByUser:(username:user1)", project30, project10_10_20, project10_10_10);
    check("selectedByUser:(username:user1),project:(id:_Root)", project30);

    //add checks after    ProjectEx.setOwnProjectsOrder / setOwnBuildTypesOrder
  }

  @Test
  public void testProjectBean() throws Exception {
    final SProject project10 = createProject("p1", "project 1");
    final SProject project20 = createProject("p2", "project 2");
    final SProject project10_10 = project10.createProject("p10_10", "p1_child1");
    final SProject project10_20 = project10.createProject("p10_20", "xxx");
    final SProject project10_10_10 = project10_10.createProject("p10_10_10", "xxx");
    final SProject project30 = createProject(project10.getProjectId(), "p3");

    Project project = new Project(project10, new Fields("projects($long)"), getBeanContext(myServer));
    assertNotNull(project.projects.projects);
    checkOrderedCollection(CollectionsUtil.convertCollection(project.projects.projects, new Converter<String, Project>() {
      public String createFrom(@NotNull final Project source) {
        return source.id;
      }
    }), project10_10.getExternalId(), project10_20.getExternalId());

    project = new Project(project10, new Fields("projects($long,$locator(name:xxx))"), getBeanContext(myServer));
    assertNotNull(project.projects.projects);
    checkOrderedCollection(CollectionsUtil.convertCollection(project.projects.projects, new Converter<String, Project>() {
      public String createFrom(@NotNull final Project source) {
        return source.id;
      }
    }), project10_20.getExternalId());

    project = new Project(project10, new Fields("projects($long,$locator(project:$any,affectedProject:(" + project10.getExternalId() + ")))"), getBeanContext(myServer));
    assertNotNull(project.projects.projects);
    checkOrderedCollection(CollectionsUtil.convertCollection(project.projects.projects, new Converter<String, Project>() {
      public String createFrom(@NotNull final Project source) {
        return source.id;
      }
    }), project10_10.getExternalId(), project10_20.getExternalId(), project10_10_10.getExternalId());
  }
}
