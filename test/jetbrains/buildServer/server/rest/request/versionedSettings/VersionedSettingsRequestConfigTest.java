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

import jetbrains.buildServer.server.rest.errors.AuthorizationFailedException;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.server.rest.model.versionedSettings.VersionedSettingsConfig;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VersionedSettingsRequestConfigTest extends VersionedSettingsRequestBaseTestCase {

  @Test
  public void testCreateConfigTest() {
    VersionedSettingsConfig config = new VersionedSettingsConfig();
    String vcsRootId = myProject.createVcsRoot(myMockVcsSupport.getName(), "ext1", "VCS Root").getExternalId();
    config.setVcsRootId(vcsRootId);
    config.setFormat("xml");
    config.setAllowUIEditing(true);
    config.setStoreSecureValuesOutsideVcs(true);
    config.setSynchronizationMode(VersionedSettingsConfig.SynchronizationMode.enabled);
    config.setBuildSettingsMode(VersionedSettingsConfig.BuildSettingsMode.useFromVCS);

    SUser user = createAdmin("user");
    makeLoggedIn(user);

    myRequest.setVersionedSettingsConfig(myProject.getExternalId(), config, null);

    jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig versionedSettingsConfig = myVersionedSettingsManager.readConfig(myProject);

    assertTrue(versionedSettingsConfig.isEnabled());
    assertEquals(versionedSettingsConfig.getBuildSettingsMode(), jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode.PREFER_VCS);
    assertEquals(versionedSettingsConfig.getCredentialsStorageType(), "credentialsJSON");
    assertEquals(versionedSettingsConfig.getFormat(), "xml");
    assertTrue(versionedSettingsConfig.isTwoWaySynchronization());
    assertEquals(versionedSettingsConfig.getVcsRootExternalId(), vcsRootId);
  }

  @Test(expectedExceptions = AuthorizationFailedException.class)
  public void testCreateConfigWithNoUser() {
    myRequest.setVersionedSettingsConfig(myProject.getExternalId(), new VersionedSettingsConfig(), null);
  }

  @Test(expectedExceptions = OperationException.class)
  public void testCreateNonPortableConfig() {
    VersionedSettingsConfig config = new VersionedSettingsConfig();
    config.setVcsRootId(myProject.createVcsRoot(myMockVcsSupport.getName(), "ext1", "VCS Root").getExternalId());
    config.setFormat("kotlin");
    config.setPortableDsl(false);

    SUser user = createAdmin("user");
    makeLoggedIn(user);
    myRequest.setVersionedSettingsConfig(myProject.getExternalId(), config, null);
  }

  @Test
  public void testConfigParams() {
    SVcsRoot vcsRoot = myProject.createVcsRoot(myMockVcsSupport.getName(), "ext1", "VCS Root");
    myFixture.enableVersionedSettings(myProject, vcsRoot);

    SUser user = createAdmin("user");
    makeLoggedIn(user);

    assertEquals(myRequest.getVersionedSettingsConfigParameter(myProject.getExternalId(), "vcsRootId"),
                 vcsRoot.getExternalId());

    myRequest.setVersionedSettingsConfigParameter(myProject.getExternalId(), "buildSettingsMode", "useFromVCS");
    assertEquals(myRequest.getVersionedSettingsConfigParameter(myProject.getExternalId(), "buildSettingsMode"), "useFromVCS");
  }

  @DataProvider(name = "configFields")
  private Object[][] getConfigFields() {
    return new Object[][] {
      {
        null,
        new ConfigFields(true, true, true, true, true, true, true)
      },
      {
        "synchronizationMode",
        new ConfigFields(true, false, false, false, false, false, false)
      },
      {
        "vcsRootId,format",
        new ConfigFields(false, true, false, false, true, false, false)
      },
      {
        "buildSettingsMode,allowUIEditing,storeSecureValuesOutsideVcs",
        new ConfigFields(false, false, false, true, false, true, true)
      }
    };
  }

  @Test(dataProvider = "configFields")
  public void testGetConfig(@Nullable String fields, @NotNull ConfigFields configFields) {
    myProject.createVcsRoot(myMockVcsSupport.getName(), "vcs_root_id", "My VCS Root");
    jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig versionedSettingsConfig =
      new jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig();
    versionedSettingsConfig.setEnabled(true);
    versionedSettingsConfig.setVcsRootExternalId("vcs_root_id");
    versionedSettingsConfig.setFormat("xml");
    versionedSettingsConfig.setTwoWaySynchronization(true);
    versionedSettingsConfig.setCredentialsStorageType("credentialsJSON");
    versionedSettingsConfig.setBuildSettingsMode(jetbrains.buildServer.serverSide.impl.versionedSettings.VersionedSettingsConfig.BuildSettingsMode.PREFER_VCS);
    versionedSettingsConfig.setShowSettingsChanges(true);
    myFixture.writeVersionedSettingsConfig(myProject, versionedSettingsConfig);

    SUser user = createAdmin("user");
    makeLoggedIn(user);

    VersionedSettingsConfig config = myRequest.getVersionedSettingsConfig(myProject.getExternalId(), fields);
    assertEquals(config.getSynchronizationMode(), configFields.isSyncModePresent ? VersionedSettingsConfig.SynchronizationMode.enabled : null);
    assertEquals(config.getVcsRootId(), configFields.isVcsRootPresent ? "vcs_root_id" : null);
    assertEquals(config.getFormat(), configFields.isFormatPresent ? "xml" : null);
    assertEquals(config.getAllowUIEditing(), configFields.isAllowUIEditingPresent ? true : null);
    assertEquals(config.getStoreSecureValuesOutsideVcs(), configFields.isStoreSecureValuesPresent ? true : null);
    assertEquals(config.getBuildSettingsMode(), configFields.isBuildModePresent ? VersionedSettingsConfig.BuildSettingsMode.useFromVCS : null);
    assertEquals(config.getShowSettingsChanges(), configFields.isShowChangesPresent ? true : null);
  }


  private static class ConfigFields {
    boolean isSyncModePresent;
    boolean isVcsRootPresent;
    boolean isShowChangesPresent;
    boolean isBuildModePresent;
    boolean isFormatPresent;
    boolean isAllowUIEditingPresent;
    boolean isStoreSecureValuesPresent;

    public ConfigFields(boolean isSyncModePresent,
                        boolean isVcsRootPresent,
                        boolean isShowChangesPresent,
                        boolean isBuildModePresent,
                        boolean isFormatPresent,
                        boolean isAllowUIEditingPresent,
                        boolean isStoreSecureValuesPresent) {
      this.isSyncModePresent = isSyncModePresent;
      this.isVcsRootPresent = isVcsRootPresent;
      this.isShowChangesPresent = isShowChangesPresent;
      this.isBuildModePresent = isBuildModePresent;
      this.isFormatPresent = isFormatPresent;
      this.isAllowUIEditingPresent = isAllowUIEditingPresent;
      this.isStoreSecureValuesPresent = isStoreSecureValuesPresent;
    }
  }

}
