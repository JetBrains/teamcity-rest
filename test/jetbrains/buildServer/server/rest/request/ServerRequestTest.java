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
import jetbrains.buildServer.server.rest.model.server.LicensingData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ServerRequestTest extends BaseServerTestCase {

  private ServerRequest myRequest;

  @BeforeClass
  public void refresh() {
    if(myFixture != null) {
      // When running as an ant task, we do not recreate the server for each test class, so let's do that manually instead.
      myFixture.recreateServer(true);
    }
  }

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    BeanContext ctx = BaseFinderTest.getBeanContext(myFixture);
    PermissionChecker checker = new PermissionChecker(myServer.getSecurityContext(), myProjectManager);
    myFixture.addService(checker);

    myRequest = new ServerRequest();
    myRequest.initForTests(
      ctx.getServiceLocator(),
      ctx.getApiUrlBuilder(),
      new BeanFactory(null),
      ctx,
      checker
    );
  }

  @Test
  public void test_user_with_enough_permissions_should_get_licensing_data() throws Throwable {
    final SUser user = createUser("user");
    user.addRole(RoleScope.globalScope(), getSysAdminRole());

    LicensingData data = myRequest.getLicensingData("licenseUseExceeded,maxAgents,unlimitedAgents,agentsLeft,maxBuildTypes,unlimitedBuildTypes,buildTypesLeft,serverLicenseType");
    assertNotNull(data.licenseUseExceeded);
    assertNotNull(data.maxAgents);
    assertNotNull(data.unlimitedAgents);
    assertNotNull(data.getAgentsLeft());
  }

  @Test
  public void test_default_fields_in_licensing_data() throws Throwable {
    final SUser user = createUser("user");
    user.addRole(RoleScope.globalScope(), getSysAdminRole());

    LicensingData data = myRequest.getLicensingData("");
    assertNotNull(data.maxAgents);
    assertNotNull(data.getAgentsLeft());
    assertNotNull(data.serverLicenseType);
    assertNotNull(data.serverEffectiveReleaseDate);
    assertNotNull(data.licenseKeys);
  }

  @Test
  public void test_user_needs_manage_server_licenses_permission_to_access_licensing_data() throws Throwable {
    final SUser user = createUser("user");

    myFixture.getSecurityContext().runAs(user, () -> {
      LicensingData data = myRequest.getLicensingData("maxAgents,serverLicenseType,agentsLeft");
      assertNull(data.maxAgents);
      assertNull(data.serverLicenseType);
      assertNull(data.getAgentsLeft());
    });

    user.addRole(RoleScope.globalScope(), myFixture.getTestRoles().createRole(Permission.MANAGE_SERVER_LICENSES));

    myFixture.getSecurityContext().runAs(user, () -> {
      LicensingData data = myRequest.getLicensingData("maxAgents,serverLicenseType,agentsLeft");
      assertNotNull(data.maxAgents);
      assertNotNull(data.serverLicenseType);
      assertNotNull(data.getAgentsLeft());
    });
  }

  @Test(description = "TW-68673")
  public void test_user_needs_view_agent_details_permission_to_access_avaliable_agents() throws Throwable {
    final SUser user = createUser("user");

    user.addRole(RoleScope.globalScope(), myFixture.getTestRoles().createRole(Permission.VIEW_AGENT_DETAILS));

    myFixture.getSecurityContext().runAs(user, () -> {
      LicensingData data = myRequest.getLicensingData("maxAgents,serverLicenseType,agentsLeft");
      assertNull(data.maxAgents);
      assertNull(data.serverLicenseType);
      assertNotNull(data.getAgentsLeft());
    });
  }
}
