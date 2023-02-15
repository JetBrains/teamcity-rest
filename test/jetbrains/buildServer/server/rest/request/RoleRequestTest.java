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

import java.util.ArrayList;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.server.rest.model.role.Permissions;
import jetbrains.buildServer.server.rest.model.role.Role;
import jetbrains.buildServer.server.rest.model.role.Roles;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.auth.DefaultRoles;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RoleRequestTest extends BaseServerTestCase {
  private RoleRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    BeanContext context = BaseFinderTest.getBeanContext(myFixture);
    PermissionChecker checker = new PermissionChecker(myServer.getSecurityContext(), myProjectManager);
    myFixture.addService(checker);

    myRequest = new RoleRequest();
    myRequest.initForTests(
      context,
      checker
    );
  }

  @Test
  public void test_get_roles() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.VIEW_USER_PROFILE));
    makeLoggedIn(user);

    Roles data = myRequest.getRoles(null);
    assertNotNull(data.count);
    assertNotEmpty(data.items);
    Role role = data.items.get(0);
    assertNotNull(role.id);
    assertNotNull(role.name);
    assertNotNull(role.href);
    assertNull(role.permissions);
    assertNull(role.included);
  }

  @Test
  public void test_get_roles_fields() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Roles result = myRequest.getRoles("role(id,included(role(id)))");
    assertNull(result.count);
    Role role = result.items.stream().filter(r -> r.id.equals(DefaultRoles.PROJECT_DEVELOPER)).findFirst().orElse(null);
    assertNotNull(role);

    assertNotNull(role.id);
    assertNull(role.name);
    assertNull(role.permissions);
    assertNotNull(role.included);
    assertNull(role.included.count);

    Role role2 = role.included.items.get(0);
    assertNotNull(role2.id);
    assertNull(role2.name);
    assertNull(role2.permissions);
    assertNull(role2.included);
  }

  @Test
  public void test_get_roles_without_rights() {
    SUser user = createUser("user");
    assertFalse(user.isPermissionGrantedGlobally(Permission.CHANGE_USER));
    makeLoggedIn(user);

    assertExceptionThrown(
      () -> myRequest.getRoles(null),
      AuthorizationFailedException.class
    );
  }

  @Test
  public void test_get_role() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role role = myRequest.getRole(DefaultRoles.PROJECT_DEVELOPER, null);
    assertNotNull(role.id);
    assertNotNull(role.name);
    assertNotNull(role.href);
    assertNotNull(role.permissions.count);
    assertTrue(role.permissions.items.stream().anyMatch(
      p -> p.id.equalsIgnoreCase(Permission.RUN_BUILD.name()) && p.name.equals(Permission.RUN_BUILD.getDescription())
    ));
    assertNotNull(role.included.count);
    assertTrue(role.included.items.stream().anyMatch(
      r -> r.id.equals(DefaultRoles.PROJECT_VIEWER) && r.name != null
    ));
  }

  @Test
  public void test_create_role() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";

    Role result = myRequest.createRole(data, null);
    assertNotNull(result.id);
    assertNotNull(result.name);
    assertEmpty(result.permissions.items);
    assertEmpty(result.included.items);
  }

  @Test
  public void test_create_role_with_included_and_permissions() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    data.permissions = new Permissions();
    data.permissions.items = new ArrayList<>();
    data.permissions.items.add(new jetbrains.buildServer.server.rest.model.user.Permission(Permission.ADMINISTER_AGENT.name()));
    data.included = new Roles();
    data.included.items = new ArrayList<>();
    data.included.items.add(new Role(DefaultRoles.PROJECT_DEVELOPER));

    Role result = myRequest.createRole(data, null);
    assertNotEmpty(result.permissions.items);
    assertTrue(Permission.ADMINISTER_AGENT.name().equalsIgnoreCase(result.permissions.items.get(0).id));
    assertNotEmpty(result.included.items);
    assertEquals(DefaultRoles.PROJECT_DEVELOPER, result.included.items.get(0).id);
  }

  @Test
  public void test_create_role_invalid_permission() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    data.permissions = new Permissions();
    data.permissions.items = new ArrayList<>();
    data.permissions.items.add(new jetbrains.buildServer.server.rest.model.user.Permission("abc"));
    assertExceptionThrown(
      () -> myRequest.createRole(data, null),
      BadRequestException.class
    );
  }

  @Test
  public void test_create_role_invalid_included() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    data.included = new Roles();
    data.included.items = new ArrayList<>();
    data.included.items.add(new Role("abc"));
    assertExceptionThrown(
      () -> myRequest.createRole(data, null),
      BadRequestException.class
    );
  }

  @Test
  public void test_create_role_duplicate() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);
    Role data;

    data = new Role();
    data.name = "test";
    data.permissions = new Permissions();
    data.permissions.items = new ArrayList<>();
    data.permissions.items.add(new jetbrains.buildServer.server.rest.model.user.Permission(Permission.ADMINISTER_AGENT.name()));
    data.permissions.items.add(new jetbrains.buildServer.server.rest.model.user.Permission(Permission.ADMINISTER_AGENT.name()));
    data.included = new Roles();
    data.included.items = new ArrayList<Role>();
    data.included.items.add(new Role(DefaultRoles.PROJECT_DEVELOPER));
    data.included.items.add(new Role(DefaultRoles.PROJECT_DEVELOPER));

    Role result = myRequest.createRole(data, null);
    assertEquals(1, result.permissions.items.size());
    assertEquals(1, result.included.items.size());
  }

  @Test
  public void test_create_role_fields() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";

    Role result = myRequest.createRole(data, "id");
    assertNotNull(result.id);
    assertNull(result.name);
    assertNull(result.permissions);
    assertNull(result.included);
  }

  @Test
  public void test_delete_role() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    Role result = myRequest.createRole(data, null);

    myRequest.deleteRole(result.id);
    assertExceptionThrown(
      () -> myRequest.getRole(result.id, null),
      NotFoundException.class
    );
  }

  @Test
  public void test_add_permission() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    Role result1 = myRequest.createRole(data, null);

    Role result2 = myRequest.addPermission(result1.id, Permission.ADMINISTER_AGENT.name(), null);
    assertNotEmpty(result2.permissions.items);
    assertTrue(Permission.ADMINISTER_AGENT.name().equalsIgnoreCase(result2.permissions.items.get(0).id));
  }

