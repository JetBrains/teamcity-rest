/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.finder.impl;

import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.BuildAgent;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.project.Project;
import jetbrains.buildServer.server.rest.model.project.PropEntityProjectFeature;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SimpleParameter;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.ProjectFeatureDescriptorFactory;
import jetbrains.buildServer.serverSide.impl.auth.RoleImpl;
import jetbrains.buildServer.serverSide.impl.projects.ProjectImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.CollectionsUtil.asMap;

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

    check("item:(id:" + project40.getExternalId() + ")", project40);
    check("item:(id:" + project40.getExternalId() + "),item:(id:"+ project20.getExternalId() + ")", project40, project20);

    check("id:XXX"); //nothing found: no error
    checkExceptionOnItemSearch(NotFoundException.class, "id:XXX");
    checkExceptionOnItemsSearch(NotFoundException.class, "project:(id:XXX)");
  }

  @Test
  public void testUserSelectedDimension() throws Exception {
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);
    ProjectEx root = myProjectManager.getRootProject();

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
    check("selectedByUser:(user:(username:user2),mode:selected_and_unknown)", project10, project10_10, project10_10_20, project10_10_30, project10_10_10, project10_20);
    check("selectedByUser:(user:(username:user2),mode:all_with_order)", root, project10, project10_10, project10_10_20, project10_10_30, project10_10_10, project10_20);
    check("selectedByUser:(user:(username:user2),mode:selected)");

    final SUser user1 = createUser("user1");
    user1.addRole(RoleScope.projectScope(project10.getProjectId()), getProjectViewerRole());
    user1.addRole(RoleScope.projectScope(project20.getProjectId()), getProjectViewerRole());
    user1.addRole(RoleScope.projectScope(project30.getProjectId()), getProjectViewerRole());


    user1.setVisibleProjects(Arrays.asList(project10.getProjectId(), project10_10_20.getProjectId(), project10_10_10.getProjectId(), project40.getProjectId(), project30.getProjectId()));
    user1.setProjectsOrder(Arrays.asList(project10.getProjectId(), project10_10_20.getProjectId(), project10_10_10.getProjectId(), project40.getProjectId(), project30.getProjectId()));

    check("selectedByUser:(username:user1)", project10, project10_10_20, project10_10_10, project30);
    check("selectedByUser:(user:(username:user1))", project10, project10_10_20, project10_10_10, project30);
    check("selectedByUser:(user:(username:user1),mode:selected_and_unknown)", project10, project10_10_20, project10_10_10, project30);
    check("selectedByUser:(user:(username:user1),mode:all_with_order)",
          root, project10, project10_10, project10_10_20, project10_10_10, project10_10_30, project10_20, project30,project20);
    check("selectedByUser:(user:(username:user1),mode:selected)", project10, project10_10_20, project10_10_10, project30);

    check("selectedByUser:(username:user1),project:(id:_Root)", project10, project30);
    check("selectedByUser:(user:(username:user1),mode:selected_and_unknown),project:(id:_Root)", project10, project30);
    check("selectedByUser:(user:(username:user1),mode:all_with_order),project:(id:_Root)", project10, project30, project20);
    check("selectedByUser:(user:(username:user1),mode:selected),project:(id:_Root)", project10, project30);

    check("selectedByUser:(username:user1),project:(id:p10)");
    check("selectedByUser:(user:(username:user1),mode:selected_and_unknown),project:(id:p10)");
    check("selectedByUser:(user:(username:user1),mode:all_with_order),project:(id:p10)", project10_10, project10_20);
    check("selectedByUser:(user:(username:user1),mode:selected),project:(id:p10)");

    user1.setVisibleProjects(Arrays.asList(project30.getProjectId(), project10_10_20.getProjectId(), project10_10_10.getProjectId()));
    user1.setProjectsOrder(Arrays.asList(project30.getProjectId(), project10_10_20.getProjectId(), project10_10_10.getProjectId()));
    check("selectedByUser:(username:user1)", project30, project10_10_20, project10_10_10);
    check("selectedByUser:(username:user1),project:(id:_Root)", project30);

    checkExceptionOnItemsSearch(LocatorProcessException.class, "selectedByUser:(username:user2,mode:selected)");
    checkExceptionOnItemsSearch(BadRequestException.class, "selectedByUser:(user:(username:user2),mode:aaa)");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "selectedByUser:(user:(username:user2),mode:aaa,ccc:ddd)");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "selectedByUser:(user:(username:user2,aaa:bbb))");

    //add checks after    ProjectEx.setOwnProjectsOrder / setOwnBuildTypesOrder
  }

  @Test
  public void testUserPermissionDimension() throws Exception {
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);
    ProjectEx root = myProjectManager.getRootProject();

    final SProject project10 = createProject("p10", "project 10");
    final SProject project10_10 = project10.createProject("p10_10", "p10 child1");
    final SProject project20 = createProject("p20", "project 20");
    final SProject project30 = createProject("p30", "project 30");

    RoleImpl role10 = new RoleImpl("role10", "custom role", new Permissions(Permission.TAG_BUILD), myFixture.getRolesManager());
    myFixture.getRolesManager().addRole(role10);
    RoleImpl role20 = new RoleImpl("role20", "custom role", new Permissions(Permission.CHANGE_SERVER_SETTINGS, Permission.LABEL_BUILD), myFixture.getRolesManager());
    myFixture.getRolesManager().addRole(role20);
    RoleImpl role30 = new RoleImpl("role30", "custom role", new Permissions(Permission.RUN_BUILD), myFixture.getRolesManager());
    myFixture.getRolesManager().addRole(role30);
    role30.addIncludedRole(role10);

    final SUser user10 = createUser("user10");
    final SUser user20 = createUser("user20");
    final SUser user30 = createUser("user30");
    final SUser user40 = createUser("user40");

    final SUserGroup group10 = myFixture.createUserGroup("group1", "group 1", "");
    final SUserGroup group20 = myFixture.createUserGroup("group1.1", "group 1.1", "");
    group10.addSubgroup(group20);
    group20.addUser(user20);

    group10.addRole(RoleScope.projectScope(project10.getProjectId()), role30);

    user10.addRole(RoleScope.projectScope(project10_10.getProjectId()), role10);
    user30.addRole(RoleScope.globalScope(), role30);
    user40.addRole(RoleScope.projectScope(project10_10.getProjectId()), role20);


    check(null, getRootProject(), project10, project10_10, project20, project30);

    check("userPermission:(user:(id:" + user10.getId() + "),permission:tag_build)", project10_10);
    checkExceptionOnItemsSearch(LocatorProcessException.class, "userPermission:(user:(id:" + user10.getId() + "))");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "userPermission:(permission:view_project)");

    check("userPermission:(user:(id:" + user20.getId() + "),permission:tag_build)", project10, project10_10);
    check("userPermission:(user:(id:" + user30.getId() + "),permission:TAG_BUILD)", getRootProject(), project10, project10_10, project20, project30); //project permission granted globally
    check("userPermission:(user:(id:" + user30.getId() + "),permission:change_server_settings)");
    check("userPermission:(user:(id:" + user40.getId() + "),permission:change_server_settings)", getRootProject(), project10, project10_10, project20, project30); //global permission
    check("userPermission:(user:(id:" + user40.getId() + "),permission:TAG_BUILD)");


    RoleImpl role11 = new RoleImpl("role11", "custom role", new Permissions(Permission.VIEW_PROJECT), myFixture.getRolesManager());
    myFixture.getRolesManager().addRole(role11);
    user10.addRole(RoleScope.projectScope(project10_10.getProjectId()), role11);
    check("userPermission:(user:(id:" + user10.getId() + "),permission:view_project)", getRootProject(), project10, project10_10); //view project is propagated on top
  }

  @Test
  public void testFeatureDimension() throws Exception {
    final SProject project10 = createProject("p10", "project 10");
    final SProject project20 = createProject("p20", "project 20");
    final SProject project30 = createProject("p30", "project 30");

    project10.addFeature("type1", asMap("a", "b", "c", "d"));
    project20.addFeature("type2", asMap("a", "b2", "c2", "d2"));

    check(null, getRootProject(), project10, project20, project30);
    check("projectFeature:(type:type1)", project10);
    check("projectFeature:(type:Type1)");
    check("projectFeature:(type:(matchType:any))", project10, project20);
    check("projectFeature:(property:(name:a))", project10, project20);
    check("projectFeature:(property:(name:a,value:b2))", project20);
    check("projectFeature:(property:(name:a),property:(name:c))", project10);
  }

  @Test
  public void testAgentPoolDimension() throws Exception {
    final SProject project10 = createProject("p10", "project 10");
    final SProject project20 = createProject("p20", "project 20");
    final SProject project30 = createProject("p30", "project 30");

    final int poolId0 = BuildAgent.DEFAULT_POOL_ID; // - project10, project20
    final int poolId10 = myFixture.getAgentPoolManager().createNewAgentPool("pool10").getAgentPoolId(); // - project20
    final int poolId20 = myFixture.getAgentPoolManager().createNewAgentPool("pool20").getAgentPoolId(); // - project30
    myFixture.getAgentPoolManager().associateProjectsWithPool(poolId10, createSet(project20.getProjectId()));
    myFixture.getAgentPoolManager().associateProjectsWithPool(poolId20, createSet(project30.getProjectId()));
    myFixture.getAgentPoolManager().dissociateProjectsFromOtherPools(poolId20, createSet(project30.getProjectId()));

    check(null, getRootProject(), project10, project20, project30);
    check("pool:(id:" + poolId0 + ")", myProjectManager.getRootProject(), project10, project20);
    check("pool:(id:" + poolId10 + ")", project20);
    check("pool:(id:" + poolId20 + ")", project30);
    check("pool:(item:(id:" + poolId0 + "),item:(id:" + poolId10 + "))", myProjectManager.getRootProject(), project10, project20);
    check("pool:(item:(id:" + poolId0 + "),item:(id:" + poolId20 + "))", myProjectManager.getRootProject(), project10, project20, project30);
    check("pool:(item:(id:" + poolId0 + "),item:(id:" + poolId20 + ")),id:" + project20.getExternalId(), project20);
    check("pool:(item:(id:" + poolId0 + "),item:(id:" + poolId20 + ")),pool:(id:" + poolId10 + ")", project20);
    check("pool:(id:" + poolId10 + "),pool:(id:" + poolId20 + ")");
    check("pool:(id:" + poolId0 + "),pool:(id:" + poolId10 + ")", project20);
    check("pool:(id:" + poolId0 + "),not(pool:(id:" + poolId10 + "))", myProjectManager.getRootProject(), project10);
  }

  @Test
  public void testVirtualDimension() throws Exception {
    SProject parent = createProject("parent");
    SProject virtual = parent.createProject("parent_virtualProject", "virtualProject");
    virtual.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "true"));

    SProject regular = parent.createProject("parent_regularProject", "regularProject");

    check("project:(name:parent),virtual:false", regular);
    check("project:(name:parent),virtual:true", virtual);

    check("project:(name:parent)", regular); // check virtual=false by default

    check("project:(name:parent),virtual:any", regular, virtual);
  }

  @Test
  public void testVirtualSingleValue() throws Exception {
    SProject parent = createProject("parent");
    SProject virtual = parent.createProject("parent_virtualProject", "virtualProject");
    virtual.addParameter(new SimpleParameter(ProjectImpl.TEAMCITY_VIRTUAL_PROJECT_PARAM, "true"));

    check("parent_virtualProject", virtual);
    check("virtualProject", virtual);
    check(virtual.getProjectId(), virtual);
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


    ProjectFeatureDescriptorFactory featureDescriptorFactory = myFixture.findSingletonService(ProjectFeatureDescriptorFactory.class);
    assert featureDescriptorFactory != null;
    project10.addFeature(featureDescriptorFactory.createProjectFeature("uniqueId10", "type10", asMap("a", "b", "c", "d"), project10.getProjectId()));
    project10_10.addFeature(featureDescriptorFactory.createProjectFeature("uniqueId20", "type20", asMap("a", "b", "c", "d"), project10_10.getProjectId()));

    project = new Project(project10_10, new Fields("$long"), getBeanContext(myServer));
    assertEquals(project.id, project10_10.getExternalId());
    assertNotNull(project.projectFeatures);
    assertEquals(Integer.valueOf(1), project.projectFeatures.count);
    List<PropEntityProjectFeature> propEntities = project.projectFeatures.propEntities;
    assertEquals(1, propEntities.size());
    PropEntityProjectFeature feature = propEntities.get(0);
    assertEquals("uniqueId20",feature.id);
    assertEquals("type20",feature.type);
    assertEquals(Integer.valueOf(2),feature.properties.getCount());
    assertEquals("a",feature.properties.getProperties().get(0).name);
    assertEquals("b",feature.properties.getProperties().get(0).value);
    assertEquals("c",feature.properties.getProperties().get(1).name);
    assertEquals("d",feature.properties.getProperties().get(1).value);
  }
}
