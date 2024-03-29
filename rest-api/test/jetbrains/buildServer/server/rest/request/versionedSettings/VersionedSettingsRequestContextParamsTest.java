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

import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsContextParameter;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsContextParameters;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatusTracker;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class VersionedSettingsRequestContextParamsTest extends VersionedSettingsRequestBaseTestCase {

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableVersionedSettings(myProject, myProject.createVcsRoot(myMockVcsSupport.getName(), "vcs_root", "VCS Root"));
  }

  @Test
  public void testGetContextParams() {
    myVersionedSettingsManager.setContextParameters(myProject, new HashMap<String, String>(){{
      put("param1", "value1");
      put("param2", "value2");
    }});

    VersionedSettingsContextParameters contextParameters = myRequest.getContextParameters(myProject.getExternalId());
    assertEquals(contextParameters.getParameters().size(), 2);

    assertNotNull(contextParameters.getParameters().stream().filter(it -> it.getName().equals("param1") && it.getValue().equals("value1")).findAny().orElse(null));
    assertNotNull(contextParameters.getParameters().stream().filter(it -> it.getName().equals("param2") && it.getValue().equals("value2")).findAny().orElse(null));
  }

  @Test
  public void testSetContextParams() {
    HashMap<String, String> params = new HashMap<String, String>() {{
      put("param1", "value1");
      put("param2", "value2");
    }};

    List<VersionedSettingsContextParameter> contextParameterList = params.entrySet().stream()
                                                                         .map(it -> new VersionedSettingsContextParameter(it.getKey(), it.getValue()))
                                                                         .collect(Collectors.toList());

    VersionedSettingsContextParameters contextParameters = new VersionedSettingsContextParameters(contextParameterList);
    myRequest.setContextParameters(myProject.getExternalId(), contextParameters);

    assertEquals(myVersionedSettingsManager.readConfig(myProject).getDslContextParameters(), params);
  }

  @Test
  public void testGetMissingContextParams() {
    myVersionedSettingsManager.setContextParameters(myProject, new HashMap<String, String>(){{
      put("param1", "value1");
      put("param2", "value2");
    }});

    VersionedSettingsStatusTracker statusTracker = myFixture.getSingletonService(VersionedSettingsStatusTracker.class);
    jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus originalStatus =
      new jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus(
        new Date(1000),
        jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsStatus.Type.WARN,
        "Some DSL params are missing"
      );
    originalStatus.setRequiredContextParameters(Arrays.asList("param1_no_value", "param2_no_value"));
    statusTracker.setStatus(Collections.singleton(myProject), originalStatus);

    VersionedSettingsContextParameters contextParameters = myRequest.getContextParameters(myProject.getExternalId());
    assertEquals(contextParameters.getParameters().size(), 4);

    assertNotNull(contextParameters.getParameters().stream().filter(it -> it.getName().equals("param1") && it.getValue().equals("value1")).findAny().orElse(null));
    assertNotNull(contextParameters.getParameters().stream().filter(it -> it.getName().equals("param2") && it.getValue().equals("value2")).findAny().orElse(null));
    assertNotNull(contextParameters.getParameters().stream().filter(it -> it.getName().equals("param1_no_value") && it.getValue() == null).findAny().orElse(null));
    assertNotNull(contextParameters.getParameters().stream().filter(it -> it.getName().equals("param2_no_value") && it.getValue() == null).findAny().orElse(null));
  }

  @Test(expectedExceptions = AccessDeniedException.class)
  public void testGetContextParamsWrongUser() {
    makeLoggedIn(createUser("user"));
    myRequest.getContextParameters(myProject.getExternalId());
  }

  @Test(expectedExceptions = AccessDeniedException.class)
  public void testSetContextParamsWrongUser() {
    loginAsUserWithOnlyViewProjectPermission();
    myRequest.setContextParameters(myProject.getExternalId(), new VersionedSettingsContextParameters(Collections.emptyList()));
  }
}
