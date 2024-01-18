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

package jetbrains.buildServer.server.rest.request.versionedSettings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsToken;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsTokens;
import jetbrains.buildServer.serverSide.CredentialsStorageEx;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class VersionedSettingsRequestTokensTest extends VersionedSettingsRequestBaseTestCase {

  private CredentialsStorageEx myProjectCredentialsStorage;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProjectCredentialsStorage = myFixture.getSingletonService(CredentialsStorageEx.class);
  }

  @Test
  public void testGetTokens() {
    Map<String, String> tokens = new HashMap<String, String>() {{
      put("token1", "value1");
      put("token2", "value2");
    }};
    myProjectCredentialsStorage.setSecureValuesForTokens(tokens, myProject);
    VersionedSettingsTokens receivedTokens = myRequest.getTokens(myProject.getExternalId(), null);
    assertEquals(receivedTokens.getTokens().size(), 2);
    assertTrue(receivedTokens.getTokens().stream().allMatch(it -> it.getValue() == null));
  }

  @Test(expectedExceptions = AccessDeniedException.class)
  public void testGetTokensWrongUser() {
    makeLoggedIn(createUser("user"));
    myRequest.getTokens(myProject.getExternalId(), null);
  }

  @Test
  public void testSetTokens() {
    Map<String, String> tokens = new HashMap<String, String>() {{
      put("token1", "value1");
      put("token2", "value2");
    }};
    myProjectCredentialsStorage.setSecureValuesForTokens(tokens, myProject);

    VersionedSettingsToken newToken = new VersionedSettingsToken();
    newToken.setName("token3");
    newToken.setValue("value3");

    myRequest.setTokens(myProject.getExternalId(), new VersionedSettingsTokens(Collections.singletonList(newToken)));
    Map<String, String> storedValues = myProjectCredentialsStorage.getStoredValues(myProject);
    assertEquals(storedValues.entrySet().size(), 3);
  }

  @Test(expectedExceptions = AuthorizationFailedException.class)
  public void testSetTokensWrongUser() {
    loginAsUserWithOnlyViewProjectPermission();
    myRequest.setTokens(myProject.getExternalId(), new VersionedSettingsTokens(Collections.emptyList()));
  }

  @Test
  public void testRemoveTokens() {
    Map<String, String> tokens = new HashMap<String, String>() {{
      put("token1", "value1");
      put("token2", "value2");
    }};
    myProjectCredentialsStorage.setSecureValuesForTokens(tokens, myProject);

    VersionedSettingsToken tokenToRemove = new VersionedSettingsToken();
    tokenToRemove.setName("token1");

    myRequest.deleteTokens(myProject.getExternalId(), new VersionedSettingsTokens(Collections.singletonList(tokenToRemove)));

    Map<String, String> storedValues = myProjectCredentialsStorage.getStoredValues(myProject);
    assertEquals(storedValues.entrySet().size(), 1);
    assertEquals(storedValues.get("token2"), "value2");
  }
  @Test(expectedExceptions = AuthorizationFailedException.class)
  public void testRemoveTokensWrongUser() {
    loginAsUserWithOnlyViewProjectPermission();
    myRequest.deleteTokens(myProject.getExternalId(), new VersionedSettingsTokens(Collections.emptyList()));
  }
}
