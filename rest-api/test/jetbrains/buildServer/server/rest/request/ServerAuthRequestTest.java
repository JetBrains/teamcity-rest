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

package jetbrains.buildServer.server.rest.request;

import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.parameters.EntityWithParameters;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.model.Fields;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.server.rest.model.server.AuthModule;
import jetbrains.buildServer.server.rest.model.server.AuthModules;
import jetbrains.buildServer.server.rest.model.server.AuthSettings;
import jetbrains.buildServer.server.rest.service.rest.ServerAuthRestService;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.serverSide.impl.auth.DefaultLoginModuleConstants.FREE_REGISTRATION_ALLOWED_KEY;

public class ServerAuthRequestTest extends BaseServerTestCase {
  private ServerAuthRequest myRequest;
  private ServiceLocator myServiceLocator;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    PermissionChecker checker = new PermissionChecker(myServer.getSecurityContext(), myProjectManager);
    myFixture.addService(checker);
    myRequest = new ServerAuthRequest();
    BeanContext ctx = BaseFinderTest.getBeanContext(myFixture);
    myServiceLocator = ctx.getServiceLocator();
    ServerAuthRestService service = new ServerAuthRestService(checker, myServiceLocator);
    myRequest.initForTests(service);
  }

  @Test
  public void test_get_auth_settings() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS) ||
               user.isPermissionGrantedGlobally(Permission.MANAGE_AUTHENTICATION_SETTINGS));
    makeLoggedIn(user);

    AuthSettings data = myRequest.getAuthSettings();
    assertFalse(data.getAllowGuest());
    assertEquals("guest", data.getGuestUsername());
    assertNull(data.getWelcomeText());
    assertFalse(data.getCollapseLoginForm());
    assertEquals(Boolean.valueOf(myFixture.getServerSettings().isPerProjectPermissionsEnabled()), data.getPerProjectPermissions());
    assertFalse(data.getEmailVerification());
    assertEquals(1, data.getModules().getModules().size());
    assertEquals("Default", data.getModules().getModules().get(0).getName());
    assertEquals("true", data.getModules().getModules().get(0).getProperties().getMap().get(FREE_REGISTRATION_ALLOWED_KEY));
  }

  @Test
  public void test_set_auth_settings() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.MANAGE_AUTHENTICATION_SETTINGS));
    makeLoggedIn(user);

    AuthSettings settings = new AuthSettings();
    settings.setAllowGuest(true);
    settings.setGuestUsername("test");
    settings.setWelcomeText("welcome");
    settings.setCollapseLoginForm(true);
    settings.setPerProjectPermissions(true);
    settings.setEmailVerification(true);

    settings.setModules(new AuthModules());
    AuthModule module1 = new AuthModule();
    module1.setName("Default");
    EntityWithParameters entity = Properties.createEntity(createMap(FREE_REGISTRATION_ALLOWED_KEY, "false"), null);
    module1.setProperties(new Properties(entity, false, null, null, Fields.ALL, myServiceLocator, null));
    AuthModule module2 = new AuthModule();
    module2.setName("mock");
    settings.getModules().setModules(Arrays.asList(module1, module2));

    AuthSettings result = myRequest.setAuthSettings(settings);
    assertTrue(result.getAllowGuest());
    assertEquals("test", result.getGuestUsername());
    assertEquals("welcome", result.getWelcomeText());
    assertTrue(result.getCollapseLoginForm());
    assertTrue(result.getPerProjectPermissions());
    assertTrue(result.getEmailVerification());

    List<AuthModule> modules = result.getModules().getModules();
    assertEquals(2, modules.size());
    assertEquals("Default", modules.get(0).getName());
    assertEquals("false", modules.get(0).getProperties().getMap().get(FREE_REGISTRATION_ALLOWED_KEY));
    assertEquals("mock", modules.get(1).getName());
  }

  @Test
  public void test_get_auth_settings_without_rights() {
    SUser user = createUser("user");
    assertFalse(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS));
    assertFalse(user.isPermissionGrantedGlobally(Permission.MANAGE_AUTHENTICATION_SETTINGS));
    makeLoggedIn(user);

    assertExceptionThrown(
      () -> myRequest.getAuthSettings(),
      AuthorizationFailedException.class
    );
  }

  @Test
  public void test_set_auth_settings_without_rights() {
    SUser user = createUser("user");
    assertFalse(user.isPermissionGrantedGlobally(Permission.MANAGE_AUTHENTICATION_SETTINGS));
    makeLoggedIn(user);

    assertExceptionThrown(
      () -> myRequest.setAuthSettings(new AuthSettings()),
      AuthorizationFailedException.class
    );
  }
}
