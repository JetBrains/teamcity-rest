/*
 * Copyright 2000-2024 JetBrains s.r.o.
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

import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.server.CleanupCron;
import jetbrains.buildServer.server.rest.model.server.CleanupDaily;
import jetbrains.buildServer.server.rest.model.server.CleanupSettings;
import jetbrains.buildServer.server.rest.model.server.LicensingData;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BeanFactory;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.MockAuthorityHolder;
import jetbrains.buildServer.users.SUser;
import org.testng.Assert;
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
    MockAuthorityHolder mockUser = new MockAuthorityHolder();

    myFixture.getSecurityContext().runAs(mockUser, () -> {
      LicensingData data = myRequest.getLicensingData("maxAgents,serverLicenseType,agentsLeft");
      assertNull(data.maxAgents);
      assertNull(data.serverLicenseType);
      assertNull(data.getAgentsLeft());
    });

    mockUser.globalPerms = new Permissions(Permission.MANAGE_SERVER_LICENSES);
    myFixture.getSecurityContext().runAs(mockUser, () -> {
      LicensingData data = myRequest.getLicensingData("maxAgents,serverLicenseType,agentsLeft");
      assertNotNull(data.maxAgents);
      assertNotNull(data.serverLicenseType);
      assertNotNull(data.getAgentsLeft());
    });
  }

  @Test(description = "TW-68673")
  public void test_user_needs_view_agent_details_permission_to_access_avaliable_agents() throws Throwable {
    MockAuthorityHolder mockUser = new MockAuthorityHolder();

    mockUser.globalPerms = new Permissions(Permission.VIEW_AGENT_DETAILS);

    myFixture.getSecurityContext().runAs(mockUser, () -> {
      LicensingData data = myRequest.getLicensingData("maxAgents,serverLicenseType,agentsLeft");
      assertNull(data.maxAgents);
      assertNull(data.serverLicenseType);
      assertNotNull(data.getAgentsLeft());
    });
  }

  @Test
  public void test_cleanup() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS));
    makeLoggedIn(user);

    CleanupSettings data = myRequest.getCleanupSettings();
    assertNotNull(data.enabled);
    assertNotNull(data.maxCleanupDuration);
    assertNotNull(data.daily);
    assertNotNull(data.daily.hour);
    assertNotNull(data.daily.minute);
    assertNull(data.cron);
  }

  @Test
  public void test_cleanup_without_permissions() {
    SUser user = createUser("user");
    assertFalse(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS));
    makeLoggedIn(user);

    try {
      myRequest.getCleanupSettings();
    } catch (AuthorizationFailedException e) {
      return;
    }
    fail("Should not have unauthorized access");
  }

  @Test
  public void test_cleanup_set_without_permissions() {
    SUser user = createUser("user");
    assertFalse(user.isPermissionGrantedGlobally(Permission.CONFIGURE_SERVER_DATA_CLEANUP));
    makeLoggedIn(user);

    try {
      myRequest.setCleanupSettings(new CleanupSettings());
    } catch (AuthorizationFailedException e) {
      return;
    }
    fail("Should not have unauthorized access");
  }

  @Test
  public void test_cleanup_set() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CONFIGURE_SERVER_DATA_CLEANUP));
    makeLoggedIn(user);

    CleanupSettings currentSettings = myRequest.getCleanupSettings();
    assertFalse(currentSettings.enabled);
    Assert.assertNotEquals(60, currentSettings.maxCleanupDuration);
    Assert.assertNotEquals(15, currentSettings.daily.hour);
    Assert.assertNotEquals(30, currentSettings.daily.minute);

    CleanupSettings newSettings = new CleanupSettings();
    newSettings.enabled = true;
    newSettings.maxCleanupDuration = 60;
    newSettings.daily = new CleanupDaily();
    newSettings.daily.hour = 15;
    newSettings.daily.minute = 30;
    CleanupSettings result = myRequest.setCleanupSettings(newSettings);

    assertTrue(result.enabled);
    assertEquals(Integer.valueOf(60), result.maxCleanupDuration);
    assertNotNull(result.daily);
    assertEquals(Integer.valueOf(15), result.daily.hour);
    assertEquals(Integer.valueOf(30), result.daily.minute);
    assertNull(result.cron);
  }

  @Test
  public void test_cleanup_set_cron() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CONFIGURE_SERVER_DATA_CLEANUP));
    makeLoggedIn(user);

    CleanupSettings data = new CleanupSettings();
    data.cron = new CleanupCron();
    data.cron.minute = "1";
    data.cron.hour = "2";
    data.cron.day = "3";
    data.cron.month = "4";
    data.cron.dayWeek = "?";
    CleanupSettings result = myRequest.setCleanupSettings(data);

    assertNull(result.daily);
    assertNotNull(result.cron);
    assertEquals("1", result.cron.minute);
    assertEquals("2", result.cron.hour);
    assertEquals("3", result.cron.day);
    assertEquals("4", result.cron.month);
    assertEquals("?", result.cron.dayWeek);
  }

  @Test
  public void test_cleanup_set_both_daily_and_cron() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CONFIGURE_SERVER_DATA_CLEANUP));
    makeLoggedIn(user);

    CleanupSettings data = new CleanupSettings();
    data.daily = new CleanupDaily();
    data.cron = new CleanupCron();

    try {
      myRequest.setCleanupSettings(data);
    } catch (BadRequestException e) {
      return;
    }
    fail("Should get exception");
  }
}