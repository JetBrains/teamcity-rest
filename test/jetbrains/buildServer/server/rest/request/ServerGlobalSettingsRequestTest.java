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

import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.data.PermissionChecker;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.server.GlobalSettings;
import jetbrains.buildServer.server.rest.service.rest.ServerGlobalSettingsRestService;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.crypt.BaseEncryptionStrategy;
import jetbrains.buildServer.serverSide.crypt.EncryptionSettings;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ServerGlobalSettingsRequestTest extends BaseServerTestCase {
  private ServerGlobalSettingsRequest myRequest;
  private ServiceLocator myServiceLocator;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    PermissionChecker checker = new PermissionChecker(myServer.getSecurityContext(), myProjectManager);
    myFixture.addService(checker);
    myRequest = new ServerGlobalSettingsRequest();
    BeanContext ctx = BaseFinderTest.getBeanContext(myFixture);
    myServiceLocator = ctx.getServiceLocator();

    ServerGlobalSettingsRestService service = new ServerGlobalSettingsRestService(checker, myServiceLocator);
    EncryptionSettings encryptionSettings = new EncryptionSettings();
    encryptionSettings.setEncryptionStrategy(BaseEncryptionStrategy.BASE_STRATEGY_NAME);
    encryptionSettings.setEncryptionKey(BaseEncryptionStrategy.BASE_KEY_NAME);
    service.initForTests(encryptionSettings);

    myRequest.initForTests(service);
  }

  @Test
  public void test_get_genral_settings() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS) ||
               user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    GlobalSettings data = myRequest.getGlobalSettings();
    assertEquals("system/artifacts", data.getArtifactDirectories());
    assertEquals("http://localhost:8111", data.getRootUrl());
    assertEquals(314572800L, data.getMaxArtifactSize().longValue()); // 300MB
    assertEquals(1000L, data.getMaxArtifactsNumber().longValue());
    assertEquals(0, data.getDefaultExecutionTimeout().intValue());
    assertEquals(0, data.getDefaultVCSCheckInterval().intValue());
    assertFalse(data.getEnforceDefaultVCSCheckInterval());
    assertEquals(60, data.getDefaultQuietPeriod().intValue());
    assertTrue(data.getArtifactsDomainIsolation());
    assertEquals("", data.getArtifactsUrl());
    assertFalse(data.getUseEncryption());
  }

  @Test
  public void test_set_global_settings() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    GlobalSettings settings = new GlobalSettings();
    settings.setArtifactDirectories("test");
    settings.setRootUrl("http://test");
    settings.setMaxArtifactSize(1000L);
    settings.setMaxArtifactsNumber(10L);
    settings.setDefaultExecutionTimeout(60);
    settings.setDefaultVCSCheckInterval(30);
    settings.setEnforceDefaultVCSCheckInterval(true);
    settings.setDefaultQuietPeriod(100);
    settings.setArtifactsDomainIsolation(false);
    settings.setArtifactsUrl("http://artifacts");

    GlobalSettings result = myRequest.setGlobalSettings(settings);
    assertEquals("test", result.getArtifactDirectories());
    assertEquals("http://test", result.getRootUrl());
    assertEquals(1000L, result.getMaxArtifactSize().longValue()); // 300MB
    assertEquals(10L, result.getMaxArtifactsNumber().longValue());
    assertEquals(60, result.getDefaultExecutionTimeout().intValue());
    assertEquals(30, result.getDefaultVCSCheckInterval().intValue());
    assertTrue(result.getEnforceDefaultVCSCheckInterval());
    assertEquals(100, result.getDefaultQuietPeriod().intValue());
    assertFalse(result.getArtifactsDomainIsolation());
    assertEquals("http://artifacts", result.getArtifactsUrl());
  }

  @Test
  public void test_get_global_settings_without_rights() {
    SUser user = createUser("user");
    assertFalse(user.isPermissionGrantedGlobally(Permission.VIEW_SERVER_SETTINGS));
    assertFalse(user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    assertExceptionThrown(
      () -> myRequest.getGlobalSettings(),
      AuthorizationFailedException.class
    );
  }

  @Test
  public void test_set_global_settings_without_rights() {
    SUser user = createUser("user");
    assertFalse(user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    assertExceptionThrown(
      () -> myRequest.setGlobalSettings(new GlobalSettings()),
      AuthorizationFailedException.class
    );
  }

  @Test
  public void test_set_encryption() {
    SUser user = createAdmin("user");
    assertTrue(user.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS));
    makeLoggedIn(user);

    GlobalSettings settings = myRequest.getGlobalSettings();
    assertFalse(settings.getUseEncryption());
    GlobalSettings result;

    settings.setUseEncryption(true);
    settings.setEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZg==");
    result = myRequest.setGlobalSettings(settings);
    assertTrue(result.getUseEncryption());

    settings.setEncryptionKey(null);
    assertExceptionThrown(
      () -> myRequest.setGlobalSettings(settings),
      BadRequestException.class
    );

    settings.setUseEncryption(null);
    result = myRequest.setGlobalSettings(settings);
    assertTrue(result.getUseEncryption());

    settings.setUseEncryption(false);
    result = myRequest.setGlobalSettings(settings);
    assertFalse(result.getUseEncryption());
  }
}
