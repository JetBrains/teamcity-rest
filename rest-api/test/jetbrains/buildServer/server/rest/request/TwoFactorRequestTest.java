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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.controllers.fakes.FakeHttpServletRequest;
import jetbrains.buildServer.server.rest.data.finder.BaseFinderTest;
import jetbrains.buildServer.server.rest.data.TwoFactorSecretKeysUpdater;
import jetbrains.buildServer.server.rest.model.user.TwoFactorCredentials;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.TwoFactorPasswordGenerator;
import jetbrains.buildServer.serverSide.impl.auth.MockTwoFactorPasswordManager;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import jetbrains.buildServer.users.impl.UserImpl;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TwoFactorRequestTest extends BaseFinderTest<TwoFactorCredentials> {
  private static final PropertyKey SECRET_KEY_PROPERTY = new SimplePropertyKey(UserImpl.SECURED_USER_PROPERTY_PREFIX + "2fa-secret");
  private static final PropertyKey RECOVERY_KEY_PROPERTY = new SimplePropertyKey(UserImpl.SECURED_USER_PROPERTY_PREFIX + "2fa-recovery");
  private TwoFactorRequest myRequest;
  private MockTwoFactorPasswordManager myManager;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final TwoFactorPasswordGenerator generator = myFixture.getSingletonService(TwoFactorPasswordGenerator.class);
    myManager = new MockTwoFactorPasswordManager(myFixture);
    final TwoFactorSecretKeysUpdater keysUpdater = new TwoFactorSecretKeysUpdater(generator, myManager);
    myFixture.addService(keysUpdater);
    myRequest = new TwoFactorRequest();
    myRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));
  }

  @Test
  public void testSetupUserKey() throws Throwable {
    final SUser user = createUser("user");
    myFixture.getSecurityContext().runAs(user, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        TwoFactorCredentials credentials = myRequest.setupTwoFactor();
        myRequest.confirmTwoFactor(credentials.uuid.toString(), 0, new FakeHttpServletRequest());
        assertNotNull(myFixture.getSecurityContext().runAsSystem(() -> user.getPropertyValue(SECRET_KEY_PROPERTY)));
        assertNotNull(myFixture.getSecurityContext().runAsSystem(() -> user.getPropertyValue(RECOVERY_KEY_PROPERTY)));
      }
    });
  }

  @Test
  public void testSetupAdminKey() throws Throwable {
    final SUser admin = createAdmin("admin");
    myFixture.getSecurityContext().runAs(admin, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() {
        TwoFactorCredentials credentials = myRequest.setupTwoFactor();
        myRequest.confirmTwoFactor(credentials.uuid.toString(), 0, new FakeHttpServletRequest());
        assertNotNull(admin.getPropertyValue(SECRET_KEY_PROPERTY));
        assertNotNull(admin.getPropertyValue(RECOVERY_KEY_PROPERTY));
      }
    });
  }

  @Test
  public void testUserCanDeleteHisOwnKey() throws Throwable {
    final SUser user = createUser("user");
    myManager.setSecretKey(user, "secret");
    Set<String> recovery = new HashSet<>(Arrays.asList("a", "b"));
    myManager.setRecoveryKeys(user, recovery);
    myFixture.getSecurityContext().runAs(user, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() {
        myRequest.deleteTwoFactor("username:user");
      }
    });
    assertNull(user.getPropertyValue(SECRET_KEY_PROPERTY));
    assertNull(user.getPropertyValue(RECOVERY_KEY_PROPERTY));
  }

  @Test
  public void testAdminCanDeleteUserKey() throws Throwable {
    final SUser user = createUser("user");
    myManager.setSecretKey(user, "secret");
    final SUser admin = createAdmin("admin");
    myFixture.getSecurityContext().runAs(admin, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() {
        myRequest.deleteTwoFactor("username:user");
      }
    });
    assertNull(user.getPropertyValue(SECRET_KEY_PROPERTY));
  }

  @Test
  public void testKeyRegeneration() throws Throwable {
    final SUser user = createUser("user");
    myFixture.getSecurityContext().runAs(user, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        TwoFactorCredentials credentials = myRequest.setupTwoFactor();
        myRequest.confirmTwoFactor(credentials.uuid.toString(), 0, new FakeHttpServletRequest());
        final String hashedRecovery = myFixture.getSecurityContext().runAsSystem(() -> user.getPropertyValue(RECOVERY_KEY_PROPERTY));
        myRequest.serveRecoveryKeys();
        final String newHashedRecovery = myFixture.getSecurityContext().runAsSystem(() -> user.getPropertyValue(RECOVERY_KEY_PROPERTY));
        Assert.assertNotEquals(hashedRecovery, newHashedRecovery);
      }
    });
  }

  @Test
  public void testUserWithout2FACantRegenerateKeys() throws Throwable {
    final SUser user = createUser("user");
    myFixture.getSecurityContext().runAs(user, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() {
        checkException(AccessDeniedException.class, () -> myRequest.serveRecoveryKeys(), "regenerate recovery for not existing 2FA");
      }
    });
  }

  @Test
  public void testUnconfirmedKey() throws Throwable {
    final SUser user = createUser("user");
    myFixture.getSecurityContext().runAs(user, new SecurityContextEx.RunAsAction() {
      @Override
      public void run() throws Throwable {
        myRequest.setupTwoFactor();
        assertNull(myFixture.getSecurityContext().runAsSystem(() -> user.getPropertyValue(SECRET_KEY_PROPERTY)));
        assertNull(myFixture.getSecurityContext().runAsSystem(() -> user.getPropertyValue(RECOVERY_KEY_PROPERTY)));
      }
    });
  }

}