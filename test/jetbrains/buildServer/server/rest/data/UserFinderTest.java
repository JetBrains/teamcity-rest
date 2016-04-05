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

package jetbrains.buildServer.server.rest.data;

import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import jetbrains.buildServer.server.rest.errors.NotFoundException;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.impl.auth.SecurityContextImpl;
import jetbrains.buildServer.users.SUser;
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

    {
      SUser result = myUserFinder.getItem("id:" + user1.getId());
      assertNotNull(result);
      assertEquals(user1.getId(), result.getId());
    }

    BaseFinderTest.checkException(NotFoundException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem("id:" + user1.getId() + "1");
      }
    }, null);

    {
      SUser result = myUserFinder.getItem("username:" + user2.getUsername());
      assertNotNull(result);
      assertEquals(user2.getId(), result.getId());
    }

    {
      SUser result = myUserFinder.getItem("username:" + "user1");
      assertNotNull(result);
      assertEquals(user1.getId(), result.getId());
    }

    {
      SUser result = myUserFinder.getItem("username:" + "USER1");
      assertNotNull(result);
      assertEquals(user1.getId(), result.getId());
    }

    BaseFinderTest.checkException(NotFoundException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem("username:" + user2.getUsername() + "1");
      }
    }, null);

    {
      SUser result = myUserFinder.getItem("id:" + user1.getId() + ",username:" + "user1");
      assertNotNull(result);
      assertEquals(user1.getId(), result.getId());
    }

    BuildPromotionFinderTest.checkException(NotFoundException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem("id:" + user1.getId() + ",username:" + "user1" + "x");
      }
    }, null);

    BaseFinderTest.checkException(LocatorProcessException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem("xxx:yyy");
      }
    }, null);
  }

  @Test
  public void testCurrentUser() throws Throwable {
    final SUser user1 = createUser("user1");

    BaseFinderTest.checkException(NotFoundException.class, new Runnable() {
      public void run() {
        myUserFinder.getItem("current");
      }
    }, "getting current user");

    SecurityContextImpl securityContext = new SecurityContextImpl();
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
      SUser result = myUserFinder.getItem("current");
      assertNotNull(result);
      assertEquals(user1.getId(), result.getId());
    }

    SecurityContextImpl securityContext = new SecurityContextImpl();
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
        assertEquals(user1.getId(), result.getId());
      }
    });

    securityContext.runAsSystem(new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        SUser result = myUserFinder.getItem("current");
        assertNotNull(result);
        assertEquals(user1.getId(), result.getId());
      }
    });
  }

  @Test
  public void testGroupLocator() throws Throwable {
    SUserGroup group1 = myFixture.createUserGroup("key1", "name1", "description");
    final SUser user1 = createUser("user1");
    final SUser user2 = createUser("user2");
    group1.addUser(user1);
    group1.addUser(user2);

    SUserGroup group2 = myFixture.createUserGroup("key2", "name2", "description");
    final SUser user3 = createUser("user3");
    group2.addUser(user3);

    check("group:(key:" + "key1" + ")", user1, user2);
    check("group:(key:" + "key2" + ")", user3);
    check("group:(key:" + getUserGroupManager().getAllUsersGroup().getKey() + ")", user1, user2, user3);

    check("group:(key:" + "key1" + "),username:user1", user1);

    checkExceptionOnItemSearch(NotFoundException.class, "group:(key:XXX)");
    checkExceptionOnItemsSearch(NotFoundException.class, "group:(key:XXX)");
  }
}
