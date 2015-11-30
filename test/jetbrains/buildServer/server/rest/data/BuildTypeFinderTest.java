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
import java.util.Collections;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.identifiers.VcsRootIdentifiersManagerImpl;
import jetbrains.buildServer.serverSide.impl.projects.ProjectManagerImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.impl.VcsManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 09.09.2014
 */
public class BuildTypeFinderTest extends BaseFinderTest<BuildTypeOrTemplate> {

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final PermissionChecker permissionChecker = new PermissionChecker(myServer.getSecurityContext());
    myFixture.addService(permissionChecker);

    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager, permissionChecker, myServer);
    final AgentFinder agentFinder = new AgentFinder(myAgentManager);

    final VcsManagerImpl vcsManager = myFixture.getVcsManager();
    final ProjectManagerImpl projectManager = myFixture.getProjectManager();
    final BuildTypeFinder buildTypeFinder = new BuildTypeFinder(projectManager, projectFinder, agentFinder, permissionChecker, myServer);
    final VcsRootFinder vcsRootFinder = new VcsRootFinder(vcsManager, projectFinder, buildTypeFinder, projectManager,
                                                          myFixture.getSingletonService(VcsRootIdentifiersManagerImpl.class),
                                                          permissionChecker);
    myFixture.addService(vcsRootFinder);
    myFixture.addService(new VcsRootInstanceFinder(vcsRootFinder, vcsManager, projectFinder, buildTypeFinder, projectManager,
                                                   myFixture.getSingletonService(VcsRootIdentifiersManagerImpl.class),
                                                   permissionChecker));

    setFinder(new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, permissionChecker, myServer));
  }

  @Test
  public void testBuildTypeTemplates() throws Exception {
    final BuildTypeOrTemplate template = new BuildTypeOrTemplate(createBuildTypeTemplate("template"));
    final BuildTypeOrTemplate buildConf = new BuildTypeOrTemplate(registerTemplateBasedBuildType("buildConf"));
    //noinspection ConstantConditions
    buildConf.getBuildType().attachToTemplate(template.getTemplate());

    check("template:(id:" + template.getTemplate().getExternalId() + "),templateFlag:false", buildConf);

    check("name:" + buildConf.getName() + ",templateFlag:false", buildConf);
    check("name:" + buildConf.getName() + ",templateFlag:true");
    check("name:" + template.getName() + ",templateFlag:false");
    check("name:" + template.getName() + ",templateFlag:true", template);
  }

  @Test
  public void testBuildTypes1() throws Exception {
    myBuildType.remove();

    final SProject project10 = createProject("p10");
    final SProject project10_10 = project10.createProject("p10_10", "p10_10");
    final SProject project10_20 = project10.createProject("p10_20", "p10_20");
    final SProject project10_10_10 = project10_10.createProject("p10_10_10", "p10_10_10");
    final SProject project20 = createProject("p20");
    final SProject project20_10 = project20.createProject("p20_10", "p20_10");
    final SProject project20_20 = project20.createProject("p20_20", "p20_20");
    final SProject project20_10_10 = project20_10.createProject("p20_10_10", "p20_10_10");
    final SProject project30 = createProject("p30");

    final BuildTypeOrTemplate p10_bt10 = new BuildTypeOrTemplate(project10.createBuildType("p10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_bt20 = new BuildTypeOrTemplate(project10.createBuildType("p10_bt20", "p10_bt20"));
    final BuildTypeOrTemplate p10_10_bt10 = new BuildTypeOrTemplate(project10_10.createBuildType("p10_10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_10_bt20 = new BuildTypeOrTemplate(project10_10.createBuildType("p10_10_bt20", "p10_10_bt20"));
    final BuildTypeOrTemplate p10_10_10_bt10 = new BuildTypeOrTemplate(project10_10_10.createBuildType("p10_10_10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_10_10_bt20 = new BuildTypeOrTemplate(project10_10_10.createBuildType("p10_10_10_bt20", "p10_10_10_bt20"));

    final BuildTypeOrTemplate p20_bt10 = new BuildTypeOrTemplate(project20.createBuildType("p20_bt10", "p20_bt10"));
    final BuildTypeOrTemplate p20_bt20 = new BuildTypeOrTemplate(project20.createBuildType("p20_bt20", "p20_bt20"));
    final BuildTypeOrTemplate p20_10_bt10 = new BuildTypeOrTemplate(project20_10.createBuildType("p20_10_bt10", "xxx"));
    final BuildTypeOrTemplate p20_10_bt20 = new BuildTypeOrTemplate(project20_10.createBuildType("p20_10_bt20", "p20_10_bt20"));
    final BuildTypeOrTemplate p20_10_10_bt10 = new BuildTypeOrTemplate(project20_10_10.createBuildType("p20_10_10_bt10", "xxx"));
    final BuildTypeOrTemplate p20_10_10_bt20 = new BuildTypeOrTemplate(project20_10_10.createBuildType("p20_10_10_bt20", "p20_10_10_bt20"));

    check(null, p10_bt20, p10_bt10, p10_10_bt20, p10_10_bt10, p10_10_10_bt20, p10_10_10_bt10, p20_bt10, p20_bt20, p20_10_bt20, p20_10_bt10, p20_10_10_bt20, p20_10_10_bt10);

    check("affectedProject:(id:" + project10_10.getExternalId() + ")", p10_10_bt20, p10_10_bt10, p10_10_10_bt20, p10_10_10_bt10);
    check("project:(id:" + project10_10.getExternalId() + ")", p10_10_bt20, p10_10_bt10);

    check("name:xxx", p10_bt10, p10_10_bt10, p10_10_10_bt10, p20_10_bt10, p20_10_10_bt10);
    check("name:xxx,affectedProject:(id:" + project10_10.getExternalId() + ")", p10_10_bt10, p10_10_10_bt10);
    check("name:xxx,project:(id:" + project10_10.getExternalId() + ")", p10_10_bt10);

    check("name:p10_10_10_bt20,affectedProject:(id:" + project10_10.getExternalId() + ")", p10_10_10_bt20);
    check("name:p10_10_10_bt10,project:(id:" + project10_10.getExternalId() + ")");

    check("name:xxx,affectedProject:(id:" + project20.getExternalId() + ")", p20_10_bt10, p20_10_10_bt10);
    check("name:xxx,project:(id:" + project20.getExternalId() + ")"); //change comparing to 9.0 when searching for single item ???

    //case sensitivity
    check("name:xxX", p10_bt10, p10_10_bt10, p10_10_10_bt10, p20_10_bt10, p20_10_10_bt10);
    check("name:P10_10_10_bt20,affectedProject:(id:" + project10_10.getExternalId() + ")", p10_10_10_bt20);

    checkExceptionOnItemSearch(BadRequestException.class, "xxx");
    check("p10_10_10_bt20", p10_10_10_bt20);
  }
  

  @Test
  public void testSnapshotDependencies() throws Exception {
    final BuildTypeOrTemplate buildConf01 = new BuildTypeOrTemplate(registerBuildType("buildConf01", "project"));
    final BuildTypeOrTemplate buildConf02 = new BuildTypeOrTemplate(registerBuildType("buildConf02", "project"));
    final BuildTypeOrTemplate buildConf1 = new BuildTypeOrTemplate(registerBuildType("buildConf1", "project"));
    final BuildTypeOrTemplate buildConf2 = new BuildTypeOrTemplate(registerBuildType("buildConf2", "project"));
    final BuildTypeOrTemplate buildConf31 = new BuildTypeOrTemplate(registerBuildType("buildConf31", "project"));
    final BuildTypeOrTemplate buildConf32 = new BuildTypeOrTemplate(registerBuildType("buildConf32", "project"));
    final BuildTypeOrTemplate buildConf4 = new BuildTypeOrTemplate(registerBuildType("buildConf4", "project"));
    final BuildTypeOrTemplate buildConf5 = new BuildTypeOrTemplate(registerBuildType("buildConf5", "project"));
    final BuildTypeOrTemplate buildConf6 = new BuildTypeOrTemplate(registerBuildType("buildConf6", "project"));

    addDependency(buildConf6.get() , buildConf5.getBuildType());
    addDependency(buildConf5.get() , buildConf31.getBuildType());

    addDependency(buildConf4.get() , buildConf31.getBuildType());
    addDependency(buildConf4.get() , buildConf32.getBuildType());
    addDependency(buildConf31.get() , buildConf2.getBuildType());
    addDependency(buildConf32.get() , buildConf2.getBuildType());
    addDependency(buildConf2.get() , buildConf1.getBuildType());
    addDependency(buildConf2.get() , buildConf01.getBuildType());
    addDependency(buildConf1.get() , buildConf01.getBuildType());
    addDependency(buildConf1.get() , buildConf02.getBuildType());


    final String baseToLocatorStart1 = "snapshotDependency:(from:(id:" + buildConf4.getId() + ")";
    check(baseToLocatorStart1 + ")");
    check(baseToLocatorStart1 + ",includeInitial:true)", buildConf4);
    check(baseToLocatorStart1 + ",includeInitial:false)");
    check(baseToLocatorStart1 + ",recursive:true)");
    check(baseToLocatorStart1 + ",includeInitial:true,recursive:false)", buildConf4);

    final String baseToLocatorStart2 = "snapshotDependency:(to:(id:" + buildConf4.getId() + ")";
    check(baseToLocatorStart2 + ")", buildConf31, buildConf32, buildConf2, buildConf1, buildConf01, buildConf02);
    check(baseToLocatorStart2 + ",includeInitial:true)", buildConf4, buildConf31, buildConf32, buildConf2, buildConf1, buildConf01, buildConf02);
    check(baseToLocatorStart2 + ",includeInitial:false)", buildConf31, buildConf32, buildConf2, buildConf1, buildConf01, buildConf02);
    check(baseToLocatorStart2 + ",recursive:true)", buildConf31, buildConf32, buildConf2, buildConf1, buildConf01, buildConf02);
    check(baseToLocatorStart2 + ",recursive:false)", buildConf31, buildConf32);
    check(baseToLocatorStart2 + ",includeInitial:true,recursive:true)", buildConf4, buildConf31, buildConf32, buildConf2, buildConf1, buildConf01, buildConf02);

    final String baseToLocatorStart3 = "snapshotDependency:(to:(id:" + buildConf31.getId() + ")";
    check(baseToLocatorStart3 + ")", buildConf2, buildConf1, buildConf01, buildConf02);
    check(baseToLocatorStart3 + ",includeInitial:true)", buildConf31, buildConf2, buildConf1, buildConf01, buildConf02);
    check(baseToLocatorStart3 + ",includeInitial:false)", buildConf2, buildConf1, buildConf01, buildConf02);
    check(baseToLocatorStart3 + ",recursive:true)", buildConf2, buildConf1, buildConf01, buildConf02);
    check(baseToLocatorStart3 + ",recursive:false)", buildConf2);
    check(baseToLocatorStart3 + ",includeInitial:true,recursive:true)", buildConf31, buildConf2, buildConf1, buildConf01, buildConf02);

    final String baseToLocatorStart4 = "snapshotDependency:(from:(id:" + buildConf31.getId() + ")";
    check(baseToLocatorStart4 + ")", buildConf4, buildConf5, buildConf6);
    check(baseToLocatorStart4 + ",includeInitial:true)", buildConf31, buildConf4, buildConf5, buildConf6);
    check(baseToLocatorStart4 + ",includeInitial:false)", buildConf4, buildConf5, buildConf6);
    check(baseToLocatorStart4 + ",recursive:true)", buildConf4, buildConf5, buildConf6);
    check(baseToLocatorStart4 + ",recursive:false)", buildConf4, buildConf5);
    check(baseToLocatorStart4 + ",includeInitial:true,recursive:true)", buildConf31, buildConf4, buildConf5, buildConf6);

    check("snapshotDependency:(from:(id:" + buildConf2.getId() + "),to:(id:" + buildConf31.getId() + "),includeInitial:true)", buildConf31, buildConf2);
    check("snapshotDependency:(from:(id:" + buildConf1.getId() + "),to:(id:" + buildConf4.getId() + "))", buildConf31, buildConf2, buildConf32);
    check("snapshotDependency:(from:(id:" + buildConf1.getId() + "),to:(id:" + buildConf5.getId() + "))", buildConf31, buildConf2);
    check("snapshotDependency:(from:(id:" + buildConf31.getId() + "),to:(id:" + buildConf32.getId() + "))");
  }

  @Test
  public void testProject() throws Exception {
    myBuildType.remove();
    final SProject project10 = createProject("p10");
    final SProject project10_10 = project10.createProject("p10_10", "xxx");
    final SProject project10_20 = project10.createProject("p10_20", "p10_20");
    project10_20.setArchived(true, getOrCreateUser("user"));
    final SProject project20 = createProject("xxx");

    final BuildTypeOrTemplate p10_bt10 = new BuildTypeOrTemplate(project10.createBuildType("p10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_bt20 = new BuildTypeOrTemplate(project10.createBuildType("p10_bt20", "p10_bt20"));
    final BuildTypeOrTemplate p10_10_bt10 = new BuildTypeOrTemplate(project10_10.createBuildType("p10_10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_10_bt20 = new BuildTypeOrTemplate(project10_10.createBuildType("p10_10_bt20", "p10_10_bt20"));
    final BuildTypeOrTemplate p20_10_bt10 = new BuildTypeOrTemplate(project10_20.createBuildType("p20_10_bt10", "p20_10_bt10"));
    final BuildTypeOrTemplate p20_10_bt20 = new BuildTypeOrTemplate(project10_20.createBuildType("p20_10_bt20", "p20_10_bt20"));

    final BuildTypeOrTemplate p20_bt10 = new BuildTypeOrTemplate(project20.createBuildType("p20_bt10", "p20_bt10"));
    final BuildTypeOrTemplate p20_bt20 = new BuildTypeOrTemplate(project20.createBuildType("p20_bt20", "p20_bt20"));

    check("project:(id:" + project10.getExternalId() + ")", p10_bt20, p10_bt10);
    check("project:(name:xxx)", p10_10_bt20, p10_10_bt10, p20_bt10, p20_bt20);
    check("project:(name:xxx,count:1)", p10_10_bt20, p10_10_bt10);
    check("project:(archived:false)", p10_bt20, p10_bt10, p10_10_bt20, p10_10_bt10, p20_bt10, p20_bt20);
    check("project:(archived:true)", p20_10_bt10, p20_10_bt20);
    check("project:(archived:any)", p10_bt20, p10_bt10, p10_10_bt20, p10_10_bt10, p20_bt10, p20_bt20, p20_10_bt10, p20_10_bt20);
  }

  @Test
  public void testUserSelectedDimension() throws Exception {
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);

    myFixture.addService(new UserFinder(myFixture));
    myBuildType.remove();
    final SProject project10 = createProject("p10", "project 10");
    final SProject project20 = createProject("p20", "project 20");
    final SProject project10_10 = project10.createProject("p10_10", "p10 child1");
    final SProject project10_20 = project10.createProject("p10_20", "p10 child2");
    final SProject project10_30 = project10.createProject("p10_30", "p10 child3");
    final SProject project30 = createProject(project10.getProjectId(), "project 30");
    final SProject project40 = createProject("p40", "project 40");

    final SBuildType p10_bt10 = project10.createBuildType("p10_bt10", "10-10");
    final SBuildType p10_bt20 = project10.createBuildType("p10_bt20", "10-20");
    final SBuildType p10_bt30 = project10.createBuildType("p10_bt30", "10-30");

    final SBuildType p10_10_bt10 = project10_10.createBuildType("p10_10_bt10", "10_10-10");
    final SBuildType p10_10_bt20 = project10_10.createBuildType("p10_10_bt20", "10_10-20");
    final SBuildType p10_10_bt30 = project10_10.createBuildType("p10_10_bt30", "10_10-30");

    final SBuildType p10_30_bt10 = project10_30.createBuildType("p10_30_bt10", "10_30-10");
    final SBuildType p10_30_bt20 = project10_30.createBuildType("p10_30_bt20", "10_30-20");
    final SBuildType p10_30_bt30 = project10_30.createBuildType("p10_30_bt30", "10_30-30");

    final SBuildType p20_bt10 = project20.createBuildType("p20_bt10", "20-10");
    final SBuildType p20_bt20 = project20.createBuildType("p20_bt20", "20-20");
    final SBuildType p20_bt30 = project20.createBuildType("p20_bt30", "20-30");

    final SBuildType p30_bt10 = project30.createBuildType("p30_bt10", "30-10");
    final SBuildType p30_bt20 = project30.createBuildType("p30_bt20", "xxx 30-20");
    final SBuildType p30_bt30 = project30.createBuildType("p30_bt30", "30-30");

    final SBuildType p40_bt10 = project40.createBuildType("p40_bt10", "40-10");
    final SBuildType p40_bt20 = project40.createBuildType("p40_bt20", "40-20");
    final SBuildType p40_bt30 = project40.createBuildType("p40_bt30", "40-30");

    final SUser user2 = createUser("user2");
    user2.addRole(RoleScope.projectScope(project10.getProjectId()), getProjectViewerRole());
    //default sorting is name-based
    checkBuildTypes("selectedByUser:(username:user2)", p10_10_bt10, p10_10_bt20, p10_10_bt30, p10_30_bt10, p10_30_bt20, p10_30_bt30, p10_bt10, p10_bt20, p10_bt30);

    user2.setVisibleProjects(Arrays.asList(project10.getProjectId(), project10_30.getProjectId(), project10_10.getProjectId(), project40.getProjectId(), project30.getProjectId()));
    user2.setProjectsOrder(Arrays.asList(project10.getProjectId(), project10_30.getProjectId(), project10_10.getProjectId(), project40.getProjectId(), project30.getProjectId()));
    checkBuildTypes("selectedByUser:(username:user2)", p10_bt10, p10_bt20, p10_bt30, p10_30_bt10, p10_30_bt20, p10_30_bt30, p10_10_bt10, p10_10_bt20, p10_10_bt30);


    final SUser user1 = createUser("user1");
    user1.addRole(RoleScope.projectScope(project10.getProjectId()), getProjectViewerRole());
    user1.addRole(RoleScope.projectScope(project20.getProjectId()), getProjectViewerRole());
    user1.addRole(RoleScope.projectScope(project30.getProjectId()), getProjectViewerRole());

    user1.setVisibleProjects(Arrays.asList(project10.getProjectId(), project10_20.getProjectId(), project10_10.getProjectId(), project40.getProjectId(), project30.getProjectId()));
    user1.setProjectsOrder(Arrays.asList(project10.getProjectId(), project10_20.getProjectId(), project10_10.getProjectId(), project40.getProjectId(), project30.getProjectId()));
    user1.setBuildTypesOrder(project10, Arrays.asList(p10_bt30, p10_bt10), Arrays.asList(p10_bt20));
    user1.setBuildTypesOrder(project10_10, Arrays.asList(p10_10_bt20), Arrays.asList(p10_10_bt10));
    user1.setBuildTypesOrder(project10_30, Arrays.asList(p10_30_bt30, p10_30_bt20, p10_30_bt10), Collections.<SBuildType>emptyList());
    user1.setBuildTypesOrder(project20, Arrays.asList(p20_bt10, p20_bt30), Arrays.asList(p20_bt20));
    user1.setBuildTypesOrder(project40, Arrays.asList(p40_bt10, p40_bt30), Arrays.asList(p40_bt20));

    checkBuildTypes("selectedByUser:(username:user1)", p10_bt30, p10_bt10, p10_10_bt20, p10_10_bt30, p30_bt10, p30_bt30, p30_bt20);
    checkBuildTypes("selectedByUser:(username:user1),project:(id:"+ project10.getExternalId() + ")", p10_bt30, p10_bt10);
    checkBuildTypes("selectedByUser:(username:user1),project:(id:"+ project30.getExternalId() + ")", p30_bt10, p30_bt30, p30_bt20);
  }

  @Test
  public void testSnapshotAndSelected() throws Exception {
    myFixture.addService(new UserFinder(myFixture));
    myBuildType.remove();
    final SBuildType buildConf01 = myProject.createBuildType("buildConf01","build conf 01");
    final SBuildType buildConf02 = myProject.createBuildType("buildConf02","build conf 02");
    final SBuildType buildConf1 = myProject.createBuildType("buildConf1", "build conf 1");
    final SBuildType buildConf2 = myProject.createBuildType("buildConf2", "build conf 2");
    final SBuildType buildConf31 = myProject.createBuildType("buildConf31","build conf 31");
    final SBuildType buildConf32 = myProject.createBuildType("buildConf32","build conf 32");
    final SBuildType buildConf4 = myProject.createBuildType("buildConf4", "build conf 4");

    addDependency(buildConf4 , buildConf31);
    addDependency(buildConf4 , buildConf32);
    addDependency(buildConf31 , buildConf2);
    addDependency(buildConf32 , buildConf2);
    addDependency(buildConf2 , buildConf1);
    addDependency(buildConf2 , buildConf01);
    addDependency(buildConf1 , buildConf01);
    addDependency(buildConf1 , buildConf02);

    checkBuildTypes("snapshotDependency:(to:(id:" + buildConf4.getExternalId() + "))", buildConf31, buildConf32, buildConf2, buildConf1, buildConf01, buildConf02);

    final SUser user1 = createUser("user1");
    user1.addRole(RoleScope.projectScope(myProject.getProjectId()), getProjectViewerRole());
    user1.setBuildTypesOrder(myProject, Arrays.asList(buildConf1, buildConf01, buildConf31, buildConf2, buildConf02), Arrays.asList(buildConf32));

    checkBuildTypes("snapshotDependency:(to:(id:" + buildConf4.getExternalId() + ")),selectedByUser:(username:user1)", buildConf1, buildConf01, buildConf31, buildConf2, buildConf02);
  }

  @Test
  public void testProjectItem() throws Exception {
    myBuildType.remove();
    final SProject project10 = createProject("p10");
    final SProject project10_10 = project10.createProject("p10_10", "xxx");

    final BuildTypeOrTemplate p10_bt10 = new BuildTypeOrTemplate(project10.createBuildType("p10_bt10", "xxx"));
    final BuildTypeOrTemplate p10_bt20 = new BuildTypeOrTemplate(project10.createBuildType("p10_bt20", "p10_bt20"));
    final BuildTypeOrTemplate p10_bt30 = new BuildTypeOrTemplate(project10.createBuildTypeTemplate("p10_bt30", "p10_bt30"));

    final PermissionChecker permissionChecker = new PermissionChecker(myServer.getSecurityContext());
    myFixture.addService(permissionChecker);

    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager, permissionChecker, myServer);
    final AgentFinder agentFinder = new AgentFinder(myAgentManager);

    final BuildTypeFinder buildTypeFinder = new BuildTypeFinder(myProjectManager, projectFinder, agentFinder, permissionChecker, myServer);

    PagedSearchResult<BuildTypeOrTemplate> result = buildTypeFinder.getBuildTypesPaged(project10, null, true);

    assertEquals(String.valueOf(result.myEntries), 2, result.myEntries.size());

    result = buildTypeFinder.getBuildTypesPaged(project10, null, false);
    assertEquals(String.valueOf(result.myEntries), 1, result.myEntries.size());
  }

  @Test
  public void testVcsDimensions() throws Exception {
    myBuildType.remove();

    final SProject project10 = createProject("p10");
    myFixture.registerVcsSupport("svn");
    myFixture.registerVcsSupport("cvs");
    final SVcsRoot vcsRoot10 = getRootProject().createVcsRoot("svn", "id10", "VCS root 10 name");
    vcsRoot10.setProperties(CollectionsUtil.asMap("url", "", "param", "%aaa%"));

    final BuildTypeTemplate template = project10.createBuildTypeTemplate("template");
    template.addConfigParameter(new SimpleParameter("aaa", "111"));
    template.addVcsRoot(vcsRoot10);

    final SBuildType buildConf10 = project10.createBuildType("buildConf10");
    buildConf10.attachToTemplate(template);

    final SBuildType buildConf20 = project10.createBuildType("buildConf20");
    buildConf20.attachToTemplate(template);
    buildConf20.addConfigParameter(new SimpleParameter("aaa", "222"));

    final SVcsRoot vcsRoot20 = project10.createVcsRoot("svn", "id20", "VCS root 20 name");
    buildConf20.addVcsRoot(vcsRoot20);


    final SBuildType buildConf30 = project10.createBuildType("buildConf30");
    final SBuildType buildConf40 = project10.createBuildType("buildConf40");
    final SVcsRoot vcsRoot30 = getRootProject().createVcsRoot("cvs", "id30", "VCS root 30 name");
    buildConf40.addVcsRoot(vcsRoot30);

    final SProject project10_10 = project10.createProject("p10_10", "p10_10");
    final SBuildType buildConf50 = project10_10.createBuildType("buildConf50");
    final SBuildType buildConf60 = project10_10.createBuildType("buildConf60");
    buildConf50.addVcsRoot(vcsRoot20);
    buildConf60.addVcsRoot(vcsRoot30);


    checkBuildTypes("templateFlag:false", buildConf10, buildConf20, buildConf30, buildConf40, buildConf50, buildConf60);
    checkBuildTypes("vcsRoot:(id:id10),templateFlag:true", template);
    checkBuildTypes("vcsRoot:(id:id10),templateFlag:false", buildConf10, buildConf20);
    checkBuildTypes("vcsRoot:(id:id10)", buildConf10, buildConf20, template);
    checkBuildTypes("vcsRoot:(type:svn)", buildConf10, buildConf20, template, buildConf50);
    checkBuildTypes("vcsRoot:(type:cvs)", buildConf40, buildConf60);
    checkBuildTypes("vcsRoot:(type:cvs),templateFlag:true");
    checkBuildTypes("vcsRoot:(type:svn),project:(id:" + project10_10.getExternalId() + ")", buildConf50);

    final VcsRootInstance vcsRootInstance10 = buildConf10.getVcsRootInstanceForParent(vcsRoot10);
    assert vcsRootInstance10 != null;

    final VcsRootInstance vcsRootInstance10_2 = buildConf20.getVcsRootInstanceForParent(vcsRoot10);
    assert vcsRootInstance10_2 != null;

    final VcsRootInstance vcsRootInstance20 = buildConf20.getVcsRootInstanceForParent(vcsRoot20);
    assert vcsRootInstance20 != null;

    checkBuildTypes("vcsRootInstance:(id:" + vcsRootInstance10.getId() + ")", buildConf10);
    checkBuildTypes("vcsRootInstance:(id:" + vcsRootInstance10_2.getId() + ")", buildConf20);
    checkBuildTypes("vcsRootInstance:(id:" + vcsRootInstance20.getId() + ")", buildConf20, buildConf50);
    checkBuildTypes("vcsRootInstance:(vcsRoot:(id:id10))", buildConf10, buildConf20);
    checkBuildTypes("vcsRootInstance:(vcsRoot:(type:svn)),project:(id:" + project10_10.getExternalId() + ")", buildConf50);
  }

  private void checkBuildTypes(@Nullable final String locator, BuildTypeSettings... items) {
    check(locator, CollectionsUtil.convertCollection(Arrays.asList(items), new Converter<BuildTypeOrTemplate, BuildTypeSettings>() {
      public BuildTypeOrTemplate createFrom(@NotNull final BuildTypeSettings source) {
        if (source instanceof SBuildType) {
          return new BuildTypeOrTemplate((SBuildType)source);
        } else {
          return new BuildTypeOrTemplate((BuildTypeTemplate)source);
        }
      }
    }).toArray(new BuildTypeOrTemplate[items.length]));
  }
}
