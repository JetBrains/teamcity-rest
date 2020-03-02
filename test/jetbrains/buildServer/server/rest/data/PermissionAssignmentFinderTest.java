/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import java.util.List;
import java.util.function.Supplier;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 * Date: 19/09/2017
 */
@Test
public class PermissionAssignmentFinderTest extends BaseFinderTest<PermissionAssignmentData>{

  private SUser myUser1;
  private ProjectEx myProject1;
  private ProjectEx myProject11;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);
    myUser1 = createUser("user1");
    setFinder(new PermissionAssignmentFinder(myUser1, myFixture));
    myProject.remove();
    
    myProject1 = createProject("project1");
    myProject11 = myProject1.createProject("project11", "project11");
  }

  private void checkUnordered(@Nullable final String locator, @NotNull final PermissionAssignmentData... items) {
    check(locator, BaseFinderTest::getDescription, BaseFinderTest::getDescription, getFinder(), new UnorderedMatchStrategy(), items);
  }

  @Test
  public void testProjectPermissions() throws Exception {
    Role roleProject = getTestRoles().createRole(Permission.VIEW_PROJECT, Permission.RUN_BUILD);
    String projectId = myProject1.getProjectId();
    myUser1.addRole(RoleScope.projectScope(projectId), roleProject);

    checkUnordered(null, pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.VIEW_PROJECT, getRootProject().getProjectId()), pa(Permission.RUN_BUILD, projectId), pa(Permission.VIEW_PROJECT, projectId),
                   pa(Permission.RUN_BUILD, myProject11.getProjectId()), pa(Permission.VIEW_PROJECT, myProject11.getProjectId()));
    checkUnordered("global:any", pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.VIEW_PROJECT, getRootProject().getProjectId()), pa(Permission.RUN_BUILD, projectId), pa(Permission.VIEW_PROJECT, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()), pa(Permission.VIEW_PROJECT, myProject11.getProjectId()));
    checkUnordered("global:true", pa(Permission.CHANGE_OWN_PROFILE));
    checkUnordered("global:false", pa(Permission.VIEW_PROJECT, getRootProject().getProjectId()), pa(Permission.RUN_BUILD, projectId), pa(Permission.VIEW_PROJECT, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()), pa(Permission.VIEW_PROJECT, myProject11.getProjectId()));
    checkUnordered("project:(id:" + myProject1.getExternalId() + ")", pa(Permission.RUN_BUILD, projectId), pa(Permission.VIEW_PROJECT, projectId));
    checkUnordered("project:(id:" + getRootProject().getExternalId() + ")", pa(Permission.VIEW_PROJECT, getRootProject().getProjectId()));
    checkUnordered("permission:" + "RUN_BUILD", pa(Permission.RUN_BUILD, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()));
    checkUnordered("permission:" + "run_build" + ",global:true");
    checkUnordered("permission:" + "view_PrOjEcT", pa(Permission.VIEW_PROJECT, getRootProject().getProjectId()), pa(Permission.VIEW_PROJECT, projectId), pa(Permission.VIEW_PROJECT, myProject11.getProjectId()));
    checkUnordered("permission:" + "view_project" + ",project:" + projectId, pa(Permission.VIEW_PROJECT, projectId));

    ProjectEx project2 = createProject("prj2", "prj2");
    Role role2 = getTestRoles().createRole(Permission.RUN_BUILD);
    myUser1.addRole(RoleScope.projectScope(project2.getProjectId()), role2);

    checkUnordered("permission:run_build,project:(item:(id:" + myProject1.getExternalId() + "),item:(id:" + project2.getExternalId() + "))", pa(Permission.RUN_BUILD, projectId),
                   pa(Permission.RUN_BUILD, project2.getProjectId()));

    checkUnordered("item:(permission:view_project,project:(id:" + myProject1.getExternalId() + ")),item:(permission:view_project,project:(id:" + project2.getExternalId() + "))",
                   pa(Permission.VIEW_PROJECT, projectId));

    checkUnordered("item:(permission:view_project,project:(id:" + project2.getExternalId() + ")),item:(permission:run_build,project:(id:" + project2.getExternalId() + "))",
                   pa(Permission.RUN_BUILD, project2.getProjectId()));

    checkUnordered("item:(permission:CHANGE_OWN_PROFILE),item:(permission:run_build,project:(id:" + myProject1.getExternalId() + "))",
                   pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.RUN_BUILD, projectId));

    checkExceptionOnItemsSearch(BadRequestException.class, "permission:XXX");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "project:(id:XXX)");
  }

  @Test
  public void testProjectPermissionsAssignedGlobally() throws Exception {
    Role roleProject = getTestRoles().createRole(Permission.VIEW_PROJECT, Permission.RUN_BUILD, Permission.ADMINISTER_AGENT);
    String projectId = myProject1.getProjectId();
    myUser1.addRole(RoleScope.globalScope(), roleProject);

    checkUnordered(null, pa(Permission.RUN_BUILD), pa(Permission.VIEW_PROJECT), pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.ADMINISTER_AGENT));
    checkUnordered("global:true", pa(Permission.RUN_BUILD), pa(Permission.VIEW_PROJECT), pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.ADMINISTER_AGENT));
    checkUnordered("global:false");
    checkUnordered("project:(internalId:" + projectId + ")", pa(Permission.RUN_BUILD), pa(Permission.VIEW_PROJECT));
    checkUnordered("project:(internalId:" + getRootProject().getProjectId() + ")", pa(Permission.RUN_BUILD), pa(Permission.VIEW_PROJECT));
    checkUnordered("permission:" + "run_build", pa(Permission.RUN_BUILD));
    checkUnordered("permission:" + "run_build" + ",project:" + projectId, pa(Permission.RUN_BUILD));
  }

  @Test
  public void testProjectPermissionsAssignedOnRoot() throws Exception {
    Role roleProject = getTestRoles().createRole(Permission.VIEW_PROJECT, Permission.RUN_BUILD, Permission.ADMINISTER_AGENT);
    String projectId = myProject1.getProjectId();
    myUser1.addRole(RoleScope.projectScope(getRootProject().getProjectId()), roleProject);
    //TeamCity API issue: this has the same effect as globally assigned role, but permissions are per-project in the case. Thus results differ (e.g. see "global:true")

    checkUnordered(null, pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.ADMINISTER_AGENT), pa(Permission.RUN_BUILD, getRootProject().getProjectId()), pa(Permission.VIEW_PROJECT, getRootProject().getProjectId()), pa(Permission.RUN_BUILD, projectId), pa(Permission.VIEW_PROJECT, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()), pa(Permission.VIEW_PROJECT, myProject11.getProjectId()));
    checkUnordered("global:true", pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.ADMINISTER_AGENT));
    checkUnordered("global:false", pa(Permission.RUN_BUILD, getRootProject().getProjectId()), pa(Permission.VIEW_PROJECT, getRootProject().getProjectId()), pa(Permission.RUN_BUILD, projectId), pa(Permission.VIEW_PROJECT, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()), pa(Permission.VIEW_PROJECT, myProject11.getProjectId()));
    checkUnordered("project:(internalId:" + projectId + ")", pa(Permission.RUN_BUILD, projectId), pa(Permission.VIEW_PROJECT, projectId));
    checkUnordered("project:(internalId:" + getRootProject().getProjectId() + ")", pa(Permission.RUN_BUILD, getRootProject().getProjectId()), pa(Permission.VIEW_PROJECT, getRootProject().getProjectId()));
    checkUnordered("permission:" + "run_build", pa(Permission.RUN_BUILD, getRootProject().getProjectId()), pa(Permission.RUN_BUILD, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()));
    checkUnordered("permission:" + "run_build" + ",project:" + projectId, pa(Permission.RUN_BUILD, projectId));
  }

  @Test
  public void testMixedPermissions() throws Exception {
    Role roleProject = getTestRoles().createRole(Permission.ADMINISTER_AGENT, Permission.RUN_BUILD);
    String projectId = myProject1.getProjectId();

    myUser1.addRole(RoleScope.projectScope(projectId), roleProject);

    checkUnordered(null, pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.ADMINISTER_AGENT), pa(Permission.RUN_BUILD, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()));
    checkUnordered("global:true", pa(Permission.CHANGE_OWN_PROFILE), pa(Permission.ADMINISTER_AGENT));
    checkUnordered("global:false", pa(Permission.RUN_BUILD, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()));
    checkUnordered("project:(internalId:" + projectId + ")", pa(Permission.RUN_BUILD, projectId));
    checkUnordered("project:(internalId:" + getRootProject().getProjectId() + ")");
    checkUnordered("permission:" + "run_build", pa(Permission.RUN_BUILD, projectId), pa(Permission.RUN_BUILD, myProject11.getProjectId()));
    checkUnordered("permission:" + "run_build" + ",project:" + projectId, pa(Permission.RUN_BUILD, projectId));
  }

  @Test
  public void testSysAdmin() throws Exception {
    myUser1.addRole(RoleScope.globalScope(), getSysAdminRole());
    String projectId = myProject1.getProjectId();

    List<PermissionAssignmentData> all = getFinder().getItems(null).myEntries;
    assertTrue(all.contains(pa(Permission.VIEW_PROJECT)));
    assertTrue(all.contains(pa(Permission.CHANGE_OWN_PROFILE)));

    List<PermissionAssignmentData> global = getFinder().getItems("global:true").myEntries;
    assertEquals(all.size(), global.size());

    checkUnordered("global:false");

    List<PermissionAssignmentData> project1Permissions = getFinder().getItems("project:" + projectId).myEntries;
    assertEquals(Arrays.stream(Permission.values()).filter(p -> p.isProjectAssociationSupported()).count(), project1Permissions.size());

    checkUnordered("permission:" + "run_build", pa(Permission.RUN_BUILD));
    checkUnordered("permission:" + "run_build" + ",project:" + projectId, pa(Permission.RUN_BUILD));
  }


  private PermissionAssignmentData pa(final Permission permission, final String projectId) {
    return new PermissionAssignmentData(permission, projectId);
  }

  private PermissionAssignmentData pa(final Permission permission) {
    return new PermissionAssignmentData(permission);
  }

  private static class UnorderedMatchStrategy implements CollectionsMatchStrategy<PermissionAssignmentData, PermissionAssignmentData> {
    @Override
    public void matchCollection(@NotNull final PermissionAssignmentData[] items, @NotNull final List<PermissionAssignmentData> result) {
      assertContains(result, items);
    }

    @Override
    public void matchSingle(final PermissionAssignmentData[] items, final Supplier<PermissionAssignmentData> singleResultSupplier) {
      if (items.length == 0) {
        assertExceptionThrown(singleResultSupplier::get, NotFoundException.class);
      } else {
        assertContains(Arrays.asList(items), singleResultSupplier.get()); //this is some strange logic defeating the purpose of the signle item search test
      }
    }
  }
}
