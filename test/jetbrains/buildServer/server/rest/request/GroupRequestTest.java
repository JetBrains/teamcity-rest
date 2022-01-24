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

import jetbrains.buildServer.groups.SUserGroup;
import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.group.Group;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 05/04/2016
 */
public class GroupRequestTest extends BaseFinderTest<UserGroup> {
  private GroupRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = new GroupRequest();
    myRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));
  }

  @Test
  void testUsers(){
    SUserGroup group1 = myFixture.createUserGroup("key1", "name1", "description");
    final SUser user1 = createUser("user1");
    final SUser user2 = createUser("user2");
    group1.addUser(user1);
    group1.addUser(user2);

    SUserGroup group2 = myFixture.createUserGroup("key2", "name2", "description");
    final SUser user3 = createUser("user3");
    group2.addUser(user3);

    {
      Group result = myRequest.serveGroup("key:key1", "$long,users($long)");
      assertNotNull(result.users);
      assertNotNull(result.users.users);
      assertEquals(2, result.users.users.size());
      assertEquals(Long.valueOf(user1.getId()), result.users.users.get(0).getId());
      assertEquals(Long.valueOf(user2.getId()), result.users.users.get(1).getId());
    }
  }

}
