/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ServerRequestTest extends BaseServerTestCase {

  private ServerRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    BeanContext ctx = BaseFinderTest.getBeanContext(myFixture);

    myRequest = new ServerRequest();
    myRequest.initForTests(
      ctx.getServiceLocator(),
      ctx.getApiUrlBuilder(),
      new BeanFactory(null),
      ctx,
      new PermissionChecker(myServer.getSecurityContext(), myProjectManager)
    );
  }

  @Test
  public void test_user_needs_view_server_settings_permission_to_access_licensing_data() throws Throwable {
    final SUser user = createUser("user");

    myFixture.getSecurityContext().runAs(user, () -> {
      assertExceptionThrown(
        () -> myRequest.getLicensingData("maxAgents,unlimitedBuildTypes,agentsLeft"),
        AuthorizationFailedException.class
      );
    });

    user.addRole(RoleScope.globalScope(), getSysAdminRole());

    myFixture.getSecurityContext().runAs(user, () -> myRequest.getLicensingData("maxAgents,unlimitedBuildTypes,agentsLeft"));
  }

  @Test(description = "TW-68673")
  public void test_user_needs_view_agent_details_permission_to_access_avaliable_agents() throws Throwable {
    final SUser user = createUser("user");

    myFixture.getSecurityContext().runAs(user, () -> {
      assertExceptionThrown(
        () -> myRequest.getLicensingData("agentsLeft"),
        AuthorizationFailedException.class
      );
    });

    user.addRole(RoleScope.globalScope(), myFixture.getTestRoles().createRole(Permission.VIEW_AGENT_DETAILS));

    myFixture.getSecurityContext().runAs(user, () -> {
      myRequest.getLicensingData("agentsLeft");
    });
  }
}
