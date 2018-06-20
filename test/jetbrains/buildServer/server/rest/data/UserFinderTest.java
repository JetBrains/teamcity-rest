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

import com.google.common.base.Stopwatch;
import jetbrains.buildServer.MockTimeService;
import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.auth.RoleImpl;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.ExceptionUtil;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 04/04/2016
 */
public class UserFinderTest extends BaseFinderTest<SUser> {
  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    setFinder(myUserFinder);
  }

  @Test
  public void testEmptyLocator() throws Throwable {
    final SUser user1 = createUser("user1");

    BaseFinderTest.checkException(BadRequestException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem(null);
      }
    }, "searching for users with null locator");

    BaseFinderTest.checkException(BadRequestException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem("");
      }
    }, "searching for users with empty locator");
  }

  @Test
  public void testSingleLocator() throws Throwable {
    final SUser user1 = createUser("user1");

    {
      SUser result = myUserFinder.getItem("user1");
      assertNotNull(result);
      assertEquals(user1.getId(), result.getId());
    }

    BaseFinderTest.checkException(NotFoundException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem("user1" + "2");
      }
    }, null);
  }

  @Test
  public void testBasic1() throws Throwable {
    final SUser user1 = createUser("user1");
    final SUser user2 = createUser(String.valueOf(user1.getId()));

    check("id:" + user1.getId(), user1);
    assertEquals(Long.valueOf(1), getFinder().getItems("id:" + user1.getId()).myActuallyProcessedCount);

    checkExceptionOnItemSearch(NotFoundException.class, "id:" + user1.getId() + "1");
    checkExceptionOnItemsSearch(NotFoundException.class, "id:" + user1.getId() + "1");

    check("username:" + user2.getUsername(), user2);
    assertEquals(Long.valueOf(1), getFinder().getItems("username:" + user2.getUsername()).myActuallyProcessedCount);

    check("username:" + "user1", user1);
    check("id:" + user1.getId() + ",username:" + "USER1", user1);
    check("username:" + "USER1", user1);

    checkExceptionOnItemSearch(NotFoundException.class, "username:" + user2.getUsername() + "1");
    checkExceptionOnItemsSearch(NotFoundException.class, "username:" + user2.getUsername() + "1");

    check("id:" + user1.getId() + ",username:" + "user1", user1);

    checkExceptionOnItemSearch(NotFoundException.class, "id:" + user1.getId() + ",username:" + "user1" + "x");
    check("id:" + user1.getId() + ",username:" + "user1" + "x");

    checkExceptionOnItemSearch(LocatorProcessException.class, "xxx:yyy");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "xxx:yyy");
  }

  @Test
  public void testInvalidLocators() throws Throwable {
    final SUser user10 = createUser("user1");

    check (null, user10);
    check("id:" + user10.getId(), user10);
    checkExceptionOnItemsSearch(LocatorProcessException.class, "aaa:bbb");
    checkExceptionOnItemsSearch(NotFoundException.class, "xxx");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "id:" + user10.getId() + ",aaa:bbb");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "id:" + user10.getId() + ",aaa:bbb");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "id:" + user10.getId() + ",xxx");

    checkExceptionOnItemSearch(LocatorProcessException.class, "aaa:bbb");
    checkExceptionOnItemSearch(NotFoundException.class, "xxx");
    checkExceptionOnItemSearch(LocatorProcessException.class, "id:" + user10.getId() + ",aaa:bbb");
    checkExceptionOnItemSearch(LocatorProcessException.class, "id:" + user10.getId() + ",xxx");

    try {
      getFinder().getItems("aaa:bbb");
      fail("No exception is thrown");
    } catch (Exception e) {
      String message = e.getMessage();
      assertContains(message, "username");
      assertContains(message, "id");
      assertContains(message, "group");
      assertContains(message, "role");
      assertNotContains(message, "hasPassword", false);
    }
  }

  @Test
  public void testCurrentUser() throws Throwable {
    final SUser user1 = createUser("user1");

    BaseFinderTest.checkException(NotFoundException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem("current");
      }
    }, "getting current user");

    SecurityContextImpl securityContext = myFixture.getSecurityContext();
    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        SUser result = myUserFinder.getItem("current");
        assertNotNull(result);
        assertEquals(user1.getId(), result.getId());
      }
    });

    BaseFinderTest.checkException(NotFoundException.class, new Runnable() {
      public void run() {
        try {
          securityContext.runAsSystem(new SecurityContextEx.RunAsAction() {
            @Override
            public void run() throws Throwable {
              myUserFinder.getItem("current");
            }
          });
        } catch (Throwable throwable) {
          ExceptionUtil.rethrowAsRuntimeException(throwable);
        }
      }
    }, "getting current user under system");
  }

  @Test
  public void testCurrentUserClash() throws Throwable {
    final SUser user1 = createUser("current");
    final SUser user2 = createUser("user1");

    {
      checkExceptionOnItemSearch(NotFoundException.class, "current");
    }

    SecurityContextImpl securityContext = myFixture.getSecurityContext();
    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        SUser result = myUserFinder.getItem("current");
        assertNotNull(result);
        assertEquals(user1.getId(), result.getId());
      }
    });

    securityContext.runAs(user2, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        SUser result = myUserFinder.getItem("current");
        assertNotNull(result);
        assertEquals(user2.getId(), result.getId());
      }
    });

    securityContext.runAsSystem(new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        checkExceptionOnItemSearch(NotFoundException.class, "current");
      }
    });
  }

  @Test
  public void testGroupLocator() throws Throwable {
    SUserGroup group1 = myFixture.createUserGroup("key1", "name1", "description");
    final SUser user1 = createUser("user1");
    final SUser user2 = createUser("user2");
    final SUser user25 = createUser("user25");
    group1.addUser(user1);
    group1.addUser(user2);
    group1.addUser(user25);

    SUserGroup group2 = myFixture.createUserGroup("key2", "name2", "description");
    final SUser user3 = createUser("user3");
    group2.addUser(user3);
    group2.addUser(user25);

    check (null, user1, user2, user25, user3);
    check("group:(key:" + group1.getKey() + ")", user1, user2, user25);
    check("group:(key:" + group2.getKey() + ")", user3, user25);
    check("group:(key:" + getUserGroupManager().getAllUsersGroup().getKey() + ")", user1, user2, user25, user3);

    check("group:(key:" + group1.getKey() + "),username:user1", user1);

    checkExceptionOnItemSearch(NotFoundException.class, "group:(key:XXX)");
    checkExceptionOnItemsSearch(NotFoundException.class, "group:(key:XXX)");

    check("group:(key:" + group1.getKey() + "),group:(key:" + group2.getKey() + ")", user25);
  }

  @Test
  public void testSecurity() throws Throwable {
    final SUser user1 = createUser("user1");
    final SUser user2 = createUser("user2");

    final SecurityContextImpl securityContext = myFixture.getSecurityContext();

    securityContext.runAs(user1, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        checkExceptionOnItemsSearch(AuthorizationFailedException.class, null);
        check("user2", user2); // this works as this is single item search actually
        checkExceptionOnItemsSearch(AuthorizationFailedException.class, "group:ALL_USERS_GROUP");

        check("user1", user1);

        checkException(AuthorizationFailedException.class, new Runnable() {
          @Override
          public void run() {
            ((UserFinder)getFinder()).getItem("user2", true);
          }
        }, null);
      }
    });
  }

  @Test
  public void testSearchByPermissions() throws Throwable {
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);

    final SUser user10 = createUser("user10");
    final SUser user20 = createUser("user20");
    final SUser user30 = createUser("user30");
    final SUser user40 = createUser("user40");
    final SUser user50 = createUser("user50");
    final SUser user60 = createUser("user60");
    final SUser user70 = createUser("user70");
    final SUser user100 = createUser("user100");

    final SUserGroup group10 = myFixture.createUserGroup("group1", "group 1", "");
    final SUserGroup group20 = myFixture.createUserGroup("group1.1", "group 1.1", "");
    group10.addSubgroup(group20);
    group20.addUser(user70);

    ProjectEx prj1 = createProject("prj1");
    ProjectEx prj1_1 = prj1.createProject("prj1_1", "prj1.1");
    ProjectEx prj3 = createProject("prj3");

    RoleImpl role10 = new RoleImpl("role10", "custom role", new Permissions(Permission.RUN_BUILD), null);
    myFixture.getRolesManager().addRole(role10);
    RoleImpl role20 = new RoleImpl("role20", "custom role", new Permissions(Permission.VIEW_PROJECT, Permission.CHANGE_SERVER_SETTINGS), myFixture.getRolesManager());
    role20.addIncludedRole(role10);
    myFixture.getRolesManager().addRole(role20);
    RoleImpl role30 = new RoleImpl("role30", "custom role", new Permissions(Permission.LABEL_BUILD, Permission.CANCEL_BUILD), myFixture.getRolesManager());
    myFixture.getRolesManager().addRole(role30);

    user10.addRole(RoleScope.globalScope(), getSysAdminRole());
    user30.addRole(RoleScope.projectScope(prj3.getProjectId()), role10);

    group10.addRole(RoleScope.projectScope(prj1.getProjectId()), role20);

    check(null, user10, user20, user30, user40, user50, user60, user70, user100);
    check("permission:(permission:run_build,project:(id:" + prj1_1.getExternalId() + "))", user10, user70);
    checkExceptionOnItemsSearch(LocatorProcessException.class, "permission:(permission:run_build,permission:label_build,project:(id:" + prj1_1.getExternalId() + "))");
    checkExceptionOnItemsSearch(LocatorProcessException.class, "permission:(permission:run_build,project:(id:a),project:(id:b))");

    assertContains(checkException(LocatorProcessException.class, () -> getFinder().getItems("permission:(project:(id:a))"), null).getMessage(), "Nothing found");

    check("permission:(permission:run_build)", user10); //global permission check

    check("permission:(permission:run_build,project:(item:" + prj3.getExternalId() + "))", user10, user30);
    check("permission:(permission:run_build,project:(item:" + prj1_1.getExternalId() + ",item:" + prj3.getExternalId() + "))",
          user10, user30, user70); //permission in one of the projects
    check("permission:(permission:run_build,project:(count:100))", user10, user30, user70);  //permission in any project of the first 100
  }

  @Test
  public void testSearchByRoles() throws Throwable {
    myFixture.getServerSettings().setPerProjectPermissionsEnabled(true);

    final SUser user10 = createUser("user10");
    final SUser user20 = createUser("user20");
    final SUser user30 = createUser("user30");
    final SUser user40 = createUser("user40");
    final SUser user50 = createUser("user50");
    final SUser user60 = createUser("user60");
    final SUser user70 = createUser("user70");
    final SUser user100 = createUser("user100");

    final SUserGroup group10 = myFixture.createUserGroup("group1", "group 1", "");
    final SUserGroup group20 = myFixture.createUserGroup("group1.1", "group 1.1", "");
    group10.addSubgroup(group20);
    group10.addUser(user60);
    group20.addUser(user70);

    ProjectEx prj1 = createProject("prj1");
    ProjectEx prj1_1 = prj1.createProject("prj1_1", "prj1.1");
    ProjectEx prj3 = createProject("prj3");

    RoleImpl role10 = new RoleImpl("role10", "custom role", new Permissions(Permission.LABEL_BUILD), null);
    myFixture.getRolesManager().addRole(role10);
    RoleImpl role20 = new RoleImpl("role20", "custom role", new Permissions(Permission.PIN_UNPIN_BUILD), myFixture.getRolesManager());
    role20.addIncludedRole(role10);
    myFixture.getRolesManager().addRole(role20);
    RoleImpl role30 = new RoleImpl("role30", "custom role", new Permissions(Permission.LABEL_BUILD, Permission.CANCEL_BUILD), myFixture.getRolesManager());
    myFixture.getRolesManager().addRole(role30);

    user10.addRole(RoleScope.globalScope(), getSysAdminRole());
    user20.addRole(RoleScope.globalScope(), getProjectAdminRole());
    user30.addRole(RoleScope.projectScope(prj1.getProjectId()), getProjectViewerRole());
    user40.addRole(RoleScope.projectScope(prj1_1.getProjectId()), getProjectViewerRole());
    user50.addRole(RoleScope.projectScope(prj3.getProjectId()), getProjectViewerRole());
    user50.addRole(RoleScope.globalScope(), role30);
    group10.addRole(RoleScope.projectScope(prj1.getProjectId()), role20);
    group10.addRole(RoleScope.projectScope(getRootProject().getProjectId()), getTestRoles().getProjectViewerRole());

    check(null, user10, user20, user30, user40, user50, user60, user70, user100);
    check("role:(scope:(project:(" + prj1_1.getExternalId() + ")),role:(id:" + getProjectAdminRole().getId() + "))", user20);
    check("role:(scope:(project:(" + prj1_1.getExternalId() + ")),role:(id:role10))", user60, user70);
    check("role:(item:(scope:(project:(" + prj1_1.getExternalId() + ")),role:(id:role10)),method:effective)", user60, user70);
    check("role:(item:(scope:(project:(" + prj1_1.getExternalId() + ")),role:(id:role10)))", user60, user70);
    check("role:(item:(scope:(project:(" + prj1_1.getExternalId() + ")),role:(id:role10)),method:byPermission)", user10, user20, user50, user60, user70);
    check("role:(scope:global)", user10, user20, user50);

    //todo: error locators
    checkExceptionOnItemsSearch(LocatorProcessException.class, "role:(aaa)");
  }

  @Test
  public void testHasPassword() throws Throwable {
    final SUser user10 = createUser("user10");
    final SUser user20 = createUser("user20");
    user20.setPassword("pwd");
    final SUser user30 = createUser("user30");
    user30.setPassword("");
    final SUser user40 = createUser("user40");
    user40.setPassword("aaa");
    user40.setPassword(null);

    check(null, user10, user20, user30, user40);

    check("hasPassword:true", user20, user30);
    check("hasPassword:false", user10, user40);
    check("hasPassword:any", user10, user20, user30, user40);

    check("username:user20,hasPassword:true", user20);
    check("username:user20,hasPassword:false");
    check("username:user20,hasPassword:any", user20);

    long delay = 500;
    setInternalProperty("rest.request.users.passwordCheckDelay.ms", String.valueOf(delay)); //disable delay in tests
    final Stopwatch start = new Stopwatch().start();
    check("password:pwd", user20);
    System.out.println("Elapsed ms: " + start.elapsedMillis());
    assertTrue(start.elapsedMillis() > 2 * delay - 100);  //check the elapsed time is at least twice the period (once for multiple items search, once - for single items search)
    assertTrue(start.elapsedMillis() < 3 * delay);  //check the elapsed time is not more then twice the time wait
    setInternalProperty("rest.request.users.passwordCheckDelay.ms", "0"); //disable delay in tests

    final Stopwatch start2 = new Stopwatch().start();
    check("password:pwd", user20);
    System.out.println("Elapsed ms: " + start2.elapsedMillis());
    assertTrue(start2.elapsedMillis() < delay - 1);  //check the elapsed time without wait is small

    check("password:()", user30);
  }


  @Test
  public void testLastLogin() throws Throwable {
    final SUser user10 = createUser("user10");
    final SUser user20 = createUser("user20");
    final SUser user30 = createUser("user30");

    final MockTimeService time = new MockTimeService(Dates.now().getTime());
    myServer.setTimeService(time);
    user10.setLastLoginTimestamp(time.getNow());
    time.jumpTo(600); //10 minutes
    user20.setLastLoginTimestamp(time.getNow());
    time.jumpTo(600); //another 10
    check(null, user10, user20, user30);
    check("lastLogin:-30m", user10, user20);
    check("lastLogin:-15m", user20);
    check("lastLogin:-15m,username:user20", user20);
    check("lastLogin:-5m");
  }

  @Test
  public void testNameEmail() throws Throwable {
    final SUser user05 = createUser("user05");
    final SUser user06 = createUser("user06");
    user06.updateUserAccount(user06.getUsername(), "User 06","user06@anotherAcme.com");
    final SUser user10 = createUser("user10");
    user10.updateUserAccount(user10.getUsername(), "User 10","user10@acme.com");
    final SUser user20 = createUser("user20");
    user20.updateUserAccount(user20.getUsername(), "User 20", "user20@acme.com");
    final SUser user30 = createUser("user30");
    user30.updateUserAccount(user30.getUsername(), "", "");
    final SUser user40 = createUser("user40");
    user40.updateUserAccount(user40.getUsername(), null, null);

    check(null, user05, user06, user10, user20, user30, user40);
    check("name:User 20", user20);
    check("name:(value:User .0,matchType:matches)", user10, user20);
    check("email:(value:@acme.com,matchType:ends-with)", user10, user20);
//    check("name:(matchType:exists)", user06, user10, user20, user30);
  }

  @Test
  public void testHelp() throws Throwable {
    String message = checkException(LocatorProcessException.class, () -> getFinder().getItems("$help"), null).getMessage();
    assertContains(message, "help requested");

    message = checkException(LocatorProcessException.class, () -> getFinder().getItem("$help"), null).getMessage();
    assertContains(message, "help requested");
  }

  @Test
  public void testLogicOps() throws Throwable {
    SUserGroup group1 = myFixture.createUserGroup("key1", "name1", "description");
    final SUser user1 = createUser("user1");
    final SUser user2 = createUser("user2");
    final SUser user3 = createUser("user3");
    final SUser user4 = createUser("user4");

    group1.addUser(user1);
    group1.addUser(user2);

    SUserGroup group2 = myFixture.createUserGroup("key2", "name2", "description");
    group2.addUser(user3);
    group2.addUser(user2);

    check (null, user1, user2, user3, user4);
    check("group:(key:" + group1.getKey() + ")", user1, user2);
    check("group:(key:" + group2.getKey() + ")", user3, user2);  //todo: should actually return sorted
    check("group:(key:" + group1.getKey() + "),group:(key:" + group2.getKey() + ")", user2);
    check("group:(key:" + group1.getKey() + "),and:(group:(key:" + group2.getKey() + "))", user2);
    check("and:(group:(key:" + group1.getKey() + "),group:(key:" + group2.getKey() + "))", user2);
    check("or:(group:(key:" + group1.getKey() + "),group:(key:" + group2.getKey() + "))", user1, user2, user3);
    check("not:(group:(key:" + group1.getKey() + "))", user3, user4);
    check("not:(or:(group:(key:" + group1.getKey() + "),group:(key:" + group2.getKey() + ")))", user4);

    checkExceptionOnItemsSearch(LocatorProcessException.class, "and:(group:(key:" + group1.getKey() + ")),and:group:(key:" + group2.getKey() + "))");  //Only single 'and' dimension is supported in locator
  }
}