@Test
  public void test_add_permission_lowercase() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    Role result1 = myRequest.createRole(data, null);

    Role result2 = myRequest.addPermission(result1.id, Permission.ADMINISTER_AGENT.name().toLowerCase(), null);
    assertNotEmpty(result2.permissions.items);
    assertTrue(Permission.ADMINISTER_AGENT.name().equalsIgnoreCase(result2.permissions.items.get(0).id));
  }

  @Test
  public void test_delete_permission() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    data.permissions = new Permissions();
    data.permissions.items = new ArrayList<>();
    data.permissions.items.add(new jetbrains.buildServer.server.rest.model.user.Permission(Permission.ADMINISTER_AGENT.name()));
    Role result1 = myRequest.createRole(data, null);

    Role result2 = myRequest.removePermission(result1.id, Permission.ADMINISTER_AGENT.name(), null);
    assertEmpty(result2.permissions.items);
  }

  @Test
  public void test_add_included() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    Role result1 = myRequest.createRole(data, null);

    Role result2 = myRequest.addIncludedRole(result1.id, DefaultRoles.PROJECT_DEVELOPER, null);
    assertNotEmpty(result2.included.items);
    assertEquals(DefaultRoles.PROJECT_DEVELOPER, result2.included.items.get(0).id);
  }

  @Test
  public void test_add_included_duplicate() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    Role result1 = myRequest.createRole(data, null);

    Role result2 = myRequest.addIncludedRole(result1.id, DefaultRoles.PROJECT_DEVELOPER, null);
    assertEquals(1, result2.included.items.size());
    Role result3 = myRequest.addIncludedRole(result1.id, DefaultRoles.PROJECT_DEVELOPER, null);
    assertEquals(1, result3.included.items.size());
  }

  @Test
  public void test_delete_included() {
    SUser user = createAdmin("user");
    makeLoggedIn(user);

    Role data = new Role();
    data.name = "test";
    data.included = new Roles();
    data.included.items = new ArrayList<>();
    data.included.items.add(new Role(DefaultRoles.PROJECT_DEVELOPER));
    Role result1 = myRequest.createRole(data, null);

    Role result2 = myRequest.removeIncludedRole(result1.id, DefaultRoles.PROJECT_DEVELOPER, null);
    assertEmpty(result2.included.items);
  }
}
